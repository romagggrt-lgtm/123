#!/usr/bin/env python3
"""Run only in an isolated, disposable CI directory. No user worlds or plugins."""
from pathlib import Path
from zipfile import ZipFile
import hashlib,json,os,shutil,subprocess,time,urllib.request

ROOT=Path(__file__).resolve().parents[1]
WORK=ROOT/'lor/target/native-server'
REPORT=ROOT/'lor/live-qa'
WORK.mkdir(parents=True,exist_ok=True);REPORT.mkdir(parents=True,exist_ok=True)
(REPORT/'latest.json').unlink(missing_ok=True)
def download(url,path):
    with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'ZLor-CI/1.0.1'}),timeout=60) as response:path.write_bytes(response.read())
try:
    metadata=json.loads(urllib.request.urlopen('https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds/latest',timeout=30).read())
    record=metadata['downloads']['server:default'];download(record['url'],WORK/'paper.jar')
    expected=record['checksums']['sha256'];assert hashlib.sha256((WORK/'paper.jar').read_bytes()).hexdigest()==expected
    jars=list((Path.home()/'.m2/repository').rglob('*.jar'))+[ROOT/'lor/target/Z_Lor.jar']
    classes=WORK/'qa-classes';classes.mkdir(exist_ok=True)
    subprocess.run(['javac','-proc:none','--release','21','-cp',os.pathsep.join(map(str,jars)),'-d',str(classes),str(ROOT/'lor/src/smoke/java/dev/zlor/qa/NativeSmoke.java')],check=True)
    plugins=WORK/'plugins';plugins.mkdir(exist_ok=True);shutil.copyfile(ROOT/'lor/target/Z_Lor.jar',plugins/'Z_Lor.jar')
    with ZipFile(plugins/'Z_Lor_QA.jar','w') as zip:
        for path in classes.rglob('*.class'):zip.write(path,str(path.relative_to(classes)))
        zip.writestr('plugin.yml',"name: Z_Lor_QA\nversion: 1.0.1\nmain: dev.zlor.qa.NativeSmoke\napi-version: '1.21.11'\ndepend: [Z_Lor]\n")
    (WORK/'eula.txt').write_text('eula=true\n')
    (WORK/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25585\nonline-mode=false\nview-distance=4\nsimulation-distance=4\nspawn-protection=0\nmax-players=3\nlevel-type=minecraft:flat\ngenerate-structures=false\n')
    npm=ROOT/'lor/target/bots';npm.mkdir(exist_ok=True);shutil.copyfile(ROOT/'tools/lor_live_bot.js',npm/'bot.js')
    subprocess.run(['npm','install','--prefix',str(npm),'--no-audit','--no-fund','mineflayer@4.39.0'],check=True)
    bots=None
    with (REPORT/'server.log').open('w') as log,(REPORT/'bots.log').open('w') as botlog:
        server=subprocess.Popen(['java','-Xms1G','-Xmx3G','-jar','paper.jar','nogui'],cwd=WORK,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT)
        deadline=time.monotonic()+300
        try:
            while time.monotonic()<deadline and server.poll() is None:
                if bots is None and 'Done (' in (REPORT/'server.log').read_text(errors='replace'):bots=subprocess.Popen(['node','bot.js'],cwd=npm,stdout=botlog,stderr=subprocess.STDOUT)
                if (WORK/'native-smoke.json').exists():break
                time.sleep(1)
        finally:
            if server.poll() is None:
                try:server.communicate(b'stop\n',timeout=30)
                except subprocess.TimeoutExpired:server.kill();server.wait()
            if bots is not None:bots.terminate();bots.wait(timeout=10)
    assert (WORK/'native-smoke.json').exists(),'No native smoke report; see server.log / bots.log'
    result=json.loads((WORK/'native-smoke.json').read_text());result['server_sha256']=expected;result['server_build']=metadata.get('id',metadata.get('build'))
    (REPORT/'latest.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    assert result['status']=='PASS',result.get('failure','Native smoke failure')
except Exception as error:
    if not (REPORT/'latest.json').exists():(REPORT/'latest.json').write_text(json.dumps({'status':'BLOCKED','error':str(error),'source_commit':os.environ.get('GITHUB_SHA'),'client_rendered':False},indent=2)+'\n')
    raise
