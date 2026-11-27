#!/usr/bin/env python3
"""Disposable Paper 1.21.11 server; two full boots and an offline test client."""
from pathlib import Path
from zipfile import ZipFile
import hashlib,json,os,shutil,subprocess,time,urllib.request

ROOT=Path(__file__).resolve().parents[1]
WORK=ROOT/'z_test/target/native-server';REPORT=ROOT/'z_test/qa'
WORK.mkdir(parents=True,exist_ok=True);REPORT.mkdir(parents=True,exist_ok=True)
results=[]
try:
    req=urllib.request.Request('https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds/latest',headers={'User-Agent':'Z_Test CI verification (server owner test)'})
    with urllib.request.urlopen(req,timeout=45) as response:meta=json.load(response)
    record=meta['downloads']['server:default']
    req=urllib.request.Request(record['url'],headers={'User-Agent':'Z_Test CI verification (server owner test)'})
    with urllib.request.urlopen(req,timeout=120) as response:(WORK/'paper.jar').write_bytes(response.read())
    actual=hashlib.sha256((WORK/'paper.jar').read_bytes()).hexdigest();assert actual==record['checksums']['sha256'],'Paper checksum mismatch'
    deps=[ROOT/'z_test/target/Z_Test-1.0.3.jar']+list((Path.home()/'.m2/repository').rglob('*.jar'))
    classes=WORK/'qa-classes';classes.mkdir(exist_ok=True)
    with (REPORT/'native-compile.log').open('w') as log:
        proc=subprocess.run(['javac','-proc:none','--release','21','-cp',os.pathsep.join(map(str,deps)),'-d',str(classes),str(ROOT/'z_test/src/smoke/java/dev/ztest/qa/NativeSmoke.java')],stdout=log,stderr=subprocess.STDOUT)
    assert proc.returncode==0,'QA harness did not compile: see native-compile.log'
    plugins=WORK/'plugins';plugins.mkdir(exist_ok=True);shutil.copyfile(ROOT/'z_test/target/Z_Test-1.0.3.jar',plugins/'Z_Test.jar');shutil.copyfile(ROOT/'lor/target/Z_Lor-1.1.1.jar',plugins/'Z_Lor.jar')
    with ZipFile(plugins/'Z_Test_QA.jar','w') as archive:
        for path in classes.rglob('*.class'):archive.write(path,str(path.relative_to(classes)))
        archive.writestr('plugin.yml',"name: Z_Test_QA\nversion: 1.0.1\nmain: dev.ztest.qa.NativeSmoke\napi-version: '1.21.11'\ndepend: [Z_Test]\n")
    config=plugins/'Z_Test';config.mkdir(exist_ok=True)
    (config/'config.yml').write_text('world-name: z_test_purgatory\nmax-rifts: 32\nportal-cooldown-seconds: 2\nsouls:\n  interval-seconds: 5\n  chance: 0.0\n  max-per-world: 6\n  spawn-distance-min: 6\n  spawn-distance-max: 8\n  drops-min: 1\n  drops-max: 1\n')
    (WORK/'eula.txt').write_text('eula=true\n')
    (WORK/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25591\nonline-mode=false\nview-distance=5\nsimulation-distance=4\nspawn-protection=0\nmax-players=3\nmax-tick-time=-1\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\ngenerate-structures=false\n')
    bots=ROOT/'z_test/target/bots';bots.mkdir(exist_ok=True);shutil.copyfile(ROOT/'tools/ztest_native_bot.js',bots/'bot.js')
    subprocess.run(['npm','install','--prefix',str(bots),'--no-audit','--no-fund','mineflayer@4.39.0'],check=True)
    for phase in (1,2):
        report=WORK/'ztest-native-report.json'
        if report.exists():report.unlink()
        with (REPORT/f'server-phase{phase}.log').open('w') as log,(REPORT/f'bot-phase{phase}.log').open('w') as botlog:
            server=subprocess.Popen(['java','-Xms512M','-Xmx2G','-jar','paper.jar','nogui'],cwd=WORK,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT)
            bot=None;deadline=time.monotonic()+300
            try:
                while time.monotonic()<deadline and server.poll() is None:
                    if bot is None and 'Done (' in (REPORT/f'server-phase{phase}.log').read_text(errors='replace'):
                        bot=subprocess.Popen(['node','bot.js'],cwd=bots,stdout=botlog,stderr=subprocess.STDOUT)
                    if report.exists():break
                    time.sleep(1)
            finally:
                if server.poll() is None:
                    try:server.communicate(b'stop\n',timeout=40)
                    except subprocess.TimeoutExpired:server.kill();server.wait()
                if bot is not None:
                    bot.terminate()
                    try:bot.wait(timeout=10)
                    except subprocess.TimeoutExpired:bot.kill();bot.wait()
        assert report.exists(),f'No report for phase {phase}; inspect server-phase{phase}.log'
        data=json.loads(report.read_text());results.append(data);assert data.get('status')=='PASS',f'Phase {phase}: {data}'
    output={'status':'PASS','source_commit':os.environ.get('GITHUB_SHA'),'server_build':meta.get('id',meta.get('build')),'server_sha256':actual,'phases':results,'visual_client':False}
    (REPORT/'latest.json').write_text(json.dumps(output,ensure_ascii=False,indent=2)+'\n')
except Exception as error:
    (REPORT/'latest.json').write_text(json.dumps({'status':'FAIL','source_commit':os.environ.get('GITHUB_SHA'),'error':str(error),'completed_phases':results,'visual_client':False},ensure_ascii=False,indent=2)+'\n')
    raise
