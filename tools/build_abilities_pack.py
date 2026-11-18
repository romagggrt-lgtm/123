#!/usr/bin/env python3
"""Add the new assets to the existing 1.14 combined pack without changing other files."""
import json, zipfile, pathlib, io
from PIL import Image, ImageDraw
root=pathlib.Path(__file__).resolve().parents[1]
src=root/'dist/Z_Server_Pack_1.14.zip'; out=root/'dist/Z_Server_Pack_1.15.zip'
source=root/'lor/assets-src/creative_staff'
base=json.loads((source/'item.json').read_text())
for name in ('core','inner_cube','ring'):
    base['elements'].extend(json.loads((source/(name+'.json')).read_text())['elements'])
for element in base['elements']:
    for i in range(3):
        if element['from'][i]>element['to'][i]:
            element['from'][i],element['to'][i]=element['to'][i],element['from'][i]
base['textures']={'0':'z_lor:item/lord_staff','particle':'z_lor:item/lord_staff'}
base.pop('format_version',None)
# Draw a legible pixel-art healing page in the existing paper item style.
im=Image.new('RGBA',(16,16),(0,0,0,0));d=ImageDraw.Draw(im)
d.polygon([(3,1),(11,1),(13,3),(13,14),(3,14)],fill='#e7d9b8',outline='#806d55')
d.polygon([(11,1),(13,3),(11,3)],fill='#b5a483')
d.rectangle((7,4,9,11),fill='#348e54');d.rectangle((5,6,11,8),fill='#348e54');d.rectangle((8,5,8,10),fill='#a6efab');d.rectangle((6,7,10,7),fill='#a6efab')
buf=io.BytesIO();im.save(buf,format='PNG')
new={
 'assets/z_lor/items/lord_staff.json':json.dumps({'model':{'type':'minecraft:model','model':'z_lor:item/lord_staff'}}).encode(),
 'assets/z_lor/models/item/lord_staff.json':json.dumps(base,separators=(',',':')).encode(),
 'assets/z_lor/textures/item/lord_staff.png':(source/'zapper_staff.png').read_bytes(),
 'assets/zbook/items/ave_revivous.json':json.dumps({'model':{'type':'minecraft:model','model':'zbook:item/ave_revivous'}}).encode(),
 'assets/zbook/models/item/ave_revivous.json':json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':'zbook:item/ave_revivous'}}).encode(),
 'assets/zbook/textures/item/ave_revivous.png':buf.getvalue(),
 'licenses/CREATE_AERONAUTICS_LICENSE.md':(source/'LICENSE.md').read_bytes(),
}
with zipfile.ZipFile(src) as old, zipfile.ZipFile(out,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as target:
 for info in old.infolist():
  if info.filename not in new: target.writestr(info,old.read(info.filename))
 for name,data in new.items():target.writestr(name,data)
print(out)
