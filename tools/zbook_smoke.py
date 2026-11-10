from pathlib import Path
from zipfile import ZipFile
import hashlib,json,os,shutil,subprocess,time,traceback,urllib.request
root=Path(__file__).resolve().parents[1];work=root/'book/target/live';report=root/'book/live-qa';work.mkdir(parents=True,exist_ok=True);report.mkdir(parents=True,exist_ok=True);server=None
for name in ['ready','begin','cast-ready','cast-set','target-ready','target-set','restart-ready','restart-done','bot-result.json']:(work/name).unlink(missing_ok=True)
def get(url):
 with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'ZBook-QA/1.0'}),timeout=60) as r:return r.read()
def wait_for(f,seconds,label):
 end=time.monotonic()+seconds
 while time.monotonic()<end:
  if f():return
  if server is not None and server.poll() is not None:raise RuntimeError('server stopped: '+label)
  time.sleep(.25)
 raise RuntimeError('timeout: '+label)
def send(s):server.stdin.write((s+'\n').encode());server.stdin.flush()
try:
 meta=json.loads(get('https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds/latest'));dl=meta['downloads']['server:default'];data=get(dl['url']);assert hashlib.sha256(data).hexdigest()==dl['checksums']['sha256'];(work/'paper.jar').write_bytes(data)
 plugins=work/'plugins';plugins.mkdir(exist_ok=True);jar=root/'book/target/Z_Book-1.0.0.jar';shutil.copyfile(jar,plugins/jar.name);(plugins/'Z_Book').mkdir(exist_ok=True);(plugins/'Z_Book/config.yml').write_text('spells:\n  ekspanor_fanfiction:\n    cooldown-seconds: 180\n')
 classes=work/'probe-classes';classes.mkdir(exist_ok=True);jars=[jar]+list((Path.home()/'.m2/repository').rglob('*.jar'))
 with (report/'compile.log').open('w') as log:r=subprocess.run(['javac','-proc:none','--release','21','-cp',os.pathsep.join(map(str,jars)),'-d',str(classes),str(root/'book/src/smoke/java/dev/zbook/qa/BookProbe.java')],stdout=log,stderr=subprocess.STDOUT)
 assert r.returncode==0,'probe compilation failed'
 with ZipFile(plugins/'Z_Book_Probe.jar','w') as z:
  for p in classes.rglob('*.class'):z.write(p,str(p.relative_to(classes)))
  z.writestr('plugin.yml',"name: Z_Book_Probe\nversion: 1.0.0\nmain: dev.zbook.qa.BookProbe\napi-version: '1.21'\ndepend: [Z_Book]\n")
 (work/'eula.txt').write_text('eula=true\n');(work/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25591\nonline-mode=false\nspawn-protection=0\nview-distance=4\nsimulation-distance=4\nlevel-type=minecraft:flat\ndifficulty=peaceful\n')
 logpath=report/'paper.log';log=logpath.open('w');server=subprocess.Popen(['java','-Xms512M','-Xmx2G','-jar','paper.jar','nogui'],cwd=work,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT)
 wait_for(lambda:'Done (' in logpath.read_text(errors='replace'),300,'startup');assert '[Z_Book] Enabling' in logpath.read_text(errors='replace');send('op BookTester')
 with (report/'bots.log').open('w') as output:
  bot=subprocess.Popen(['node',str(root/'tools/zbook_bot.js'),str(work)],cwd=root/'book/target/bots',stdout=output,stderr=subprocess.STDOUT)
  wait_for(lambda:(work/'ready').exists(),80,'bot joined');send('fill -6 70 -6 6 70 6 minecraft:stone');send('setblock 2 71 2 minecraft:crafting_table');send('tp BookTester 0.5 71 0.5 0 -90');time.sleep(2);(work/'begin').write_text('1')
  wait_for(lambda:(work/'cast-ready').exists() or bot.poll() is not None,120,'ready for non-admin cast')
  if bot.poll() is None:send('tp BookTester 0.5 71 0.5 0 -90');send('deop BookTester');time.sleep(1);(work/'cast-set').write_text('1')
  wait_for(lambda:(work/'target-ready').exists() or bot.poll() is not None,120,'ready for strike')
  if bot.poll() is None:send('tp BookTester 0.5 71 0.5 0 90');time.sleep(1);(work/'target-set').write_text('1')
  wait_for(lambda:(work/'restart-ready').exists() or bot.poll() is not None,80,'before server restart')
  if bot.poll() is None:
   send('stop');server.wait(timeout=40);log.close();logpath=report/'paper-restart.log';log=logpath.open('w');server=subprocess.Popen(['java','-Xms512M','-Xmx2G','-jar','paper.jar','nogui'],cwd=work,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT);wait_for(lambda:'Done (' in logpath.read_text(errors='replace'),300,'restart');(work/'restart-done').write_text('1')
  assert bot.wait(timeout=80)==0,'bot failed: see bots.log'
 result=json.loads((work/'bot-result.json').read_text());shutil.copyfile(jar,report/'tested-plugin.jar');(report/'result.json').write_text(json.dumps({'status':'PASS','paper_build':meta['id'],'plugin_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'checks':result['checks']},indent=2))
except Exception as ex:
 if (work/'bot-result.json').exists():shutil.copyfile(work/'bot-result.json',report/'bot-result.json')
 (report/'result.json').write_text(json.dumps({'status':'FAIL','error':str(ex),'trace':traceback.format_exc()},indent=2));raise
finally:
 if server is not None and server.poll() is None:
  try:server.communicate(b'stop\n',timeout=30)
  except subprocess.TimeoutExpired:server.kill();server.wait()
