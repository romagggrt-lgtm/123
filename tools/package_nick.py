#!/usr/bin/env python3
"""Package only the JAR used in the successful Paper acceptance run."""
from pathlib import Path
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED
import hashlib,json,shutil,subprocess
root=Path(__file__).resolve().parents[1]; project=root/'nick'; dist=root/'dist'; report=project/'live-qa'
def archive(path,files):
 with ZipFile(path,'w',ZIP_DEFLATED) as z:
  for name,data in sorted(files.items()):
   i=ZipInfo(name,(2026,1,1,0,0,0));i.compress_type=ZIP_DEFLATED;z.writestr(i,data)
def main():
 result=json.loads((report/'result.json').read_text());compat=json.loads((report/'compat/result.json').read_text())
 assert result['status']=='PASS' and result['offline_reload_restart']=='PASS'
 assert compat['status']=='PASS' and compat['offline_reload_restart']=='PASS' and compat['plugin_sha256']==result['plugin_sha256']
 jar=(report/'tested-plugin.jar').read_bytes();assert hashlib.sha256(jar).hexdigest()==result['plugin_sha256']
 with ZipFile(report/'tested-plugin.jar') as z:
  assert z.testzip() is None and b"version: '1.0.0'" in z.read('plugin.yml')
  assert not any(n.startswith(('io/netty/','org/junit/','org/mockbukkit/')) for n in z.namelist())
 # The latest evidence commit was created by CI against its direct parent.
 evidence=subprocess.check_output(['git','log','-1','--format=%H','--','nick/live-qa/result.json'],cwd=root,text=True).strip()
 parent=subprocess.check_output(['git','rev-parse',evidence+'^'],cwd=root,text=True).strip()
 assert subprocess.run(['git','diff','--quiet',parent,'--','nick/src/main','nick/pom.xml'],cwd=root).returncode==0, 'Sources differ from the native-tested commit'
 dist.mkdir(exist_ok=True);(dist/'Z_Nick-1.0.0.jar').write_bytes(jar)
 source={}
 for p in project.rglob('*'):
  if p.is_file() and 'target' not in p.parts and 'live-qa' not in p.parts:source[str(p.relative_to(root))]=p.read_bytes()
 for p in ['tools/znick_smoke.py','tools/znick_smoke_bot.js','tools/package_nick.py','.github/workflows/znick-paper.yml']:source[p]=(root/p).read_bytes()
 archive(dist/'Z_Nick-1.0.0-sources.zip',source)
 install={'plugins/Z_Nick-1.0.0.jar':jar,'README_RU.md':(project/'README_RU.md').read_bytes(),'config.example.yml':(project/'src/main/resources/config.yml').read_bytes(),'source/Z_Nick-1.0.0-sources.zip':(dist/'Z_Nick-1.0.0-sources.zip').read_bytes()}
 for p in report.rglob('*'):
  if p.is_file() and p.name!='tested-plugin.jar':install['verification/'+str(p.relative_to(report))]=p.read_bytes()
 archive(dist/'Z_Nick-1.0.0-install.zip',install)
 for name in ['Z_Nick-1.0.0.jar','Z_Nick-1.0.0-sources.zip','Z_Nick-1.0.0-install.zip']:
  data=(dist/name).read_bytes();(dist/(name+'.sha256')).write_text(hashlib.sha256(data).hexdigest()+'\n');print(name,len(data),hashlib.sha256(data).hexdigest())
if __name__=='__main__':main()
