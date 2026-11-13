package dev.zplast;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import dev.zplast.audio.AudioImporter;
import dev.zplast.audio.AudioLimits;
import dev.zplast.audio.OggClip;

/** The page sends a bounded raw MP3 body. No user-supplied path reaches disk. */
final class Web implements AutoCloseable {
 record Ticket(UUID owner,String name,boolean color,boolean admin,long expires){}
 private final ZPlast plugin;private final Catalog catalog;private final HttpServer server;
 private final ConcurrentMap<String,Ticket> tickets=new ConcurrentHashMap<>();
 private final ConcurrentMap<String,long[]> rates=new ConcurrentHashMap<>();
 private final SecureRandom random=new SecureRandom();private final ExecutorService executor;private final Semaphore conversions;
 private final long maxBytes,maxSeconds;private final int ownerLimit;private final ThreadPoolExecutor links;
 volatile boolean available;
 Web(ZPlast plugin,Catalog catalog) throws IOException{
  this.plugin=plugin;this.catalog=catalog;var config=plugin.getConfig();
  maxBytes=Math.clamp(config.getLong("upload.max-file-size-mb",15),1,20)*1024*1024;maxSeconds=Math.clamp(config.getLong("upload.max-duration-seconds",600),1,600);ownerLimit=Math.max(0,config.getInt("limits.max-tracks-per-player",20));
  AudioLimits.restrict((int)maxBytes,(int)maxSeconds);
  int parallel=Math.clamp(config.getInt("converter.max-parallel",2),1,4);conversions=new Semaphore(parallel);
  links=new ThreadPoolExecutor(parallel,parallel,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),r->{Thread t=new Thread(r,"Z_Plast-Link");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
  server=HttpServer.create(new InetSocketAddress(config.getString("web.bind-address","0.0.0.0"),Math.clamp(config.getInt("web.port",8765),1,65535)),32);
  executor=new ThreadPoolExecutor(parallel+4,parallel+4,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32),r->{Thread t=new Thread(r,"Z_Plast-Web");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());server.setExecutor(executor);
  server.createContext("/upload/",this::upload);server.createContext("/pack/",this::pack);server.start();
  available=true;
 }
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
   Path mp3=Files.createTempFile(catalog.root(),"upload-",".mp3");
   try{
    try(InputStream in=e.getRequestBody();OutputStream out=Files.newOutputStream(mp3)){byte[] buf=new byte[8192];long count=0;for(int n;(n=in.read(buf))!=-1;){count+=n;if(count>maxBytes)throw new IllegalArgumentException("Файл слишком большой");out.write(buf,0,n);}}
    ImportResult result=importFile(ticket,mp3,filename);plugin.uploadCompleted(ticket.owner,result.track,result.duplicate);
    text(e,200,result.duplicate?"Эта запись уже есть: пластинка будет выдана в игре.":"Запись готова! Вернитесь в игру и примите ресурспак.");
   }catch(IllegalArgumentException ex){text(e,400,ex.getMessage());}catch(Exception ex){plugin.getLogger().warning("Ошибка загрузки пластинки: "+ex.getMessage());text(e,400,"Не удалось обработать MP3. Проверьте файл и его длительность.");}finally{Files.deleteIfExists(mp3);}
  }finally{e.close();}
 }
 private record ImportResult(Track track,boolean duplicate){}
 private ImportResult importFile(Ticket ticket,Path mp3,String filename)throws Exception{
  long size=Files.size(mp3);if(size==0||size>maxBytes)throw new IllegalArgumentException("Файл пустой или слишком большой.");
  MessageDigest hash=MessageDigest.getInstance("SHA-256");byte[] first=new byte[3];int prefix=0;
  try(InputStream in=Files.newInputStream(mp3)){byte[] buf=new byte[8192];for(int n;(n=in.read(buf))!=-1;){for(int i=0;i<n&&prefix<3;i++)first[prefix++]=buf[i];hash.update(buf,0,n);}}
  if(prefix<2||!((prefix==3&&first[0]=='I'&&first[1]=='D'&&first[2]=='3')||((first[0]&255)==255&&((first[1]&0xe0)==0xe0))))throw new IllegalArgumentException("По ссылке нужен сам MP3-файл, а не страница сайта.");
  String sha=HexFormat.of().formatHex(hash.digest());Track existing=catalog.byHash(sha);if(existing!=null)return new ImportResult(existing,true);
  if(!conversions.tryAcquire(90,TimeUnit.SECONDS))throw new IllegalArgumentException("Очередь занята, попробуйте позже.");
  Path ogg=null;
  try{
   ogg=Files.createTempFile(catalog.root(),"convert-",".ogg");
   byte[] audio;try{audio=AudioImporter.read(mp3);}catch(Exception ex){throw new IllegalArgumentException("Не удалось прочитать MP3 или превышена допустимая длительность.",ex);}
   double duration=OggClip.validate(audio);if(duration>maxSeconds)throw new IllegalArgumentException("Запись слишком длинная.");Files.write(ogg,audio);
   byte[] idBytes=new byte[12];random.nextBytes(idBytes);String id=HexFormat.of().formatHex(idBytes);
   String display=filename.toLowerCase(Locale.ROOT).endsWith(".mp3")?filename.substring(0,filename.length()-4):filename;
   String clean=ZPlast.normalizeName(display,ticket.color,plugin.nameLimit);
   Track t=new Track(id,clean,ticket.owner.toString(),ticket.name,sha,System.currentTimeMillis(),size,duration);Track actual;
   synchronized(catalog){if(catalog.byHash(sha)==null&&!ticket.admin&&ownerLimit>0&&catalog.count(ticket.owner)>=ownerLimit)throw new IllegalArgumentException("Достигнут лимит загруженных треков.");actual=catalog.add(t,ogg);}
   return new ImportResult(actual,!actual.id.equals(id));
  }finally{conversions.release();if(ogg!=null)Files.deleteIfExists(ogg);}
 }
 boolean importLink(Ticket ticket,String link){
  try{links.execute(()->{
   Path file=null;
   try{file=Files.createTempFile(catalog.root(),"link-",".mp3");DirectDownload.Result result=DirectDownload.download(link,file,maxBytes);
    String path=result.uri().getRawPath();String filename=(path==null||path.isBlank())?"Без названия":path.substring(path.lastIndexOf('/')+1);
    try{filename=URLDecoder.decode(filename,StandardCharsets.UTF_8);}catch(IllegalArgumentException ignored){filename="Без названия";}
    ImportResult imported=importFile(ticket,file,filename);plugin.uploadCompleted(ticket.owner,imported.track,imported.duplicate);
   }catch(Exception ex){String message=ex instanceof IOException||ex instanceof IllegalArgumentException?ex.getMessage():"Не удалось загрузить запись.";plugin.linkError(ticket.owner,message);}
   finally{if(file!=null)try{Files.deleteIfExists(file);}catch(IOException ignored){}plugin.linkFinished(ticket.owner);}
  });return true;}catch(RejectedExecutionException ex){return false;}
 }
 private static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
 private static String page(String name){return """
<!doctype html><html lang="ru"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Z_Plast</title><style>body{font:16px system-ui;background:#0b0917;color:#eee;display:grid;place-items:center;min-height:100vh;margin:0}main{background:#171226;border:1px solid #743cff;border-radius:20px;padding:32px;width:min(82vw,440px);box-shadow:0 0 40px #5b20ff44}h1{color:#cfacff}#drop{border:2px dashed #8b62c5;border-radius:12px;padding:24px;text-align:center}button{background:#773bff;color:white;border:0;border-radius:8px;padding:12px 20px;margin-top:16px;cursor:pointer}progress{width:100%;margin-top:20px}</style><main><h1>♫ Z_Plast</h1><p>Загрузка пластинки для <strong>"""+escape(name)+"""
</strong></p><div id="drop">Перетащите MP3 сюда или выберите файл<br><input id="file" type="file" accept=".mp3,audio/mpeg"></div><button id="send">Загрузить</button><progress id="bar" max="100" value="0"></progress><p id="status"></p></main><script>const f=document.querySelector('#file'),d=document.querySelector('#drop'),s=document.querySelector('#status'),b=document.querySelector('#send');d.ondragover=e=>e.preventDefault();d.ondrop=e=>{e.preventDefault();f.files=e.dataTransfer.files};b.onclick=()=>{const x=f.files[0];if(!x||!x.name.toLowerCase().endsWith('.mp3')){s.textContent='Выберите MP3';return}b.disabled=true;let r=new XMLHttpRequest();r.open('POST',location.pathname);r.setRequestHeader('X-Filename',encodeURIComponent(x.name));r.upload.onprogress=e=>{if(e.lengthComputable)document.querySelector('#bar').value=e.loaded/e.total*100};r.onload=()=>s.textContent=r.responseText;r.onerror=()=>s.textContent='Ошибка сети';r.send(x)};</script></html>
""";}
 @Override public void close(){available=false;tickets.clear();server.stop(0);executor.shutdownNow();links.shutdownNow();}
}
