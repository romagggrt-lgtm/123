package dev.zcheck;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Synchronous, durable transitions; never writes on movement. */
class CheckStore {
    final Path file, audit;
    CheckStore(Path directory){file=directory.resolve("checks.yml");audit=directory.resolve("audit.log");}
    Map<UUID,CheckRecord> load() throws Exception {
        if(!Files.exists(file))return Map.of();
        if(Files.size(file)>16*1024*1024)throw new IOException("checks.yml is too large");
        YamlConfiguration y=new YamlConfiguration();y.load(file.toFile());
        if(y.getInt("schema",-1)!=1)throw new IOException("Unsupported checks.yml schema");
        var rows=y.getConfigurationSection("checks");if(rows==null)throw new IOException("Missing checks section");
        Map<UUID,CheckRecord> result=new LinkedHashMap<>();
        for(String key:rows.getKeys(false))try{
            var row=Objects.requireNonNull(rows.getConfigurationSection(key));
            for(String k:List.of("name","world","x","y","z","started"))if(!row.contains(k))throw new IllegalArgumentException("Missing "+k);
            UUID id=UUID.fromString(key),world=UUID.fromString(Objects.requireNonNull(row.getString("world")));
            String mod=row.getString("moderator");
            var r=new CheckRecord(id,row.getString("name"),mod==null?null:UUID.fromString(mod),world,row.getDouble("x"),row.getDouble("y"),row.getDouble("z"),row.getLong("started"));
            result.put(id,r);
        }catch(RuntimeException bad){throw new IOException("Invalid check "+key,bad);}
        return Map.copyOf(result);
    }
    void save(Map<UUID,CheckRecord> all)throws IOException {
        YamlConfiguration y=new YamlConfiguration();y.set("schema",1);y.createSection("checks");
        all.values().stream().sorted(Comparator.comparing(r->r.player().toString())).forEach(r->{
            String k="checks."+r.player()+".";y.set(k+"name",r.name());if(r.moderator()!=null)y.set(k+"moderator",r.moderator().toString());
            y.set(k+"world",r.world().toString());y.set(k+"x",r.x());y.set(k+"y",r.y());y.set(k+"z",r.z());y.set(k+"started",r.started());
        });
        byte[] bytes=y.saveToString().getBytes(StandardCharsets.UTF_8);
        if(bytes.length>16*1024*1024)throw new IOException("Too many checks");
        Files.createDirectories(file.getParent());Path temp=Files.createTempFile(file.getParent(),"checks-",".tmp");
        try{
            try(FileChannel c=FileChannel.open(temp,StandardOpenOption.WRITE)){ByteBuffer b=ByteBuffer.wrap(bytes);while(b.hasRemaining())c.write(b);c.force(true);}
            Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temp);}
    }
    void audit(String event,CheckRecord r)throws IOException {
        Files.createDirectories(audit.getParent());
        String line=Instant.now()+" event="+event+" player="+r.player()+" name="+r.name()+" moderator="+(r.moderator()==null?"console":r.moderator())+System.lineSeparator();
        Files.writeString(audit,line,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    }
}
