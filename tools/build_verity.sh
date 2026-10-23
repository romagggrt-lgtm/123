#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -d /workspace/tools/jdk-21.0.12.1 ]]; then
  export JAVA_HOME=/workspace/tools/jdk-21.0.12.1
  export PATH="$JAVA_HOME/bin:$PATH"
fi
maven=$(command -v mvn || true)
if [[ -z "$maven" ]]; then maven=/workspace/tools/apache-maven-3.9.11/bin/mvn; fi
settings=$(mktemp)
trap 'rm -f "$settings"' EXIT
python3 - "$settings" <<'PY'
import os,sys,urllib.parse,xml.etree.ElementTree as E
root=E.Element('settings')
p=urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY',os.environ.get('https_proxy','')))
if p.hostname:
    proxy=E.SubElement(E.SubElement(root,'proxies'),'proxy')
    for k,v in [('id','cloud'),('active','true'),('protocol','http'),('host',p.hostname),('port',str(p.port or 80))]: E.SubElement(proxy,k).text=v
    if p.username: E.SubElement(proxy,'username').text=urllib.parse.unquote(p.username)
    if p.password: E.SubElement(proxy,'password').text=urllib.parse.unquote(p.password)
with open(sys.argv[1],'wb') as f: f.write(E.tostring(root))
PY
if [[ -f /etc/ssl/certs/java/cacerts ]]; then export MAVEN_OPTS="${MAVEN_OPTS:-} -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"; fi
"$maven" -B -s "$settings" -Dmaven.repo.local="${SHAH_MAVEN_CACHE:-/workspace/.m2/repository}" -f verity/pom.xml package
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as E
reports=list(Path('verity/target/surefire-reports').glob('TEST-*.xml'))
assert reports, 'No test reports'
totals={k:0 for k in ('tests','failures','errors','skipped')}
for report in reports:
    r=E.parse(report).getroot()
    for k in totals: totals[k]+=int(r.get(k,'0'))
assert totals['tests']>0 and not any(totals[k] for k in ('failures','errors','skipped')), totals
print('Verified JUnit results (no silently skipped MockBukkit checks):',totals)
PY
python3 tools/build_pack.py
python3 tools/check_pack.py
python3 tools/preview_verity.py
cp verity/target/Z_Verity_1.0.2.jar dist/Z_Verity_1.0.2.jar
sha256sum dist/Z_Verity_1.0.2.jar dist/Z_Server_Pack_1.05.zip dist/Verity-preview_1.0.2.png > dist/Verity-SHA256SUMS
