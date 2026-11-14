#!/usr/bin/env python3
"""Compare the old and new model inside opaque targets with an unmodified client."""
from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED
from http.server import ThreadingHTTPServer,SimpleHTTPRequestHandler
from functools import partial
import hashlib,json,os,shutil,subprocess,sys,threading,time,traceback,urllib.request
from PIL import Image
from Xlib import X,display
from Xlib.ext import xtest
ROOT=Path(__file__).resolve().parents[1];WORK=ROOT/'qa-voidometer/server';REPORT=ROOT/'qa-voidometer/evidence';WORK.mkdir(parents=True,exist_ok=True);REPORT.mkdir(parents=True,exist_ok=True)
sys.path.insert(0,str(ROOT/'tools'));import zap_vanilla_client as vanilla
vanilla.GAME=ROOT/'qa-voidometer/client';vanilla.REPORT=REPORT
server=client=http=None

def get(url):
 with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'ZServerPack-QA/1.14'}),timeout=90) as r:return r.read()
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
 old=ROOT/'dist/Z_Server_Pack_1.13.zip';new=ROOT/'dist/Z_Server_Pack_1.14.zip';testpack=WORK/'qa-pack.zip'
 model='assets/smplore/models/block/stationary_voidometer.json'
 with ZipFile(old) as previous,ZipFile(new) as current,ZipFile(testpack,'w',ZIP_DEFLATED) as z:
  before=json.loads(previous.read(model));after=json.loads(current.read(model));assert before['elements'][0].get('light_emission',0)==0 and after['elements'][0]['light_emission']==15
  for entry in current.infolist():z.writestr(entry,current.read(entry.filename))
  before['display']={'fixed':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1,1,1]}}
  z.writestr('assets/z_lor/models/block/qa_old_stationary.json',json.dumps(before))
  z.writestr('assets/z_lor/items/qa_old_stationary.json',json.dumps({'model':{'type':'minecraft:model','model':'z_lor:block/qa_old_stationary'}}))
 meta=json.loads(get('https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds/latest'));dl=meta['downloads']['server:default'];data=get(dl['url']);assert hashlib.sha256(data).hexdigest()==dl['checksums']['sha256'];(WORK/'paper.jar').write_bytes(data)
 (WORK/'eula.txt').write_text('eula=true\n');sha1=hashlib.sha1(testpack.read_bytes()).hexdigest()
 (WORK/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25585\nonline-mode=false\nspawn-protection=0\nview-distance=4\nsimulation-distance=4\nlevel-type=minecraft:flat\ndifficulty=peaceful\nresource-pack=http://127.0.0.1:25583/qa-pack.zip\nresource-pack-sha1='+sha1+'\nrequire-resource-pack=true\n')
 http=ThreadingHTTPServer(('127.0.0.1',25583),partial(SimpleHTTPRequestHandler,directory=str(WORK)));threading.Thread(target=http.serve_forever,daemon=True).start()
 logpath=REPORT/'server.log';log=logpath.open('w');server=subprocess.Popen(['java','-Xms512M','-Xmx2G','-jar','paper.jar','nogui'],cwd=WORK,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT)
 wait_for(lambda:'Done (' in logpath.read_text(errors='replace'),300,'server startup')
 for cmd in ['gamerule doDaylightCycle false','time set midnight','fill -8 64 -8 8 74 8 minecraft:air','fill -8 64 -8 8 64 8 minecraft:stone','fill -8 65 -8 -8 74 8 minecraft:stone','fill 8 65 -8 8 74 8 minecraft:stone','fill -8 65 -8 8 74 -8 minecraft:stone','fill -8 65 8 8 74 8 minecraft:stone','fill -8 74 -8 8 74 8 minecraft:stone','setblock -2 65 0 minecraft:target','setblock 1 65 0 minecraft:target']:
  send(cmd)
 def spawn(x,key,tag):
  send('summon minecraft:item_display '+str(x)+' 65.5 0.5 {Tags:["'+tag+'"],item:{id:"minecraft:target",count:1,components:{"minecraft:item_model":"'+key+'"}},item_display:"fixed",transformation:{scale:[1.002f,1.002f,1.002f]}}')
 spawn(-1.5,'z_lor:qa_old_stationary','qa_old');spawn(1.5,'z_lor:world_stationary_voidometer','qa_new');send('say QA_BUILD_COMPLETE')
 wait_for(lambda:'QA_BUILD_COMPLETE' in logpath.read_text(errors='replace'),30,'world built')
 command=vanilla.prepare();(vanilla.GAME/'options.txt').write_text('onboardAccessibility:false\nstartedCleanly:true\nlang:en_us\nguiScale:2\ngamma:0.5\nrenderDistance:4\nsimulationDistance:4\nmaxFps:30\nfullscreen:false\nenableVsync:false\nparticles:0\ntutorialStep:none\n')
 clientlog=(REPORT/'client-console.log').open('w');client=subprocess.Popen(command,cwd=vanilla.GAME,stdout=clientlog,stderr=subprocess.STDOUT)
 wait_for(lambda:'LorObserver joined the game' in logpath.read_text(errors='replace'),240,'vanilla client joined')
 send('gamemode creative LorObserver');send('tp LorObserver 0 65 -5 0 11');send('effect clear LorObserver');send('say QA_CLIENT_POSITIONED')
 wait_for(lambda:'QA_CLIENT_POSITIONED' in logpath.read_text(errors='replace'),30,'client positioned')
 time.sleep(20)
 conn=display.Display(os.environ['DISPLAY']);screen=conn.screen();root=screen.root
 xtest.fake_input(conn,X.KeyPress,detail=conn.keysym_to_keycode(0xffbe));xtest.fake_input(conn,X.KeyRelease,detail=conn.keysym_to_keycode(0xffbe));conn.sync();time.sleep(2)
 pix=root.get_image(0,0,screen.width_in_pixels,screen.height_in_pixels,X.ZPixmap,0xffffffff);Image.frombytes('RGB',(screen.width_in_pixels,screen.height_in_pixels),pix.data,'raw','BGRX').save(REPORT/'before-and-after.png')
 client_log=vanilla.GAME/'logs/latest.log'
 if client_log.exists():shutil.copyfile(client_log,REPORT/'client.log')
 logs=logpath.read_text(errors='replace');assert 'Summoned new Item Display' in logs or logs.count('Summoned new')>=2, 'item displays were not summoned'
 (REPORT/'result.json').write_text(json.dumps({'status':'CAPTURED','paper_build':meta['id'],'minecraft':'1.21.11 vanilla','gamma':0.5,'baseline':'left','changed':'right','resource_pack_sha1':hashlib.sha1(new.read_bytes()).hexdigest(),'review':'Compare screenshot brightness; capture alone is not a pass.'},indent=2))
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
