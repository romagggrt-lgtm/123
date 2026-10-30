from pathlib import Path
import hashlib,json,subprocess,time,urllib.request,shutil,traceback
root=Path(__file__).resolve().parents[1];work=root/'morph/target/live';report=root/'morph/live-qa';work.mkdir(parents=True,exist_ok=True);report.mkdir(parents=True,exist_ok=True);server=None
for name in ['ready','begin','bot-result.json']:(work/name).unlink(missing_ok=True)
def get(url):
 with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'ZMorph-CI/1.0.0'}),timeout=60) as r:return r.read()
def wait_for(f,seconds,label):
 deadline=time.monotonic()+seconds
 while time.monotonic()<deadline:
  if f():return
  if server is not None and server.poll() is not None:raise RuntimeError('Server stopped: '+label)
  time.sleep(.25)
 raise RuntimeError('Timed out: '+label)
def send(s):server.stdin.write((s+'\n').encode());server.stdin.flush()
try:
 meta=json.loads(get('https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds/latest'));dl=meta['downloads']['server:default'];data=get(dl['url']);assert hashlib.sha256(data).hexdigest()==dl['checksums']['sha256'];(work/'paper.jar').write_bytes(data)
 (work/'plugins').mkdir(exist_ok=True)
 for p in [root/'morph/target/Z_Morph-1.0.0.jar',root/'morph/target/deps/LibsDisguises-26.10.24.jar',root/'morph/target/deps/packetevents-spigot-2.14.0.jar',root/'dist/Z_Nick-1.0.0.jar']:shutil.copyfile(p,work/'plugins'/p.name)
 (work/'eula.txt').write_text('eula=true\n');(work/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25593\nonline-mode=false\nspawn-protection=0\nview-distance=5\nsimulation-distance=4\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":63},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\n')
 logpath=report/'paper.log';log=logpath.open('w');server=subprocess.Popen(['java','-Xms512M','-Xmx2G','-jar','paper.jar','nogui'],cwd=work,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT);read=lambda:logpath.read_text(errors='replace')
 wait_for(lambda:'Done (' in read(),300,'startup');assert '[Z_Morph] Enabling' in read() and 'Доступно мобов:' in read(),'Plugin did not enable'
 send('op Morpher')
 with (report/'bots.log').open('w') as botlog:
  bot=subprocess.Popen(['node',str(root/'tools/zmorph_bot.js'),str(work)],cwd=root/'morph/target/bots',stdout=botlog,stderr=subprocess.STDOUT)
  wait_for(lambda:(work/'ready').exists(),60,'player join');send('tp MorphObserver Morpher');(work/'begin').write_text('1')
  assert bot.wait(timeout=50)==0,'Bot failed'
 result=json.loads((work/'bot-result.json').read_text());(report/'bot-result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2))
 target=result['targetUUID'].lower();observed=[e for e in result['spawns'] if (e.get('uuid') or '').lower()==target]
 result['observed_target']=observed;names=[e.get('name') for e in observed];assert 'zombie' in names and 'creeper' in names and 'player' in names and not result['disconnected'],result
 text=read();assert '[Z_Morph] Ошибка' not in text and '[Z_Morph] Не удалось' not in text
 jar=root/'morph/target/Z_Morph-1.0.0.jar';shutil.copyfile(jar,report/'tested-plugin.jar')
 (report/'result.json').write_text(json.dumps({'status':'PASS','paper_build':meta['id'],'plugin_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'details':result},ensure_ascii=False,indent=2))
except Exception as e:
 (report/'result.json').write_text(json.dumps({'status':'FAIL','error':str(e),'trace':traceback.format_exc()},ensure_ascii=False,indent=2));raise
finally:
 if server is not None and server.poll() is None:
  try:server.communicate(b'stop\n',timeout=30)
  except subprocess.TimeoutExpired:server.kill();server.wait()
