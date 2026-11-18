package dev.zuluz;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

/** Effects are shown only to the selected player; the underlying blocks are never changed. */
final class Hallucinations implements Listener, AutoCloseable {
 private record FakeBlock(UUID world,int x,int y,int z){}
 private record FakeMob(UUID viewer,long expires){}
 private final JavaPlugin p;
 private final Set<UUID> enabled=new HashSet<>();
 private final Map<UUID,Set<FakeBlock>> ores=new HashMap<>();
 private final Map<UUID,FakeMob> mobs=new HashMap<>();
 private final Random random=new Random();
 private long tick;
 Hallucinations(JavaPlugin p){this.p=p;for(String id:p.getConfig().getStringList("hallucinations"))try{enabled.add(UUID.fromString(id));}catch(IllegalArgumentException ignored){}}
 void set(Player player,boolean value){if(value)enabled.add(player.getUniqueId());else{enabled.remove(player.getUniqueId());clear(player);}p.getConfig().set("hallucinations",enabled.stream().map(UUID::toString).sorted().toList());p.saveConfig();}
 boolean active(Player player){return enabled.contains(player.getUniqueId());}
 void tick(){
  tick++;
  if(tick%5==0) for(var entry:List.copyOf(mobs.entrySet())){
   Entity e=Bukkit.getEntity(entry.getKey());FakeMob data=entry.getValue();Player target=Bukkit.getPlayer(data.viewer());
   if(e==null||!e.isValid()||target==null||!active(target)||!target.getWorld().equals(e.getWorld())||tick>=data.expires()||e.getLocation().distanceSquared(target.getLocation())>900){if(e!=null)e.remove();mobs.remove(entry.getKey());continue;}
   Location at=e.getLocation(),to=target.getLocation();double distance=at.distance(to);
   if(distance>2.5&&distance<20){var step=to.toVector().subtract(at.toVector()).setY(0);if(step.lengthSquared()>.01){step.normalize().multiply(.25);Location next=at.clone().add(step);if(next.getBlock().isPassable()&&next.clone().add(0,1,0).getBlock().isPassable()&&next.clone().subtract(0,1,0).getBlock().getType().isSolid()){next.setDirection(step);e.teleport(next);}}}
  }
  if(tick%100!=0)return;
  for(UUID id:List.copyOf(enabled)){Player player=Bukkit.getPlayer(id);if(player==null||player.isDead())continue;spawnOre(player);if(mobs.values().stream().filter(m->m.viewer.equals(id)).count()<2)spawnMob(player);}
 }
 private void spawnOre(Player player){
  Set<FakeBlock> set=ores.computeIfAbsent(player.getUniqueId(),x->new HashSet<>());
  if(set.size()>=6){FakeBlock old=set.iterator().next();set.remove(old);restore(player,old);}
  Location center=player.getLocation();World world=center.getWorld();
  for(int i=0;i<36;i++){int x=center.getBlockX()+random.nextInt(11)-5,y=center.getBlockY()+random.nextInt(7)-3,z=center.getBlockZ()+random.nextInt(11)-5;if(y<world.getMinHeight()||y>=world.getMaxHeight()||!world.isChunkLoaded(x>>4,z>>4))continue;Block b=world.getBlockAt(x,y,z);Material type=b.getType();if(type!=Material.STONE&&type!=Material.DEEPSLATE)continue;
   boolean exposed=false;for(int[] d:new int[][]{{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}})if(b.getRelative(d[0],d[1],d[2]).isPassable()){exposed=true;break;}if(!exposed)continue;
   FakeBlock key=new FakeBlock(world.getUID(),x,y,z);if(set.add(key))player.sendBlockChange(b.getLocation(),(type==Material.DEEPSLATE?Material.DEEPSLATE_DIAMOND_ORE:Material.DIAMOND_ORE).createBlockData());break;
  }
 }
 private void spawnMob(Player player){
  Location pos=player.getLocation();World w=pos.getWorld();double angle=random.nextDouble()*Math.PI*2;int x=pos.getBlockX()+(int)(Math.cos(angle)*8),z=pos.getBlockZ()+(int)(Math.sin(angle)*8);if(!w.isChunkLoaded(x>>4,z>>4))return;
  for(int dy=2;dy>=-3;dy--){int y=pos.getBlockY()+dy;if(y<=w.getMinHeight()||y+2>=w.getMaxHeight())continue;Location where=new Location(w,x+.5,y,z+.5);if(!where.getBlock().isPassable()||!where.clone().add(0,1,0).getBlock().isPassable()||!where.clone().subtract(0,1,0).getBlock().getType().isSolid())continue;
   Class<? extends Mob> type=switch(random.nextInt(4)){case 0->Zombie.class;case 1->Skeleton.class;case 2->Creeper.class;default->Spider.class;};
   Mob mob=w.spawn(where,type,e->{e.setVisibleByDefault(false);e.setAI(false);e.setSilent(true);e.setInvulnerable(true);e.setCollidable(false);e.setPersistent(false);e.setRemoveWhenFarAway(true);});mobs.put(mob.getUniqueId(),new FakeMob(player.getUniqueId(),tick+300));player.showEntity(p,mob);break;
  }
 }
 private void restore(Player p,FakeBlock f){World w=Bukkit.getWorld(f.world);if(w!=null&&p.getWorld().equals(w)&&w.isChunkLoaded(f.x>>4,f.z>>4))p.sendBlockChange(new Location(w,f.x,f.y,f.z),w.getBlockAt(f.x,f.y,f.z).getBlockData());}
 private void clear(Player player){Set<FakeBlock> set=ores.remove(player.getUniqueId());if(set!=null)for(FakeBlock f:set)restore(player,f);for(var entry:List.copyOf(mobs.entrySet()))if(entry.getValue().viewer.equals(player.getUniqueId())){Entity e=Bukkit.getEntity(entry.getKey());if(e!=null)e.remove();mobs.remove(entry.getKey());}}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void onBreak(BlockBreakEvent e){Block b=e.getBlock();Set<FakeBlock> set=ores.get(e.getPlayer().getUniqueId());FakeBlock f=new FakeBlock(b.getWorld().getUID(),b.getX(),b.getY(),b.getZ());if(set!=null&&set.remove(f)){e.setCancelled(true);p.getServer().getScheduler().runTask(p,()->restore(e.getPlayer(),f));}}
 @EventHandler(priority=EventPriority.HIGHEST) public void onDamage(EntityDamageEvent e){if(mobs.containsKey(e.getEntity().getUniqueId())){e.setCancelled(true);if(e instanceof EntityDamageByEntityEvent){mobs.remove(e.getEntity().getUniqueId());e.getEntity().remove();}}}
 @EventHandler public void onChange(EntityChangeBlockEvent e){if(mobs.containsKey(e.getEntity().getUniqueId()))e.setCancelled(true);}
 @EventHandler public void onQuit(PlayerQuitEvent e){clear(e.getPlayer());}
 @EventHandler public void onWorld(PlayerChangedWorldEvent e){clear(e.getPlayer());}
 @Override public void close(){for(Player player:Bukkit.getOnlinePlayers())clear(player);for(UUID id:List.copyOf(mobs.keySet())){Entity e=Bukkit.getEntity(id);if(e!=null)e.remove();}mobs.clear();}
}
