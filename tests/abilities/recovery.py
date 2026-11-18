from pathlib import Path
import os,subprocess,time,json,traceback,urllib.request,hashlib,shutil
root=Path(__file__).resolve().parents[2];work=root/'tests/abilities/live';report=root/'tests/abilities/output';server=None
for n in ['crash-ready','crash-begin','crash-captured','recovery-result.json']:(work/n).unlink(missing_ok=True)
env=dict(os.environ);env['NODE_PATH']=str(root/'tests/abilities/node_modules')
def wait(fn,secs,label):
 end=time.monotonic()+secs
 while time.monotonic()<end:
  if fn():return
  if server and server.poll() is not None:raise RuntimeError('Paper exited: '+label)
  time.sleep(.2)
 raise RuntimeError('timeout '+label)
def launch(log):
 global server
 server=subprocess.Popen(['java','-Xms512M','-Xmx2G','-jar','paper.jar','nogui'],cwd=work,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT)
def send(s):server.stdin.write((s+'\n').encode());server.stdin.flush()
try:
 work.mkdir(parents=True,exist_ok=True);report.mkdir(parents=True,exist_ok=True)
 if not (work/'paper.jar').exists():
  def get(url):
   with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'ZAbilitiesRecoveryQA/1.0'}),timeout=90) as r:return r.read()
  meta=json.loads(get('https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds/latest'));info=meta['downloads']['server:default'];data=get(info['url']);assert hashlib.sha256(data).hexdigest()==info['checksums']['sha256'];(work/'paper.jar').write_bytes(data)
  plugins=work/'plugins';plugins.mkdir(exist_ok=True);shutil.copy(root/'uluz/target/Z_Uluz-1.0.0.jar',plugins);shutil.copy('/tmp/packetevents.jar',plugins/'packetevents.jar');(work/'eula.txt').write_text('eula=true\n');(work/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25589\nonline-mode=false\nspawn-protection=0\nview-distance=4\nsimulation-distance=4\nlevel-type=minecraft:flat\ndifficulty=peaceful\n')
 logpath=report/'crash-paper.log'
 with logpath.open('w') as log:
  launch(log);wait(lambda:logpath.exists() and 'Done (' in logpath.read_text(errors='replace'),300,'startup')
  send('op Operator')
  with (report/'crash-bot.log').open('w') as botlog:
   bot=subprocess.Popen(['node',str(root/'tests/abilities/crash.js'),str(work),'start'],stdout=botlog,stderr=subprocess.STDOUT,env=env)
   wait(lambda:(work/'crash-ready').exists() or bot.poll() is not None,60,'connect');assert bot.poll() is None
   send('fill -20 70 -20 20 70 20 minecraft:stone');send('tp Operator 0.5 71 0.5');send('tp Victim 8.5 71 0.5');send('gamemode survival Operator');send('clear Operator');send('give Operator diamond 2');time.sleep(2);send('save-all flush');time.sleep(3);(work/'crash-begin').write_text('1')
   wait(lambda:(work/'crash-captured').exists() or bot.poll() is not None,30,'capture');assert bot.poll() is None
   server.kill();server.wait();bot.terminate();bot.wait(timeout=10)
 logpath=report/'recovery-paper.log'
 with logpath.open('w') as log:
  launch(log);wait(lambda:logpath.exists() and 'Done (' in logpath.read_text(errors='replace'),300,'restart')
  with (report/'recovery-bot.log').open('w') as botlog:
   bot=subprocess.Popen(['node',str(root/'tests/abilities/crash.js'),str(work),'verify'],stdout=botlog,stderr=subprocess.STDOUT,env=env);assert bot.wait(timeout=60)==0,'player state not restored'
  result=json.loads((work/'recovery-result.json').read_text());(report/'recovery.json').write_text(json.dumps({'status':'PASS',**result},indent=2))
except Exception as e:
 (report/'recovery.json').write_text(json.dumps({'status':'FAIL','error':str(e),'trace':traceback.format_exc()},indent=2));raise
finally:
 if server and server.poll() is None:
  try:server.communicate(b'stop\n',timeout=30)
  except subprocess.TimeoutExpired:server.kill();server.wait()
