#!/usr/bin/env python3
"""Verify and package the exact tested SimpleClans patch; no server logs/data."""
from pathlib import Path
import hashlib, json, shutil, sys, xml.etree.ElementTree as E, zipfile
from package_gold import archive

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / 'dist'
MODULE = ROOT / 'simpleclans-fix'
VERSION = '2.19.2-05e7abc-locale-fix.1'
RELEASE = 'SimpleClans_2.19.2-locale-fix.1'
ACF = 'net/sacredlabyrinth/phaed/simpleclans/acf/'
MANAGER = ACF + 'BukkitCommandManager.class'

def sha(data): return hashlib.sha256(data).hexdigest()

def class_members(data):
    """Extract raw JVM field/method_info records independently of ASM."""
    assert data[:4] == b'\xca\xfe\xba\xbe'
    pos = 8
    def number(size):
        nonlocal pos
        n = int.from_bytes(data[pos:pos+size], 'big'); pos += size; return n
    constants = {}
    count = number(2); index = 1
    while index < count:
        tag = number(1)
        if tag == 1:
            length = number(2); constants[index] = data[pos:pos+length].decode('utf-8', errors='replace'); pos += length
        elif tag in (3,4,9,10,11,12,17,18): pos += 4
        elif tag in (5,6): pos += 8; index += 1
        elif tag in (7,8,16,19,20): pos += 2
        elif tag == 15: pos += 3
        else: raise AssertionError(f'Unknown constant tag {tag}')
        index += 1
    header_start = pos; pos += 6
    interfaces = number(2); pos += 2*interfaces
    header = data[header_start:pos]
    def members():
        nonlocal pos
        result = {}
        for _ in range(number(2)):
            start = pos
            access = number(2); name = constants[number(2)]; descriptor = constants[number(2)]
            for _ in range(number(2)):
                number(2); length = number(4); pos += length
            result[(name,descriptor)] = data[start:pos]
        return result
    fields = members(); methods = members()
    return header, fields, methods

def main():
    original = Path(sys.argv[1])
    target = MODULE / 'target' / ('SimpleClans_' + VERSION + '.jar')
    suites = [E.parse(p).getroot() for p in (MODULE/'target/surefire-reports').glob('TEST-*.xml')]
    totals = {k:sum(int(s.get(k,0)) for s in suites) for k in ('tests','failures','errors','skipped')}
    assert totals['tests'] >= 11 and not any(totals[k] for k in ('failures','errors','skipped')), totals
    preserved = 0
    with zipfile.ZipFile(original) as before, zipfile.ZipFile(target) as after:
        assert after.testzip() is None
        existing = {n for n in before.namelist() if not n.endswith('/')}
        added = set(after.namelist()) - existing
        assert added == {ACF+'ClientLocale.class', *('META-INF/server-fixes/'+n for n in ('SimpleClans-GPL-3.0.txt','ACF-MIT.txt','NOTICE.txt'))}, added
        changed = {n for n in existing if before.read(n) != after.read(n)}
        assert changed == {'plugin.yml',MANAGER}, changed
        preserved = len(existing) - len(changed)
        assert f"version: '{VERSION}'" in after.read('plugin.yml').decode()
        oldheader,oldfields,oldmethods = class_members(before.read(MANAGER))
        header,fields,methods = class_members(after.read(MANAGER))
        assert header == oldheader and fields == oldfields and methods.keys() == oldmethods.keys()
        changed_methods = [n for n in methods if methods[n] != oldmethods[n]]
        assert changed_methods == [('readPlayerLocale','(Lorg/bukkit/entity/Player;)V')], changed_methods
        assert int.from_bytes(after.read(ACF+'ClientLocale.class')[6:8],'big') == 65
    DIST.mkdir(exist_ok=True)
    shutil.copyfile(target,DIST/(RELEASE+'.jar'))
    shutil.copyfile(MODULE/'README.md',DIST/(RELEASE+'.README.md'))
    report = {'plugin':'SimpleClans','version':VERSION,'java':21,'paper_api':'1.21.11',
              'original_sha256':sha(original.read_bytes()),'patched_sha256':sha(target.read_bytes()),
              'junit':totals,'test_cases':[t.get('name') for s in suites for t in s.findall('testcase')],
              'original_locale_exception_reproduced':True,
              'unchanged_existing_jar_entries':preserved,
              'changed_existing_jar_entries':sorted(changed),
              'changed_existing_methods':changed_methods,'all_other_methods_and_fields_identical':True,
              'added_entries':sorted(added),'resource_pack_changed':False,
              'other_plugins_changed':False,'paper_runtime_tested':False,'user_log_included':False}
    report_path = DIST/(RELEASE+'.report.json')
    report_path.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    source = {p.relative_to(ROOT).as_posix():p for p in MODULE.rglob('*') if p.is_file() and 'target' not in p.relative_to(MODULE).parts}
    for name in ('build_simpleclans_fix.sh','package_simpleclans_fix.py','package_gold.py'):
        source['tools/'+name] = ROOT/'tools'/name
    source['tools/LICENSE'] = ROOT/'LICENSE'
    upstream = MODULE/'upstream/SimpleClans-05e7abc-Source.zip'
    assert upstream.is_file(), 'Full corresponding upstream source is required'
    with zipfile.ZipFile(upstream) as z: assert z.testzip() is None
    archive(DIST/(RELEASE+'-Source.zip'),source)
    archive(DIST/(RELEASE+'-install.zip'),{
        'plugins/'+RELEASE+'.jar':DIST/(RELEASE+'.jar'),
        'README.md':MODULE/'README.md',
        RELEASE+'.report.json':report_path,
        'LICENSE.txt':MODULE/'LICENSE',
    })
    names = [RELEASE+suffix for suffix in ('.jar','-Source.zip','-install.zip','.report.json')]
    (DIST/(RELEASE+'.SHA256SUMS')).write_text(''.join(sha((DIST/n).read_bytes())+'  '+n+'\n' for n in names))
    for n in names: print(n,(DIST/n).stat().st_size)
    print('Tests:',totals,'Preserved entries:',preserved,'Changed methods:',changed_methods)

if __name__ == '__main__': main()
