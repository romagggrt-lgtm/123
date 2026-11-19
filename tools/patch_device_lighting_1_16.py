#!/usr/bin/env python3
"""Keep the existing pack and illuminate the world-only device models."""
from pathlib import Path
from zipfile import ZipFile,ZipInfo,ZIP_DEFLATED
import hashlib,json
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'dist/Z_Server_Pack_1.15.zip';OUT=ROOT/'dist/Z_Server_Pack_1.16.zip'
KINDS=('magnetite_core','void_absorber','void_alarm','void_siren')
def encode(value):return (json.dumps(value,ensure_ascii=False,separators=(',',':'))+'\n').encode()
def cube():
 # The parent minecraft:block/cube contains this exact cube and uses the
 # same inferred UV coordinates. Preserve its six face textures and culling.
 return [{'from':[0,0,0],'to':[16,16,16],'faces':{side:{'texture':'#'+side,'cullface':side} for side in ('north','south','east','west','up','down')}}]
def resolved_elements(entries,model,visited=None):
 visited=visited or set()
 if model in visited:raise ValueError('model parent cycle: '+model)
 visited.add(model)
 if model=='minecraft:block/cube':return cube()
 namespace,path=model.split(':',1);d=json.loads(entries['assets/'+namespace+'/models/'+path+'.json'])
 if 'elements' in d:return d['elements']
 if 'parent' not in d:raise ValueError('no elements: '+model)
 return resolved_elements(entries,d['parent'],visited)
def main():
 with ZipFile(BASE) as z:
  assert z.testzip() is None
  assert len(z.namelist())==len(set(z.namelist()))
  entries={i.filename:z.read(i.filename) for i in z.infolist() if not i.is_dir()}
 before=entries.copy();changed=[]
 for kind in KINDS:
  for suffix in ('','_lit'):
   path=f'assets/z_lor/models/block/world_{kind}{suffix}.json';d=json.loads(entries[path]);old=resolved_elements(entries,d['parent'])
   d['elements']=json.loads(json.dumps(old))
   for element in d['elements']:element['light_emission']=15
   entries[path]=encode(d);changed.append(path)
   # This patch only adds per-element light emission, keeping the exact geometry,
   # cull faces, UVs, rotations and indicator layers of the original model.
   for a,b in zip(old,d['elements']):
    a=dict(a);b=dict(b);a.pop('light_emission',None);b.pop('light_emission',None);assert a==b,path
 metadata=json.loads(entries['pack.mcmeta']);metadata['pack']['description']='Z_Server Pack 1.16 • исправлена яркость моделей устройств';entries['pack.mcmeta']=encode(metadata)
 assert set(before)==set(entries) and {n for n in entries if entries[n]!=before[n]}==set(changed)|{'pack.mcmeta'}
 with ZipFile(OUT,'w',ZIP_DEFLATED,compresslevel=9) as z:
  for name,content in sorted(entries.items()):
   info=ZipInfo(name,(2020,1,1,0,0,0));info.compress_type=ZIP_DEFLATED;z.writestr(info,content)
 with ZipFile(OUT) as z:
  assert z.testzip() is None and len(z.namelist())==len(entries)
  for path in changed:
   d=json.loads(z.read(path));assert d['elements'] and all(e.get('light_emission')==15 for e in d['elements'])
  assert json.loads(z.read('assets/smplore/models/block/stationary_voidometer.json'))['elements'][0]['light_emission']==15
 result={'base':BASE.name,'output':OUT.name,'sha1':hashlib.sha1(OUT.read_bytes()).hexdigest(),'changed_models':changed,'unchanged_files':len(entries)-len(changed)-1,'light_emission':15}
 OUT.with_suffix('.report.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n');print(json.dumps(result,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
