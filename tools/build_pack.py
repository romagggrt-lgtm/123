#!/usr/bin/env python3
"""Build an original fallback model/audio. Does not extract or use third-party mod assets."""
from pathlib import Path
import shutil
import json, struct, zlib, subprocess, zipfile, hashlib
ROOT=Path(__file__).resolve().parents[1]
PACK=ROOT/'target/resource-pack'
if PACK.exists():
    shutil.rmtree(PACK)

def write(path, data):
    p=PACK/path; p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

# Preserve every existing Shahed asset byte-for-byte. This release only adds
# resources in z_verity; it never rebuilds the Shahed plugin or its resources.
with zipfile.ZipFile(ROOT/'dist/Z_Server_Pack_1.03.zip') as base:
    for entry in base.infolist():
        if not entry.is_dir():
            path=PACK/entry.filename
            path.parent.mkdir(parents=True,exist_ok=True)
            path.write_bytes(base.read(entry))

def chunk(tag,data):
    return struct.pack('>I',len(data))+tag+data+struct.pack('>I',zlib.crc32(tag+data)&0xffffffff)

(PACK/'assets/z_verity').mkdir(parents=True,exist_ok=True)
write(Path('assets/z_verity/items/verity_egg.json'), {'model': {'type':'minecraft:model', 'model':'z_verity:item/verity_egg'}})
# ItemDisplay uses the 1.21 item-model component.  Every model identifier
# passed to ItemMeta#setItemModel therefore needs an item definition under
# assets/<namespace>/items/, which then points at the traditional model JSON.
write(Path('assets/z_verity/items/verity_box.json'), {
    'model': {'type':'minecraft:model', 'model':'z_verity:item/verity_box'}
})
write(Path('assets/z_verity/items/verity_sphere.json'), {
    'model': {'type':'minecraft:model', 'model':'z_verity:item/verity_sphere'}
})

from verity_model import build_sphere, ATLAS_SIZE
sphere_model, sphere_pixels=build_sphere()
write(Path('assets/z_verity/models/item/verity_sphere.json'), sphere_model)
write(Path('assets/z_verity/models/item/verity_egg.json'), {'parent':'z_verity:item/verity_sphere'})
write(Path('assets/z_verity/models/item/verity_box.json'), {
    'credit':'Original procedural Verity box for Z_Verity.',
    'textures':{'crate':'z_verity:item/verity_box'},
    'elements':[{'from':[1,1,1],'to':[15,15,15],'faces':{f:{'texture':'#crate'} for f in ('north','south','east','west','up','down')}}]
})

def png_rgba(path, width, height, pixel):
    rows=[]
    for y in range(height):
        rows.append(b'\0'+b''.join(bytes(pixel(x,y)) for x in range(width)))
    data=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',width,height,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(b''.join(rows)))+chunk(b'IEND',b'')
    path.parent.mkdir(parents=True,exist_ok=True); path.write_bytes(data)

png_rgba(PACK/'assets/z_verity/textures/item/verity_yellow.png',16,16,
    lambda x,y:(255,235,5,255))
png_rgba(PACK/'assets/z_verity/textures/item/verity_skin.png',ATLAS_SIZE,ATLAS_SIZE,
    lambda x,y:sphere_pixels[(y*ATLAS_SIZE+x)*4:(y*ATLAS_SIZE+x)*4+4])
def box_pixel(x,y):
    # Plain cardboard with a central strip of tape, as in the reference.
    tape=13<=x<=18
    grain=((x*13+y*7)%5)-2
    color=(155,119,55) if tape else (132,95,39)
    if y in (0,31): color=tuple(v-12 for v in color)
    if 24<=x<=28 and 7<=y<=9: color=(121,84,33)
    return (*[max(0,min(255,v+grain)) for v in color],255)
png_rgba(PACK/'assets/z_verity/textures/item/verity_box.png',32,32,box_pixel)

verity_mp3=ROOT/'assets-src/1023.MP3'
verity_ogg=PACK/'assets/z_verity/sounds/verity.ogg'
verity_ogg.parent.mkdir(parents=True,exist_ok=True)
subprocess.run(['ffmpeg','-v','error','-y','-i',str(verity_mp3),'-ac','1','-fflags','+bitexact','-flags:a','+bitexact','-c:a','libvorbis','-q:a','5',str(verity_ogg)],check=True)
write(Path('assets/z_verity/sounds.json'), {'verity':{'sounds':[{'name':'z_verity:verity','stream':False,'attenuation_distance':32}]}})
(PACK/'LICENSE.txt').write_text(
    'Shahed fallback model, texture and engine audio: original work, CC0-1.0.\n'
    'Verity sound: converted from the user-provided 1023.MP3 at the user request;\n'
    'the uploader retains any rights to that recording. Remove this asset if you\n'
    'do not have permission to redistribute it.\n'
    'No Forge code or third-party mod model assets are included.\n',
    encoding='utf-8')
dest=ROOT/'dist/Z_Server_Pack_1.05.zip'; dest.parent.mkdir(exist_ok=True)
with zipfile.ZipFile(dest,'w',zipfile.ZIP_DEFLATED) as z:
    for path in sorted(PACK.rglob('*')):
        if path.is_file():
            info=zipfile.ZipInfo(path.relative_to(PACK).as_posix(), date_time=(2026,1,1,0,0,0))
            info.compress_type=zipfile.ZIP_DEFLATED
            z.writestr(info,path.read_bytes())
print(dest)
digest=hashlib.sha1(dest.read_bytes()).hexdigest()
(dest.parent/'Z_Server_Pack_1.05.zip.sha1').write_text(digest+'\n')
print('SHA-1:',digest)
