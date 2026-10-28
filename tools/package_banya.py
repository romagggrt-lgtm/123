#!/usr/bin/env python3
"""Package the exact JAR accepted by the disposable native Paper run."""
from pathlib import Path
from zipfile import ZipFile,ZipInfo,ZIP_DEFLATED
import hashlib,json,shutil,struct
ROOT=Path(__file__).resolve().parents[1];DIST=ROOT/'dist';PROJECT=ROOT/'banya'
def archive(path,data):
 with ZipFile(path,'w',ZIP_DEFLATED) as z:
  for name,body in sorted(data.items()):
   entry=ZipInfo(name,(2026,1,1,0,0,0));entry.compress_type=ZIP_DEFLATED;z.writestr(entry,body)
def main():
 native=json.loads((PROJECT/'live-qa/latest.json').read_text());assert native['status']=='PASS','Native acceptance must pass first'
 jar=PROJECT/'live-qa/tested-plugin.jar';assert jar.is_file(),'Use the exact native-tested JAR'
 assert hashlib.sha256(jar.read_bytes()).hexdigest()==native['plugin_sha256']
 with ZipFile(jar) as z:
  assert z.testzip() is None;assert 'plugin.yml' in z.namelist()
  for name in z.namelist():
   if name.endswith('.class'):assert struct.unpack('>H',z.read(name)[6:8])[0]==65
   assert not name.startswith(('org/junit/','org/mockbukkit/','dev/zbanya/qa/'))
 shutil.copyfile(jar,DIST/'Z_Banya.jar');shutil.copyfile(jar,DIST/'Z_Banya_1.0.0.jar')
 pack=DIST/'Z_Server_Pack_1.09.zip';packsha=hashlib.sha1(pack.read_bytes()).hexdigest();assert native['pack_sha1']==packsha
 properties=(PROJECT/'server.properties.txt').read_text();assert 'resource-pack-sha1='+packsha in properties
 source={}
 for path in PROJECT.rglob('*'):
  if path.is_file() and 'target' not in path.parts and 'live-qa' not in path.parts and '__pycache__' not in path.parts:source[str(path.relative_to(ROOT))]=path.read_bytes()
 for name in ['build_banya_pack.py','test_banya_pack.py','merge_smplore_pack.py','banya_live_smoke.py','banya_live_bot.js','banya_vanilla_client.py','package_banya.py']:source['tools/'+name]=(ROOT/'tools'/name).read_bytes()
 source['dist/Z_Server_Pack_1.08.zip']=(DIST/'Z_Server_Pack_1.08.zip').read_bytes()
 with ZipFile(DIST/'Z_Banya_ResourcePack.zip') as z:
  for name in z.namelist():source['resourcepack/'+name]=z.read(name)
 archive(DIST/'Z_Banya_1.0.0_sources.zip',source)
 for src,dst in [('README_RU.md','Z_Banya_1.0.0_README_RU.md'),('TESTING_RU.md','Z_Banya_1.0.0_TESTING_RU.md'),('server.properties.txt','Z_Banya-server.properties_1.0.0.txt')]:shutil.copyfile(PROJECT/src,DIST/dst)
 install={'plugins/Z_Banya.jar':jar.read_bytes(),'resourcepack/Z_Server_Pack_1.09.zip':pack.read_bytes(),'optional/Z_Banya_ResourcePack.zip':(DIST/'Z_Banya_ResourcePack.zip').read_bytes(),'source/Z_Banya_1.0.0_sources.zip':(DIST/'Z_Banya_1.0.0_sources.zip').read_bytes(),'README_RU.md':(PROJECT/'README_RU.md').read_bytes(),'TESTING_RU.md':(PROJECT/'TESTING_RU.md').read_bytes(),'server.properties.txt':(PROJECT/'server.properties.txt').read_bytes(),'config.example.yml':(PROJECT/'src/main/resources/config.yml').read_bytes()}
 for path in (PROJECT/'live-qa').iterdir():
  if path.is_file() and path.name not in ['tested-plugin.jar']:install['verification/'+path.name]=path.read_bytes()
 archive(DIST/'Z_Banya_1.0.0_install.zip',install)
 names=['Z_Banya.jar','Z_Banya_1.0.0.jar','Z_Server_Pack_1.09.zip','Z_Banya_ResourcePack.zip','Z_Banya_1.0.0_sources.zip','Z_Banya_1.0.0_install.zip']
 hashes={name:{'bytes':(DIST/name).stat().st_size,'sha256':hashlib.sha256((DIST/name).read_bytes()).hexdigest(),'sha1':hashlib.sha1((DIST/name).read_bytes()).hexdigest()} for name in names}
 for name,digest in hashes.items():(DIST/(name+'.sha256')).write_text(digest['sha256']+'\n')
 report={'plugin':'Z_Banya','version':'1.0.0','native':native,'food_name':'Вкусняшка Тома','unit_tests':17,'resource_pack_tests':5,'assets':json.loads((DIST/'Z_Server_Pack_1.09.report.json').read_text()),'files':hashes}
 (DIST/'Z_Banya_1.0.0.report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(json.dumps(hashes,indent=2))
if __name__=='__main__':main()
