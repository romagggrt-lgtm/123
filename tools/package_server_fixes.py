#!/usr/bin/env python3
"""Package the tested server fixes. User logs and existing server data are never read here."""
from pathlib import Path
import hashlib,json,shutil,subprocess,xml.etree.ElementTree as E,zipfile
from package_gold import archive

ROOT=Path(__file__).resolve().parents[1]
DIST=ROOT/'dist'
RELEASE='Server-Plugin-Fixes_2026-10-25'

def tests(module,minimum):
    suites=[E.parse(p).getroot() for p in (ROOT/module/'target/surefire-reports').glob('TEST-*.xml')]
    totals={k:sum(int(s.get(k,0)) for s in suites) for k in ('tests','failures','errors','skipped')}
    assert totals['tests']>=minimum and not any(totals[k] for k in ('failures','errors','skipped')),totals
    return {'junit':totals,'test_cases':[t.get('name') for s in suites for t in s.findall('testcase')]}
def main():
    gold=tests('gold',48);cache=tests('inventorycache',10)
    for module,jar,version in [('gold','Z_Gold','1.0.2'),('inventorycache','InventoryCache','1.0.1')]:
        source=ROOT/module/'target'/f'{jar}.jar'
        with zipfile.ZipFile(source) as z:
            assert z.testzip() is None and f"version: '{version}'" in z.read('plugin.yml').decode()
            main='dev/zgold/ZGoldPlugin.class' if module=='gold' else 'dev/inventorycache/InventoryCachePlugin.class'
            assert int.from_bytes(z.read(main)[6:8],'big')==65
        shutil.copyfile(source,DIST/f'{jar}_{version}.jar');shutil.copyfile(source,DIST/f'{jar}.jar')
    javap=Path('/workspace/tools/jdk-21.0.12.1/bin/javap')
    if not javap.exists():javap=Path(shutil.which('javap'))
    disassembly=subprocess.check_output([str(javap),'-classpath',str(DIST/'InventoryCache_1.0.1.jar'),'-c','dev.inventorycache.InventoryCommand'],text=True)
    assert 'getCommands:()Ljava/util/Collection;' in disassembly
    assert 'getCommands:()Ljava/util/Set;' not in disassembly
    report={'release':RELEASE,'minecraft':'1.21.11','java':21,
            'Z_Gold_1.0.2':gold,'InventoryCache_1.0.1':cache,
            'evidence_counts':{'inventorycache_command_event_errors':26,'gold_watchdog_reports':8},
            'inventorycache_getCommands_descriptor':'()Ljava/util/Collection;',
            'resource_pack_changed':False,'other_plugin_jars_changed':False,
            'paper_runtime_tested':False,'user_log_included':False}
    (DIST/(RELEASE+'.report.json')).write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    cache_report={'plugin':'InventoryCache','version':'1.0.1','java':21,'paper_api':'1.21.11-R0.1-SNAPSHOT',**cache,
                  'original_linkage_error_reproduced':True,'new_descriptor':'()Ljava/util/Collection;','paper_runtime_tested':False}
    (DIST/'InventoryCache-tests_1.0.1.json').write_text(json.dumps(cache_report,ensure_ascii=False,indent=2)+'\n')
    shutil.copyfile(ROOT/'inventorycache/README.md',DIST/'InventoryCache_1.0.1.README.md')
    source={}
    for module in ('gold','inventorycache'):
        for p in (ROOT/module).rglob('*'):
            if p.is_file() and 'target' not in p.relative_to(ROOT/module).parts:source[p.relative_to(ROOT).as_posix()]=p
    source['LICENSE']=ROOT/'LICENSE';source['SERVER-FIXES-2026-10-25.md']=ROOT/'SERVER-FIXES-2026-10-25.md'
    for name in ('build_server_fixes.sh','package_server_fixes.py','package_gold.py'):
        source['tools/'+name]=ROOT/'tools'/name
    archive(DIST/(RELEASE+'-Source.zip'),source)
    archive(DIST/'InventoryCache-Source_1.0.1.zip',{n:p for n,p in source.items() if n.startswith('inventorycache/') or n=='LICENSE'})
    shutil.copyfile(ROOT/'SERVER-FIXES-2026-10-25.md',DIST/(RELEASE+'.README.md'))
    install={'plugins/Z_Gold_1.0.2.jar':DIST/'Z_Gold_1.0.2.jar',
             'plugins/InventoryCache_1.0.1.jar':DIST/'InventoryCache_1.0.1.jar',
             'README.md':ROOT/'SERVER-FIXES-2026-10-25.md',
             'Z_Gold-UPDATE.md':ROOT/'gold/UPDATE-1.0.2.md',
             'InventoryCache-README.md':ROOT/'inventorycache/README.md',
             RELEASE+'.report.json':DIST/(RELEASE+'.report.json'),
             RELEASE+'-Source.zip':DIST/(RELEASE+'-Source.zip')}
    archive(DIST/(RELEASE+'.zip'),install)
    names=['Z_Gold_1.0.2.jar','InventoryCache_1.0.1.jar',RELEASE+'.zip',RELEASE+'-Source.zip','InventoryCache-Source_1.0.1.zip']
    (DIST/(RELEASE+'.SHA256SUMS')).write_text(''.join(hashlib.sha256((DIST/n).read_bytes()).hexdigest()+'  '+n+'\n' for n in names))
    for name in names:print(name,(DIST/name).stat().st_size)
    print('Tests:',gold['junit'],cache['junit'])
if __name__=='__main__':main()
