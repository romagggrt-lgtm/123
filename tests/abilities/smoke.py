from pathlib import Path
import os,sys,json,time,hashlib,subprocess,shutil,urllib.request,traceback
root=Path(__file__).resolve().parents[2];work=root/'tests/abilities/live';work.mkdir(parents=True,exist_ok=True);report=root/'tests/abilities/output';report.mkdir(exist_ok=True);server=None
def get(url):
 with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'ZAbilitiesQA/1.0'}),timeout=90) as r:return r.read()
def wait_for(test,seconds,label):
 end=time.monotonic()+seconds
 while time.monotonic()<end:
  if test():return
  if server and server.poll() is not None:raise Exception('server exited: '+label)
  time.sleep(.2)
 raise Exception('timeout: '+label)
def send(c):server.stdin.write((c+'\n').encode());server.stdin.flush()
try:
 meta=json.loads(get('https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds/latest'));info=meta['downloads']['server:default'];data=get(info['url']);assert hashlib.sha256(data).hexdigest()==info['checksums']['sha256'];(work/'paper.jar').write_bytes(data)
 plugins=work/'plugins';plugins.mkdir(exist_ok=True)
 for path in ['dist/Z_Book-1.2.0.jar','dist/Z_Lor-1.1.0.jar','dist/Z_Plast-1.2.0.jar','uluz/target/Z_Uluz-1.0.1.jar','tests/abilities/probe/target/Probe-1.jar']:
  jar=root/path;shutil.copy(jar,plugins/jar.name)
 shutil.copy('/tmp/packetevents.jar',plugins/'packetevents.jar')
 config=plugins/'Z_Plast/config.yml';config.parent.mkdir(exist_ok=True);config.write_text('web:\n  bind-address: 127.0.0.1\n  port: 18865\n  public-url: http://127.0.0.1:18865\n')
 shutil.copy(root/'tests/zplast/sample.mp3',work/'sample.mp3')
 (work/'eula.txt').write_text('eula=true\n');(work/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25589\nonline-mode=false\nspawn-protection=0\nview-distance=4\nsimulation-distance=4\nlevel-type=minecraft:flat\ndifficulty=normal\n')
 logpath=report/'paper.log';log=logpath.open('w');server=subprocess.Popen(['java','-Xms512M','-Xmx2G','-jar','paper.jar','nogui'],cwd=work,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT)
 wait_for(lambda:logpath.exists() and 'Done (' in logpath.read_text(errors='replace'),300,'startup');logtext=logpath.read_text(errors='replace')
 for name in ['Z_Book','Z_Lor','Z_Plast','Z_Uluz','AbilitiesProbe']:
  assert f'[{name}] Enabling' in logtext and f'[{name}] Disabling' not in logtext, f'{name} not enabled'
 send('op Operator');send('op Observer');send('gamerule doMobSpawning false')
 with (report/'bot.log').open('w') as out:
  env=dict(os.environ);env['NODE_PATH']=str(root/'tests/abilities/node_modules');bot=subprocess.Popen(['node',str(root/'tests/abilities/bot.js'),str(work)],cwd=root/'tests/abilities',stdout=out,stderr=subprocess.STDOUT,env=env)
  wait_for(lambda:(work/'ready').exists() or bot.poll() is not None,100,'bots join');assert bot.poll() is None,'bots failed to join'
  send('fill -20 70 -20 20 70 20 minecraft:stone');send('tp Operator 0.5 71 0.5');send('tp Victim 8.5 71 0.5');send('tp Observer 4.5 71 5.5');send('clear Operator');send('give Operator minecraft:diamond 2');send('clear Victim');send('give Victim minecraft:dirt 12');time.sleep(3);(work/'begin').write_text('1')
  assert bot.wait(timeout=160)==0,'bot checks failed'
 checks=json.loads((work/'bot-result.json').read_text());logtext=logpath.read_text(errors='replace');checks['checks']['place_actor']= 'PLACE_ACTOR=Victim' in logtext;checks['checks']['break_actor']='BREAK_ACTOR=Victim' in logtext
 if not all(checks['checks'].values()):raise AssertionError('Some checks failed: '+str(checks['checks']))
 (report/'result.json').write_text(json.dumps({'status':'PASS','paper_build':meta['id'],**checks},indent=2))
except Exception as e:
 if (work/'bot-result.json').exists():shutil.copy(work/'bot-result.json',report/'bot-result.json')
 (report/'result.json').write_text(json.dumps({'status':'FAIL','error':str(e),'trace':traceback.format_exc()},indent=2));raise
finally:
 if server and server.poll() is None:
  try:server.communicate(b'stop\n',timeout=30)
  except subprocess.TimeoutExpired:server.kill();server.wait()
