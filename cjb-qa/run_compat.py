#!/usr/bin/env python3
"""Test an identical public copy of the user's JAR on a disposable Paper and genuine client."""
from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED
from http.server import ThreadingHTTPServer,SimpleHTTPRequestHandler
from functools import partial
import array,hashlib,json,math,os,shutil,subprocess,sys,threading,time,traceback,urllib.request,uuid,wave
import nbtlib
from PIL import Image
from Xlib import X,display
from Xlib.ext import xtest
root=Path(__file__).resolve().parents[1];work=root/'cjb-qa/target/runtime';report=root/'cjb-qa/evidence';work.mkdir(parents=True,exist_ok=True);report.mkdir(parents=True,exist_ok=True)
for f in work.glob('cjbqa-*'):f.unlink()
sys.path.insert(0,str(root/'tools'));import zap_vanilla_client as vanilla
vanilla.GAME=root/'cjb-qa/target/visual-client';vanilla.REPORT=report
server=client=http=audio=None;recording=work/'audio.wav';checks={};details={};result={}
def fetch(url):
 req=urllib.request.Request(url,headers={'User-Agent':'MinecraftCompatibilityTest/1.0'})
 with urllib.request.urlopen(req,timeout=120) as r:return r.read()
def wait_for(f,seconds,label):
 end=time.monotonic()+seconds
 while time.monotonic()<end:
  if f():return
  if server is not None and server.poll() is not None:raise RuntimeError('Server stopped during '+label)
  if client is not None and client.poll() is not None:raise RuntimeError('Client stopped during '+label)
  if (work/'cjbqa-setup-error').exists():raise RuntimeError((work/'cjbqa-setup-error').read_text())
  time.sleep(.2)
 raise RuntimeError('Timed out: '+label)
def send(s):server.stdin.write((s+'\n').encode());server.stdin.flush()
def audio_amplitudes(path):
 with wave.open(str(path),'rb') as w:
  assert w.getnchannels()==1 and w.getsampwidth()==2
  hz=w.getframerate();a=array.array('h');a.frombytes(w.readframes(w.getnframes()))
  if sys.byteorder!='little':a.byteswap()
 window=hz//2;values=[]
 for start in range(0,len(a)-window+1,window):
  def amp(freq):
   omega=2*math.pi*freq/hz;re=0.0;im=0.0
   for i,x in enumerate(a[start:start+window]):re+=x*math.cos(omega*i);im+=x*math.sin(omega*i)
   return 2*math.hypot(re,im)/(window*32768)
  values.append({'tone':round(amp(511),6),'reference':round(amp(620),6)})
 return values
