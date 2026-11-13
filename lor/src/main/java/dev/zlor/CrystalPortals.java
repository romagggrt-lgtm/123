package dev.zlor;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;

/** Four-player-crystal portal. No terrain is overwritten and the blast is cosmetic. */
public final class CrystalPortals implements Listener {
 private final ZLorPlugin p;
 private final Set<String> pending=new HashSet<>(), expiring=new HashSet<>();
 private final Set<UUID> traveling=new HashSet<>();
 public CrystalPortals(ZLorPlugin p){this.p=p;}
 private record Candidate(State.Pos pos,List<UUID> ids){}
 @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
 public void placed(PlayerInteractEvent e){
  if(e.getHand()==null||e.useItemInHand()==Event.Result.DENY||e.getClickedBlock()==null||!e.getAction().isRightClick()||e.getItem()==null||e.getItem().getType()!=Material.END_CRYSTAL)return;
  Block base=e.getClickedBlock();if(base.getType()!=Material.OBSIDIAN&&base.getType()!=Material.BEDROCK)return;
  World world=base.getWorld();int x=base.getX(),y=base.getY()+1,z=base.getZ();
  Bukkit.getScheduler().runTaskLater(p,()->{if(!world.isChunkLoaded(x>>4,z>>4))return;
   for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(dx!=0||dz!=0){Candidate c=candidate(world,x+dx,y,z+dz);if(c!=null){create(c);return;}}
  },2L);
 }
 private Candidate candidate(World w,int x,int y,int z){
  Block center=w.getBlockAt(x,y,z);State.Pos pos=new State.Pos(p.worldKey(w),x,y,z);
  if(!center.getType().isAir()||p.state().crystalPortals.containsKey(pos.key())||pending.contains(pos.key()))return null;
  for(int[][] offsets:List.of(new int[][]{{1,0},{-1,0},{0,1},{0,-1}},new int[][]{{1,1},{1,-1},{-1,1},{-1,-1}})){
   List<UUID> found=new ArrayList<>();boolean valid=true;
   for(int[] offset:offsets){int bx=x+offset[0],bz=z+offset[1];Block base=w.getBlockAt(bx,y-1,bz);
    if(base.getType()!=Material.OBSIDIAN&&base.getType()!=Material.BEDROCK){valid=false;break;}
    EnderCrystal crystal=w.getNearbyEntities(new Location(w,bx+.5,y+.5,bz+.5),.48,1,.48,e->e instanceof EnderCrystal).stream().map(e->(EnderCrystal)e).filter(e->e.getLocation().getBlockX()==bx&&e.getLocation().getBlockY()==y&&e.getLocation().getBlockZ()==bz).findFirst().orElse(null);
    if(crystal==null){valid=false;break;}found.add(crystal.getUniqueId());
   }
   if(valid&&new HashSet<>(found).size()==4&&p.state().crystalPortals.values().stream().noneMatch(v->v.crystals.stream().anyMatch(found::contains)))return new Candidate(pos,List.copyOf(found));
  }return null;
 }
 private void create(Candidate c){
  String key=c.pos.key();if(!pending.add(key))return;
  State.CrystalPortal portal=new State.CrystalPortal();portal.center=c.pos;portal.crystals.addAll(c.ids);portal.expires=System.currentTimeMillis()+1_200_000L;
  p.store.change(s->{if(s.crystalPortals.containsKey(key)||s.crystalPortals.values().stream().anyMatch(v->v.crystals.stream().anyMatch(c.ids::contains)))throw new IllegalArgumentException("Кристаллы уже используются");s.crystalPortals.put(key,portal);},s->{
   pending.remove(key);World w=p.world(c.pos.world());Block b=w==null?null:w.getBlockAt(c.pos.x(),c.pos.y(),c.pos.z());
   if(b==null||!b.getType().isAir()||c.ids.stream().anyMatch(id->!(Bukkit.getEntity(id) instanceof EnderCrystal))){p.store.change(next->next.crystalPortals.remove(key));return;}
   b.setType(Material.END_PORTAL,false);w.playSound(b.getLocation().add(.5,.5,.5),Sound.BLOCK_END_PORTAL_SPAWN,1,1);
  },error->{pending.remove(key);p.getLogger().warning("Не удалось сохранить портал: "+error.getMessage());});
 }
 public void restore(){for(var portal:List.copyOf(p.state().crystalPortals.values()))if(portal.expires<=System.currentTimeMillis())expire(portal);}
 public void tick(){if(p.tick%20!=0)return;for(var portal:List.copyOf(p.state().crystalPortals.values()))if(portal.expires<=System.currentTimeMillis())expire(portal);}
 private void expire(State.CrystalPortal portal){
  State.Pos pos=portal.center;String key=pos.key();World w=p.world(pos.world());if(w==null||!expiring.add(key))return;
  // At most four already-generated chunks may contain the portal and its crystals.
  List<java.util.concurrent.CompletableFuture<org.bukkit.Chunk>> chunks=new ArrayList<>();Set<Long> seen=new HashSet<>();
  for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){int cx=(pos.x()+dx)>>4,cz=(pos.z()+dz)>>4;long pair=((long)cx<<32)^(cz&0xffffffffL);if(seen.add(pair))chunks.add(w.getChunkAtAsync(cx,cz,false));}
  java.util.concurrent.CompletableFuture.allOf(chunks.toArray(java.util.concurrent.CompletableFuture[]::new)).whenComplete((v,error)->Bukkit.getScheduler().runTask(p,()->{
   if(error!=null||p.stopping()){expiring.remove(key);return;}Block block=w.getBlockAt(pos.x(),pos.y(),pos.z());
   if(block.getType()==Material.END_PORTAL)block.setType(Material.AIR,false);
   for(UUID id:portal.crystals){Entity entity=Bukkit.getEntity(id);if(entity instanceof EnderCrystal&&entity.getWorld().equals(w))entity.remove();}
   Location at=new Location(w,pos.x()+.5,pos.y()+.5,pos.z()+.5);w.spawnParticle(Particle.EXPLOSION_EMITTER,at,1);w.playSound(at,Sound.ENTITY_GENERIC_EXPLODE,1,1);
   p.store.change(s->s.crystalPortals.remove(key),s->expiring.remove(key),err->expiring.remove(key));
  }));
 }
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
 public void move(PlayerMoveEvent e){
  if(e.getTo()==null)return;Block b=e.getTo().getBlock();State.Pos pos=new State.Pos(p.worldKey(b.getWorld()),b.getX(),b.getY(),b.getZ());
  State.CrystalPortal portal=p.state().crystalPortals.get(pos.key());if(portal==null||portal.expires<=System.currentTimeMillis()||b.getType()!=Material.END_PORTAL||!traveling.add(e.getPlayer().getUniqueId()))return;
  Player player=e.getPlayer();World end=Bukkit.getWorlds().stream().filter(w->w.getEnvironment()==World.Environment.THE_END).findFirst().orElse(null);
  if(end==null){traveling.remove(player.getUniqueId());player.sendMessage(Component.text("Энд сейчас недоступен."));return;}
  player.sendActionBar(Component.text("Поиск безопасного места на островах Энда…"));find(player,end,0);
 }
 private void find(Player player,World end,int attempts){
  UUID id=player.getUniqueId();if(!player.isOnline()||p.stopping()){traveling.remove(id);return;}
  if(attempts>=24){traveling.remove(id);player.sendMessage(Component.text("Не удалось найти безопасный остров. Попробуйте ещё раз."));return;}
  double angle=ThreadLocalRandom.current().nextDouble(Math.PI*2),radius=ThreadLocalRandom.current().nextDouble(1300,5000);int x=(int)(Math.cos(angle)*radius),z=(int)(Math.sin(angle)*radius);
  end.getChunkAtAsync(x>>4,z>>4,true).whenComplete((chunk,error)->Bukkit.getScheduler().runTask(p,()->{
   if(!player.isOnline()||p.stopping()){traveling.remove(id);return;}if(error!=null||chunk==null){find(player,end,attempts+1);return;}
   for(int dx=0;dx<16;dx+=2)for(int dz=0;dz<16;dz+=2){int xx=(chunk.getX()<<4)+dx,zz=(chunk.getZ()<<4)+dz;int y=end.getHighestBlockYAt(xx,zz);Block floor=end.getBlockAt(xx,y,zz);
    if(floor.getType().isSolid()&&floor.getType()!=Material.BEDROCK&&y>p.voids.height(end)+2&&y+2<end.getMaxHeight()&&floor.getRelative(0,1,0).isPassable()&&floor.getRelative(0,2,0).isPassable()){
     Location destination=new Location(end,xx+.5,y+1,zz+.5,player.getYaw(),player.getPitch());player.teleportAsync(destination).whenComplete((success,err)->Bukkit.getScheduler().runTask(p,()->{traveling.remove(id);if(err!=null||!Boolean.TRUE.equals(success))player.sendMessage(Component.text("Телепортация не удалась."));}));return;
    }
   }find(player,end,attempts+1);
  }));
 }
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
 public void nativePortal(PlayerPortalEvent e){Block b=e.getFrom().getBlock();if(p.state().crystalPortals.containsKey(new State.Pos(p.worldKey(b.getWorld()),b.getX(),b.getY(),b.getZ()).key()))e.setCancelled(true);}
 @EventHandler public void quit(PlayerQuitEvent e){traveling.remove(e.getPlayer().getUniqueId());}
}
