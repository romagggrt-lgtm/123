#!/usr/bin/env python3
"""Compare old and fixed world device models using an unmodified 1.21.11 client."""
from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED
from http.server import ThreadingHTTPServer,SimpleHTTPRequestHandler
from functools import partial
import hashlib,json,os,shutil,subprocess,sys,threading,time,traceback,urllib.request
from PIL import Image,ImageStat
from Xlib import X,display
from Xlib.ext import xtest
ROOT=Path(__file__).resolve().parents[1];WORK=ROOT/'qa-device-lighting/server';REPORT=ROOT/'qa-device-lighting/evidence';WORK.mkdir(parents=True,exist_ok=True);REPORT.mkdir(parents=True,exist_ok=True)
sys.path.insert(0,str(ROOT/'tools'));import zap_vanilla_client as vanilla
vanilla.GAME=ROOT/'qa-device-lighting/client';vanilla.REPORT=REPORT
server=client=http=None
def get(url):
 with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'ZServerPack-QA/1.16'}),timeout=90) as r:return r.read()
def wait_for(f,timeout,label):
 end=time.monotonic()+timeout
 while time.monotonic()<end:
  if f():return
  if server is not None and server.poll() is not None:raise RuntimeError('server stopped: '+label)
  if client is not None and client.poll() is not None:raise RuntimeError('client stopped: '+label)
  time.sleep(.2)
 raise RuntimeError('timeout: '+label)
