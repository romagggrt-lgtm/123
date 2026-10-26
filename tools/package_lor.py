#!/usr/bin/env python3
"""Package actual Maven test evidence and ready files; never assert live Minecraft QA."""
from pathlib import Path
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED
from datetime import datetime, timezone
import hashlib, json, os, re, shutil, struct, subprocess, tempfile
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
DIST=ROOT/'dist'
MODULE=ROOT/'lor'
VERSION='1.0.1'
RAW='https://raw.githubusercontent.com/romagggrt-lgtm/123/refs/heads/feature/z-lor-1.0.1-fixes/dist/'

def write_json(path,value):
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def hashes(path):
    data=path.read_bytes()
    return {'file':path.name,'bytes':len(data),'sha1':hashlib.sha1(data).hexdigest(),'sha256':hashlib.sha256(data).hexdigest()}

def archive(path,files):
    with ZipFile(path,'w',ZIP_DEFLATED) as z:
        for name,data in sorted(files.items()):
            entry=ZipInfo(name,(2026,1,1,0,0,0));entry.compress_type=ZIP_DEFLATED
            z.writestr(entry,data)
    with ZipFile(path) as z:assert z.testzip() is None

def main():
    jar=MODULE/'target/Z_Lor.jar'
    assert jar.exists(),'Run mvn verify first'
    suites=[]
    for path in sorted((MODULE/'target/surefire-reports').glob('TEST-*.xml')):
        tree=ET.parse(path).getroot()
        suites.append({'name':tree.attrib['name'],**{key:int(tree.attrib.get(key,0)) for key in ['tests','failures','errors','skipped']},'seconds':float(tree.attrib['time']),
                       'cases':[case.attrib['name'] for case in tree.findall('testcase')]})
        assert path.stat().st_mtime<=jar.stat().st_mtime,'JAR predates test results'
    totals={key:sum(s[key] for s in suites) for key in ['tests','failures','errors','skipped']}
    assert totals['tests']>=109 and totals['failures']==totals['errors']==totals['skipped']==0,totals
    with ZipFile(jar) as z:
        assert z.testzip() is None
        descriptor=z.read('plugin.yml').decode()
        assert re.search(r'^name: Z_Lor$',descriptor,re.M) and "version: '1.0.1'" in descriptor and "api-version: '1.21.11'" in descriptor
        major=struct.unpack('>H',z.read('dev/zlor/ZLorPlugin.class')[6:8])[0]
        assert major==65,major
        assert 'dev/zlor/lib/bytebuddy/ByteBuddy.class' in z.namelist()
        assert any(name.startswith('dev/zlor/lib/javazoom/') for name in z.namelist())
        assert any(name.startswith('META-INF/zlor-licenses/') for name in z.namelist())
        assert not any(name.startswith(('org/bukkit/','org/mockbukkit/','org/junit/','com/google/gson/')) for name in z.namelist())
    java=Path(os.environ['JAVA_HOME'])/'bin/java' if os.environ.get('JAVA_HOME') else Path(shutil.which('java') or 'java')
    smoke=subprocess.run([str(java),'-cp',os.pathsep.join([str(MODULE/'target/test-classes'),str(jar)]),'dev.zlor.CodecSmoke',str(MODULE/'src/test/resources/audio/tone.mp3'),str(MODULE/'target/shaded-smoke.ogg')],check=True,capture_output=True,text=True).stdout.strip()
    assert 'SHADED_CODEC_PASS' in smoke,smoke
    pack=DIST/'Z_Server_Pack_1.08.zip'
    pack_report=json.loads((DIST/'Z_Server_Pack_1.08.report.json').read_text())
    assert hashes(pack)['sha1']==pack_report['sha1']
    pack_tests=json.loads((MODULE/'target/pack-regressions.json').read_text())
    assert pack_tests['status']=='PASS' and pack_tests['pack_sha1']==hashes(pack)['sha1']
    audio=[]
    with ZipFile(pack) as z, tempfile.TemporaryDirectory(prefix='zlor-audio-') as temporary:
        for name in z.namelist():
            if not name.endswith('.ogg'):continue
            path=Path(temporary)/Path(name).name;path.write_bytes(z.read(name))
            details=json.loads(subprocess.run(['ffprobe','-v','error','-show_entries','stream=codec_name,sample_rate,channels:format=duration','-of','json',str(path)],check=True,capture_output=True,text=True).stdout)
            stream=details['streams'][0];assert stream['codec_name']=='vorbis' and float(details['format']['duration'])>0
            if name.startswith('assets/smplore/'):assert stream['channels']==1,name
            subprocess.run(['ffmpeg','-v','error','-i',str(path),'-f','null','-'],check=True,capture_output=True)
            audio.append({'file':name,**stream,'seconds':float(details['format']['duration']),'decode':'PASS'})
    now=datetime.now(timezone.utc).isoformat()
    live=[]
    for line in (MODULE/'reference/ACCEPTANCE_SOURCE_RU.md').read_text().splitlines():
        match=re.match(r'\| ([A-Z][0-9]{2}) \| (.*?) \| (.*?) \|$',line)
        if match:live.append({'id':match[1],'scenario':match[2],'expected':match[3],'status':'BLOCKED','reason':'No real Paper server / vanilla clients available in build environment; unit tests are partial evidence only'})
    native_path=MODULE/'live-qa/latest.json'
    native=json.loads(native_path.read_text()) if native_path.exists() else {'status':'BLOCKED','reason':'No native report available'}
    review_path=MODULE/'live-qa/visual-review.json'
    review=json.loads(review_path.read_text()) if review_path.exists() else {'status':'BLOCKED','reason':'No completed vanilla image review'}
    visual_verified=review.get('status')=='PASS' and review.get('pack_sha1')==hashes(pack)['sha1']
    pack_report['minecraft_client_tested']=visual_verified
    if visual_verified:
        pack_report['client_review']='lor/live-qa/visual-review.json'
        write_json(DIST/'Z_Server_Pack_1.08.report.json',pack_report)
    tested=native.get('source_commit')
    code_matches=bool(tested) and subprocess.run(['git','diff','--quiet',tested,'--','lor/src/main','lor/pom.xml'],cwd=ROOT,capture_output=True).returncode==0
    evidence={'generated_at_utc':now,'plugin':'Z_Lor','version':VERSION,'target':'Paper 1.21.11 / Java 21','automatic_tests':{'status':'PASS','totals':totals,'suites':suites},
              'jar_structure':{'status':'PASS','class_major':major,'paper_api_not_bundled':True,'test_libraries_not_bundled':True,'codec_smoke':smoke.replace(str(ROOT),'PROJECT')},
              'resource_pack':pack_report,'resource_pack_regressions':pack_tests,'audio_decode':audio,
              'native_smoke':native,'visual_review':review,'native_tested_production_code_unchanged':code_matches,
              'live_server_tested':bool(native.get('server')),'vanilla_client_tested':visual_verified,'production_acceptance_complete':False,
              'paper_download':{'build':native.get('server_build'),'sha256':native.get('server_sha256'),'status':'PASS' if native.get('server_sha256') else 'BLOCKED','runner':'isolated GitHub Actions'},
              'test_environment_limits':['MockBukkit boundary adapter is explicitly replaced with a fake-ready fixture; unsupported display render tick is intentionally replaced by a no-op (actual render tested separately)','NativeBoundaryTest wraps a fake tick list, not NMS','Dragon SCALE attribute and DamageSource delivery have explicit test fixtures; no native rendering or armour proof','Death/drop events are synthetic, not a live death pipeline'],
              'live_acceptance':live,
              'known_differences':['642-cell composite item model replaces Fabric surface shader','Private world TextDisplay HUD replaces Fabric screen HUD','TARGET carrier retains native blast resistance and vertical signal behaviour','Vanilla dragon bodies by current user request; complete native scale/hitbox acceptance remains separate','No separate forced spit/breath/ram diagnostic controls','Server-folder audio import replaces local client file picker'],
              'not_implemented':['Player heart HUD recolouring','New potions and recovery balance','Enderman growth and active enriched endermite','Altars, staff, automatic story egg and calendar automation']}
    write_json(MODULE/'TEST_RESULTS.json',evidence)
    final_jar=DIST/'Z_Lor_1.0.1.jar';shutil.copyfile(jar,final_jar)
    readme=DIST/'Z_Lor_1.0.1.README.md';shutil.copyfile(MODULE/'README_RU.md',readme)
    testing=DIST/'Z_Lor_1.0.1.TESTING.md';shutil.copyfile(MODULE/'TESTING_RU.md',testing)
    properties=DIST/'Z_Lor-server.properties_1.0.1.txt'
    properties.write_text('resource-pack='+RAW+pack.name+'\nresource-pack-sha1='+hashes(pack)['sha1']+'\nresource-pack-id=17e36816-ac9c-57bc-8a02-d7a0221a689e\nrequire-resource-pack=true\nresource-pack-prompt={"text":"Z_Server_Pack 1.08"}\n',encoding='utf-8')
    source={}
    for path in MODULE.rglob('*'):
        if path.is_file() and 'target' not in path.relative_to(MODULE).parts and '__pycache__' not in path.parts:
            source['Z_Lor_Source/'+str(path.relative_to(ROOT))]=path.read_bytes()
    for name in ['build_lor.sh','build_lor_pack.py','build_lor_visuals.py','test_lor_pack.py','package_lor.py','merge_smplore_pack.py','lor_live_smoke.py','lor_live_bot.js','lor_vanilla_client.py']:
        source['Z_Lor_Source/tools/'+name]=(ROOT/'tools'/name).read_bytes()
    source['Z_Lor_Source/.github/workflows/zlor-native-smoke.yml']=(ROOT/'.github/workflows/zlor-native-smoke.yml').read_bytes()
    source['Z_Lor_Source/LICENSE']=(ROOT/'LICENSE').read_bytes()
    base=DIST/'Z_Server_Pack_1.05_Pantera_1.0.1.zip'
    source['Z_Lor_Source/dist/'+base.name]=base.read_bytes()
    sources=DIST/'Z_Lor-Source_1.0.1.zip';archive(sources,source)
    report=DIST/'Z_Lor_1.0.1.report.json'
    evidence['artifacts']=[hashes(path) for path in [final_jar,pack,sources,DIST/'Z_Lor_ResourcePack_1.0.1.zip',DIST/'Z_Lor_Overlay_1.0.1.zip']]
    write_json(report,evidence)
    install={'plugins/Z_Lor.jar':final_jar.read_bytes(),'resource-pack/'+pack.name:pack.read_bytes(),
             'source/'+sources.name:sources.read_bytes(),'README_RU.md':readme.read_bytes(),'TESTING_RU.md':testing.read_bytes(),'server.properties.txt':properties.read_bytes(),
             'report.json':report.read_bytes(),'config.example.yml':(MODULE/'src/main/resources/config.yml').read_bytes(),
             'IMPLEMENTATION-MATRIX.md':(MODULE/'IMPLEMENTATION-MATRIX.md').read_bytes(),'RECIPES_RU.md':(MODULE/'RECIPES_RU.md').read_bytes(),
             'THIRD_PARTY.md':(MODULE/'THIRD_PARTY.md').read_bytes(),'LICENSE':(ROOT/'LICENSE').read_bytes()}
    for path in (MODULE/'live-qa').glob('*'):
        if path.is_file():install['live-qa/'+path.name]=path.read_bytes()
    install['SHA256SUMS']=''.join(hashlib.sha256(data).hexdigest()+'  '+name+'\n' for name,data in sorted(install.items())).encode()
    installer=DIST/'Z_Lor_1.0.1-install.zip';archive(installer,install)
    files=[final_jar,installer,sources,pack,report,readme,testing,properties,DIST/'Z_Lor_ResourcePack_1.0.1.zip',DIST/'Z_Lor_Overlay_1.0.1.zip']
    for path in [final_jar,installer,sources]:
        for algorithm in ['sha1','sha256']:path.with_suffix(path.suffix+'.'+algorithm).write_text(hashes(path)[algorithm]+'\n')
    (DIST/'Z_Lor_1.0.1.SHA256SUMS').write_text(''.join(hashes(path)['sha256']+'  '+path.name+'\n' for path in files))
    print(json.dumps({'tests':totals,'native_smoke':native.get('status'),'full_live_acceptance':'BLOCKED','java_class_major':major,'verified_ogg':len(audio),'artifacts':[hashes(path) for path in [final_jar,installer,sources,pack]]},ensure_ascii=False,indent=2))

if __name__=='__main__':main()
