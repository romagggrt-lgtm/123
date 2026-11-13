package dev.zplast;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** The page sends a bounded raw MP3 body. No user-supplied path reaches disk. */
final class Web implements AutoCloseable {
 record Ticket(UUID owner,String name,boolean color,boolean admin,long expires){}
 private final ZPlast plugin;private final Catalog catalog;private final HttpServer server;
 private final ConcurrentMap<String,Ticket> tickets=new ConcurrentHashMap<>();
 private final ConcurrentMap<String,long[]> rates=new ConcurrentHashMap<>();
 private final SecureRandom random=new SecureRandom();private final ExecutorService executor;private final Semaphore conversions;
 private final String ffmpeg,ffprobe;private final long maxBytes,maxSeconds;private final int ownerLimit;
 volatile boolean available;
 Web(ZPlast plugin,Catalog catalog) throws IOException{
  this.plugin=plugin;this.catalog=catalog;var config=plugin.getConfig();ffmpeg=config.getString("converter.ffmpeg-path","ffmpeg");ffprobe=config.getString("converter.ffprobe-path","ffprobe");
  maxBytes=Math.clamp(config.getLong("upload.max-file-size-mb",15),1,100)*1024*1024;maxSeconds=Math.clamp(config.getLong("upload.max-duration-seconds",600),1,3600);ownerLimit=Math.max(0,config.getInt("limits.max-tracks-per-player",20));
  int parallel=Math.clamp(config.getInt("converter.max-parallel",2),1,8);conversions=new Semaphore(parallel);
  server=HttpServer.create(new InetSocketAddress(config.getString("web.bind-address","0.0.0.0"),Math.clamp(config.getInt("web.port",8765),1,65535)),32);
  executor=new ThreadPoolExecutor(parallel+4,parallel+4,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32),r->{Thread t=new Thread(r,"Z_Plast-Web");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());server.setExecutor(executor);
  server.createContext("/upload/",this::upload);server.createContext("/pack/",this::pack);server.start();
  executor.execute(()->{available=check(ffmpeg)&&check(ffprobe);if(!available)plugin.getLogger().warning("ffmpeg или ffprobe недоступен: загрузка музыки отключена.");});
 }
 private boolean check(String command){try{Process process=new ProcessBuilder(command,"-version").redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();if(!process.waitFor(5,TimeUnit.SECONDS)){process.destroyForcibly();return false;}return process.exitValue()==0;}catch(Exception e){return false;}}
 String ticket(UUID id,String name,boolean color,boolean admin,long ttl){byte[] bytes=new byte[32];random.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);tickets.entrySet().removeIf(e->e.getValue().expires<System.currentTimeMillis());tickets.put(token,new Ticket(id,name,color,admin,System.currentTimeMillis()+ttl));return token;}
 private boolean allow(HttpExchange e){String ip=e.getRemoteAddress().getAddress().getHostAddress();long now=System.currentTimeMillis();if(rates.size()>4096)rates.entrySet().removeIf(v->now-v.getValue()[0]>60_000);long[] value=rates.computeIfAbsent(ip,k->new long[]{now,0});synchronized(value){if(now-value[0]>60_000){value[0]=now;value[1]=0;}return ++value[1]<=90;}}
 private static void response(HttpExchange e,int status,String type,byte[] body)throws IOException{e.getResponseHeaders().set("Content-Type",type);e.getResponseHeaders().set("X-Content-Type-Options","nosniff");e.getResponseHeaders().set("Cache-Control","no-store");e.sendResponseHeaders(status,body.length);try(OutputStream out=e.getResponseBody()){out.write(body);}}
 private static void text(HttpExchange e,int status,String body)throws IOException{response(e,status,"text/plain; charset=utf-8",body.getBytes(StandardCharsets.UTF_8));}
 private void pack(HttpExchange e)throws IOException{
  try{if(!allow(e)){text(e,429,"Слишком много запросов");return;}String name=e.getRequestURI().getPath().substring("/pack/".length());if(!e.getRequestMethod().equals("GET")||!name.matches("[a-f0-9]{40}\\.zip")){text(e,404,"Не найдено");return;}Path file=catalog.packFor(name.substring(0,40));if(file==null||!Files.isRegularFile(file)){text(e,404,"Не найдено");return;}e.getResponseHeaders().set("Content-Type","application/zip");e.sendResponseHeaders(200,Files.size(file));try(OutputStream out=e.getResponseBody()){Files.copy(file,out);}}finally{e.close();}
 }
 private void upload(HttpExchange e)throws IOException{
  try{
   if(!allow(e)){text(e,429,"Слишком много запросов");return;}String token=e.getRequestURI().getPath().substring("/upload/".length());if(!token.matches("[A-Za-z0-9_-]{43}")){text(e,404,"Ссылка недействительна");return;}
   Ticket ticket=tickets.get(token);if(ticket==null||ticket.expires<System.currentTimeMillis()){tickets.remove(token);text(e,410,"Ссылка истекла или уже использована");return;}
   if(e.getRequestMethod().equals("GET")){response(e,200,"text/html; charset=utf-8",page(ticket.name).getBytes(StandardCharsets.UTF_8));return;}
   if(!e.getRequestMethod().equals("POST")){text(e,405,"Метод не поддерживается");return;}
   if(!tickets.remove(token,ticket)){text(e,410,"Ссылка уже использована");return;}
   String filename;try{filename=URLDecoder.decode(e.getRequestHeaders().getFirst("X-Filename"),StandardCharsets.UTF_8);}catch(Exception ex){text(e,400,"Выберите MP3-файл");return;}
   if(!filename.toLowerCase(Locale.ROOT).endsWith(".mp3")){text(e,400,"Нужен файл .mp3");return;}
   long declared;try{declared=Long.parseLong(Optional.ofNullable(e.getRequestHeaders().getFirst("Content-Length")).orElse("-1"));}catch(NumberFormatException ex){declared=-1;}
   if(declared>maxBytes){text(e,413,"Файл слишком большой");return;}
   Path mp3=Files.createTempFile(catalog.root(),"upload-",".mp3"),ogg=Files.createTempFile(catalog.root(),"convert-",".ogg");
   try{
    MessageDigest hash=MessageDigest.getInstance("SHA-256");long size=0;byte[] prefix=new byte[3];int prefixSize=0;
    try(InputStream in=e.getRequestBody();OutputStream out=Files.newOutputStream(mp3)){byte[] buf=new byte[8192];for(int n;(n=in.read(buf))!=-1;){size+=n;if(size>maxBytes)throw new IllegalArgumentException("Файл слишком большой");for(int i=0;i<n&&prefixSize<3;i++)prefix[prefixSize++]=buf[i];out.write(buf,0,n);hash.update(buf,0,n);}}
    if(prefixSize<2||!((prefixSize==3&&prefix[0]=='I'&&prefix[1]=='D'&&prefix[2]=='3')||((prefix[0]&255)==255&&((prefix[1]&0xe0)==0xe0))))throw new IllegalArgumentException("Файл не похож на MP3");
    String sha=HexFormat.of().formatHex(hash.digest());Track existing=catalog.byHash(sha);if(existing!=null){plugin.uploadCompleted(ticket.owner,existing,true);text(e,200,"Эта запись уже есть: пластинка будет выдана в игре.");return;}
    if(!available)throw new IllegalArgumentException("Конвертер сейчас недоступен");
    if(!conversions.tryAcquire(90,TimeUnit.SECONDS))throw new IllegalArgumentException("Очередь занята, попробуйте ещё раз");
    double duration;try{
     duration=duration(mp3);if(!Double.isFinite(duration)||duration<=0||duration>maxSeconds)throw new IllegalArgumentException("Некорректная или слишком длинная запись");
     Process process=new ProcessBuilder(ffmpeg,"-nostdin","-v","error","-y","-i",mp3.toString(),"-vn","-ac","1","-ar","44100","-c:a","libvorbis","-b:a","128k",ogg.toString()).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
     if(!process.waitFor(120,TimeUnit.SECONDS)){process.destroyForcibly();throw new IOException("Таймаут ffmpeg");}if(process.exitValue()!=0||Files.size(ogg)==0)throw new IllegalArgumentException("Не удалось преобразовать MP3");
    }finally{conversions.release();}
    byte[] idBytes=new byte[12];random.nextBytes(idBytes);String id=HexFormat.of().formatHex(idBytes);String clean=ZPlast.normalizeName(filename.substring(0,filename.length()-4),ticket.color,plugin.nameLimit);
    Track t=new Track(id,clean,ticket.owner.toString(),ticket.name,sha,System.currentTimeMillis(),size,duration);Track actual;
    synchronized(catalog){if(catalog.byHash(sha)==null&&!ticket.admin&&ownerLimit>0&&catalog.count(ticket.owner)>=ownerLimit)throw new IllegalArgumentException("Достигнут лимит загруженных треков");actual=catalog.add(t,ogg);}
    plugin.uploadCompleted(ticket.owner,actual,!actual.id.equals(id));text(e,200,"Запись готова! Вернитесь в игру и примите ресурспак.");
   }catch(IllegalArgumentException ex){text(e,400,ex.getMessage());}catch(Exception ex){plugin.getLogger().warning("Ошибка загрузки пластинки: "+ex);text(e,500,"Не удалось обработать запись. Попробуйте снова.");}finally{Files.deleteIfExists(mp3);Files.deleteIfExists(ogg);}
  }finally{e.close();}
 }
 private double duration(Path input)throws Exception{Process p=new ProcessBuilder(ffprobe,"-v","quiet","-show_entries","format=duration","-of","default=noprint_wrappers=1:nokey=1",input.toString()).redirectError(ProcessBuilder.Redirect.DISCARD).start();if(!p.waitFor(20,TimeUnit.SECONDS)){p.destroyForcibly();throw new IOException("Таймаут ffprobe");}if(p.exitValue()!=0)throw new IllegalArgumentException("Повреждённый MP3");byte[] out=p.getInputStream().readNBytes(128);return Double.parseDouble(new String(out,StandardCharsets.UTF_8).trim());}
 private static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
 private static String page(String name){return """
<!doctype html><html lang="ru"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Z_Plast</title><style>body{font:16px system-ui;background:#0b0917;color:#eee;display:grid;place-items:center;min-height:100vh;margin:0}main{background:#171226;border:1px solid #743cff;border-radius:20px;padding:32px;width:min(82vw,440px);box-shadow:0 0 40px #5b20ff44}h1{color:#cfacff}#drop{border:2px dashed #8b62c5;border-radius:12px;padding:24px;text-align:center}button{background:#773bff;color:white;border:0;border-radius:8px;padding:12px 20px;margin-top:16px;cursor:pointer}progress{width:100%;margin-top:20px}</style><main><h1>♫ Z_Plast</h1><p>Загрузка пластинки для <strong>"""+escape(name)+"""
</strong></p><div id="drop">Перетащите MP3 сюда или выберите файл<br><input id="file" type="file" accept=".mp3,audio/mpeg"></div><button id="send">Загрузить</button><progress id="bar" max="100" value="0"></progress><p id="status"></p></main><script>const f=document.querySelector('#file'),d=document.querySelector('#drop'),s=document.querySelector('#status'),b=document.querySelector('#send');d.ondragover=e=>e.preventDefault();d.ondrop=e=>{e.preventDefault();f.files=e.dataTransfer.files};b.onclick=()=>{const x=f.files[0];if(!x||!x.name.toLowerCase().endsWith('.mp3')){s.textContent='Выберите MP3';return}b.disabled=true;let r=new XMLHttpRequest();r.open('POST',location.pathname);r.setRequestHeader('X-Filename',encodeURIComponent(x.name));r.upload.onprogress=e=>{if(e.lengthComputable)document.querySelector('#bar').value=e.loaded/e.total*100};r.onload=()=>s.textContent=r.responseText;r.onerror=()=>s.textContent='Ошибка сети';r.send(x)};</script></html>
""";}
 @Override public void close(){available=false;tickets.clear();server.stop(0);executor.shutdownNow();}
}
