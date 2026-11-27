package dev.ztest;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

public final class Souls implements Listener {
    private final ZTestPlugin p;
    private final NamespacedKey soul, visual;
    private final Random random=new Random();
    private final Map<UUID,Location> destinations=new HashMap<>();
    Souls(ZTestPlugin plugin){p=plugin;soul=new NamespacedKey(p,"soul");visual=new NamespacedKey(p,"soul_visual");}
    void start(){
        long interval=Math.max(5,Math.min(600,p.getConfig().getInt("souls.interval-seconds",30)))*20L;
        Bukkit.getScheduler().runTaskTimer(p,this::spawnTick,interval,interval);
        Bukkit.getScheduler().runTaskTimer(p,this::moveTick,1L,1L);
    }
    private void spawnTick(){
        World w=p.purgatory;if(w==null||w.getPlayers().isEmpty())return;
        int limit=Math.max(6,Math.min(300,p.getConfig().getInt("souls.max-per-world",30)));
        int count=(int)w.getEntitiesByClass(Vex.class).stream().filter(this::isSoul).count();
        int min=Math.max(6,Math.min(80,p.getConfig().getInt("souls.spawn-distance-min",12))),max=Math.max(min,Math.min(96,p.getConfig().getInt("souls.spawn-distance-max",24)));
        double chance=Math.max(0,Math.min(1,p.getConfig().getDouble("souls.chance",.45)));
        for(Player player:w.getPlayers()){
            if(count+6>limit)break;if(player.getGameMode()==GameMode.SPECTATOR||random.nextDouble()>chance)continue;
            if(w.getNearbyEntitiesByType(Vex.class,player.getLocation(),32,this::isSoul).size()>=6)continue;
            double angle=random.nextDouble()*Math.PI*2,dist=min+random.nextDouble()*(max-min);
            int x=player.getLocation().getBlockX()+(int)Math.round(Math.cos(angle)*dist),z=player.getLocation().getBlockZ()+(int)Math.round(Math.sin(angle)*dist);
            if(w.isChunkLoaded(x>>4,z>>4))count+=spawnGroup(player,x,z);
        }
    }
    private int spawnGroup(Player player,int x,int z){
        World w=player.getWorld();List<Location> positions=new ArrayList<>();
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
            if(!w.isChunkLoaded((x+dx)>>4,(z+dz)>>4))continue;
            int y=Terrain.GROUND+2;
            if(w.getBlockAt(x+dx,y,z+dz).isPassable()&&w.getBlockAt(x+dx,y+1,z+dz).isPassable()&&w.getBlockAt(x+dx,Terrain.GROUND,z+dz).getType().isSolid())positions.add(new Location(w,x+dx+.5,y,z+dz+.5));
        }
        if(positions.size()<6)return 0;Collections.shuffle(positions,random);int spawned=0;
        for(int i=0;i<6;i++)try{
            Vex v=w.spawn(positions.get(i),Vex.class,CreatureSpawnEvent.SpawnReason.CUSTOM,e->{e.getPersistentDataContainer().set(soul,PersistentDataType.BYTE,(byte)1);configure(e);});
            if(v.isValid()){attach(v);spawned++;}
        }catch(IllegalArgumentException ex){p.getLogger().warning("Не удалось создать душу: "+ex.getMessage());}
        return spawned;
    }
    private void configure(Vex v){
        v.setCustomName("§dДуша");v.setCustomNameVisible(false);v.setLimitedLifetime(false);v.setRemoveWhenFarAway(true);v.setPersistent(false);
        v.setAI(false);v.setGravity(false);v.setInvisible(true);v.setSilent(true);v.setTarget(null);
        if(v.getEquipment()!=null)v.getEquipment().clear();
    }
    private void attach(Vex v){
        if(v.getPassengers().stream().anyMatch(e->e.getPersistentDataContainer().has(visual,PersistentDataType.BYTE)))return;
        ItemStack item=new ItemStack(Material.PAPER);item.editMeta(m->m.setItemModel(new NamespacedKey("z_test","soul")));
        ItemDisplay display=v.getWorld().spawn(v.getLocation(),ItemDisplay.class,d->{
            d.setItemStack(item);d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);d.setPersistent(false);
            d.getPersistentDataContainer().set(visual,PersistentDataType.BYTE,(byte)1);
            d.setInterpolationDuration(3);d.setTeleportDuration(3);
        });
        if(!v.addPassenger(display))display.remove();
    }
    private void moveTick(){
        World w=p.purgatory;if(w==null)return;
        Set<UUID> alive=new HashSet<>();
        for(Vex v:w.getEntitiesByClass(Vex.class)){
            if(!isSoul(v))continue;alive.add(v.getUniqueId());
            if(v.hasAI()||!v.isInvisible())configure(v);
            attach(v);
            Location at=v.getLocation(),to=destinations.get(v.getUniqueId());
            if(to==null||to.distanceSquared(at)<1.0||random.nextInt(100)==0){
                double angle=random.nextDouble()*Math.PI*2, distance=3+random.nextDouble()*5;
                to=at.clone().add(Math.cos(angle)*distance,0,Math.sin(angle)*distance);
                to.setY(Terrain.GROUND+2.0+random.nextDouble()*.65);
                if(!w.isChunkLoaded(to.getBlockX()>>4,to.getBlockZ()>>4)||!to.getBlock().isPassable()||!to.clone().add(0,1,0).getBlock().isPassable())to=at.clone().add(0,.1,0);
                destinations.put(v.getUniqueId(),to);
            }
            Vector direction=to.toVector().subtract(at.toVector());
            if(direction.lengthSquared()>.001){
                direction.normalize().multiply(.035);
                Location next=at.clone().add(direction);
                next.setYaw((float)Math.toDegrees(Math.atan2(-direction.getX(),direction.getZ())));next.setPitch(0);
                if(next.getBlock().isPassable()&&next.clone().add(0,.7,0).getBlock().isPassable()){
                    if(!v.teleport(next,org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN,io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS))destinations.remove(v.getUniqueId());
                }else destinations.remove(v.getUniqueId());
            }
        }
        destinations.keySet().retainAll(alive);
    }
    void cleanup(){
        if(p.purgatory==null)return;
        for(Vex v:p.purgatory.getEntitiesByClass(Vex.class))if(isSoul(v)){for(Entity e:v.getPassengers())if(e.getPersistentDataContainer().has(visual,PersistentDataType.BYTE))e.remove();v.remove();}
    }
    public boolean isSoul(Vex v){return v.getPersistentDataContainer().has(soul,PersistentDataType.BYTE);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void target(EntityTargetEvent e){if(e.getEntity() instanceof Vex v&&isSoul(v))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void damage(EntityDamageByEntityEvent e){if(e.getDamager() instanceof Vex v&&isSoul(v))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void death(EntityDeathEvent e){
        if(!(e.getEntity() instanceof Vex v)||!isSoul(v))return;
        v.getPassengers().stream().filter(d->d.getPersistentDataContainer().has(visual,PersistentDataType.BYTE)).forEach(Entity::remove);
        destinations.remove(v.getUniqueId());e.getDrops().clear();
        int min=Math.max(1,Math.min(64,p.getConfig().getInt("souls.drops-min",1))),max=Math.max(min,Math.min(64,p.getConfig().getInt("souls.drops-max",2)));
        var drop=p.detector.shard();drop.setAmount(min+random.nextInt(max-min+1));e.getDrops().add(drop);e.setDroppedExp(0);
    }
}
