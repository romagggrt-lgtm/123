#!/usr/bin/env python3
"""Native Minecraft 1.21.11 item models and byte-preserving server pack merge."""
from pathlib import Path
from zipfile import ZipFile,ZipInfo,ZIP_DEFLATED
import hashlib,json,math,random,subprocess,io
from PIL import Image,ImageDraw
from merge_smplore_pack import check_references
ROOT=Path(__file__).resolve().parents[1];ASSET=ROOT/'banya/assets-src';DIST=ROOT/'dist';BASE=DIST/'Z_Server_Pack_1.08.zip'
def encoded(obj):return (json.dumps(obj,ensure_ascii=False,separators=(',',':'))+'\n').encode()
def archive(path,data):
 with ZipFile(path,'w',ZIP_DEFLATED) as z:
  for name,body in sorted(data.items()):
   entry=ZipInfo(name,(2026,1,1,0,0,0));entry.compress_type=ZIP_DEFLATED;z.writestr(entry,body)
def png(im):
 out=io.BytesIO();im.save(out,format='PNG');return out.getvalue()
def cube(lo,hi,uv):
 return {'from':lo,'to':hi,'faces':{side:{'texture':'#skin','uv':uv} for side in ['north','south','east','west','up','down']}}
def main(reencode=False):
 ASSET.mkdir(exist_ok=True)
 source=ASSET/'ambient-original.mp3';ogg=ASSET/'ambient.ogg'
 # Two-pass EBU loudness normalisation, preserving the original 10-second performance.
 first=subprocess.run(['ffmpeg','-hide_banner','-i',str(source),'-af','loudnorm=I=-14:TP=-1:LRA=11:print_format=json','-f','null','-'],capture_output=True,text=True,check=True).stderr
 measured=json.loads(first[first.rfind('{'):first.rfind('}')+1])
 filt='loudnorm=I=-14:TP=-1:LRA=11:measured_I={input_i}:measured_LRA={input_lra}:measured_TP={input_tp}:measured_thresh={input_thresh}:offset={target_offset}:linear=true'.format(**measured)
 if reencode or not ogg.exists():
  subprocess.run(['ffmpeg','-y','-v','error','-i',str(source),'-af',filt,'-ar','44100','-ac','2','-c:a','libvorbis','-q:a','5',str(ogg)],check=True)
 duration=float(subprocess.check_output(['ffprobe','-v','error','-show_entries','format=duration','-of','default=nw=1:nk=1',str(ogg)]))
 (ROOT/'banya/src/main/resources/ambient-duration.txt').write_text(str(duration)+'\n')
 probe=subprocess.run(['ffmpeg','-hide_banner','-i',str(ogg),'-af','loudnorm=I=-14:TP=-1:LRA=11:print_format=json','-f','null','-'],capture_output=True,text=True,check=True).stderr
 loud=json.loads(probe[probe.rfind('{'):probe.rfind('}')+1]);assert float(loud['input_tp'])<=-.5
 assets={'assets/zbanya/sounds/ambient.ogg':ogg.read_bytes(),'assets/zbanya/sounds.json':encoded({'ambient':{'sounds':[{'name':'zbanya:ambient','stream':True,'attenuation_distance':16}]}})}
 rng=random.Random(8391);im=Image.new('RGBA',(64,64));pixels=im.load()
 for y in range(64):
  for x in range(64):
   noise=rng.randint(-12,12);base=(79,44,22) if x<24 else (114,66,31) if x<48 else (150,92,46)
   stripe=8 if (x+2*y)%17<3 else 0;pixels[x,y]=tuple(max(0,min(255,c+noise+stripe)) for c in base)+(255,)
 # Separate UV patches for charcoal fly bodies, grey eyes, translucent wings.
 draw=ImageDraw.Draw(im);draw.rectangle((0,48,15,63),fill=(21,19,17,255));draw.rectangle((16,48,31,63),fill=(175,153,128,255));draw.rectangle((32,48,47,63),fill=(211,219,217,165))
 texture=png(im);assets['assets/zbanya/textures/item/vkusnyashka.png']=texture
 body=[]
 # Voxel tube following a tapering helix, with a broad rounded base and curled tip.
 for layer in range(2):
  for z in range(-5,6):
   half=max(.5,math.sqrt(max(0,25-z*z)));body.append(cube([8-half,1+layer,8+z-.5],[8+half,2+layer,8+z+.5],[0,0,6,6]))
 for i in range(104):
  t=i/103;angle=t*math.pi*5.3;radius=4.6*(1-t);cx=8+math.cos(angle)*radius;cz=8+math.sin(angle)*radius;cy=2.2+t*10.4;size=2.8-1.2*t
  uv=[6+(i%4),1+(i%3),10+(i%4),5+(i%3)] if i%4 else [0,0,4,4]
  body.append(cube([round(cx-size/2,3),round(cy-size/2,3),round(cz-size/2,3)],[round(cx+size/2,3),round(cy+size/2,3),round(cz+size/2,3)],uv))
 transforms={'gui':{'rotation':[25,35,0],'translation':[0,-1,0],'scale':[.75,.75,.75]},'ground':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.5,.5,.5]},'fixed':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1,1,1]},'firstperson_righthand':{'rotation':[0,-25,8],'translation':[1.5,2,1],'scale':[.48,.48,.48]},'firstperson_lefthand':{'rotation':[0,25,-8],'translation':[1.5,2,1],'scale':[.48,.48,.48]},'thirdperson_righthand':{'rotation':[0,90,0],'translation':[0,2,1],'scale':[.4,.4,.4]},'thirdperson_lefthand':{'rotation':[0,-90,0],'translation':[0,2,1],'scale':[.4,.4,.4]}}
 frames=[]
 for frame in range(8):
  elements=list(body)
  for fly in range(3):
   a=frame*math.pi/4+fly*math.pi*2/3;r=6.6;cx=8+math.cos(a)*r;cz=8+math.sin(a)*r;cy=9+fly*2+math.sin(a*2)*.8
   elements.append(cube([cx-.48,cy-.4,cz-.6],[cx+.48,cy+.4,cz+.6],[0,12,4,16]))
   elements.append(cube([cx-.5,cy,cz-.7],[cx+.5,cy+.3,cz-.5],[4,12,8,16]))
   for sign in [-1,1]:
    lift=.3 if frame%2 else .8
    elements.append(cube([cx+sign*.8-.8,cy+.2,cz-.45],[cx+sign*.8+.8,cy+.2+lift,cz+.45],[8,12,12,16]))
  name=f'vkusnyashka_{frame}';model={'ambientocclusion':False,'gui_light':'front','textures':{'skin':'zbanya:item/vkusnyashka','particle':'zbanya:item/vkusnyashka'},'elements':elements,'display':transforms}
  assets[f'assets/zbanya/models/item/{name}.json']=encoded(model);frames.append({'threshold':frame,'model':{'type':'minecraft:model','model':'zbanya:item/'+name}})
 assets['assets/zbanya/items/vkusnyashka.json']=encoded({'model':{'type':'minecraft:range_dispatch','property':'minecraft:custom_model_data','index':0,'fallback':frames[0]['model'],'entries':frames}})
 with ZipFile(BASE) as z:base={n:z.read(n) for n in z.namelist()}
 assert not any(n in base for n in assets)
 merged={**base,**assets};meta=json.loads(base['pack.mcmeta']);meta['pack']['description']='Z_Server_Pack 1.09 | Shahed + Verity + Golda + Pantera + Z_Lor + Z_Banya | 1.21.11';merged['pack.mcmeta']=encoded(meta)
 for n,data in base.items():
  if n!='pack.mcmeta':assert merged[n]==data
 counts=check_references(merged)
 standalone={'pack.mcmeta':encoded({'pack':{'min_format':[75,0],'max_format':[75,0],'description':'Z_Banya 1.0.0 | 1.21.11'}}),**assets}
 for path,files in [(DIST/'Z_Server_Pack_1.09.zip',merged),(DIST/'Z_Banya_ResourcePack.zip',standalone)]:
  archive(path,files);path.with_suffix(path.suffix+'.sha1').write_text(hashlib.sha1(path.read_bytes()).hexdigest()+'\n')
 report={'base':BASE.name,'base_sha256':hashlib.sha256(BASE.read_bytes()).hexdigest(),'old_files_preserved':len(base)-1,'new_files':len(assets),'sha1':hashlib.sha1((DIST/'Z_Server_Pack_1.09.zip').read_bytes()).hexdigest(),'sound_seconds':duration,'source_mp3_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'source_loudness':measured,'ogg_loudness':loud,'normalisation':'two-pass EBU R128 -14 LUFS / -1 dBTP','geometry_elements_per_frame':len(body)+12,'fly_frames':8,'validated':counts}
 (DIST/'Z_Server_Pack_1.09.report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(json.dumps(report,indent=2))
if __name__=='__main__':
 import argparse
 parser=argparse.ArgumentParser();parser.add_argument('--reencode-audio',action='store_true',help='Re-encode the original MP3 rather than reuse the verified OGG');main(parser.parse_args().reencode_audio)
