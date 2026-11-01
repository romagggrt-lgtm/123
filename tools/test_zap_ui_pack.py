#!/usr/bin/env python3
"""Static checks for Z_AP's tiled bitmap font and the merged pack."""
from pathlib import Path
from PIL import Image
import zipfile,json,io,hashlib,re
root=Path(__file__).resolve().parents[1];base=root/'dist/Z_Server_Pack_1.10.zip';pack=root/'dist/Z_Server_Pack_1.11.zip'
with zipfile.ZipFile(base) as old,zipfile.ZipFile(pack) as new:
 assert new.testzip() is None
 assert all(old.read(n)==new.read(n) for n in old.namelist() if n!='pack.mcmeta')
 data=json.loads(new.read('assets/z_ap/font/gui.json'));seen=set();providers=data['providers'];assert providers[0]['type']=='space'
 for p in providers[1:]:
  assert p['type']=='bitmap' and p['ascent']<=p['height']
  namespace,loc=p['file'].split(':',1);im=Image.open(io.BytesIO(new.read(f'assets/{namespace}/textures/{loc}'))).convert('RGBA')
  rows=p['chars'];assert len(rows)==1 and rows[0];w=im.width//len(rows[0]);h=im.height
  assert w<=256 and h<=256 and im.width%len(rows[0])==0,(p['file'],w,h)
  for c in rows[0]:assert c not in seen;seen.add(c)
  for i in range(len(rows[0])):
   a=im.crop((i*w,0,(i+1)*w,h)).getchannel('A');assert a.getbbox() and a.getbbox()[2]==w,(p['file'],i)
  if not p['file'].endswith('welcome_row0.png') and p['file'].endswith('_row0.png'):assert p['ascent']==13
  if not p['file'].endswith('welcome_row1.png') and p['file'].endswith('_row1.png'):assert p['ascent']==-43
  if p['file'].endswith('_row2.png'):assert p['ascent']==-99
  if p['file'].endswith('_row3.png'):assert p['ascent']==-155
 expected=set(map(chr, list(range(0xe100,0xe14c))+[0xe150,0xe151,0xe152,0xe153]));assert seen==expected
 # The title is painted after items by the vanilla container screen: the
 # relevant cells must stay transparent so the real items remain visible.
 for name,slots in [('home',[(8+18*(i%9),18+18*(i//9)) for i in [0,3,5,7,9,18,21,23,25,27,36,39,41,43,45]]),('generic',[(8+18*c,18+18*r) for r in range(6) for c in range(9)]),('anvil',[(27,47),(76,47),(134,47)])]:
  image=Image.open(root/'ap/ui-assets'/f'{name}.png').convert('RGBA')
  for x,y in slots:assert image.getpixel(((x+8)*4,(y+8)*4))[3]==0,(name,x,y)
 for icon in ['home','players','teleport','mode','morph','kits','nick','vanish','banya','world','region','settings','invisible']:
  for path in [f'assets/z_ap/items/{icon}.json',f'assets/z_ap/models/item/{icon}.json',f'assets/z_ap/textures/item/{icon}.png']:assert path in new.namelist(),path
 config=(root/'ap/src/main/resources/config.yml').read_text();constant=(root/'ap/src/main/java/dev/zap/GuiTheme.java').read_text();sha=hashlib.sha1(pack.read_bytes()).hexdigest();assert sha in config and sha in constant
 print(f'PASS: {len(seen)} glyphs fit the atlas, preserved {len(old.namelist())-1} previous files; SHA-1 {sha}')
