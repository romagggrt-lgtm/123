package dev.ztest;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vex;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;

public final class Souls implements Listener {
    private final ZTestPlugin p;private final NamespacedKey soul;private final Random random=new Random();
    Souls(ZTestPlugin plugin){p=plugin;soul=new NamespacedKey(p,"soul");}
    void start(){long interval=Math.max(5,Math.min(600,p.getConfig().getInt("souls.interval-seconds",30)))*20L;Bukkit.getScheduler().runTaskTimer(p,this::tick,interval,interval);}
    private void tick(){
        World w=p.purgatory;if(w==null||w.getPlayers().isEmpty())return;
        int limit=Math.max(6,Math.min(300,p.getConfig().getInt("souls.max-per-world",30)));
        int count=(int)w.getEntitiesByClass(Vex.class).stream().filter(this::isSoul).count();
        int min=Math.max(6,Math.min(80,p.getConfig().getInt("souls.spawn-distance-min",12))),max=Math.max(min,Math.min(96,p.getConfig().getInt("souls.spawn-distance-max",24)));
        double chance=Math.max(0,Math.min(1,p.getConfig().getDouble("souls.chance",.45)));
        for(Player player:w.getPlayers()){
            if(count+6>limit)break;if(player.getGameMode()==GameMode.SPECTATOR||random.nextDouble()>chance)continue;
            boolean close=w.getNearbyEntitiesByType(Vex.class,player.getLocation(),32,this::isSoul).size()>=6;if(close)continue;
            double angle=random.nextDouble()*Math.PI*2,dist=min+random.nextDouble()*(max-min);int x=player.getLocation().getBlockX()+(int)Math.round(Math.cos(angle)*dist),z=player.getLocation().getBlockZ()+(int)Math.round(Math.sin(angle)*dist);
            if(!w.isChunkLoaded(x>>4,z>>4))continue;count+=spawnGroup(player,x,z);
        }
    }
    private int spawnGroup(Player target,int x,int z){
        World w=target.getWorld();List<Location> positions=new ArrayList<>();
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
            if(!w.isChunkLoaded((x+dx)>>4,(z+dz)>>4))continue;
            int y=Terrain.GROUND+2;if(w.getBlockAt(x+dx,y,z+dz).isPassable()&&w.getBlockAt(x+dx,y+1,z+dz).isPassable()&&w.getBlockAt(x+dx,Terrain.GROUND,z+dz).getType().isSolid())positions.add(new Location(w,x+dx+.5,y,z+dz+.5));
        }
        if(positions.size()<6)return 0;Collections.shuffle(positions,random);int spawned=0;
        for(int i=0;i<6;i++)try{Vex v=w.spawn(positions.get(i),Vex.class,CreatureSpawnEvent.SpawnReason.CUSTOM,entity->{entity.getPersistentDataContainer().set(soul,PersistentDataType.BYTE,(byte)1);entity.setCustomName("§dДуша");entity.setCustomNameVisible(false);entity.setLimitedLifetime(false);entity.setRemoveWhenFarAway(true);entity.setPersistent(false);if(entity.getEquipment()!=null)entity.getEquipment().clear();entity.setTarget(target);});if(v.isValid())spawned++;}catch(IllegalArgumentException ex){p.getLogger().warning("Не удалось создать душу: "+ex.getMessage());}
        return spawned;
    }
    public boolean isSoul(Vex v){return v.getPersistentDataContainer().has(soul,PersistentDataType.BYTE);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void death(EntityDeathEvent e){
        if(!(e.getEntity() instanceof Vex v)||!isSoul(v))return;
        e.getDrops().clear();int min=Math.max(1,Math.min(64,p.getConfig().getInt("souls.drops-min",1))),max=Math.max(min,Math.min(64,p.getConfig().getInt("souls.drops-max",2)));
        var drop=p.detector.shard();drop.setAmount(min+random.nextInt(max-min+1));e.getDrops().add(drop);e.setDroppedExp(0);
    }
}
