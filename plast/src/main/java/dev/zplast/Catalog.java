package dev.zplast;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Disk operations run in worker threads; the monitor serializes commits and pack snapshots. */
final class Catalog {
 record Pack(Path path,String sha,byte[] digest){}
 private final Path root,tracks,pack;private final ZPlast plugin;
 private final Map<String,Track> all=new LinkedHashMap<>();
 private static final Gson JSON=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
 volatile Pack current;
 Catalog(ZPlast plugin) throws IOException{
  this.plugin=plugin;root=plugin.getDataFolder().toPath();tracks=root.resolve("tracks");pack=root.resolve("pack");Files.createDirectories(tracks);Files.createDirectories(pack);
  Path data=root.resolve("tracks.json");if(!Files.exists(data))return;
  try{JsonArray array=JsonParser.parseString(Files.readString(data)).getAsJsonArray();for(JsonElement e:array){try{Track t=JSON.fromJson(e,Track.class);if(valid(t)&&Files.isRegularFile(trackFile(t.id)))all.putIfAbsent(t.id,t);else plugin.getLogger().warning("Пропущена повреждённая запись пластинки");}catch(RuntimeException ex){plugin.getLogger().warning("Пропущена повреждённая запись пластинки");}}}
  catch(RuntimeException e){throw new IOException("Повреждён tracks.json; исходный файл сохранён",e);}
 }
 private boolean valid(Track t){try{return t!=null&&t.id!=null&&t.id.matches("[a-f0-9]{24}")&&t.sha256!=null&&t.sha256.matches("[a-f0-9]{64}")&&t.name!=null&&t.name.length()<=2048&&t.ownerName!=null&&UUID.fromString(t.owner)!=null&&Double.isFinite(t.seconds)&&t.seconds>0&&t.size>0;}catch(Exception e){return false;}}
 private Path trackFile(String id){if(!id.matches("[a-f0-9]{24}"))throw new IllegalArgumentException();return tracks.resolve(id+".ogg");}
 synchronized List<Track> list(){return all.values().stream().sorted(Comparator.comparingLong((Track t)->t.created).reversed()).toList();}
 synchronized Track get(String id){return all.get(id);}
 synchronized Track byHash(String hash){return all.values().stream().filter(t->t.sha256.equals(hash)).findFirst().orElse(null);}
 synchronized long count(UUID id){return all.values().stream().filter(t->t.owner.equals(id.toString())).count();}
 synchronized Track add(Track candidate,Path ogg) throws IOException{
  Track existing=byHash(candidate.sha256);if(existing!=null)return existing;
  if(all.containsKey(candidate.id))throw new IOException("Track id collision");
  Path destination=trackFile(candidate.id);move(ogg,destination);all.put(candidate.id,candidate);
  try{save();}catch(IOException error){all.remove(candidate.id);Files.deleteIfExists(destination);throw error;}return candidate;
 }
 synchronized boolean remove(String id) throws IOException{
  Track t=all.remove(id);if(t==null)return false;
  try{save();}catch(IOException e){all.put(id,t);throw e;}Files.deleteIfExists(trackFile(id));return true;
 }
 synchronized boolean rename(String id,String name) throws IOException{
  Track t=all.get(id);if(t==null)return false;String previous=t.name;t.name=name;try{save();}catch(IOException e){t.name=previous;throw e;}return true;
 }
 private void save() throws IOException{
  Path temp=Files.createTempFile(root,"tracks-",".tmp");try{Files.writeString(temp,JSON.toJson(all.values()),StandardCharsets.UTF_8);move(temp,root.resolve("tracks.json"));}finally{Files.deleteIfExists(temp);}
 }
 private static void move(Path from,Path to) throws IOException{try{Files.move(from,to,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(from,to,StandardCopyOption.REPLACE_EXISTING);}}
 Pack build() throws Exception{
  final List<Track> snapshot; synchronized(this){snapshot=all.values().stream().sorted(Comparator.comparing(t->t.id)).toList();}
  Path tmp=Files.createTempFile(pack,"pack-",".zip");
  try{
   try(var out=new ZipOutputStream(Files.newOutputStream(tmp))){
    put(out,"pack.mcmeta","{\"pack\":{\"min_format\":[75,0],\"max_format\":[75,0],\"description\":\"Z_Plast • звуки пластинок\"}}".getBytes(StandardCharsets.UTF_8));
    JsonObject events=new JsonObject();List<Track> ordered=snapshot;
    for(Track track:ordered){JsonObject sound=new JsonObject();sound.addProperty("name","zplast:track_"+track.id);sound.addProperty("stream",true);JsonArray sounds=new JsonArray();sounds.add(sound);JsonObject event=new JsonObject();event.add("sounds",sounds);events.add("track."+track.id,event);}
    put(out,"assets/zplast/sounds.json",JSON.toJson(events).getBytes(StandardCharsets.UTF_8));
    for(Track track:ordered){ZipEntry entry=new ZipEntry("assets/zplast/sounds/track_"+track.id+".ogg");entry.setTime(0);out.putNextEntry(entry);Files.copy(trackFile(track.id),out);out.closeEntry();}
   }
   MessageDigest md=MessageDigest.getInstance("SHA-1");try(InputStream in=Files.newInputStream(tmp)){byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)md.update(b,0,n);}byte[] digest=md.digest();String sha=HexFormat.of().formatHex(digest);
   Path destination=pack.resolve("sounds-"+sha+".zip");if(Files.exists(destination))Files.delete(tmp);else move(tmp,destination);
   Path latest=pack.resolve("Z_Plast_Sounds.zip"),copy=Files.createTempFile(pack,"latest-",".zip");try{Files.copy(destination,copy,StandardCopyOption.REPLACE_EXISTING);move(copy,latest);}finally{Files.deleteIfExists(copy);}
   return current=new Pack(destination,sha,digest);
  }finally{Files.deleteIfExists(tmp);}
 }
 private static void put(ZipOutputStream out,String name,byte[] bytes)throws IOException{ZipEntry e=new ZipEntry(name);e.setTime(0);out.putNextEntry(e);out.write(bytes);out.closeEntry();}
 Path root(){return root;}
 Path packFor(String hash){return hash.matches("[a-f0-9]{40}")?pack.resolve("sounds-"+hash+".zip"):null;}
}
