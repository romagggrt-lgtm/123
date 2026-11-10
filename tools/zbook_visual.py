from pathlib import Path
from zipfile import ZipFile
from http.server import ThreadingHTTPServer,SimpleHTTPRequestHandler
from functools import partial
import hashlib,json,os,shutil,subprocess,sys,threading,time,traceback,urllib.request,uuid
import nbtlib
from PIL import Image
from Xlib import X,display
from Xlib.ext import xtest
root=Path(__file__).resolve().parents[1];work=root/'book/target/visual-server';report=root/'book/visual-qa';work.mkdir(parents=True,exist_ok=True);report.mkdir(parents=True,exist_ok=True)
(work/'book-ready').unlink(missing_ok=True);(work/'book-lightning').unlink(missing_ok=True)
sys.path.insert(0,str(root/'tools'));import zap_vanilla_client as vanilla
vanilla.GAME=root/'book/target/visual-client';vanilla.REPORT=report
server=client=http=None

def get(url):
 with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'ZBook-QA/1.0'}),timeout=90) as r:return r.read()
def wait_for(f,seconds,label):
 end=time.monotonic()+seconds
 while time.monotonic()<end:
  if f():return
  if server is not None and server.poll() is not None:raise RuntimeError('server stopped: '+label)
  if client is not None and client.poll() is not None:raise RuntimeError('client stopped: '+label)
  time.sleep(.25)
 raise RuntimeError('timeout: '+label)
def send(s):server.stdin.write((s+'\n').encode());server.stdin.flush()
try:
 command=vanilla.prepare();command[command.index('--username')+1]='BookVisual';command[command.index('--quickPlayMultiplayer')+1]='127.0.0.1:25592';digest=bytearray(hashlib.md5(b'OfflinePlayer:BookVisual').digest());digest[6]=(digest[6]&15)|48;digest[8]=(digest[8]&63)|128;command[command.index('--uuid')+1]=uuid.UUID(bytes=bytes(digest)).hex
 nbtlib.File({'servers':nbtlib.List[nbtlib.Compound]([nbtlib.Compound({'name':nbtlib.String('Z_Book Visual QA'),'ip':nbtlib.String('127.0.0.1:25592'),'acceptTextures':nbtlib.Byte(1)})])}).save(vanilla.GAME/'servers.dat')
 (vanilla.GAME/'options.txt').write_text('onboardAccessibility:false\nstartedCleanly:true\nlang:ru_ru\nguiScale:2\nrenderDistance:4\nsimulationDistance:4\nmaxFps:30\nfullscreen:false\nenableVsync:false\nparticles:0\ntutorialStep:none\n')
 meta=json.loads(get('https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds/latest'));download=meta['downloads']['server:default'];data=get(download['url']);assert hashlib.sha256(data).hexdigest()==download['checksums']['sha256'];(work/'paper.jar').write_bytes(data)
 jar=root/'book/target/Z_Book-1.0.0.jar';plugins=work/'plugins';plugins.mkdir(exist_ok=True);shutil.copyfile(jar,plugins/jar.name);classes=work/'qa-classes';classes.mkdir(exist_ok=True)
 jars=[jar]+list((Path.home()/'.m2/repository').rglob('*.jar'));compile=subprocess.run(['javac','-proc:none','--release','21','-cp',os.pathsep.join(map(str,jars)),'-d',str(classes),str(root/'book/src/smoke/java/dev/zbook/qa/BookVisual.java')],capture_output=True,text=True);(report/'compile.log').write_text(compile.stdout+compile.stderr);assert compile.returncode==0
 with ZipFile(plugins/'Z_Book_VisualQA.jar','w') as z:
  for p in classes.rglob('*.class'):z.write(p,str(p.relative_to(classes)))
  z.writestr('plugin.yml',"name: Z_Book_VisualQA\nversion: 1.0.0\nmain: dev.zbook.qa.BookVisual\napi-version: '1.21'\ndepend: [Z_Book]\n")
 pack=root/'dist/Z_Server_Pack_1.12.zip';sha1=hashlib.sha1(pack.read_bytes()).hexdigest();(work/'eula.txt').write_text('eula=true\n');(work/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25592\nonline-mode=false\nspawn-protection=0\nview-distance=4\nsimulation-distance=4\nlevel-type=minecraft:flat\ndifficulty=peaceful\nresource-pack=http://127.0.0.1:25593/'+pack.name+'\nresource-pack-sha1='+sha1+'\nrequire-resource-pack=true\n')
 http=ThreadingHTTPServer(('127.0.0.1',25593),partial(SimpleHTTPRequestHandler,directory=str(pack.parent)));threading.Thread(target=http.serve_forever,daemon=True).start()
 logpath=report/'server.log';log=logpath.open('w');server=subprocess.Popen(['java','-Xms512M','-Xmx2G','-jar','paper.jar','nogui'],cwd=work,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT);wait_for(lambda:'Done (' in logpath.read_text(errors='replace'),300,'server startup');send('op BookVisual');time.sleep(1)
 clientlog=(report/'client-console.log').open('w');client=subprocess.Popen(command,cwd=vanilla.GAME,stdout=clientlog,stderr=subprocess.STDOUT);wait_for(lambda:(work/'book-ready').exists(),240,'client connected');time.sleep(2)
 conn=display.Display(os.environ['DISPLAY']);sc=conn.screen();screen=sc.root
 def click(x,y,button=1):
  xtest.fake_input(conn,X.MotionNotify,x=x,y=y);xtest.fake_input(conn,X.ButtonPress,detail=button);xtest.fake_input(conn,X.ButtonRelease,detail=button);conn.sync()
 def capture(name):
  pix=screen.get_image(0,0,sc.width_in_pixels,sc.height_in_pixels,X.ZPixmap,0xffffffff);Image.frombytes('RGB',(sc.width_in_pixels,sc.height_in_pixels),pix.data,'raw','BGRX').save(report/name)
 click(640,360,3);time.sleep(2);capture('01-index.png');click(600,107);time.sleep(1);capture('02-spell.png');click(600,251);time.sleep(1);capture('03-armed.png');click(640,360,3);time.sleep(1);capture('04-cast.png');wait_for(lambda:(work/'book-lightning').exists(),12,'book button followed by cast');(report/'result.json').write_text(json.dumps({'status':'PASS','checks':['native_book_page_navigation','book_callback_without_confirmation','book_closed_and_right_click_cast'],'paper_build':meta['id'],'resource_pack_sha1':sha1},indent=2))
except Exception as ex:
 (report/'result.json').write_text(json.dumps({'status':'FAIL','error':str(ex),'trace':traceback.format_exc()},indent=2));raise
finally:
 if client is not None and client.poll() is None:client.terminate();client.wait(timeout=20)
 if server is not None and server.poll() is None:
  try:server.communicate(b'stop\n',timeout=30)
  except subprocess.TimeoutExpired:server.kill();server.wait()
 if http is not None:http.shutdown()
 logfile=vanilla.GAME/'logs/latest.log'
 if logfile.exists():shutil.copyfile(logfile,report/'client.log')
