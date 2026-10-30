from pathlib import Path
import hashlib,json,subprocess,time,urllib.request,shutil,traceback
root=Path(__file__).resolve().parents[1];work=root/'ap/target/live';report=root/'ap/live-qa';work.mkdir(parents=True,exist_ok=True);report.mkdir(parents=True,exist_ok=True);server=None
for name in ['ready','begin','bot-result.json']:(work/name).unlink(missing_ok=True)
def get(url):
 with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'ZAP-CI/1.0'}),timeout=60) as r:return r.read()
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
 paths=[root/'ap/target/Z_AP-1.0.0.jar',root/'dist/Z_Morph-1.0.0.jar',root/'ap/target/deps/LibsDisguises.jar',root/'ap/target/deps/packetevents.jar',root/'dist/Z_Nick-1.0.0.jar',root/'dist/Z_Check-1.0.0.jar',root/'dist/Z_Banya-1.0.2.jar',root/'dist/InventoryCache_1.0.1.jar',root/'ap/test-only/Nevidimka-1.0.2.jar']
 for p in paths:shutil.copyfile(p,work/'plugins'/p.name)
 (work/'eula.txt').write_text('eula=true\n');(work/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25594\nonline-mode=false\nspawn-protection=0\nview-distance=5\nsimulation-distance=4\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":63},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\n')
 logpath=report/'paper.log';log=logpath.open('w');server=subprocess.Popen(['java','-Xms512M','-Xmx2G','-jar','paper.jar','nogui'],cwd=work,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT);read=lambda:logpath.read_text(errors='replace')
 wait_for(lambda:'Done (' in read(),300,'startup');assert '[Z_AP] Enabling' in read(),'Panel failed to enable'
 send('op APAdmin');send('op APLocked')
 with (report/'bots.log').open('w') as botlog:
  bot=subprocess.Popen(['node',str(root/'tools/zap_bot.js'),str(work)],cwd=root/'ap/target/bots',stdout=botlog,stderr=subprocess.STDOUT)
  wait_for(lambda:(work/'ready').exists(),60,'players join');send('give APAdmin diamond 3');(work/'begin').write_text('1')
  assert bot.wait(timeout=90)==0,'Bot failed; see bots.log and bot-result.json'
 result=json.loads((work/'bot-result.json').read_text());(report/'bot-result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2))
 auth=(work/'plugins/Z_AP/auth.yml').read_text();assert '324657' not in auth and 'locked-until:' in auth,'Credential or lockout storage failed'
 jar=root/'ap/target/Z_AP-1.0.0.jar';shutil.copyfile(jar,report/'tested-plugin.jar')
 (report/'result.json').write_text(json.dumps({'status':'PASS','paper_build':meta['id'],'plugin_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'checks':result['checks']},ensure_ascii=False,indent=2))
except Exception as e:
 if (work/'bot-result.json').exists():shutil.copyfile(work/'bot-result.json',report/'bot-result.json')
 (report/'result.json').write_text(json.dumps({'status':'FAIL','error':str(e),'trace':traceback.format_exc()},ensure_ascii=False,indent=2));raise
finally:
 if server is not None and server.poll() is None:
  try:server.communicate(b'stop\n',timeout=30)
  except subprocess.TimeoutExpired:server.kill();server.wait()
