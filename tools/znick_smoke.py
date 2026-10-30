from pathlib import Path
import hashlib,json,os,subprocess,threading,time,urllib.request,shutil,traceback
root=Path(__file__).resolve().parents[1]; compat=os.environ.get('ZNICK_COMPAT')=='1'; work=root/('nick/target/live-compat' if compat else 'nick/target/live'); report=root/('nick/live-qa/compat' if compat else 'nick/live-qa'); work.mkdir(parents=True,exist_ok=True); report.mkdir(parents=True,exist_ok=True)
def get(url):
 with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'ZNick-CI/1.0.0'}),timeout=50) as r:return r.read()
server=None
try:
 meta=json.loads(get('https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds/latest')); dl=meta['downloads']['server:default']; data=get(dl['url']); assert hashlib.sha256(data).hexdigest()==dl['checksums']['sha256'];(work/'paper.jar').write_bytes(data)
 (work/'plugins').mkdir(exist_ok=True);shutil.copyfile(root/'nick/target/Z_Nick-1.0.0.jar',work/'plugins/Z_Nick-1.0.0.jar')
 if compat:
  for name,url in [('TAB-6.2.0.jar','https://github.com/NEZNAMY/TAB/releases/download/6.2.0/TAB.v6.2.0.jar'),('EssentialsX-2.22.0.jar','https://github.com/EssentialsX/Essentials/releases/download/2.22.0/EssentialsX-2.22.0.jar'),('EssentialsXChat-2.22.0.jar','https://github.com/EssentialsX/Essentials/releases/download/2.22.0/EssentialsXChat-2.22.0.jar')]:
   (work/'plugins'/name).write_bytes(get(url))
 (work/'eula.txt').write_text('eula=true\n');(work/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25591\nonline-mode=false\nspawn-protection=0\nview-distance=4\nsimulation-distance=4\nlevel-type=minecraft:flat\n')
 log=(report/'paper.log').open('w');server=subprocess.Popen(['java','-Xms512M','-Xmx2G','-jar','paper.jar','nogui'],cwd=work,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT)
 def wait_for(phrase,seconds):
  deadline=time.monotonic()+seconds
  while time.monotonic()<deadline:
   if phrase in (report/'paper.log').read_text(errors='replace'):return
   if server.poll() is not None:raise RuntimeError('Server stopped while waiting for '+phrase)
   time.sleep(.5)
  raise RuntimeError('Timed out waiting for '+phrase)
 wait_for('Done (',300)
 server.stdin.write(b'op NickAdmin\n');server.stdin.flush()
 bots=subprocess.Popen(['node',str(root/'tools/znick_smoke_bot.js'),str(work)],cwd=root/'nick/target/bots',stdout=(report/'bots.log').open('w'),stderr=subprocess.STDOUT)
 deadline=time.monotonic()+120
 while time.monotonic()<deadline and bots.poll() is None:
  ready=work/'ready'
  if ready.exists():
   ready.unlink();server.stdin.write('team add testteam\nteam join testteam NickTarget\n/znick set &#54DAF4н&#54C5EAи&#54B1DFк NickTarget\n'.encode());server.stdin.flush()
  for filename,command in [('change','/znick set &bЛапа NickTarget\n'),('remove','/znick remove NickTarget\n')]:
   marker=work/filename
   if marker.exists():
    marker.unlink();server.stdin.write(command.encode());server.stdin.flush()
  time.sleep(.25)
 exitcode=bots.wait(timeout=5)
 if (work/'result.json').exists(): shutil.copyfile(work/'result.json',report/'bot-result.json')
 assert exitcode==0,'Bot failure'
 text=(report/'paper.log').read_text(errors='replace'); assert 'Ошибка обработки пакета Z_Nick' not in text and 'Не удалось включить Z_Nick' not in text, 'Plugin reported error'
 result=json.loads((work/'result.json').read_text()); assert result['sawNickname'] and result['sawAlias'] and result['sawTeam'] and result['chatNick'] and result['sawChanged'] and result['sawRestored'] and (compat or result['restoredTeam']) and not result['disconnected'],result
 # Verify an offline target, reload and a real server restart.
 server.stdin.write('/znick set &aКиса NickTarget\n'.encode());server.stdin.flush()
 wait_for('Ник NickTarget установлен: Киса',20)
 server.stdin.write(b'/znick reload\n');server.stdin.flush()
 wait_for('Конфигурация и сохранённые ники перезагружены',20)
 server.communicate(b'stop\n',timeout=60); log.close()
 restart_log=(report/'restart.log').open('w')
 server=subprocess.Popen(['java','-Xms512M','-Xmx2G','-jar','paper.jar','nogui'],cwd=work,stdin=subprocess.PIPE,stdout=restart_log,stderr=subprocess.STDOUT)
 deadline=time.monotonic()+180
 while time.monotonic()<deadline:
  text=(report/'restart.log').read_text(errors='replace')
  if 'Done (' in text:break
  if server.poll() is not None:raise RuntimeError('Server failed during restart')
  time.sleep(.5)
 else:raise RuntimeError('Restart timed out')
 assert 'загружено ников 1' in text, 'Nickname was not restored on restart'
 data=(work/'plugins/Z_Nick/nicknames.yml').read_text();assert 'Киса' in data and 'NickTarget' in data
 jar=root/'nick/target/Z_Nick-1.0.0.jar';shutil.copyfile(jar,report/'tested-plugin.jar')
 (report/'result.json').write_text(json.dumps({'status':'PASS','paper_build':meta['id'],'plugin_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'offline_reload_restart':'PASS','plugins':(['TAB 6.2.0','EssentialsX 2.22.0','EssentialsXChat 2.22.0'] if compat else []),'details':result},ensure_ascii=False,indent=2))
except Exception as e:
 (report/'result.json').write_text(json.dumps({'status':'FAIL','error':str(e),'trace':traceback.format_exc()},ensure_ascii=False,indent=2));raise
finally:
 if server is not None and server.poll() is None:
  try:server.communicate(b'stop\n',timeout=30)
  except subprocess.TimeoutExpired:server.kill();server.wait()
