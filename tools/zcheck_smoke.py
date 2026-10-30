from pathlib import Path
import hashlib,json,subprocess,time,urllib.request,shutil,traceback
root=Path(__file__).resolve().parents[1];work=root/'check/target/live';report=root/'check/live-qa';work.mkdir(parents=True,exist_ok=True);report.mkdir(parents=True,exist_ok=True);server=None
for f in work.glob('*'):
 if f.name in {'readyone','readytwo','freezeone','freezetwo','release_request','released','bot-one.json','bot-two.json'}:f.unlink()
def get(url):
 with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'ZCheck-CI/1.0.0'}),timeout=60) as r:return r.read()
def await_test(predicate,seconds,label):
 deadline=time.monotonic()+seconds
 while time.monotonic()<deadline:
  if predicate():return
  if server is not None and server.poll() is not None:raise RuntimeError('Server stopped: '+label)
  time.sleep(.25)
 raise RuntimeError('Timed out: '+label)
def send(cmd):server.stdin.write((cmd+'\n').encode());server.stdin.flush()
try:
 meta=json.loads(get('https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds/latest'));dl=meta['downloads']['server:default'];data=get(dl['url']);assert hashlib.sha256(data).hexdigest()==dl['checksums']['sha256'];(work/'paper.jar').write_bytes(data)
 (work/'plugins').mkdir(exist_ok=True);jar=root/'check/target/Z_Check-1.0.0.jar';shutil.copyfile(jar,work/'plugins'/jar.name);shutil.copyfile(jar,report/'tested-plugin.jar')
 (work/'eula.txt').write_text('eula=true\n');(work/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25592\nonline-mode=false\nspawn-protection=0\nview-distance=4\nsimulation-distance=4\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":63},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\n')
 results=[]
 for phase in ['one','two']:
  logfile=report/f'paper-{phase}.log';log=logfile.open('w');server=subprocess.Popen(['java','-Xms512M','-Xmx2G','-jar','paper.jar','nogui'],cwd=work,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT)
  read=lambda:logfile.read_text(errors='replace')
  await_test(lambda:'Done (' in read(),300,'startup')
  if phase=='two':assert 'Загружено активных проверок: 1' in read(),'Check was not restored after restart'
  with (report/f'bot-{phase}.log').open('w') as botlog:
   bot=subprocess.Popen(['node',str(root/'tools/zcheck_bot.js'),str(work),phase],cwd=root/'check/target/bots',stdout=botlog,stderr=subprocess.STDOUT)
   await_test(lambda:(work/('ready'+phase)).exists(),60,'player join')
   if phase=='one':
    send('zc CheckTester');await_test(lambda:'вызван на проверку' in read(),20,'start check')
   (work/('freeze'+phase)).write_text('1')
   if phase=='one':send('tp CheckTester 20 70 20');send('damage CheckTester 10')
   if phase=='two':
    await_test(lambda:(work/'release_request').exists(),25,'release request');send('zc CheckTester');await_test(lambda:'проверка игрока checktester завершена' in read().lower(),20,'release');(work/'released').write_text('1')
   assert bot.wait(timeout=60)==0,'bot failed'
  result=json.loads((work/f'bot-{phase}.json').read_text());results.append(result)
  assert result['freezeDistance']<.3 and result['health']>=19,result
  if phase=='two':assert result['freeDistance']>.5,result
  if phase=='one':await_test(lambda:'вышел с проверки' in read(),20,'logout notice')
  server.communicate(b'stop\n',timeout=60);log.close();server=None
 (report/'result.json').write_text(json.dumps({'status':'PASS','server_build':meta['id'],'plugin_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'phases':results},ensure_ascii=False,indent=2))
except Exception as e:
 (report/'result.json').write_text(json.dumps({'status':'FAIL','error':str(e),'trace':traceback.format_exc()},ensure_ascii=False,indent=2));raise
finally:
 if server is not None and server.poll() is None:
  try:server.communicate(b'stop\n',timeout=30)
  except subprocess.TimeoutExpired:server.kill();server.wait()
