package dev.zpantera;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.InvalidConfigurationException;

final class PanteraStore {
    private final Path file;
    PanteraStore(Path file) { this.file=file; }
    void load(Map<UUID,PanteraState> target) throws IOException, InvalidConfigurationException {
        if (!Files.exists(file)) return;
        if (Files.size(file)>2*1024*1024) throw new IOException("Pantera state exceeds 2 MiB; preserved");
        var y=new YamlConfiguration(); y.load(file.toFile());
        if (y.getInt("schema")!=1) throw new IOException("Unknown Pantera schema; preserved");
        Map<UUID,PanteraState> loaded=new LinkedHashMap<>();
        try {
            var section=y.getConfigurationSection("panteras");
            if (section!=null) {
                if (section.getKeys(false).size()>1024) throw new IllegalArgumentException("Too many records");
                for (String key:section.getKeys(false)) {
                    var s=Objects.requireNonNull(section.getConfigurationSection(key));
                    UUID id=UUID.fromString(key), world=UUID.fromString(Objects.requireNonNull(s.getString("world")));
                    double x=s.getDouble("x"), py=s.getDouble("y"), z=s.getDouble("z"), h=s.getDouble("health");
                    float yaw=(float)s.getDouble("yaw");
                    if (!Double.isFinite(x)||!Double.isFinite(py)||!Double.isFinite(z)||!Float.isFinite(yaw)
                        ||Math.abs(x)>30_000_000||Math.abs(z)>30_000_000||Math.abs(py)>4096||!Double.isFinite(h)||h<=0||h>1024)
                        throw new IllegalArgumentException("Invalid coordinates/health");
                    // World may be temporarily unavailable; retaining its UUID does not load its chunks.
                    PanteraState p=new PanteraState(id,new Location(Bukkit.getWorld(world),x,py,z,yaw,0),h);
                    p.world=world;
                    if (s.contains("owner")) {
                        p.owner=UUID.fromString(Objects.requireNonNull(s.getString("owner"))); p.expiresAt=s.getLong("expires-at");
                        if (p.expiresAt<=0) throw new IllegalArgumentException("Invalid lifetime");
                    }
                    loaded.put(id,p);
                }
            }
        } catch (RuntimeException e) { throw new IOException("Invalid Pantera state; source preserved",e); }
        target.putAll(loaded);
    }
    void save(Map<UUID,PanteraState> records) throws IOException {
        var y=new YamlConfiguration(); y.set("schema",1);
        for (PanteraState p:records.values()) {
            var s=y.createSection("panteras."+p.id); s.set("world",p.world.toString());
            s.set("x",p.x); s.set("y",p.y); s.set("z",p.z); s.set("yaw",p.yaw); s.set("health",p.health);
            if (p.owner!=null) { s.set("owner",p.owner.toString()); s.set("expires-at",p.expiresAt); }
        }
        Files.createDirectories(file.getParent()); Path tmp=Files.createTempFile(file.getParent(),"pantera-",".tmp");
        try {
            Files.writeString(tmp,y.saveToString(),StandardCharsets.UTF_8);
            try(var channel=java.nio.channels.FileChannel.open(tmp,StandardOpenOption.WRITE)) { channel.force(true); }
            try { Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(tmp); }
    }
}
