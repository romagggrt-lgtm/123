#!/usr/bin/env python3
"""Build an original fallback model/audio. Does not extract or use third-party mod assets."""
from pathlib import Path
import json, math, struct, wave, zlib, subprocess, zipfile, hashlib
ROOT=Path(__file__).resolve().parents[1]
PACK=ROOT/'target/resource-pack'

def write(path, data):
    p=PACK/path; p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

write(Path('pack.mcmeta'), {'pack':{'pack_format':75,'min_format':75,'max_format':75,'description':'shah · original fallback aircraft · Minecraft 1.21.11'}})
write(Path('assets/shah/items/shahed.json'), {'model':{'type':'minecraft:model','model':'shah:item/shahed'}})
write(Path('assets/shah/items/egg.json'), {'model':{'type':'minecraft:model','model':'minecraft:item/phantom_spawn_egg'}})
faces={f:{'texture':'#body','uv':[0,0,16,16]} for f in ('north','south','east','west','up','down')}
elements=[]
def box(a,b): elements.append({'from':a,'to':b,'faces':faces})
# Nose points toward +Z, matching Bukkit yaw=0. Stepped delta wings are actual volumetric geometry.
box([6.5,7,-6],[9.5,10,23])
box([7,7.5,23],[9,9.5,27])
box([7.5,8,27],[8.5,9,30])
for side in (-1,1):
    for i in range(7):
        x0=8+side*(2+i*2.6); x1=8+side*(4.6+i*2.6)
        box([min(x0,x1),8,-8],[max(x0,x1),9.1,18-i*3.5])
    # Vertical wingtip fins and tail rudders.
    x=8+side*20
    box([x-.4,8,-8],[x+.4,14,-1])
box([6,7,-11],[10,10,-6])
box([7.7,3,-12],[8.3,14,-11.3])
box([2,8.2,-12],[14,8.8,-11.3])
write(Path('assets/shah/models/item/shahed.json'), {
    'credit':'Original procedural fallback by shah project. No assets from the requested mods are used.',
    'textures':{'body':'shah:entity/shahed','particle':'shah:entity/shahed'}, 'elements':elements,
    'display':{
        'firstperson_righthand':{'rotation':[0,160,0],'translation':[1,-1,-3],'scale':[.22,.22,.22]},
        'firstperson_lefthand':{'rotation':[0,200,0],'translation':[1,-1,-3],'scale':[.22,.22,.22]},
        'thirdperson_righthand':{'rotation':[0,180,0],'translation':[0,2,-1],'scale':[.28,.28,.28]},
        'thirdperson_lefthand':{'rotation':[0,180,0],'translation':[0,2,-1],'scale':[.28,.28,.28]},
        'gui':{'rotation':[25,135,0],'scale':[.27,.27,.27]},
        'ground':{'scale':[.25,.25,.25]},'fixed':{'scale':[.3,.3,.3]}
    }})
# A self-contained 16x16 PNG, muted warm grey with a restrained panel pattern.
p=PACK/'assets/shah/textures/entity/shahed.png'; p.parent.mkdir(parents=True,exist_ok=True)
raw=b''.join(b'\0'+b''.join(bytes((136+(x+y)%4*4,133+(x+y)%4*4,117+(x+y)%4*4,255)) for x in range(16)) for y in range(16))
def chunk(tag,data): return struct.pack('>I',len(data))+tag+data+struct.pack('>I',zlib.crc32(tag+data)&0xffffffff)
p.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',16,16,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b''))
# Authored periodic low-frequency engine sample; no recordings or mod sounds.
wav=ROOT/'target/engine.wav'; rate=44100
with wave.open(str(wav),'wb') as f:
    f.setparams((1,2,rate,0,'NONE','not compressed'))
    samples=[]
    for n in range(rate):
        t=n/rate
        fade=min(1,t/.06,(1-t)/.06)
        value=(math.sin(2*math.pi*86*t)+.32*math.sin(2*math.pi*172*t)+.16*math.sin(2*math.pi*258*t))*(.85+.15*math.sin(2*math.pi*12*t))*fade
        samples.append(struct.pack('<h',int(value*6500)))
    f.writeframes(b''.join(samples))
audio=PACK/'assets/shah/sounds/engine.ogg'; audio.parent.mkdir(parents=True,exist_ok=True)
subprocess.run(['ffmpeg','-v','error','-y','-i',str(wav),'-fflags','+bitexact','-flags:a','+bitexact','-c:a','libvorbis','-q:a','4',str(audio)],check=True)
write(Path('assets/shah/sounds.json'),{'engine_'+str(i):{'sounds':[{'name':'shah:engine','stream':False,'attenuation_distance':45}]} for i in range(1024)})
(PACK/'LICENSE.txt').write_text('Original procedural model, texture and engine audio: CC0-1.0.\nNo third-party Minecraft mod assets are included.\n',encoding='utf-8')
dest=ROOT/'dist/Shahed-ResourcePack.zip'; dest.parent.mkdir(exist_ok=True)
with zipfile.ZipFile(dest,'w',zipfile.ZIP_DEFLATED) as z:
    for path in sorted(PACK.rglob('*')):
        if path.is_file():
            info=zipfile.ZipInfo(path.relative_to(PACK).as_posix(), date_time=(2026,1,1,0,0,0))
            info.compress_type=zipfile.ZIP_DEFLATED
            z.writestr(info,path.read_bytes())
print(dest)
digest=hashlib.sha1(dest.read_bytes()).hexdigest()
(dest.parent/'Shahed-ResourcePack.zip.sha1').write_text(digest+'\n')
print('SHA-1:',digest)
