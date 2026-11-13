from pathlib import Path
import os,json,time,hashlib,subprocess,shutil,urllib.request,traceback
root=Path(__file__).resolve().parents[2];work=root/'tests/zplast/live';work.mkdir(parents=True,exist_ok=True);report=root/'tests/zplast/output';report.mkdir(exist_ok=True);server=None

def get(url):
 with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'ZPlast-QA/1.1'}),timeout=90) as r:return r.read()
def wait_for(test,seconds,label):
 end=time.monotonic()+seconds
 while time.monotonic()<end:
  if test():return
  if server and server.poll() is not None:raise RuntimeError('server stopped: '+label)
  time.sleep(.2)
 raise RuntimeError('timeout: '+label)
def send(command):server.stdin.write((command+'\n').encode());server.stdin.flush()
try:
 meta=json.loads(get('https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds/latest'));info=meta['downloads']['server:default'];data=get(info['url']);assert hashlib.sha256(data).hexdigest()==info['checksums']['sha256'];(work/'paper.jar').write_bytes(data)
 plugins=work/'plugins';plugins.mkdir(exist_ok=True);jar=root/'plast/target/Z_Plast-1.1.0.jar';shutil.copy(jar,plugins/jar.name)
 config=plugins/'Z_Plast/config.yml';config.parent.mkdir(exist_ok=True);config.write_text('web:\n  bind-address: 127.0.0.1\n  port: 18865\n  public-url: http://127.0.0.1:18865\nconverter:\n  ffmpeg-path: /missing/ffmpeg\n  ffprobe-path: /missing/ffprobe\n')
 (work/'eula.txt').write_text('eula=true\n');(work/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25589\nonline-mode=false\nspawn-protection=0\nview-distance=4\nsimulation-distance=4\nlevel-type=minecraft:flat\ndifficulty=peaceful\n')
 shutil.copy(root/'tests/zplast/sample.mp3',work/'sample.mp3');logpath=report/'paper.log';log=logpath.open('w');env=dict(os.environ);env['PATH']='/no-ffmpeg-available';java=str(Path(os.environ['JAVA_HOME'])/'bin/java')
 server=subprocess.Popen([java,'-Xms512M','-Xmx2G','-jar','paper.jar','nogui'],cwd=work,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,env=env)
 wait_for(lambda:logpath.exists() and 'Done (' in logpath.read_text(errors='replace'),300,'startup');logs=logpath.read_text(errors='replace');assert '[Z_Plast] Enabling' in logs and '[Z_Plast] Disabling' not in logs
 send('op PlastTester')
 with (report/'bot.log').open('w') as out:
  bot=subprocess.Popen(['node',str(root/'tests/zplast/bot.js'),str(work)],cwd=root/'tests/zplast',stdout=out,stderr=subprocess.STDOUT)
  wait_for(lambda:(work/'ready').exists() or bot.poll() is not None,80,'bot');assert bot.poll() is None,'bot failed to join'
  send('fill -6 70 -6 6 70 6 minecraft:stone');send('tp PlastTester 0.5 71 0.5 0 0');time.sleep(2);(work/'begin').write_text('1');assert bot.wait(timeout=170)==0,'bot checks failed'
 result=json.loads((work/'bot-result.json').read_text());(report/'result.json').write_text(json.dumps({'status':'PASS','paper_build':meta['id'],'ffmpeg_path':'/missing/ffmpeg','ffprobe_path':'/missing/ffprobe','checks':result['checks']},ensure_ascii=False,indent=2))
except Exception as e:
 if (work/'bot-result.json').exists():shutil.copy(work/'bot-result.json',report/'bot-result.json')
 (report/'result.json').write_text(json.dumps({'status':'FAIL','error':str(e),'trace':traceback.format_exc()},indent=2));raise
finally:
 if server and server.poll() is None:
  try:server.communicate(b'stop\n',timeout=30)
  except subprocess.TimeoutExpired:server.kill();server.wait()
