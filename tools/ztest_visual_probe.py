#!/usr/bin/env python3
from pathlib import Path
from zipfile import ZipFile
from PIL import Image
from Xlib import X,display
from Xlib.ext import xtest
import hashlib,json,os,shutil,subprocess,time,urllib.request
from lor_vanilla_client import prepare,launch,GAME
ROOT=Path(__file__).resolve().parents[1];WORK=ROOT/'z_test/target/visual-server';REPORT=ROOT/'z_test/visual-qa'
WORK.mkdir(parents=True,exist_ok=True);REPORT.mkdir(parents=True,exist_ok=True)
try:
 prepare()
 resource=GAME/'resourcepacks';resource.mkdir(exist_ok=True);shutil.copyfile(ROOT/'dist/Z_Server_Pack_1.17.zip',resource/'zserver.zip')
 with (GAME/'options.txt').open('a') as f:f.write('resourcePacks:["file/zserver.zip"]\n')
 req=urllib.request.Request('https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds/latest',headers={'User-Agent':'Z_Test visual QA'})
 meta=json.load(urllib.request.urlopen(req,timeout=45));record=meta['downloads']['server:default']
 req=urllib.request.Request(record['url'],headers={'User-Agent':'Z_Test visual QA'})
 (WORK/'paper.jar').write_bytes(urllib.request.urlopen(req,timeout=90).read());assert hashlib.sha256((WORK/'paper.jar').read_bytes()).hexdigest()==record['checksums']['sha256']
 deps=[ROOT/'z_test/target/Z_Test-1.0.1.jar']+list((Path.home()/'.m2/repository').rglob('*.jar'));classes=WORK/'classes';classes.mkdir(exist_ok=True)
 with (REPORT/'compile.log').open('w') as log:
  proc=subprocess.run(['javac','-proc:none','--release','21','-cp',os.pathsep.join(map(str,deps)),'-d',str(classes),str(ROOT/'z_test/src/visual/java/dev/ztest/visual/VisualProbe.java')],stdout=log,stderr=subprocess.STDOUT)
 assert proc.returncode==0,'compile failed'
 plugins=WORK/'plugins';plugins.mkdir(exist_ok=True);shutil.copyfile(ROOT/'z_test/target/Z_Test-1.0.1.jar',plugins/'Z_Test.jar')
 with ZipFile(plugins/'VisualProbe.jar','w') as z:
  for c in classes.rglob('*.class'):z.write(c,str(c.relative_to(classes)))
  z.writestr('plugin.yml',"name: VisualProbe\nversion: 1\nmain: dev.ztest.visual.VisualProbe\napi-version: '1.21.11'\ndepend: [Z_Test]\n")
 (WORK/'eula.txt').write_text('eula=true\n');(WORK/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25585\nonline-mode=false\nview-distance=5\nsimulation-distance=4\nspawn-protection=0\nallow-flight=true\nmax-tick-time=-1\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\ngenerate-structures=false\n')
 with (REPORT/'server.log').open('w') as log:
  server=subprocess.Popen(['java','-Xms512M','-Xmx2G','-jar','paper.jar','nogui'],cwd=WORK,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT);client=None
  try:
   deadline=time.monotonic()+360
   while time.monotonic()<deadline and server.poll() is None:
    if client is None and 'Done (' in (REPORT/'server.log').read_text(errors='replace'):client=launch()
    if (WORK/'visual-error.txt').exists():raise RuntimeError((WORK/'visual-error.txt').read_text())
    if (WORK/'visual-ready.txt').exists():
     time.sleep(5);conn=display.Display(os.environ['DISPLAY']);screen=conn.screen();pixels=screen.root.get_image(0,0,screen.width_in_pixels,screen.height_in_pixels,X.ZPixmap,0xffffffff)
     Image.frombytes('RGB',(screen.width_in_pixels,screen.height_in_pixels),pixels.data,'raw','BGRX').save(REPORT/'ghost.png')
     windows=screen.root.query_tree().children
     for win in windows:
      name=win.get_wm_name()
      if name and 'Minecraft' in name:
       win.set_input_focus(X.RevertToParent,X.CurrentTime);conn.sync();break
     time.sleep(1)
     for _ in range(5):
      xtest.fake_input(conn,X.ButtonPress,detail=1);xtest.fake_input(conn,X.ButtonRelease,detail=1);conn.sync();time.sleep(1)
     break
    time.sleep(1)
   assert (REPORT/'ghost.png').exists(),'visual timeout'
  finally:
   if client is not None:client.terminate();client.wait(timeout=15)
   if server.poll() is None:
    try:server.communicate(b'stop\n',timeout=40)
    except subprocess.TimeoutExpired:server.kill();server.wait()
 logs=GAME/'logs/latest.log'
 if logs.exists():shutil.copyfile(logs,REPORT/'client.log')
 (REPORT/'result.json').write_text(json.dumps({'status':'CAPTURED','probe':(WORK/'visual-ready.txt').read_text(),'source_commit':os.environ.get('GITHUB_SHA'),'client':'Minecraft Java 1.21.11','pack':'Z_Server_Pack_1.17.zip','attack_event':(WORK/'visual-damage.txt').read_text() if (WORK/'visual-damage.txt').exists() else 'NONE','swing_event':(WORK/'visual-swing.txt').read_text() if (WORK/'visual-swing.txt').exists() else 'NONE','death_event':(WORK/'visual-death.txt').read_text() if (WORK/'visual-death.txt').exists() else 'NONE'},indent=2))
except Exception as e:
 (REPORT/'result.json').write_text(json.dumps({'status':'FAIL','error':str(e),'source_commit':os.environ.get('GITHUB_SHA')},indent=2));raise
