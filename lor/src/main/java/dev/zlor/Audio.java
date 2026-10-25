package dev.zlor;

import dev.zlor.rules.*;
import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import org.bukkit.*;
import org.bukkit.entity.Player;

public final class Audio implements AutoCloseable {
    public static final List<String> TRACKS=List.of("alarm","exchange","watch","frontier","evacuation","mechanical","classic","pulse");
    public static final List<String> NAMES=List.of("Тревога","Перекличка","Дозор","Рубеж","Эвакуация","Механическая","Классическая","Пульс");
    public static final double[] SECONDS={11.1,12,9.6,29,50.2,12,12,12};
    private static final class Speaker {
        final UUID device;final int voice;final String sound;final Location location;final int range,track;final float volume;
        long next;final Set<UUID> listeners=new HashSet<>();
        Speaker(State.Device d,int voice,Location location){device=d.id;this.voice=voice;this.location=location;range=d.range;track=d.sound;volume=d.volume/100f;sound="z_lor:siren_"+voice+"_"+d.sound+"_r"+d.range;}
    }
    private record Preview(String sound,UUID session,long until){}
    private final ZLorPlugin p;
    private final Map<UUID,Speaker> speakers=new HashMap<>();
    private final Set<UUID> heard=new HashSet<>();
    private final Map<UUID,Preview> previews=new HashMap<>();
    private final Map<UUID,Future<?>> imports=new HashMap<>();
    private final ThreadPoolExecutor worker;
    private final Path folder,cache,published;
    private HttpServer server;
    private ExecutorService httpWorker;
    private volatile String packHash="";
    private volatile Set<String> packCatalogue=Set.of();
    public Audio(ZLorPlugin p)throws Exception{
        this.p=p;folder=p.getDataFolder().toPath().resolve("audio-import");cache=p.getDataFolder().toPath().resolve("audio-cache");published=p.getDataFolder().toPath().resolve("generated-pack.zip");
        Files.createDirectories(folder);Files.createDirectories(cache);AudioLimits.load(p.getDataFolder().toPath());
        AudioLimits.restrict(p.getConfig().getInt("audio.max-bytes",20971520),p.getConfig().getInt("audio.max-seconds",600));
        worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(4),r->{var thread=new Thread(r,"Z_Lor-Audio");thread.setDaemon(true);thread.setContextClassLoader(p.getClass().getClassLoader());return thread;});
        if(p.getConfig().getBoolean("audio.http-enabled",false))startHttp();
    }
    public void beginTick(){heard.clear();}
    public void siren(State.Device device,boolean powered){
        if(!powered||device.volume<=0){stopDevice(device.id);return;}heard.add(device.id);
        var current=speakers.get(device.id);
        if(current!=null&&(current.range!=device.range||current.track!=device.sound||Math.abs(current.volume-device.volume/100f)>.001)){stopDevice(device.id);current=null;}
        if(current==null){
            int max=Math.clamp(p.getConfig().getInt("audio.siren-voices",32),1,32),voice=0;
            var used=new HashSet<Integer>();speakers.values().forEach(s->used.add(s.voice));while(voice<max&&used.contains(voice))voice++;if(voice>=max)return;
            current=new Speaker(device,voice,p.devices.center(device));speakers.put(device.id,current);
        }
        if(p.tick%5!=0)return;
        Set<UUID> nearby=new HashSet<>();boolean loop=p.now()>=current.next;
        for(Player player:current.location.getWorld().getPlayers())if(player.getLocation().distanceSquared(current.location)<=current.range*current.range){
            nearby.add(player.getUniqueId());if(loop||!current.listeners.contains(player.getUniqueId()))player.playSound(current.location,current.sound,SoundCategory.BLOCKS,current.volume,1);
        }
        for(UUID id:current.listeners)if(!nearby.contains(id)){Player player=Bukkit.getPlayer(id);if(player!=null)player.stopSound(current.sound,SoundCategory.BLOCKS);}
        current.listeners.clear();current.listeners.addAll(nearby);
        if(loop)current.next=p.now()+(long)(SECONDS[current.track]*1000);
    }
    public void stopDevice(UUID id){
        var current=speakers.remove(id);if(current==null)return;
        for(UUID listener:current.listeners){Player player=Bukkit.getPlayer(listener);if(player!=null)player.stopSound(current.sound,SoundCategory.BLOCKS);}
    }
    public void tick(){
        for(UUID id:new HashSet<>(speakers.keySet()))if(!heard.contains(id))stopDevice(id);
        for(var entry:new ArrayList<>(previews.entrySet()))if(entry.getValue().until<=p.now()||!p.menus.current(entry.getKey(),entry.getValue().session)){
            Player player=Bukkit.getPlayer(entry.getKey());if(player!=null)stopPreview(player);else previews.remove(entry.getKey());
        }
    }
    public void preview(Player player,State.Device device,UUID session){
        stopPreview(player);String sound="z_lor:preview_"+device.sound+"_r"+device.range;
        player.playSound(p.devices.center(device),sound,SoundCategory.PLAYERS,device.volume/100f,1);
        previews.put(player.getUniqueId(),new Preview(sound,session,p.now()+(long)(SECONDS[device.sound]*1000)));
    }
    public void previewGate(Player player,String key,double volume,UUID session){
        stopPreview(player);String sound=gateSound(key);player.playSound(player.getLocation(),sound,SoundCategory.PLAYERS,(float)volume,1);
        previews.put(player.getUniqueId(),new Preview(sound,session,p.now()+Math.min(600_000,(long)(customSeconds(key)*1000))));
    }
    private double customSeconds(String key){if(key.startsWith("custom:")){var data=p.state().audio.get(key.substring(7));if(data!=null)return data.seconds();}return 10;}
    public void stopPreview(Player player){var preview=previews.remove(player.getUniqueId());if(preview!=null)player.stopSound(preview.sound,SoundCategory.PLAYERS);}
    public static String gateSound(String key){
        if(key.startsWith("custom:")&&OggClip.validHash(key.substring(7)))return "z_lor:custom_"+key.substring(7);
        return switch(key){case "enderman"->"minecraft:entity.enderman.teleport";case "anchor"->"minecraft:block.respawn_anchor.deplete";default->"minecraft:block.end_portal_frame.fill";};
    }
    public void refusal(Player player,Location location,String key,double volume){player.playSound(location,gateSound(key),SoundCategory.PLAYERS,(float)volume,1);}
    public List<String> importFiles(){
        try(var files=Files.list(folder)){return files.filter(Files::isRegularFile).map(f->f.getFileName().toString()).filter(f->f.toLowerCase(Locale.ROOT).matches(".*\\.(mp3|wav|ogg)")).sorted().limit(64).toList();}
        catch(IOException e){return List.of();}
    }
    public void importFile(Player player,String name){
        p.roles.require(player);if(imports.containsKey(player.getUniqueId()))throw new IllegalArgumentException("Уже идёт импорт; /lore audio cancel");
        if(name.contains("/")||name.contains("\\")||name.contains(".."))throw new IllegalArgumentException("Выберите имя из audio-import");
        if(p.getConfig().getString("audio.base-pack","").isBlank()||p.getConfig().getString("audio.public-pack-url","").isBlank())throw new IllegalArgumentException("Для нового звука сначала настройте audio.base-pack и audio.public-pack-url; встроенные записи уже работают");
        try{
            Path file=folder.resolve(name).toRealPath();if(!file.startsWith(folder.toRealPath()))throw new IllegalArgumentException("Файл находится вне audio-import");
            Future<?> future=worker.submit(()->{
                try{
                    byte[] audio=AudioImporter.read(file);String hash=OggClip.hash(audio);double seconds=OggClip.validate(audio);
                    StateStore.atomicWrite(cache.resolve(hash+".ogg"),audio);
                    if(Thread.currentThread().isInterrupted())throw new InterruptedException();
                    Map<String,State.Audio> catalogue=new LinkedHashMap<>();
                    // Build only from a detached snapshot; the game state stays on the main thread.
                    var metadata=new State.Audio(hash,name,seconds);
                    p.getServer().getScheduler().runTask(p,()->{
                        if(!imports.containsKey(player.getUniqueId())||!p.roles.scen(player)){imports.remove(player.getUniqueId());return;}
                        catalogue.putAll(p.state().audio);catalogue.put(hash,metadata);
                        var build=worker.submit(()->{
                            try{buildPack(catalogue);if(Thread.currentThread().isInterrupted()||!p.isEnabled())return;p.getServer().getScheduler().runTask(p,()->{
                                if(!imports.containsKey(player.getUniqueId()))return;
                                p.change(player,"Импортирован звук "+name,s->{if(!imports.containsKey(player.getUniqueId()))throw new IllegalArgumentException("Импорт отменён");s.audio.put(hash,metadata);},s->{imports.remove(player.getUniqueId());player.sendMessage("Звук подготовлен. Примените новый общий пак: /lore audio publish; затем выберите запись в настройках врат");});
                            });}
                            catch(Exception e){if(p.isEnabled())p.getServer().getScheduler().runTask(p,()->{imports.remove(player.getUniqueId());p.error(player,e);});}
                        });
                        imports.put(player.getUniqueId(),build);
                    });
                }catch(Exception e){if(p.isEnabled()&&!p.stopping())p.getServer().getScheduler().runTask(p,()->{imports.remove(player.getUniqueId());p.error(player,e);});}
            });imports.put(player.getUniqueId(),future);player.sendMessage("Импорт звука запущен");
        }catch(IOException e){throw new IllegalArgumentException("Файл не найден в audio-import",e);}
    }
    public void cancel(Player player){Future<?> job=imports.remove(player.getUniqueId());if(job!=null){job.cancel(true);player.sendMessage("Импорт отменён");}}
    public synchronized void buildPack(Map<String,State.Audio> catalogue)throws Exception{
        if(catalogue.size()>32)throw new IOException("Максимум 32 импортированные записи");
        Path base=Path.of(p.getConfig().getString("audio.base-pack",""));if(!Files.isRegularFile(base))throw new IOException("Общий базовый пак не найден");
        var entries=new TreeMap<String,byte[]>();
        try(var zip=new ZipFile(base.toFile())){
            if(zip.size()>20_000)throw new IOException("Слишком много записей в базовом паке");long size=0;
            for(var iterator=zip.entries();iterator.hasMoreElements();){var entry=iterator.nextElement();if(entry.isDirectory())continue;
                if(entry.getName().contains("..")||entry.getName().startsWith("/")||(size+=entry.getSize())>256*1024*1024)throw new IOException("Небезопасный базовый пак");
                entries.put(entry.getName(),zip.getInputStream(entry).readAllBytes());
            }
        }
        var sounds=entries.containsKey("assets/z_lor/sounds.json")?JsonParser.parseString(new String(entries.get("assets/z_lor/sounds.json"),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject():new JsonObject();
        for(var entry:catalogue.entrySet()){
            if(Thread.currentThread().isInterrupted())throw new InterruptedException("Сборка пака отменена");
            String hash=entry.getKey();if(!OggClip.validHash(hash))throw new IOException("Некорректный SHA");byte[] audio=Files.readAllBytes(cache.resolve(hash+".ogg"));
            if(!OggClip.hash(audio).equals(hash))throw new IOException("Повреждён hash-cache");OggClip.validate(audio);
            entries.put("assets/z_lor/sounds/custom/"+hash+".ogg",audio);
            if(entries.values().stream().mapToLong(data->data.length).sum()>128L*1024*1024)throw new IOException("Общий пак превысит 128 МиБ");
            var event=new JsonObject();var clips=new JsonArray();var clip=new JsonObject();clip.addProperty("name","z_lor:custom/"+hash);clip.addProperty("stream",true);clip.addProperty("attenuation_distance",32);clips.add(clip);event.add("sounds",clips);sounds.add("custom_"+hash,event);
        }
        entries.put("assets/z_lor/sounds.json",StateStore.JSON.toJson(sounds).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var buffer=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(buffer)){for(var entry:entries.entrySet()){if(Thread.currentThread().isInterrupted())throw new InterruptedException();var info=new ZipEntry(entry.getKey());info.setTime(0);zip.putNextEntry(info);zip.write(entry.getValue());zip.closeEntry();}}
        byte[] data=buffer.toByteArray();String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-1").digest(data));
        if(Thread.currentThread().isInterrupted())throw new InterruptedException();
        StateStore.atomicWrite(published.getParent().resolve("packs").resolve(hash+".zip"),data);StateStore.atomicWrite(published,data);
        packCatalogue=Set.copyOf(catalogue.keySet());packHash=hash;
    }
    public void publish(Player player){
        p.roles.require(player);String url=p.getConfig().getString("audio.public-pack-url","");
        if(packHash.isEmpty()||!Files.isRegularFile(published)||!url.startsWith("https://")&&!url.startsWith("http://"))throw new IllegalArgumentException("Новый общий пак ещё не подготовлен или нет публичного URL");
        if(!imports.isEmpty()||!packCatalogue.equals(p.state().audio.keySet()))throw new IllegalArgumentException("Дождитесь успешного завершения импорта перед обновлением пака");
        if(url.contains("?"))throw new IllegalArgumentException("Укажите audio.public-pack-url без параметров запроса");
        url=url+"?sha1="+packHash;
        var id=p.packId();byte[] digest=HexFormat.of().parseHex(packHash);
        for(Player online:Bukkit.getOnlinePlayers())online.setResourcePack(id,url,digest,net.kyori.adventure.text.Component.text("Обновлены серверные звуки"),false);
        player.sendMessage("Общий пак предложен один раз. Для следующих входов сохраните в server.properties:");player.sendMessage("resource-pack="+url);player.sendMessage("resource-pack-sha1="+packHash);
    }
    private void startHttp()throws IOException{
        String bind=p.getConfig().getString("audio.http-bind","0.0.0.0");int port=p.getConfig().getInt("audio.http-port",8124);
        String route=p.getConfig().getString("audio.http-path","/zlor-pack.zip");if(!route.startsWith("/")||route.contains(".."))throw new IOException("Неверный путь HTTP");
        server=HttpServer.create(new InetSocketAddress(bind,port),8);
        server.createContext(route,exchange->{
            String query=exchange.getRequestURI().getQuery();Path served=published;
            if(query!=null){if(!query.matches("sha1=[a-f0-9]{40}")){exchange.sendResponseHeaders(404,-1);exchange.close();return;}served=published.getParent().resolve("packs").resolve(query.substring(5)+".zip");}
            if(!exchange.getRequestURI().getPath().equals(route)||!Set.of("GET","HEAD").contains(exchange.getRequestMethod())||!Files.isRegularFile(served)){exchange.sendResponseHeaders(404,-1);exchange.close();return;}
            exchange.getResponseHeaders().set("Content-Type","application/zip");exchange.getResponseHeaders().set("Cache-Control",query==null?"no-cache":"public, max-age=31536000, immutable");long size=Files.size(served);
            exchange.sendResponseHeaders(200,exchange.getRequestMethod().equals("HEAD")?-1:size);
            try{if(!exchange.getRequestMethod().equals("HEAD"))try(var output=exchange.getResponseBody()){Files.copy(served,output);}}finally{exchange.close();}
        });
        httpWorker=Executors.newFixedThreadPool(2,r->{var thread=new Thread(r,"Z_Lor-Pack-HTTP");thread.setDaemon(true);return thread;});server.setExecutor(httpWorker);server.start();
    }
    public String packHash(){return packHash;}
    @Override public void close(){for(UUID id:new HashSet<>(speakers.keySet()))stopDevice(id);for(Player player:Bukkit.getOnlinePlayers())stopPreview(player);imports.values().forEach(f->f.cancel(true));imports.clear();worker.shutdownNow();if(server!=null)server.stop(0);if(httpWorker!=null)httpWorker.shutdownNow();}
}
