#!/usr/bin/env python3
"""Give the stationary voidometer model a minimum light level in Minecraft 1.21.11."""
from pathlib import Path
from zipfile import ZipFile,ZipInfo,ZIP_DEFLATED
import hashlib,json

ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'dist/Z_Server_Pack_1.13.zip'
OUTPUT=ROOT/'dist/Z_Server_Pack_1.14.zip'
MODEL='assets/smplore/models/block/stationary_voidometer.json'

def encode(obj):return (json.dumps(obj,ensure_ascii=False,separators=(',',':'))+'\n').encode('utf-8')

def main():
 with ZipFile(BASE) as z:
  assert z.testzip() is None
  names=z.namelist()
  if len(names)!=len(set(names)):raise ValueError('Duplicate entries in base pack')
  data={name:z.read(name) for name in names if not name.endswith('/')}
 before=data.copy();model=json.loads(data[MODEL]);elements=model['elements']
 if len(elements)!=1 or elements[0]['from']!=[0,0,0] or elements[0]['to']!=[16,16,16]:raise ValueError('Unexpected device geometry')
 # Vanilla model light emission raises the baked face light without changing
 # global shaders, world lighting, directional shading or the original texture.
 for element in elements:element['light_emission']=15
 data[MODEL]=encode(model)
 metadata=json.loads(data['pack.mcmeta']);metadata['pack']['description']='Z_Server Pack 1.14 • исправлено освещение стационарного войдометра';data['pack.mcmeta']=encode(metadata)
 changed=[k for k in before if before[k]!=data[k]]
 assert set(changed)=={'pack.mcmeta',MODEL} and len(before)==len(data),changed
 with ZipFile(OUTPUT,'w',ZIP_DEFLATED,compresslevel=9) as z:
  for name,content in sorted(data.items()):
   info=ZipInfo(name,(2020,1,1,0,0,0));info.compress_type=ZIP_DEFLATED;z.writestr(info,content)
 with ZipFile(OUTPUT) as z:
  assert z.testzip() is None
  assert len(z.namelist())==len(before)
  for name,content in data.items():assert z.read(name)==content,name
 result={'base':BASE.name,'output':OUTPUT.name,'base_sha1':hashlib.sha1(BASE.read_bytes()).hexdigest(),'sha1':hashlib.sha1(OUTPUT.read_bytes()).hexdigest(),'sha256':hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),'assets_preserved':len(data)-len(changed),'changed_entries':changed,'light_emission':15}
 OUTPUT.with_suffix('.report.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
 OUTPUT.with_suffix('.zip.sha1').write_text(result['sha1']+'\n')
 print(json.dumps(result,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
