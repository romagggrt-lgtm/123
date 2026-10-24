#!/usr/bin/env python3
"""Package the tested Z_Gold module without rebuilding or editing other plugins."""
from pathlib import Path
import hashlib, json, shutil, xml.etree.ElementTree as E, zipfile

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / 'dist'


def archive(path, files):
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED) as out:
        for name, source in sorted(files.items()):
            entry = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0)); entry.compress_type = zipfile.ZIP_DEFLATED
            out.writestr(entry, source.read_bytes())
    with zipfile.ZipFile(path) as check: assert check.testzip() is None


def main():
    suites = [E.parse(p).getroot() for p in (ROOT / 'gold/target/surefire-reports').glob('TEST-*.xml')]
    totals = {key: sum(int(s.get(key, 0)) for s in suites) for key in ('tests', 'failures', 'errors', 'skipped')}
    assert totals['tests'] == 27 and not any(totals[key] for key in ('failures', 'errors', 'skipped')), totals
    report = {'plugin': 'Z_Gold', 'version': '1.0.0', 'java': '21', 'paper_api': '1.21.11-R0.1-SNAPSHOT',
              'junit': totals, 'test_cases': [t.get('name') for s in suites for t in s.findall('testcase')],
              'minecraft_client_tested': False, 'paper_runtime_tested': False,
              'paper_runtime_unavailable': 'Official server binary host blocked by network proxy: 403 Forbidden',
              'resource_pack_report': 'Z_Server_Pack_1.05_Gold.report.json'}
    (DIST / 'Z_Gold-tests_1.0.0.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    shutil.copyfile(ROOT / 'gold/README.md', DIST / 'Z_Gold_1.0.0.README.md')
    shutil.copyfile(ROOT / 'gold/TESTING.md', DIST / 'Z_Gold_1.0.0.TESTING.md')
    source = {}
    for path in (ROOT / 'gold').rglob('*'):
        if path.is_file() and 'target' not in path.relative_to(ROOT / 'gold').parts:
            source[path.relative_to(ROOT).as_posix()] = path
    for name in ('build_gold_pack.py', 'preview_gold.py', 'preview_verity.py', 'merge_smplore_pack.py', 'build_gold.sh', 'package_gold.py'):
        source['tools/' + name] = ROOT / 'tools' / name
    for name in ('Z_Server_Pack_1.05.zip', 'Z_Server_Pack_1.05_Gold.zip', 'Z_Gold-tests_1.0.0.json', 'Z_Server_Pack_1.05_Gold.report.json'):
        source['dist/' + name] = DIST / name
    source['LICENSE'] = ROOT / 'LICENSE'
    archive(DIST / 'Z_Gold-Source_1.0.0.zip', source)
    install_names = ['Z_Gold.jar', 'Z_Server_Pack_1.05_Gold.zip', 'Z_Gold_1.0.0.README.md', 'Z_Gold_1.0.0.TESTING.md',
                     'Z_Gold-tests_1.0.0.json', 'Z_Server_Pack_1.05_Gold.zip.sha1', 'Z_Gold-preview_1.0.0.png']
    archive(DIST / 'Z_Gold_1.0.0-install.zip', {name: DIST / name for name in install_names})
    names = ['Z_Gold.jar', 'Z_Gold_1.0.0.jar', 'Z_Server_Pack_1.05_Gold.zip', 'Z_Gold-Source_1.0.0.zip', 'Z_Gold_1.0.0-install.zip']
    (DIST / 'Z_Gold-SHA256SUMS').write_text(''.join(hashlib.sha256((DIST / name).read_bytes()).hexdigest() + '  ' + name + '\n' for name in names))
    for name in names: print(name, (DIST / name).stat().st_size)


if __name__ == '__main__': main()
