from pathlib import Path
from zipfile import ZipFile
import hashlib,json,os,shutil,subprocess,time,traceback,urllib.request
root=Path(__file__).resolve().parents[1];work=root/'lor/target/brew-server';report=root/'lor/brew-qa';work.mkdir(parents=True,exist_ok=True);report.mkdir(parents=True,exist_ok=True);server=None
for f in ['brew-restart-marker','brew-smoke.json']:(work/f).unlink(missing_ok=True)
def get(u):
 with urllib.request.urlopen(urllib.request.Request(u,headers={'User-Agent':'ZLor-Brewing-CI/1.0.0'}),timeout=60) as r:return r.read()
try:
 meta=json.loads(get('https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds/latest'));dl=meta['downloads']['server:default'];data=get(dl['url']);assert hashlib.sha256(data).hexdigest()==dl['checksums']['sha256'];(work/'paper.jar').write_bytes(data)
 jar=root/'lor/target/Z_Lor-1.0.2.jar';deps=list((Path.home()/'.m2/repository').rglob('*.jar'));classes=work/'qa-classes';classes.mkdir(exist_ok=True)
 with (report/'compile.log').open('w') as log:
  p=subprocess.run(['javac','-proc:none','--release','21','-cp',os.pathsep.join(map(str,[jar]+deps)),'-d',str(classes),str(root/'lor/src/smoke/java/dev/zlor/brewqa/BrewSmoke.java')],stdout=log,stderr=subprocess.STDOUT)
 assert p.returncode==0,'QA compile failed; see compile.log'
 plugins=work/'plugins';plugins.mkdir(exist_ok=True);shutil.copyfile(jar,plugins/jar.name);shutil.copyfile(jar,report/'tested-plugin.jar')
 with ZipFile(plugins/'Brew_QA.jar','w') as z:
  for path in classes.rglob('*.class'):z.write(path,str(path.relative_to(classes)))
  z.writestr('plugin.yml',"name: Brew_QA\nversion: 1.0\nmain: dev.zlor.brewqa.BrewSmoke\napi-version: '1.21.11'\ndepend: [Z_Lor]\n")
 (work/'eula.txt').write_text('eula=true\n');(work/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25594\nonline-mode=false\nspawn-protection=0\nview-distance=5\nsimulation-distance=4\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":63},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\n')
 phases=[]
 for run in [0,1]:
  (work/'brew-smoke.json').unlink(missing_ok=True);bots=None
  with (report/f'server-{run}.log').open('w') as log,(report/f'bots-{run}.log').open('w') as botlog:
   server=subprocess.Popen(['java','-Xms1G','-Xmx3G','-jar','paper.jar','nogui'],cwd=work,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT);deadline=time.monotonic()+300
   while time.monotonic()<deadline and server.poll() is None:
    if run==0 and bots is None and 'Done (' in (report/f'server-{run}.log').read_text(errors='replace'):
     bots=subprocess.Popen(['node',str(root/'tools/zlor_brew_bot.js')],cwd=root/'lor/target/brew-bots',stdout=botlog,stderr=subprocess.STDOUT)
    if (work/'brew-smoke.json').exists():break
    time.sleep(.5)
   if server.poll() is None:
    try:server.communicate(b'stop\n',timeout=40)
    except subprocess.TimeoutExpired:server.kill();server.wait()
   if bots is not None:bots.terminate();bots.wait(timeout=10)
   server=None
  assert (work/'brew-smoke.json').exists(),'No QA report, see server log'
  result=json.loads((work/'brew-smoke.json').read_text());phases.append(result)
  assert result['status']==('RESTART' if run==0 else 'PASS'),result
  if run==0:(work/'brew-restart-marker').write_text('1')
 (report/'result.json').write_text(json.dumps({'status':'PASS','paper_build':meta['id'],'source_commit':os.environ.get('GITHUB_SHA'),'plugin_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'phases':phases},ensure_ascii=False,indent=2))
except Exception as e:
 (report/'result.json').write_text(json.dumps({'status':'FAIL','error':str(e),'trace':traceback.format_exc()},ensure_ascii=False,indent=2));raise
finally:
 if server is not None and server.poll() is None:
  try:server.communicate(b'stop\n',timeout=30)
  except subprocess.TimeoutExpired:server.kill();server.wait()
