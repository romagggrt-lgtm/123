package dev.zap;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.mindrot.jbcrypt.BCrypt;

/** Persistent salted hashes and lockouts. Called on main thread except BCrypt.checkpw. */
final class Auth {
 private final Path path; private int failures,lockSeconds,cost;
 private String hash; private final Map<UUID,Integer> attempts=new HashMap<>();
 private final Map<UUID,Long> locked=new HashMap<>();
 Auth(Path dir,int failures,int lockSeconds,int cost) throws IOException {
  Files.createDirectories(dir);path=dir.resolve("auth.yml");configure(failures,lockSeconds,cost);
  if(Files.exists(path)){
   YamlConfiguration data=YamlConfiguration.loadConfiguration(path.toFile());hash=data.getString("password-hash");
   if(hash==null||!hash.matches("\\$2[ayb]\\$[0-9]{2}\\$.{53}"))throw new IOException("Invalid password hash, refusing to reset credentials");
   var sec=data.getConfigurationSection("players");if(sec!=null)for(String key:sec.getKeys(false))try{UUID id=UUID.fromString(key);attempts.put(id,sec.getInt(key+".attempts"));locked.put(id,sec.getLong(key+".locked-until"));}catch(IllegalArgumentException ignored){}
  } else {hash=BCrypt.hashpw("324657",BCrypt.gensalt(this.cost));save();}
 }
 void configure(int failures,int lockSeconds,int cost){this.failures=Math.clamp(failures,1,20);this.lockSeconds=Math.clamp(lockSeconds,30,86400);this.cost=Math.clamp(cost,10,12);}
 String currentHash(){return hash;}
 static boolean validPassword(String s){return s!=null&&!s.isEmpty()&&s.getBytes(StandardCharsets.UTF_8).length<=72;}
 static boolean verify(String input,String hash){try{return validPassword(input)&&BCrypt.checkpw(input,hash);}catch(IllegalArgumentException e){return false;}}
 boolean locked(UUID id,long now){return locked.getOrDefault(id,0L)>now;}
 void fail(UUID id,long now) throws IOException {int n=attempts.getOrDefault(id,0)+1;if(n>=failures){locked.put(id,now+lockSeconds*1000L);n=0;}attempts.put(id,n);save();}
 void success(UUID id) throws IOException {attempts.remove(id);locked.remove(id);save();}
 void change(String newHash) throws IOException {String old=hash;hash=newHash;try{save();}catch(IOException e){hash=old;throw e;}}
 int cost(){return cost;}
 private void save() throws IOException {
  YamlConfiguration data=new YamlConfiguration();data.set("password-hash",hash);
  for(UUID id:attempts.keySet())data.set("players."+id+".attempts",attempts.get(id));
  for(UUID id:locked.keySet())data.set("players."+id+".locked-until",locked.get(id));
  Path temp=Files.createTempFile(path.getParent(),"auth-",".tmp");
  try{data.save(temp.toFile());try{Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){throw new IOException("Atomic auth storage unavailable",e);}}
  finally{Files.deleteIfExists(temp);}
 }
}
