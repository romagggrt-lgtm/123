#!/usr/bin/env python3
"""Disposable Paper 1.21.11 / Java 21, three clients, real full-server restart."""
from pathlib import Path
from zipfile import ZipFile
from functools import partial
from http.server import ThreadingHTTPServer,SimpleHTTPRequestHandler
import hashlib,json,os,subprocess,shutil,time,threading,urllib.request
from banya_vanilla_client import prepare,launch,capture_controller
ROOT=Path(__file__).resolve().parents[1];WORK=ROOT/'banya/target/native-server';REPORT=ROOT/'banya/live-qa'
WORK.mkdir(parents=True,exist_ok=True);REPORT.mkdir(parents=True,exist_ok=True)
for path in REPORT.iterdir():
 if path.is_file() and path.name not in {'build.log','prepare.log','runner.log'}:path.unlink()
def download(url,path):
 with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'ZBanya-CI/1.0.0'}),timeout=60) as r:path.write_bytes(r.read())
try:
 shutil.copyfile(ROOT/'banya/target/Z_Banya.jar',REPORT/'tested-plugin.jar')
 prepare()
 meta=json.loads(urllib.request.urlopen('https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds/latest',timeout=30).read());record=meta['downloads']['server:default'];download(record['url'],WORK/'paper.jar');expected=record['checksums']['sha256'];assert hashlib.sha256((WORK/'paper.jar').read_bytes()).hexdigest()==expected
 jars=[ROOT/'banya/target/Z_Banya.jar']+list((Path.home()/'.m2/repository/io/papermc/paper/paper-api/1.21.11-R0.1-SNAPSHOT').glob('*.jar'))+[p for p in (Path.home()/'.m2/repository').rglob('*.jar') if 'paper-api' not in p.parts]
 classes=WORK/'qa-classes';classes.mkdir(exist_ok=True)
 with (REPORT/'compile.log').open('w') as log:
  r=subprocess.run(['javac','-proc:none','--release','21','-cp',os.pathsep.join(map(str,jars)),'-d',str(classes),str(ROOT/'banya/src/smoke/java/dev/zbanya/qa/NativeSmoke.java')],stdout=log,stderr=subprocess.STDOUT)
 assert r.returncode==0,'Native QA compilation failed: compile.log'
 plugins=WORK/'plugins';plugins.mkdir(exist_ok=True);shutil.copyfile(ROOT/'banya/target/Z_Banya.jar',plugins/'Z_Banya.jar')
 with ZipFile(plugins/'Z_Banya_QA.jar','w') as z:
  for path in classes.rglob('*.class'):z.write(path,str(path.relative_to(classes)))
  z.writestr('plugin.yml',"name: Z_Banya_QA\nversion: 1.0.0\nmain: dev.zbanya.qa.NativeSmoke\napi-version: '1.21.11'\ndepend: [Z_Banya]\n")
 (WORK/'eula.txt').write_text('eula=true\n')
 pack=ROOT/'dist/Z_Server_Pack_1.09.zip'
 (WORK/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25585\nonline-mode=false\nview-distance=6\nsimulation-distance=4\nspawn-protection=0\nmax-players=3\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":127},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\ngenerate-structures=false\nresource-pack=http://127.0.0.1:25586/'+pack.name+'\nresource-pack-sha1='+hashlib.sha1(pack.read_bytes()).hexdigest()+'\nrequire-resource-pack=true\n')
 http=ThreadingHTTPServer(('127.0.0.1',25586),partial(SimpleHTTPRequestHandler,directory=str(pack.parent)));threading.Thread(target=http.serve_forever,daemon=True).start()
 npm=ROOT/'banya/target/bots';npm.mkdir(exist_ok=True);shutil.copyfile(ROOT/'tools/banya_live_bot.js',npm/'bot.js');subprocess.run(['npm','install','--prefix',str(npm),'--no-audit','--no-fund','mineflayer@4.39.0'],check=True)
 reports=[]
 for boot in [1,2]:
  (WORK/'banya-native.json').unlink(missing_ok=True);bots=None;client=None;stop=threading.Event();capture=None
  with (REPORT/f'server-phase{boot}.log').open('w') as log,(REPORT/f'bots-phase{boot}.log').open('w') as botlog:
   server=subprocess.Popen(['java','-Xms1G','-Xmx3G','-jar','paper.jar','nogui'],cwd=WORK,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT);deadline=time.monotonic()+360
   try:
    while time.monotonic()<deadline and server.poll() is None:
     if bots is None and 'Done (' in (REPORT/f'server-phase{boot}.log').read_text(errors='replace'):
      bots=subprocess.Popen(['node','bot.js'],cwd=npm,stdout=botlog,stderr=subprocess.STDOUT);client=launch();capture=threading.Thread(target=capture_controller,args=(stop,),daemon=True);capture.start()
     if (WORK/'banya-native.json').exists():break
     time.sleep(.5)
   finally:
    if server.poll() is None:
     try:server.communicate(b'stop\n',timeout=30)
     except subprocess.TimeoutExpired:server.kill();server.wait()
    if bots is not None:bots.terminate();bots.wait(timeout=10)
    if client is not None:
     stop.set();capture.join(timeout=5);client.terminate();client.wait(timeout=10)
     for source,name in [(ROOT/'banya/target/vanilla-client/logs/latest.log',f'client-phase{boot}.log'),(REPORT/'vanilla-console.log',f'console-phase{boot}.log')]:
      if source.exists():shutil.copyfile(source,REPORT/name)
  assert (WORK/'banya-native.json').exists(),f'No native report for boot {boot}'
  result=json.loads((WORK/'banya-native.json').read_text());reports.append(result)
  (REPORT/f'phase{boot}.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
  assert result['status']==('RESTART' if boot==1 else 'PASS'),result.get('failure','Unexpected phase status')
 packets=[]
 if (npm/'sound-packets.jsonl').exists():
  shutil.copyfile(npm/'sound-packets.jsonl',REPORT/'sound-packets.jsonl');packets=[json.loads(line) for line in (REPORT/'sound-packets.jsonl').read_text().splitlines()]
 outside=[p for p in packets if p['username']=='BanyaOutside'];inside=[p for p in packets if p['username']=='BanyaTester'];plays=[p for p in inside if 'stop' not in p['packet']];stops=[p for p in inside if 'stop' in p['packet']]
 assert not outside,'Ambient leaked to outside client';assert len(plays)>=2 and len(stops)>=2,'Private looping/stop packets missing'
 combined={'status':'PASS','source_commit':os.environ.get('GITHUB_SHA'),'server_sha256':expected,'server_build':meta['id'],'phases':reports,'sound_packets':{'inside_plays':len(plays),'inside_stops':len(stops),'outside':len(outside)},'pack_sha1':hashlib.sha1(pack.read_bytes()).hexdigest(),'plugin_sha256':hashlib.sha256((REPORT/'tested-plugin.jar').read_bytes()).hexdigest(),'client':'Unmodified official Minecraft 1.21.11','client_rendered':'See PNG captures; manual review required'}
 (REPORT/'latest.json').write_text(json.dumps(combined,ensure_ascii=False,indent=2)+'\n')
except Exception as error:
 (REPORT/'latest.json').write_text(json.dumps({'status':'FAIL','error':str(error),'source_commit':os.environ.get('GITHUB_SHA')},indent=2)+'\n');raise
