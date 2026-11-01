#!/usr/bin/env python3
"""Launch an unmodified official client in disposable Xvfb CI, then capture frames.
No client binaries, worlds, account tokens or authentication are distributed.
"""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from zipfile import ZipFile
import hashlib,json,os,subprocess,time,urllib.request,uuid
ROOT=Path(__file__).resolve().parents[1]
GAME=ROOT/'lor/target/vanilla-client'
REPORT=ROOT/'lor/live-qa'

def fetch(record,path,sha='sha1'):
    if path.exists() and hashlib.new(sha,path.read_bytes()).hexdigest()==record.get(sha):return
    path.parent.mkdir(parents=True,exist_ok=True)
    for attempt in range(3):
        try:
            data=urllib.request.urlopen(record['url'],timeout=60).read()
            if sha in record:assert hashlib.new(sha,data).hexdigest()==record[sha],str(path)
            path.write_bytes(data);return
        except Exception:
            if attempt==2:raise
            time.sleep(.5)

def prepare():
    GAME.mkdir(parents=True,exist_ok=True)
    manifest=json.loads(urllib.request.urlopen('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json',timeout=30).read())
    version=next(v for v in manifest['versions'] if v['id']=='1.21.11')
    metadata=json.loads(urllib.request.urlopen(version['url'],timeout=30).read())
    client=GAME/'client.jar';fetch(metadata['downloads']['client'],client)
    classpath=[client];jobs=[]
    for lib in metadata['libraries']:
        if lib.get('rules'):
            allowed=False
            for rule in lib['rules']:
                spec=rule.get('os',{})
                if spec.get('name','linux')=='linux' and not rule.get('features'):
                    allowed=rule['action']=='allow'
            if not allowed:continue
        record=lib['downloads'].get('artifact')
        if record:
            path=GAME/'libraries'/record['path'];jobs.append((record,path));classpath.append(path)
        classifier=lib.get('natives',{}).get('linux')
        if classifier:
            record=lib['downloads']['classifiers'][classifier.replace('${arch}','64')]
            jobs.append((record,GAME/'libraries'/record['path']))
    index=metadata['assetIndex'];indexfile=GAME/'assets/indexes'/str(index['id']+'.json');fetch(index,indexfile)
    assets=json.loads(indexfile.read_text())['objects']
    for key,record in assets.items():
        if key.endswith(('.ogg','.mp3')):continue
        digest=record['hash'];jobs.append(({'url':f'https://resources.download.minecraft.net/{digest[:2]}/{digest}','sha1':digest},GAME/'assets/objects'/digest[:2]/digest))
    with ThreadPoolExecutor(max_workers=16) as pool:list(pool.map(lambda task:fetch(*task),jobs))
    natives=GAME/'natives';natives.mkdir(exist_ok=True)
    for path in (GAME/'libraries').rglob('*.jar'):
        if 'natives-linux' not in path.name:continue
        with ZipFile(path) as zip:
            for name in zip.namelist():
                if name.endswith('.so'):(natives/Path(name).name).write_bytes(zip.read(name))
    import nbtlib
    nbtlib.File({'servers':nbtlib.List[nbtlib.Compound]([nbtlib.Compound({'name':nbtlib.String('Disposable Z_Lor QA'),'ip':nbtlib.String('127.0.0.1:25585'),'acceptTextures':nbtlib.Byte(1)})])}).save(GAME/'servers.dat')
    (GAME/'options.txt').write_text('onboardAccessibility:false\nstartedCleanly:true\nlang:en_us\nguiScale:2\nrenderDistance:6\nsimulationDistance:5\nmaxFps:30\nfullscreen:false\nenableVsync:false\nparticles:0\nshowSubtitles:false\ntutorialStep:none\n')
    digest=bytearray(hashlib.md5(b'OfflinePlayer:LorObserver').digest());digest[6]=(digest[6]&15)|48;digest[8]=(digest[8]&63)|128
    command=['java','-Xms512M','-Xmx2G','-Djava.library.path='+str(natives),'-Dorg.lwjgl.librarypath='+str(natives),'-cp',os.pathsep.join(map(str,classpath)),metadata['mainClass'],'--username','LorObserver','--version','1.21.11','--gameDir',str(GAME),'--assetsDir',str(GAME/'assets'),'--assetIndex',str(index['id']),'--uuid',uuid.UUID(bytes=bytes(digest)).hex,'--accessToken','0','--userType','msa','--versionType','release','--width','1280','--height','720','--quickPlayMultiplayer','127.0.0.1:25585']
    (GAME/'command.json').write_text(json.dumps(command));return command

def launch():
    command=json.loads((GAME/'command.json').read_text())
    return subprocess.Popen(command,cwd=GAME,stdout=(REPORT/'vanilla-console.log').open('w'),stderr=subprocess.STDOUT)


if __name__=='__main__':prepare()
