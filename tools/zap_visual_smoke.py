#!/usr/bin/env python3
"""Disposable Paper and an unmodified Minecraft client with the server pack."""
from pathlib import Path
from zipfile import ZipFile
from http.server import ThreadingHTTPServer,SimpleHTTPRequestHandler
from functools import partial
import hashlib,json,os,shutil,subprocess,sys,time,urllib.request,uuid
import nbtlib
from PIL import Image
from Xlib import X,XK,display
from Xlib.ext import xtest
ROOT=Path(__file__).resolve().parents[1];WORK=ROOT/'ap/target/visual-server';REPORT=ROOT/'ap/visual-qa';WORK.mkdir(parents=True,exist_ok=True);REPORT.mkdir(parents=True,exist_ok=True)
for file in WORK.glob('visual-*'):file.unlink()
sys.path.insert(0,str(ROOT/'tools'))
import zap_vanilla_client as vanilla
vanilla.GAME=ROOT/'ap/target/visual-client';vanilla.REPORT=REPORT
server=None;client=None;http=None;watch_stop=None

def fetch(url):
 with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'ZAP-visual-QA/1.1'}),timeout=90) as r:return r.read()
def wait_for(f,seconds,label):
 deadline=time.monotonic()+seconds
 while time.monotonic()<deadline:
  if f():return
  if server is not None and server.poll() is not None:raise RuntimeError('Server stopped: '+label)
  if client is not None and client.poll() is not None:raise RuntimeError('Client stopped: '+label)
  time.sleep(.25)
 raise RuntimeError('Timed out: '+label)
