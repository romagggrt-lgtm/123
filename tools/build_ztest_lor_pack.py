#!/usr/bin/env python3
"""Extend the latest server pack without altering existing assets."""
from pathlib import Path
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED
from PIL import Image,ImageDraw
import io,json,hashlib
R=Path(__file__).resolve().parents[1]
base=R/'dist/Z_Server_Pack_1.16.zip';output=R/'dist/Z_Server_Pack_1.17.zip'
with ZipFile(base) as z: files={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
old=files.copy()
def put(path,value):
    assert path not in files,path
    files[path]=json.dumps(value,ensure_ascii=False,separators=(',',':')).encode() if isinstance(value,dict) else value
def png(im):
    b=io.BytesIO();im.save(b,format='PNG');return b.getvalue()
def item(name,model,ns):put('assets/'+ns+'/items/'+name+'.json',{'model':{'type':'minecraft:model','model':model}})
# Use the exact authored texture and original geometry as our source. Adapt the
# body/arms to Java block-model elements; mushroom variants are deliberately omitted.
put('assets/z_test/textures/item/soul.png',(R/'z_test/assets-src/ghost.png').read_bytes())
geo=json.loads((R/'z_test/assets-src/ghost.geo.json').read_text())['minecraft:geometry'][0]
elements=[]
for bone in geo['bones']:
    if bone['name'] not in ('glow_1','right_arm','left_arm','eyes'):continue
    for cube in bone.get('cubes',[]):
        x,y,z=cube['origin'];w,h,d=cube['size'];u,v=cube['uv']; uv=lambda a,b,c,d:[a/4,b/4,c/4,d/4]
        faces={'east':{'uv':uv(u,v+d,u+d,v+d+h),'texture':'#skin'},'north':{'uv':uv(u+d,v+d,u+d+w,v+d+h),'texture':'#skin'},'west':{'uv':uv(u+d+w,v+d,u+2*d+w,v+d+h),'texture':'#skin'},'south':{'uv':uv(u+2*d+w,v+d,u+2*d+2*w,v+d+h),'texture':'#skin'},'up':{'uv':uv(u+d,v,u+d+w,v+d),'texture':'#skin'},'down':{'uv':uv(u+d+w,v,u+d+2*w,v+d),'texture':'#skin'}}
        el={'from':[x+8,y+4,z+8],'to':[x+w+8,y+h+4,z+d+8],'faces':faces,'shade':False}
        if bone['name'] in ('right_arm','left_arm'):
            pivot=bone['pivot'];el['rotation']={'origin':[pivot[0]+8,pivot[1]+4,pivot[2]+8],'axis':'z','angle':-45 if bone['name']=='right_arm' else 45}
        elements.append(el)
put('assets/z_test/models/item/soul.json',{'ambientocclusion':False,'textures':{'skin':'z_test:item/soul'},'elements':elements,'display':{'fixed':{'scale':[1.4,1.4,1.4]}}})
item('soul','z_test:item/soul','z_test')
# A pale cyan/purple amethyst-like shard with visible faceted highlights.
im=Image.new('RGBA',(16,16));d=ImageDraw.Draw(im)
d.polygon([(2,14),(4,8),(7,4),(11,1),(14,2),(14,6),(11,10),(7,13),(4,15)],fill='#5b7caa')
d.polygon([(4,12),(6,7),(10,3),(12,2),(13,3),(12,6),(9,10),(6,13)],fill='#9bd7ed')
d.polygon([(7,7),(10,3),(12,2),(11,6),(9,8)],fill='#e1faff')
d.polygon([(4,10),(7,8),(9,9),(6,13),(3,14)],fill='#927ec3')
d.polygon([(11,7),(13,4),(14,3),(14,6),(11,10),(7,13),(9,10)],fill='#758ac8')
put('assets/z_test/textures/item/soul_shard.png',png(im))
put('assets/z_test/models/item/soul_shard.json',{'parent':'minecraft:item/generated','textures':{'layer0':'z_test:item/soul_shard'}});item('soul_shard','z_test:item/soul_shard','z_test')
# Hand/inventory sprite inspired by the golden crown with a red centre jewel.
im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
d.polygon([(4,23),(4,13),(6,13),(6,7),(8,7),(8,15),(12,16),(14,11),(18,11),(20,16),(24,15),(24,7),(26,7),(26,13),(28,13),(28,23)],fill='#996018')
d.polygon([(5,21),(5,14),(7,14),(7,8),(8,8),(8,16),(13,17),(15,12),(18,12),(20,17),(25,16),(25,8),(26,8),(26,14),(27,14),(27,21)],fill='#e2ae33')
d.rectangle((4,22,28,24),fill='#ad7020');d.rectangle((5,21,27,22),fill='#ffdc67');d.rectangle((15,14,18,18),fill='#9c1b27');d.rectangle((16,14,17,16),fill='#f45051');d.rectangle((6,12,7,14),fill='#48d9e8');d.rectangle((25,12,26,14),fill='#48d9e8')
put('assets/z_lor/textures/item/eneit_crown.png',png(im))
put('assets/z_lor/models/item/eneit_crown_hand.json',{'parent':'minecraft:item/generated','textures':{'layer0':'z_lor:item/eneit_crown'}})
# Model wraps around the player head. A single-pixel palette is used for each
# element so that it stays crisp from all directions.
palette={'gold':'#d9a32b','bright':'#ffe17b','dark':'#9c6818','jewel':'#b42132','shine':'#ff6161','cyan':'#45cfe7'}
for name,color in palette.items():put('assets/z_lor/textures/item/crown_'+name+'.png',png(Image.new('RGBA',(1,1),color)))
textures={k:'z_lor:item/crown_'+k for k in palette};elements=[]
def cube(a,b,c):elements.append({'from':a,'to':b,'faces':{face:{'texture':'#'+c} for face in ('north','south','east','west','up','down')},'shade':False})
cube([0,11,0],[16,13,1],'gold');cube([0,11,15],[16,13,16],'gold');cube([0,11,1],[1,13,15],'gold');cube([15,11,1],[16,13,15],'gold')
for side in (0,15):
    for x in (1,5,10,14):cube([x,13,side],[x+1,15 if x in (1,14) else 14,side+1],'bright')
    for z in (4,11):cube([side,13,z],[side+1,14,z+1],'bright')
cube([6,12,-.1],[10,15,.2],'dark');cube([7,12.5,-.2],[9,14.5,.3],'jewel');cube([7,13.5,-.3],[8,14.5,.4],'shine')
put('assets/z_lor/models/item/eneit_crown_head.json',{'ambientocclusion':False,'textures':textures,'elements':elements,'display':{'head':{'translation':[0,0,0],'scale':[1,1,1]}}})
put('assets/z_lor/items/eneit_crown.json',{'model':{'type':'minecraft:select','property':'minecraft:display_context','cases':[{'when':'head','model':{'type':'minecraft:model','model':'z_lor:item/eneit_crown_head'}}],'fallback':{'type':'minecraft:model','model':'z_lor:item/eneit_crown_hand'}}})
put('Z_Test-Ghosts-LICENSE.txt',(R/'z_test/assets-src/GPL-3.0.txt').read_bytes());put('Z_Test-Ghosts-NOTICE.txt',(R/'z_test/assets-src/NOTICE.txt').read_bytes())
meta=json.loads(files['pack.mcmeta']);meta['pack']['description']='Z_Server Pack 1.17 • души и Корона Енейта';files['pack.mcmeta']=json.dumps(meta,ensure_ascii=False).encode()
for n,b in old.items():assert n=='pack.mcmeta' or files[n]==b,n
with ZipFile(output,'w',ZIP_DEFLATED) as z:
    for n,b in sorted(files.items()):
        entry=ZipInfo(n,(2026,1,1,0,0,0));entry.compress_type=ZIP_DEFLATED;z.writestr(entry,b)
output.with_suffix('.zip.sha1').write_text(hashlib.sha1(output.read_bytes()).hexdigest()+'\n')
print('Created',output,'preserved',len(old)-1,'existing files; sha1',hashlib.sha1(output.read_bytes()).hexdigest())
