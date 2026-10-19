#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if ! command -v javac >/dev/null; then
  export JAVA_HOME=/workspace/tools/jdk-21.0.12.1
  export PATH="$JAVA_HOME/bin:$PATH"
fi
command -v javac >/dev/null
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
"$maven" -B -s "$settings" -Dmaven.repo.local="${SHAH_MAVEN_CACHE:-/workspace/.m2/repository}" package
bash tools/check_math.sh
python3 tools/build_pack.py
python3 tools/check_pack.py
cp target/Shahed.jar dist/Shahed.jar
