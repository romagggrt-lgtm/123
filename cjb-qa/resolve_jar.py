#!/usr/bin/env python3
"""Find the public Modrinth artifact identical to the uploaded JAR, without publishing the upload."""
from pathlib import Path
import hashlib,json,urllib.request
sha1='c8d3268124dc9e45af58998e7e3c0c7f938801b6'
sha256='7bff0566996bdc809752a41379b4cd8caff1bf26047bfb355ea9691d2fe59838'
def read(url):
 req=urllib.request.Request(url,headers={'User-Agent':'MinecraftCompatibilityTest/1.0 (Paper compatibility verification)'})
 with urllib.request.urlopen(req,timeout=90) as r:return r.read()
root=Path('cjb-qa/target');root.mkdir(parents=True,exist_ok=True)
project=json.loads(read('https://api.modrinth.com/v2/project/bs-customjukebox'))
version=json.loads(read('https://api.modrinth.com/v2/version_file/'+sha1+'?algorithm=sha1'))
assert version['project_id']==project['id'],'The SHA-1 match is not part of the official CustomJukebox project'
files=[f for f in version['files'] if f.get('hashes',{}).get('sha1')==sha1]
assert len(files)==1,'Unable to find the exact uploaded file in official version metadata'
data=read(files[0]['url']);digest=hashlib.sha256(data).hexdigest();assert digest==sha256,'Public JAR differs from the uploaded file'
(root/'CustomJukebox-3.7.1.jar').write_bytes(data)
(root/'official-match.json').write_text(json.dumps({'jar_sha256':digest,'jar_sha1':sha1,'filename':files[0]['filename'],'version_number':version.get('version_number'),'game_versions':version.get('game_versions',[]),'loaders':version.get('loaders',[]),'source':'Official Modrinth artifact, SHA-256 matches the user upload'},ensure_ascii=False,indent=2))
print((root/'official-match.json').read_text())
