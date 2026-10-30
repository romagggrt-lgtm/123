from pathlib import Path
from PIL import Image,ImageDraw
import json,zipfile,io,hashlib
root=Path(__file__).resolve().parents[1]
source=root/'dist/Z_Server_Pack_1.09.zip';output=root/'dist/Z_Server_Pack_1.10.zip'
icons=['players','teleport','mode','morph','kits','nick','vanish','banya','world','region','settings','back','home','close','next','search','refresh','summon','confirm','speed']
S=4
for name in icons:
 im=Image.new('RGBA',(32*S,32*S));d=ImageDraw.Draw(im); purple='#a984ff';light='#f0dfff';dark='#3a225d';edge='#714ab5'
 def rect(box,fill,outline=None,width=1):d.rectangle(tuple(v*S for v in box),fill=fill,outline=outline,width=width*S)
 def line(points,fill=light,width=2):d.line([(x*S,y*S) for x,y in points],fill=fill,width=width*S,joint='curve')
 def ellipse(box,fill,outline=None,width=1):d.ellipse(tuple(v*S for v in box),fill=fill,outline=outline,width=width*S)
 rect((2,2,29,29),'#1c142d',edge);rect((4,4,27,27),dark)
 if name in ('back','next'):
  x=9 if name=='back' else 23;v=1 if name=='back' else -1;line([(x+v*8,9),(x,16),(x+v*8,23)]);line([(x,16),(x+v*14,16)],purple)
 elif name in ('close','confirm'):
  line([(9,9),(23,23)] if name=='close' else [(8,16),(13,21),(24,10)],'#ff9ab7' if name=='close' else '#c3ffd9',3)
  if name=='close':line([(23,9),(9,23)],'#ff9ab7',3)
 elif name=='players':
  ellipse((12,6,20,14),light);ellipse((5,10,11,16),purple);ellipse((21,10,27,16),purple);d.rounded_rectangle((8*S,17*S,24*S,25*S),radius=4*S,fill=light);rect((4,19,7,24),purple);rect((25,19,28,24),purple)
 elif name=='home' or name=='banya':
  line([(6,15),(16,7),(26,15)],light,3);rect((9,15,23,25),purple);rect((14,19,18,25),dark)
  if name=='banya':rect((21,7,24,11),light)
 elif name in ('teleport','summon'):
  ellipse((7,7,25,25),None,purple,2);line([(16,7),(16,25)],light);line([(7,16),(25,16)],light);line([(12,11),(16,7),(20,11)],light)
 elif name=='mode':
  d.rounded_rectangle((6*S,10*S,26*S,22*S),radius=5*S,fill=light);line([(10,16),(15,16)],dark,2);line([(12,13),(12,19)],dark,2);ellipse((20,13,22,15),purple);ellipse((23,16,25,18),purple)
 elif name=='morph':
  rect((8,7,24,24),purple);rect((10,10,14,14),dark);rect((18,10,22,14),dark);rect((14,16,18,21),dark);rect((10,19,13,23),dark);rect((19,19,22,23),dark)
 elif name=='kits':
  rect((6,10,26,24),'#d2a16f',light);rect((6,10,26,16),'#e7bb82');rect((15,14,18,19),dark)
 elif name=='nick':
  d.polygon([(x*S,y*S) for x,y in [(7,8),(19,8),(25,14),(14,25),(7,18)]],fill='#ed9cbb');ellipse((17,10,20,13),dark);line([(11,18),(15,14)],light)
 elif name=='vanish':
  d.polygon([(x*S,y*S) for x,y in [(5,16),(10,11),(16,9),(22,11),(27,16),(22,21),(16,23),(10,21)]],fill=light);ellipse((12,12,20,20),purple);ellipse((15,14,18,18),dark)
 elif name=='world':ellipse((7,7,25,25),purple);line([(7,16),(25,16)],light);line([(16,7),(16,25)],light);line([(10,10),(22,22)],light,1)
 elif name=='region':
  line([(8,24),(24,8)],light,3);d.polygon([(7*S,8*S),(13*S,7*S),(18*S,12*S),(14*S,16*S)],fill=purple);rect((20,21,24,25),light)
 elif name=='settings':
  ellipse((8,8,24,24),purple);ellipse((13,13,19,19),dark)
  for x,y in [(14,5),(14,24),(5,14),(24,14)]:rect((x,y,x+3,y+3),light)
 elif name=='search':ellipse((8,7,20,19),None,light,2);line([(19,18),(25,24)],purple,3)
 elif name=='refresh':
  d.arc((7*S,7*S,25*S,25*S),20,300,fill=light,width=2*S);d.polygon([(23*S,7*S),(26*S,14*S),(19*S,12*S)],fill=purple)
 elif name=='speed':
  line([(9,7),(23,7),(17,14),(23,14),(11,25),(14,17),(8,17)],light,2)
 im.resize((32,32),Image.Resampling.LANCZOS).save(root/'ap/assets'/f'{name}.png')
with zipfile.ZipFile(source) as old,zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as out:
 existing=set(old.namelist())
 for info in old.infolist():
  b=old.read(info.filename)
  if info.filename=='pack.mcmeta':
   meta=json.loads(b);meta['pack']['description']='Z_Server_Pack 1.10 | Z_AP + существующие плагины | 1.21.11';b=json.dumps(meta,ensure_ascii=False,separators=(',',':')).encode()
  out.writestr(info,b)
 for name in icons:
  entries={f'assets/z_ap/textures/item/{name}.png':(root/'ap/assets'/f'{name}.png').read_bytes(),f'assets/z_ap/models/item/{name}.json':json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':'z_ap:item/'+name}},separators=(',',':')).encode(),f'assets/z_ap/items/{name}.json':json.dumps({'model':{'type':'minecraft:model','model':'z_ap:item/'+name}},separators=(',',':')).encode()}
  for path,data in entries.items():
   if path in existing:raise SystemExit('Resource collision: '+path)
   out.writestr(path,data)
with zipfile.ZipFile(source) as old,zipfile.ZipFile(output) as new:
 for name in old.namelist():
  if name!='pack.mcmeta' and old.read(name)!=new.read(name):raise AssertionError(name)
 print('Preserved',len(old.namelist())-1,'existing entries; added',len(icons),'icons')
for algo in ('sha1','sha256'):
 digest=hashlib.new(algo,output.read_bytes()).hexdigest();(output.parent/(output.name+'.'+algo)).write_text(digest+'\n');print(algo,digest)
