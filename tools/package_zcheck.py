from pathlib import Path
from zipfile import ZipFile,ZipInfo,ZIP_DEFLATED
import hashlib,json,subprocess
root=Path(__file__).resolve().parents[1];p=root/'check';dist=root/'dist';r=p/'live-qa';result=json.loads((r/'result.json').read_text());assert result['status']=='PASS'
jar=(r/'tested-plugin.jar').read_bytes();assert hashlib.sha256(jar).hexdigest()==result['plugin_sha256']
evidence=subprocess.check_output(['git','log','-1','--format=%H','--','check/live-qa/result.json'],cwd=root,text=True).strip();parent=subprocess.check_output(['git','rev-parse',evidence+'^'],cwd=root,text=True).strip()
assert subprocess.run(['git','diff','--quiet',parent,'--','check/src/main','check/pom.xml'],cwd=root).returncode==0
with ZipFile(r/'tested-plugin.jar') as z:assert z.testzip() is None and b"version: '1.0.0'" in z.read('plugin.yml')
def archive(path,files):
 with ZipFile(path,'w',ZIP_DEFLATED) as z:
  for n,b in sorted(files.items()):
   i=ZipInfo(n,(2026,1,1,0,0,0));i.compress_type=ZIP_DEFLATED;z.writestr(i,b)
source={}
for f in p.rglob('*'):
 if f.is_file() and 'target' not in f.parts and 'live-qa' not in f.parts:source[str(f.relative_to(root))]=f.read_bytes()
for f in ['tools/zcheck_bot.js','tools/zcheck_smoke.py','tools/package_zcheck.py','.github/workflows/zcheck-paper.yml']:source[f]=(root/f).read_bytes()
dist.mkdir(exist_ok=True);(dist/'Z_Check-1.0.0.jar').write_bytes(jar);archive(dist/'Z_Check-1.0.0-sources.zip',source)
install={'plugins/Z_Check-1.0.0.jar':jar,'README_RU.md':(p/'README_RU.md').read_bytes(),'config.example.yml':(p/'src/main/resources/config.yml').read_bytes(),'source/Z_Check-1.0.0-sources.zip':(dist/'Z_Check-1.0.0-sources.zip').read_bytes()}
for f in r.rglob('*'):
 if f.is_file() and f.name!='tested-plugin.jar':install['verification/'+str(f.relative_to(r))]=f.read_bytes()
archive(dist/'Z_Check-1.0.0-install.zip',install)
for n in ['Z_Check-1.0.0.jar','Z_Check-1.0.0-sources.zip','Z_Check-1.0.0-install.zip']:
 data=(dist/n).read_bytes();(dist/(n+'.sha256')).write_text(hashlib.sha256(data).hexdigest()+'\n');print(n,len(data),hashlib.sha256(data).hexdigest())
