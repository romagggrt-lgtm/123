package dev.ztest;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Every mutation is committed before it is exposed to gameplay. */
public final class StateStore {
    private final Path file;
    private Map<UUID,Rift> rifts=new LinkedHashMap<>();
    private Set<Point> detectors=new LinkedHashSet<>();
    public StateStore(File folder) throws IOException, InvalidConfigurationException {
        Files.createDirectories(folder.toPath());file=folder.toPath().resolve("state.yml");
        if(Files.exists(file)){
            var y=new YamlConfiguration();y.load(file.toFile());
            if(y.getInt("schema",0)!=1)throw new IOException("Неизвестная версия state.yml; данные не изменены");
            for(Object item:y.getList("rifts",List.of())){Rift r=Rift.read(item);if(rifts.putIfAbsent(r.id(),r)!=null)throw new IOException("Повторяющийся ID разлома");}
            for(Object item:y.getList("detectors",List.of()))if(!detectors.add(Point.read(item)))throw new IOException("Повторяющийся детектор");
        }
    }
    public Collection<Rift> rifts(){return List.copyOf(rifts.values());}
    public boolean hasDetector(Point p){return detectors.contains(p);}
    public int detectorCount(){return detectors.size();}
    public void addRift(Rift r)throws IOException{var n=new LinkedHashMap<>(rifts);if(n.putIfAbsent(r.id(),r)!=null)throw new IOException("ID разлома уже занят");write(n,detectors);rifts=n;}
    public void removeRift(UUID id)throws IOException{var n=new LinkedHashMap<>(rifts);n.remove(id);write(n,detectors);rifts=n;}
    public void addDetector(Point p)throws IOException{var n=new LinkedHashSet<>(detectors);n.add(p);write(rifts,n);detectors=n;}
    public void removeDetector(Point p)throws IOException{var n=new LinkedHashSet<>(detectors);n.remove(p);write(rifts,n);detectors=n;}
    private void write(Map<UUID,Rift> r,Set<Point> d)throws IOException{
        var y=new YamlConfiguration();y.set("schema",1);y.set("rifts",r.values().stream().map(Rift::save).toList());y.set("detectors",d.stream().map(Point::save).toList());
        Path temp=Files.createTempFile(file.getParent(),"state-",".tmp");
        try{y.save(temp.toFile());if(Files.exists(file))Files.copy(file,file.resolveSibling("state.yml.bak"),StandardCopyOption.REPLACE_EXISTING);try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temp);}
    }
}
