#!/usr/bin/env python3
"""Merge new assets into the existing 1.12 pack without replacing user assets."""
from pathlib import Path
from zipfile import ZipFile,ZipInfo,ZIP_DEFLATED
from PIL import Image, ImageDraw
import hashlib,io,json,random
ROOT=Path(__file__).resolve().parents[1]
base=ROOT/'dist/Z_Server_Pack_1.12.zip'
output=ROOT/'dist/Z_Server_Pack_1.13.zip'
assets={}
with ZipFile(base) as z:
 for n in z.namelist():
  if not n.endswith('/'): assets[n]=z.read(n)
def add(path,obj):assets[path]=json.dumps(obj,ensure_ascii=False,separators=(',',':')).encode()
def png(path,img):
 b=io.BytesIO();img.save(b,'PNG');assets[path]=b.getvalue()
front=Image.open(ROOT/'tools/expansion-assets/stationary_voidometer_front.png').convert('RGBA')
png('assets/smplore/textures/block/stationary_voidometer_front.png',front)
# Existing machine side/roof textures give the new model a consistent family appearance.
textures={'front':'smplore:block/stationary_voidometer_front','side':'smplore:block/void_alarm_side','top':'smplore:block/void_alarm_top','bottom':'smplore:block/void_alarm_bottom','particle':'smplore:block/void_alarm_side'}
faces={side:{'texture':'#'+tex} for side,tex in [('north','front'),('south','side'),('east','side'),('west','side'),('up','top'),('down','bottom')]}
add('assets/smplore/models/block/stationary_voidometer.json',{'textures':textures,'elements':[{'from':[0,0,0],'to':[16,16,16],'faces':faces}]})
add('assets/smplore/items/stationary_voidometer.json',{'model':{'type':'minecraft:model','model':'smplore:block/stationary_voidometer'}})
for suffix in ['', '_lit']:
 add('assets/z_lor/items/world_stationary_voidometer'+suffix+'.json',{'model':{'type':'minecraft:model','model':'z_lor:block/world_stationary_voidometer'}})
add('assets/z_lor/models/block/world_stationary_voidometer.json',{'parent':'smplore:block/stationary_voidometer','display':{'fixed':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1,1,1]}}})
# Deliberately model the fireball in 3D, not as an item/generated flat sprite.
random.seed(73)
for name,colors in {'core':[(255,249,84),(255,211,20),(255,166,0)],'fire':[(255,175,0),(255,112,0),(236,50,0)],'ember':[(245,70,0),(197,24,2),(255,111,0)]}.items():
 im=Image.new('RGBA',(16,16));px=im.load()
 for y in range(16):
  for x in range(16):
   d=((x-7.5)**2+(y-7.5)**2)**.5; idx=0 if d<4 else 1 if d<7 else 2
   if random.random()<.13:idx=min(2,idx+1)
   px[x,y]=(*colors[idx],255)
 png('assets/zbook/textures/item/flame_'+name+'.png',im)
def cube(a,b,texture):return {'from':a,'to':b,'faces':{f:{'texture':'#'+texture} for f in ['north','south','east','west','up','down']}}
elements=[cube([4,4,4],[12,12,12],'core'),cube([3,5,3],[13,11,13],'fire'),cube([5,3,5],[11,13,11],'fire'),cube([6,13,6],[10,16,10],'ember'),cube([1,6,6],[4,10,10],'ember'),cube([12,6,6],[15,10,10],'ember'),cube([6,6,1],[10,10,4],'ember'),cube([6,6,12],[10,10,15],'ember')]
add('assets/zbook/models/item/redus_flimano_projectile.json',{'textures':{'core':'zbook:item/flame_core','fire':'zbook:item/flame_fire','ember':'zbook:item/flame_ember','particle':'zbook:item/flame_fire'},'elements':elements,'display':{'ground':{'rotation':[0,0,0],'translation':[0,3,0],'scale':[1,1,1]},'fixed':{'rotation':[0,0,0],'scale':[1,1,1]},'gui':{'rotation':[30,225,0],'scale':[.8,.8,.8]}}})
add('assets/zbook/items/redus_flimano_projectile.json',{'model':{'type':'minecraft:model','model':'zbook:item/redus_flimano_projectile'}})
# A distinct spell page with a fire seal.
im=Image.new('RGBA',(16,16));d=ImageDraw.Draw(im);d.polygon([(2,1),(11,1),(14,4),(14,14),(2,14)],fill=(233,211,158,255),outline=(112,71,43,255));d.polygon([(11,1),(14,4),(11,4)],fill=(191,159,111,255));d.polygon([(5,11),(4,8),(7,9),(6,5),(9,7),(11,5),(11,9),(10,11),(7,12)],fill=(238,71,5,255));d.polygon([(6,10),(7,7),(8,9),(10,8),(9,11)],fill=(255,204,30,255))
png('assets/zbook/textures/item/redus_flimano.png',im)
add('assets/zbook/models/item/redus_flimano.json',{'parent':'minecraft:item/generated','textures':{'layer0':'zbook:item/redus_flimano'}})
add('assets/zbook/items/redus_flimano.json',{'model':{'type':'minecraft:model','model':'zbook:item/redus_flimano'}})
# The requested custom disc drawing is left to the user; this delegates to a vanilla texture.
add('assets/zplast/items/record.json',{'model':{'type':'minecraft:model','model':'zplast:item/record'}})
add('assets/zplast/models/item/record.json',{'parent':'minecraft:item/generated','textures':{'layer0':'minecraft:item/music_disc_13'}})
meta=json.loads(assets['pack.mcmeta']);meta['pack']['description']='Z_Server Pack 1.13 • Z_Book, Z_Lor, Z_Plast';add('pack.mcmeta',meta)
with ZipFile(output,'w',compression=ZIP_DEFLATED,compresslevel=9) as z:
 for path,data in sorted(assets.items()):
  info=ZipInfo(path,(2020,1,1,0,0,0));info.compress_type=ZIP_DEFLATED;z.writestr(info,data)
print(output,hashlib.sha1(output.read_bytes()).hexdigest(),len(assets))
