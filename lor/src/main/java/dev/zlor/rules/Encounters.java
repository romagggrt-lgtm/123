package dev.zlor.rules;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Manual campaigns. Starting records UUIDs BEFORE spawn; unload is never victory. */
public final class Encounters {
    public enum Type { DEFAULT, ENRICHED, VOID_KING }
    public enum Mode { IDLE, SPAWNING, ACTIVE, WON, STOPPED }
    public static final List<List<Type>> WAVES=List.of(
        List.of(Type.DEFAULT),List.of(Type.ENRICHED),List.of(Type.DEFAULT,Type.DEFAULT),List.of(Type.DEFAULT,Type.DEFAULT),
        List.of(Type.DEFAULT,Type.ENRICHED),List.of(Type.ENRICHED,Type.ENRICHED),List.of(Type.DEFAULT,Type.DEFAULT,Type.ENRICHED),
        List.of(Type.ENRICHED,Type.ENRICHED,Type.DEFAULT),List.of(Type.ENRICHED,Type.ENRICHED,Type.ENRICHED),List.of(Type.ENRICHED,Type.ENRICHED,Type.ENRICHED,Type.ENRICHED));
    public record Run(int wave,Mode mode,Map<UUID,Type> members,Set<UUID> dead,boolean oldStar) {
        public Run {members=Map.copyOf(members);dead=Set.copyOf(dead);if(wave<0||wave>10||mode==null||members.size()>64||!members.keySet().containsAll(dead))throw new IllegalArgumentException("Invalid wave state");}
        public boolean active(){return mode==Mode.ACTIVE||mode==Mode.SPAWNING;}
        public Run death(UUID id){if(!active()||!members.containsKey(id)||dead.contains(id))return this;var deaths=new HashSet<>(dead);deaths.add(id);return new Run(wave,deaths.size()==members.size()?Mode.WON:mode,members,deaths,oldStar);}
        public Run mode(Mode value){return new Run(wave,value,members,dead,oldStar);}
        public static Run empty(){return new Run(0,Mode.IDLE,Map.of(),Set.of(),false);}
    }
    public static String name(Type type){return switch(type){case DEFAULT->"Дракон Края";case ENRICHED->"Обогащённый дракон Края";case VOID_KING->"Царь Войда";};}
    public static double health(Type type){return switch(type){case DEFAULT->200;case ENRICHED->500;case VOID_KING->1500;};}
    public static Run start(Run before,int number,boolean oldStar){
        if(before.active())throw new IllegalArgumentException("Сначала останови текущую волну.");if(number<1||number>10)throw new IllegalArgumentException("Номер волны:1–10");
        var members=new LinkedHashMap<UUID,Type>();for(var type:WAVES.get(number-1))members.put(UUID.randomUUID(),type);return new Run(number,Mode.SPAWNING,members,Set.of(),oldStar);
    }
    public static final class Store {
        private final Path file;
        public Store(Path file){this.file=file;}
        public Run load()throws IOException{if(!Files.exists(file))return Run.empty();try{
            if(Files.size(file)>65536)throw new IllegalArgumentException();var p=new Properties();try(var r=Files.newBufferedReader(file)){p.load(r);}
            if(!"1".equals(p.getProperty("schema")))throw new IllegalArgumentException("schema");var members=new HashMap<UUID,Type>();var dead=new HashSet<UUID>();
            for(var key:p.stringPropertyNames())if(key.startsWith("dragon."))members.put(UUID.fromString(key.substring(7)),Type.valueOf(p.getProperty(key)));else if(key.startsWith("dead.")){if(!p.getProperty(key).equals("true"))throw new IllegalArgumentException();dead.add(UUID.fromString(key.substring(5)));}
            String star=p.getProperty("oldStar");if(!Set.of("true","false").contains(star))throw new IllegalArgumentException();return new Run(Integer.parseInt(p.getProperty("wave")),Mode.valueOf(p.getProperty("mode")),members,dead,Boolean.parseBoolean(star));
        }catch(RuntimeException e){throw new IOException("Состояние волн повреждено; исходный файл сохранён",e);}}
        public void save(Run run)throws IOException{var p=new Properties();p.setProperty("schema","1");p.setProperty("wave",""+run.wave);p.setProperty("mode",run.mode.name());p.setProperty("oldStar",""+run.oldStar);run.members.forEach((id,t)->p.setProperty("dragon."+id,t.name()));for(var id:run.dead)p.setProperty("dead."+id,"true");write(file,p);}
    }
    public static void write(Path file,Properties p)throws IOException{Files.createDirectories(file.getParent());var temp=Files.createTempFile(file.getParent(),"smplore-",".tmp");try{try(var out=Files.newBufferedWriter(temp)){p.store(out,"SmpLore");}Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}}
    private Encounters(){}
}
