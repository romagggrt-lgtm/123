#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -d /workspace/tools/jdk-21.0.12.1 ]]; then
  export JAVA_HOME=/workspace/tools/jdk-21.0.12.1
  export PATH="$JAVA_HOME/bin:$PATH"
fi
maven=$(command -v mvn || true)
if [[ -z "$maven" ]]; then maven=/workspace/tools/apache-maven-3.9.11/bin/mvn; fi
python3 tools/build_gold_pack.py
maven_args=()
if [[ "${ZGOLD_OFFLINE:-0}" == 1 ]]; then maven_args+=(-o); fi
"$maven" -B "${maven_args[@]}" -Dmaven.repo.local="${ZGOLD_MAVEN_CACHE:-/workspace/.m2/repository}" -f gold/pom.xml package
python3 - <<'PY'
from pathlib import Path
import json, xml.etree.ElementTree as E
reports=list(Path('gold/target/surefire-reports').glob('TEST-*.xml'))
assert reports, 'No test reports'
totals={k:0 for k in ('tests','failures','errors','skipped')}
for report in reports:
    r=E.parse(report).getroot()
    for k in totals: totals[k]+=int(r.get(k,'0'))
assert totals['tests']>0 and not any(totals[k] for k in ('failures','errors','skipped')), totals
Path('dist/Z_Gold-tests_1.0.0.json').write_text(json.dumps({'junit':totals,'minecraft_client_tested':False},indent=2)+'\n')
print('Verified tests, including no silently skipped MockBukkit checks:',totals)
PY
cp gold/target/Z_Gold.jar dist/Z_Gold.jar
cp gold/target/Z_Gold.jar dist/Z_Gold_1.0.0.jar
sha256sum dist/Z_Gold.jar dist/Z_Gold_1.0.0.jar dist/Z_Server_Pack_1.05_Gold.zip > dist/Z_Gold-SHA256SUMS