try:
 official=json.loads((root/'cjb-qa/target/official-match.json').read_text());details['official_artifact']=official
 userjar=root/'cjb-qa/target/CustomJukebox-3.7.1.jar';checks['exact_uploaded_jar_sha256']=hashlib.sha256(userjar.read_bytes()).hexdigest()=='7bff0566996bdc809752a41379b4cd8caff1bf26047bfb355ea9691d2fe59838';assert checks['exact_uploaded_jar_sha256']
 # Merge a test-only 511 Hz sound into the real existing server pack. Preserve every old asset.
 tone=work/'test.ogg';subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','lavfi','-i','sine=frequency=511:duration=20:sample_rate=44100','-ac','1','-c:a','libvorbis','-q:a','5',str(tone)],check=True)
 base=root/'dist/Z_Server_Pack_1.12.zip';testpack=work/'test-resourcepack.zip'
 with ZipFile(base) as z:assets={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
 oldassets=assets.copy();sounds=json.loads(assets.get('assets/minecraft/sounds.json',b'{}'));assert 'music_disc.epic_journey' not in sounds
 sounds['music_disc.epic_journey']={'sounds':[{'name':'records/cjbqa_tone','stream':True}]};assets['assets/minecraft/sounds.json']=json.dumps(sounds).encode();assets['assets/minecraft/sounds/records/cjbqa_tone.ogg']=tone.read_bytes()
 with ZipFile(testpack,'w',ZIP_DEFLATED,compresslevel=6) as z:
  for name,data in assets.items():z.writestr(name,data)
 checks['existing_pack_assets_preserved']=all(assets.get(n)==b for n,b in oldassets.items());packsha=hashlib.sha1(testpack.read_bytes()).hexdigest();details['test_pack_sha1']=packsha
 metadata=json.loads(fetch('https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds/latest'));dl=metadata['downloads']['server:default'];data=fetch(dl['url']);assert hashlib.sha256(data).hexdigest()==dl['checksums']['sha256'];(work/'paper.jar').write_bytes(data);details['paper_build']=metadata['id']
 plugins=work/'plugins';plugins.mkdir(exist_ok=True);shutil.copyfile(userjar,plugins/userjar.name)
 # Test alongside a known subset of the user's earlier plugins; do not touch the real server.
 for n in ['Z_Book-1.0.0.jar','Z_Banya-1.0.2.jar','Z_Nick-1.0.0.jar','Z_Check-1.0.0.jar','InventoryCache_1.0.1.jar']:shutil.copyfile(root/'dist'/n,plugins/n)
 ap=fetch('https://raw.githubusercontent.com/romagggrt-lgtm/123/b1b90d4c491b02a5cba3bc3c2ab1d42b908ff1b2/dist/Z_AP-1.1.1.jar');assert hashlib.sha256(ap).hexdigest()=='c4c2ccddf0fa94650db55574622fdb24ad3d12699c26e3a8efb885c7526ebc7f';(plugins/'Z_AP-1.1.1.jar').write_bytes(ap)
 (plugins/'Z_AP').mkdir(exist_ok=True);(plugins/'Z_AP/config.yml').write_text('ui:\n  server-pack-sha1: "'+packsha+'"\n')
 classes=work/'qa-classes';classes.mkdir(exist_ok=True);jars=[userjar]+list((Path.home()/'.m2/repository').rglob('*.jar'))
 c=subprocess.run(['javac','-proc:none','--release','21','-cp',os.pathsep.join(map(str,jars)),'-d',str(classes),str(root/'cjb-qa/CompatibilityProbe.java')],capture_output=True,text=True);(report/'compile.log').write_text(c.stdout+c.stderr);assert c.returncode==0,'Unable to compile test-only probe'
 with ZipFile(plugins/'CJB_CompatibilityProbe.jar','w') as z:
  for f in classes.rglob('*.class'):z.write(f,str(f.relative_to(classes)))
  z.writestr('plugin.yml',"name: CJB_CompatibilityProbe\nversion: 1.0\nmain: dev.cjbqa.CompatibilityProbe\napi-version: '1.21'\ndepend: [CustomJukebox]\n")
 (work/'eula.txt').write_text('eula=true\n');(work/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25582\nonline-mode=false\nspawn-protection=0\nview-distance=4\nsimulation-distance=4\nlevel-type=minecraft:flat\ndifficulty=peaceful\nresource-pack=http://127.0.0.1:25583/'+testpack.name+'\nresource-pack-sha1='+packsha+'\nrequire-resource-pack=true\n')
 http=ThreadingHTTPServer(('127.0.0.1',25583),partial(SimpleHTTPRequestHandler,directory=str(work)));threading.Thread(target=http.serve_forever,daemon=True).start()
 command=vanilla.prepare();command[command.index('--username')+1]='CjbTester';command[command.index('--quickPlayMultiplayer')+1]='127.0.0.1:25582';d=bytearray(hashlib.md5(b'OfflinePlayer:CjbTester').digest());d[6]=(d[6]&15)|48;d[8]=(d[8]&63)|128;command[command.index('--uuid')+1]=uuid.UUID(bytes=bytes(d)).hex
 nbtlib.File({'servers':nbtlib.List[nbtlib.Compound]([nbtlib.Compound({'name':nbtlib.String('CustomJukebox QA'),'ip':nbtlib.String('127.0.0.1:25582'),'acceptTextures':nbtlib.Byte(1)})])}).save(vanilla.GAME/'servers.dat')
 (vanilla.GAME/'options.txt').write_text('onboardAccessibility:false\nstartedCleanly:true\nlang:en_us\nguiScale:2\nrenderDistance:4\nsimulationDistance:4\nmaxFps:30\nfullscreen:false\nenableVsync:false\nparticles:0\ntutorialStep:none\nsoundCategory_master:1.0\nsoundCategory_record:1.0\n')
 logpath=report/'server.log';log=logpath.open('w');server=subprocess.Popen(['java','-Xms512M','-Xmx2G','-jar','paper.jar','nogui'],cwd=work,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT)
 wait_for(lambda:'Done (' in logpath.read_text(errors='replace'),300,'Paper startup');content=logpath.read_text(errors='replace');checks['plugin_enabled']='[CustomJukebox] Enabling' in content and '[CustomJukebox] Disabling' not in content;assert checks['plugin_enabled'],'CustomJukebox did not enable'
 send('op CjbTester');time.sleep(1);clog=(report/'client-console.log').open('w');client=subprocess.Popen(command,cwd=vanilla.GAME,stdout=clog,stderr=subprocess.STDOUT)
 wait_for(lambda:(work/'cjbqa-ready').exists(),240,'client with pack and custom disc');time.sleep(2)
 checks['resource_pack_loaded']=(work/'cjbqa-pack-status').read_text().strip()=='SUCCESSFULLY_LOADED';checks['custom_disc_given']=(work/'cjbqa-give-command').read_text().strip()=='true' and 'disc=epic_journey' in (work/'cjbqa-ready').read_text();checks['gui_works_for_non_op']=(work/'cjbqa-gui').read_text().strip()=='true:true';details['coexisting_plugins']=(work/'cjbqa-plugins').read_text().splitlines();checks['selected_plugins_enabled']=all(line.endswith('=true') for line in details['coexisting_plugins'])
 assert all(checks[k] for k in ('resource_pack_loaded','custom_disc_given','gui_works_for_non_op','selected_plugins_enabled')), 'Pack, give, GUI or selected plugin startup failed'
 conn=display.Display(os.environ['DISPLAY']);sc=conn.screen();screen=sc.root
 def click(x,y,button=1):
  xtest.fake_input(conn,X.MotionNotify,x=x,y=y);xtest.fake_input(conn,X.ButtonPress,detail=button);xtest.fake_input(conn,X.ButtonRelease,detail=button);conn.sync()
 def capture(name):
  pix=screen.get_image(0,0,sc.width_in_pixels,sc.height_in_pixels,X.ZPixmap,0xffffffff);Image.frombytes('RGB',(sc.width_in_pixels,sc.height_in_pixels),pix.data,'raw','BGRX').save(report/name)
 capture('01-disc-ready.png')
 audio=subprocess.Popen(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','pulse','-i','cjbqa_sink.monitor','-ac','1','-ar','44100',str(recording)],stdin=subprocess.PIPE,stdout=subprocess.DEVNULL,stderr=(report/'audio-capture.log').open('w'));time.sleep(1.2)
 if audio.poll() is not None:raise RuntimeError('Could not start test audio capture')
 click(640,360,3);wait_for(lambda:(work/'cjbqa-sound').exists(),18,'custom sound delivery');time.sleep(3.5);capture('02-playing.png')
 checks['disc_inserted_and_playback_started']='inserted=true' in (work/'cjbqa-play-start').read_text();details['sound_event']=(work/'cjbqa-sound').read_text();checks['custom_sound_sent_to_player']='music_disc.epic_journey' in details['sound_event']
 click(640,360,3);wait_for(lambda:(work/'cjbqa-stop').exists(),12,'stop on ejection');time.sleep(1.5);capture('03-stopped.png');checks['stop_sent_after_ejection']=True
 audio.communicate(b'q\n',timeout=12);audio=None;amplitudes=audio_amplitudes(recording);details['audio_amplitude_by_half_second']=amplitudes;peak=max((x['tone'] for x in amplitudes),default=0);checks['test_tone_heard_by_vanilla_client']=peak>0.001 and any(x['tone']>0.001 and x['tone']>x['reference']*4 for x in amplitudes);checks['tone_stops_after_ejection']=len(amplitudes)>=4 and max(x['tone'] for x in amplitudes[-2:])<max(0.001,peak*.15)
 logs=(vanilla.GAME/'logs/latest.log').read_text(errors='replace');checks['no_missing_test_sound_in_client_log']='Unable to play unknown soundEvent: minecraft:music_disc.epic_journey' not in logs and 'Missing sound for event: minecraft:music_disc.epic_journey' not in logs
 assert all(checks.values()),'One or more compatibility checks failed'
 result={'status':'PASS','checks':checks,'details':details}
except Exception as ex:
 result={'status':'FAIL','error':str(ex),'checks':checks,'details':details,'trace':traceback.format_exc()};raise
finally:
 if audio is not None and audio.poll() is None:
  try:audio.communicate(b'q\n',timeout=10)
  except Exception:audio.kill();audio.wait()
 if client is not None and client.poll() is None:client.terminate();client.wait(timeout=20)
 if server is not None and server.poll() is None:
  try:server.communicate(b'stop\n',timeout=40)
  except subprocess.TimeoutExpired:server.kill();server.wait()
 if http is not None:http.shutdown()
 logfile=vanilla.GAME/'logs/latest.log'
 if logfile.exists():shutil.copyfile(logfile,report/'client.log')
 (report/'result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2))