def send(command):server.stdin.write((command+'\n').encode());server.stdin.flush()
try:
 old=ROOT/'dist/Z_Server_Pack_1.15.zip';new=ROOT/'dist/Z_Server_Pack_1.16.zip';testpack=WORK/'qa-pack.zip'
 with ZipFile(old) as previous,ZipFile(new) as current,ZipFile(testpack,'w',ZIP_DEFLATED) as z:
  for entry in current.infolist():z.writestr(entry,current.read(entry.filename))
  for kind in ('void_siren','void_absorber'):
   model='assets/z_lor/models/block/world_'+kind+'.json';before=json.loads(previous.read(model));after=json.loads(current.read(model));assert 'elements' not in before and all(e['light_emission']==15 for e in after['elements'])
   z.writestr('assets/z_lor/models/block/qa_old_'+kind+'.json',json.dumps(before));z.writestr('assets/z_lor/items/qa_old_'+kind+'.json',json.dumps({'model':{'type':'minecraft:model','model':'z_lor:block/qa_old_'+kind}}))
 meta=json.loads(get('https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds/latest'));dl=meta['downloads']['server:default'];data=get(dl['url']);assert hashlib.sha256(data).hexdigest()==dl['checksums']['sha256'];(WORK/'paper.jar').write_bytes(data)
 (WORK/'eula.txt').write_text('eula=true\n');sha1=hashlib.sha1(testpack.read_bytes()).hexdigest()
 (WORK/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25585\nonline-mode=false\nspawn-protection=0\nview-distance=4\nsimulation-distance=4\nlevel-type=minecraft:flat\ndifficulty=peaceful\nresource-pack=http://127.0.0.1:25583/qa-pack.zip\nresource-pack-sha1='+sha1+'\nrequire-resource-pack=true\n')
 http=ThreadingHTTPServer(('127.0.0.1',25583),partial(SimpleHTTPRequestHandler,directory=str(WORK)));threading.Thread(target=http.serve_forever,daemon=True).start()
 logpath=REPORT/'server.log';log=logpath.open('w');server=subprocess.Popen(['java','-Xms512M','-Xmx2G','-jar','paper.jar','nogui'],cwd=WORK,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT)
 wait_for(lambda:'Done (' in logpath.read_text(errors='replace'),300,'server startup')
 command=vanilla.prepare();(vanilla.GAME/'options.txt').write_text('onboardAccessibility:false\nstartedCleanly:true\nlang:en_us\nguiScale:2\ngamma:0.5\nrenderDistance:4\nsimulationDistance:4\nmaxFps:30\nfullscreen:false\nenableVsync:false\nparticles:0\nshowSubtitles:false\ntutorialStep:none\n')
 clientlog=(REPORT/'client-console.log').open('w');client=subprocess.Popen(command,cwd=vanilla.GAME,stdout=clientlog,stderr=subprocess.STDOUT)
 wait_for(lambda:'LorObserver joined the game' in logpath.read_text(errors='replace'),240,'vanilla client joined')
 send('forceload add -8 -8 8 8');time.sleep(3)
 for cmd in ['time set midnight','fill -8 64 -8 8 74 8 minecraft:air','fill -8 64 -8 8 64 8 minecraft:stone','fill -8 65 -8 -8 74 8 minecraft:stone','fill 8 65 -8 8 74 8 minecraft:stone','fill -8 65 -8 8 74 -8 minecraft:stone','fill -8 65 8 8 74 8 minecraft:stone','fill -8 74 -8 8 74 8 minecraft:stone','setblock -2 65 0 minecraft:target','setblock 1 65 0 minecraft:target']:
  send(cmd)
 send('gamemode creative LorObserver');send('tp LorObserver 0 65 -5 0 11');send('effect clear LorObserver');send('say QA_CLIENT_POSITIONED');wait_for(lambda:'QA_CLIENT_POSITIONED' in logpath.read_text(errors='replace'),30,'client positioned')
 conn=display.Display(os.environ['DISPLAY']);screen=conn.screen();root=screen.root
 xtest.fake_input(conn,X.KeyPress,detail=conn.keysym_to_keycode(0xffbe));xtest.fake_input(conn,X.KeyRelease,detail=conn.keysym_to_keycode(0xffbe));conn.sync()
 def spawn(x,key,tag,brightness):
  # The absorber uses an explicit Display.Brightness(0,15) in the live plugin.
  light=',brightness:{block:0,sky:15}' if brightness else ''
  send('summon minecraft:item_display '+str(x)+' 65.5 0.5 {Tags:["'+tag+'"],item:{id:"minecraft:target",count:1,components:{"minecraft:item_model":"'+key+'"}},item_display:"fixed"'+light+',transformation:{translation:[0f,0f,0f],left_rotation:[0f,0f,0f,1f],scale:[1.002f,1.002f,1.002f],right_rotation:[0f,0f,0f,1f]}}')
 result={}
 for kind in ('void_siren','void_absorber'):
  send('kill @e[type=minecraft:item_display,tag=qa_device]');spawn(-1.5,'z_lor:qa_old_'+kind,'qa_device',kind=='void_absorber');spawn(1.5,'z_lor:world_'+kind,'qa_device',kind=='void_absorber');send('say QA_'+kind)
  wait_for(lambda:'QA_'+kind in logpath.read_text(errors='replace'),30,'models spawned');time.sleep(20)
  pix=root.get_image(0,0,screen.width_in_pixels,screen.height_in_pixels,X.ZPixmap,0xffffffff);frame=Image.frombytes('RGB',(screen.width_in_pixels,screen.height_in_pixels),pix.data,'raw','BGRX');frame.save(REPORT/(kind+'.png'))
  gray=frame.convert('L');fixed=ImageStat.Stat(gray.crop((480,335,580,435))).mean[0];original=ImageStat.Stat(gray.crop((800,335,900,435))).mean[0]
  result[kind]={'old':round(original,2),'fixed':round(fixed,2)};assert fixed>40 and fixed>original*2, f'{kind}: {fixed:.2f} vs {original:.2f}'
 logs=logpath.read_text(errors='replace');assert logs.count('Summoned new Item Display')>=4 and 'Serialization errors' not in logs
 (REPORT/'result.json').write_text(json.dumps({'status':'PASS','paper_build':meta['id'],'minecraft':'1.21.11 vanilla','gamma':0.5,'comparisons':result,'sha1':hashlib.sha1(new.read_bytes()).hexdigest()},indent=2))
except Exception as ex:
 (REPORT/'result.json').write_text(json.dumps({'status':'FAIL','error':str(ex),'trace':traceback.format_exc()},indent=2));raise
finally:
 if client and client.poll() is None:client.terminate();client.wait(timeout=20)
 if server and server.poll() is None:
  try:server.communicate(b'stop\n',timeout=30)
  except subprocess.TimeoutExpired:server.kill();server.wait()
 if http:http.shutdown()
 logfile=vanilla.GAME/'logs/latest.log'
 if logfile.exists():shutil.copyfile(logfile,REPORT/'client.log')