def send(s):server.stdin.write((s+'\n').encode());server.stdin.flush()
try:
 command=vanilla.prepare();command[command.index('--username')+1]='ZapVisual';command[command.index('--quickPlayMultiplayer')+1]='127.0.0.1:25596'
 digest=bytearray(hashlib.md5(b'OfflinePlayer:ZapVisual').digest());digest[6]=(digest[6]&15)|48;digest[8]=(digest[8]&63)|128;command[command.index('--uuid')+1]=uuid.UUID(bytes=bytes(digest)).hex
 nbtlib.File({'servers':nbtlib.List[nbtlib.Compound]([nbtlib.Compound({'name':nbtlib.String('Z_AP Visual QA'),'ip':nbtlib.String('127.0.0.1:25596'),'acceptTextures':nbtlib.Byte(1)})])}).save(vanilla.GAME/'servers.dat')
 (vanilla.GAME/'options.txt').write_text('onboardAccessibility:false\nstartedCleanly:true\nlang:ru_ru\nguiScale:2\nrenderDistance:4\nsimulationDistance:4\nmaxFps:30\nfullscreen:false\nenableVsync:false\nparticles:0\ntutorialStep:none\n')
 metadata=json.loads(fetch('https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds/latest'));dl=metadata['downloads']['server:default'];data=fetch(dl['url']);assert hashlib.sha256(data).hexdigest()==dl['checksums']['sha256'];(WORK/'paper.jar').write_bytes(data)
 jars=[ROOT/'ap/target/Z_AP-1.1.1.jar']+list((Path.home()/'.m2/repository').rglob('*.jar'));classes=WORK/'qa-classes';classes.mkdir(exist_ok=True)
 with (REPORT/'compile.log').open('w') as log:compiled=subprocess.run(['javac','-proc:none','--release','21','-cp',os.pathsep.join(map(str,jars)),'-d',str(classes),str(ROOT/'ap/src/smoke/java/dev/zap/qa/VisualProbe.java')],stdout=log,stderr=subprocess.STDOUT)
 assert compiled.returncode==0,'Could not compile visual probe'
 plugins=WORK/'plugins';plugins.mkdir(exist_ok=True)
 for source in [ROOT/'ap/target/Z_AP-1.1.1.jar',ROOT/'dist/Z_Morph-1.0.0.jar',ROOT/'ap/target/deps/LibsDisguises.jar',ROOT/'ap/target/deps/packetevents.jar',ROOT/'dist/Z_Nick-1.0.0.jar',ROOT/'dist/Z_Check-1.0.0.jar',ROOT/'dist/Z_Banya-1.0.2.jar',ROOT/'dist/InventoryCache_1.0.1.jar',ROOT/'ap/test-only/Nevidimka-1.0.2.jar']:shutil.copyfile(source,plugins/source.name)
 with ZipFile(plugins/'Z_AP_VisualQA.jar','w') as z:
  for path in classes.rglob('*.class'):z.write(path,str(path.relative_to(classes)))
  z.writestr('plugin.yml',"name: Z_AP_VisualQA\nversion: 1.0.0\nmain: dev.zap.qa.VisualProbe\napi-version: '1.21.11'\ndepend: [Z_AP]\n")
 pack=ROOT/'dist/Z_Server_Pack_1.11.zip';digest=hashlib.sha1(pack.read_bytes()).hexdigest();(WORK/'eula.txt').write_text('eula=true\n');(WORK/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25596\nonline-mode=false\nspawn-protection=0\nview-distance=4\nsimulation-distance=4\nlevel-type=minecraft:flat\ndifficulty=peaceful\nresource-pack=http://127.0.0.1:25597/'+pack.name+'\nresource-pack-sha1='+digest+'\nrequire-resource-pack=true\n')
 http=ThreadingHTTPServer(('127.0.0.1',25597),partial(SimpleHTTPRequestHandler,directory=str(pack.parent)))
 import threading;threading.Thread(target=http.serve_forever,daemon=True).start()
 logpath=REPORT/'server.log';log=logpath.open('w');server=subprocess.Popen(['java','-Xms512M','-Xmx2G','-jar','paper.jar','nogui'],cwd=WORK,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT)
 wait_for(lambda:'Done (' in logpath.read_text(errors='replace'),300,'server startup');send('op ZapVisual');time.sleep(1)
 clientlog=(REPORT/'client-console.log').open('w');client=subprocess.Popen(command,cwd=vanilla.GAME,stdout=clientlog,stderr=subprocess.STDOUT)
 watch_stop=threading.Event()
 def watch():
  try:
   d=display.Display(os.environ['DISPLAY']);sc=d.screen();r=sc.root
   while not watch_stop.wait(8):
    px=r.get_image(0,0,sc.width_in_pixels,sc.height_in_pixels,X.ZPixmap,0xffffffff);Image.frombytes('RGB',(sc.width_in_pixels,sc.height_in_pixels),px.data,'raw','BGRX').save(REPORT/'00-awaiting.png')
   d.close()
  except Exception as error:print('Visual watchdog:',error,flush=True)
 threading.Thread(target=watch,daemon=True).start()
 wait_for(lambda:(WORK/'visual-dialog').exists(),240,'resource pack and login dialog');watch_stop.set();time.sleep(3)
 connection=display.Display(os.environ['DISPLAY']);screen=connection.screen();root=screen.root
 def capture(name):
  pix=root.get_image(0,0,screen.width_in_pixels,screen.height_in_pixels,X.ZPixmap,0xffffffff);Image.frombytes('RGB',(screen.width_in_pixels,screen.height_in_pixels),pix.data,'raw','BGRX').save(REPORT/name)
 def click(x,y):
  xtest.fake_input(connection,X.MotionNotify,x=x,y=y);xtest.fake_input(connection,X.ButtonPress,detail=1);xtest.fake_input(connection,X.ButtonRelease,detail=1);connection.sync()
 def tap(name):
  code=connection.keysym_to_keycode(XK.string_to_keysym(name));xtest.fake_input(connection,X.KeyPress,code);xtest.fake_input(connection,X.KeyRelease,code);connection.sync()
 def enter(value):
  click(500,245);time.sleep(.4)
  for char in value:
   tap(char);time.sleep(.08)
  time.sleep(.5)
 capture('01-dialog.png');enter('111111');capture('02-filled-wrong.png')
 from PIL import ImageChops
 assert ImageChops.difference(Image.open(REPORT/'01-dialog.png').crop((421,225,860,263)),Image.open(REPORT/'02-filled-wrong.png').crop((421,225,860,263))).getbbox(), 'Typed password is not visible in field'
 click(640,686)
 auth=WORK/'plugins/Z_AP/auth.yml';wait_for(lambda:auth.exists() and 'attempts: 1' in auth.read_text(),20,'wrong password counted')
 tap('slash');time.sleep(.3);tap('a');tap('p');tap('Return');time.sleep(1.5);enter('324657');capture('03-filled-correct.png');click(640,686);time.sleep(.55);capture('04-welcome.png')
 wait_for(lambda:(WORK/'visual-chest-count').exists() and int((WORK/'visual-chest-count').read_text())>=1,20,'home after correct password');assert (WORK/'visual-home-themed').exists(),'Server did not select themed home';time.sleep(1);capture('05-home.png')
 click(604,190);wait_for(lambda:int((WORK/'visual-chest-count').read_text())>=2,12,'mode card click');time.sleep(1);capture('06-modes.png');click(568,262);wait_for(lambda:(WORK/'visual-mode').exists() and (WORK/'visual-mode').read_text()=='CREATIVE',12,'clickable mode label');time.sleep(.6);capture('07-after-mode.png')
 assert '324657' not in logpath.read_text(errors='replace'),'Password leaked to server log'
 result={'status':'PASS','paper_build':metadata['id'],'client':'official unmodified Minecraft 1.21.11','resource_pack_sha1':digest,'checks':['wrong_password_rejected','correct_password_accepted','password_not_logged','menu_opened','mode_label_click'],'captures':['01-dialog.png','02-filled-wrong.png','03-filled-correct.png','04-welcome.png','05-home.png','06-modes.png','07-after-mode.png']}
 shutil.copyfile(ROOT/'ap/target/Z_AP-1.1.1.jar',REPORT/'tested-plugin.jar')
 result['plugin_sha256']=hashlib.sha256((REPORT/'tested-plugin.jar').read_bytes()).hexdigest()
 (REPORT/'result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2))
except Exception as e:
 (REPORT/'result.json').write_text(json.dumps({'status':'FAIL','error':str(e)},ensure_ascii=False,indent=2));raise
finally:
 if watch_stop is not None:watch_stop.set()
 if client is not None and client.poll() is None:client.terminate();client.wait(timeout=20)
 if server is not None and server.poll() is None:
  try:server.communicate(b'stop\n',timeout=40)
  except subprocess.TimeoutExpired:server.kill();server.wait()
 if http is not None:http.shutdown()
 logs=vanilla.GAME/'logs/latest.log'
 if logs.exists():shutil.copyfile(logs,REPORT/'client.log')
