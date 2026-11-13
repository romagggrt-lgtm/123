from pathlib import Path
import os,sys,json,time,hashlib,subprocess,shutil,urllib.request,traceback
root=Path(__file__).resolve().parents[2];work=root/'tests/expansion/live';work.mkdir(parents=True,exist_ok=True);report=root/'tests/expansion/output';report.mkdir(exist_ok=True);server=None

def get(url):
 with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'ZExpansionQA/1.0'}),timeout=90) as r:return r.read()
def wait_for(test,seconds,label):
 end=time.monotonic()+seconds
 while time.monotonic()<end:
  if test():return
  if server and server.poll() is not None:raise Exception('server exited: '+label)
  time.sleep(.2)
 raise Exception('timeout: '+label)
def send(command):server.stdin.write((command+'\n').encode());server.stdin.flush()
try:
 meta=json.loads(get('https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds/latest'));info=meta['downloads']['server:default'];data=get(info['url']);assert hashlib.sha256(data).hexdigest()==info['checksums']['sha256'];(work/'paper.jar').write_bytes(data)
 plugins=work/'plugins';plugins.mkdir(exist_ok=True)
 for path in ['book/target/Z_Book-1.1.0.jar','tools-plugin/target/A_Tools-1.0.0.jar','lor/target/Z_Lor-1.0.3.jar','plast/target/Z_Plast-1.0.0.jar','ap/target/Z_AP-1.2.0.jar']:
  jar=root/path;shutil.copy(jar,plugins/jar.name)
 (plugins/'Z_Plast').mkdir(exist_ok=True);(plugins/'Z_Plast/config.yml').write_text('web:\n  bind-address: 127.0.0.1\n  port: 8765\n  public-url: http://127.0.0.1:8765\n')
 (work/'eula.txt').write_text('eula=true\n');(work/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25589\nonline-mode=false\nspawn-protection=0\nview-distance=4\nsimulation-distance=4\nlevel-type=minecraft:flat\ndifficulty=peaceful\n')
 subprocess.run(['ffmpeg','-v','error','-y','-f','lavfi','-i','sine=frequency=440:duration=1','-codec:a','libmp3lame',str(work/'sample.mp3')],check=True)
 logpath=report/'paper.log';log=logpath.open('w');server=subprocess.Popen(['java','-Xms512M','-Xmx2G','-jar','paper.jar','nogui'],cwd=work,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT)
 wait_for(lambda:logpath.exists() and 'Done (' in logpath.read_text(errors='replace'),300,'startup')
 logtext=logpath.read_text(errors='replace')
 for name in ['Z_Book','A_Tools','Z_Lor','Z_Plast','Z_AP']:
  assert f'[{name}] Enabling' in logtext, f'{name} not enabled'
  assert f'[{name}] Disabling' not in logtext, f'{name} disabled after startup'
 send('op ExpansionTester')
 with (report/'bot.log').open('w') as out:
  bot=subprocess.Popen(['node',str(root/'tests/expansion/bot.js'),str(work)],cwd=root/'tests/expansion',stdout=out,stderr=subprocess.STDOUT)
  wait_for(lambda:(work/'ready').exists() or bot.poll() is not None,80,'bot joins');assert bot.poll() is None,'bot failed to join'
  send('fill -6 70 -6 6 70 6 minecraft:stone');send('tp ExpansionTester 0.5 71 0.5 0 0');time.sleep(2);(work/'begin').write_text('1')
  assert bot.wait(timeout=125)==0,'bot checks failed'
 result=json.loads((work/'bot-result.json').read_text());(report/'result.json').write_text(json.dumps({'status':'PASS','paper_build':meta['id'],'checks':result['checks']},indent=2))
except Exception as e:
 if (work/'bot-result.json').exists():shutil.copy(work/'bot-result.json',report/'bot-result.json')
 (report/'result.json').write_text(json.dumps({'status':'FAIL','error':str(e),'trace':traceback.format_exc()},indent=2));raise
finally:
 if server and server.poll() is None:
  try:server.communicate(b'stop\n',timeout=30)
  except subprocess.TimeoutExpired:server.kill();server.wait()
