package dev.zlor;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** The staff uses real player abilities; all cooldowns are persisted on the holder. */
public final class LordStaff implements Listener,AutoCloseable {
 private record Defender(UUID owner,long expires){}
 private record Bullet(UUID owner,Vector direction,long expires){}
 private final ZLorPlugin p;
 private final Map<UUID,Defender> defenders=new HashMap<>();
 private final Map<UUID,Bullet> bullets=new HashMap<>();
 public LordStaff(ZLorPlugin p){this.p=p;}
 private NamespacedKey cooldown(String id){return new NamespacedKey("z_lor","lord_staff_"+id);}
 private long remaining(Player player,String id){Long end=player.getPersistentDataContainer().get(cooldown(id),PersistentDataType.LONG);return Math.max(0,(end==null?0:end)-System.currentTimeMillis());}
 private boolean ready(Player player,String id){long left=remaining(player,id);if(left<=0)return true;player.sendMessage(Component.text("[Посох] Перезарядка: "+((left+999)/1000)+" сек.",NamedTextColor.LIGHT_PURPLE));return false;}
 private void used(Player player,String id,int seconds,String label){player.getPersistentDataContainer().set(cooldown(id),PersistentDataType.LONG,System.currentTimeMillis()+seconds*1000L);player.sendMessage(Component.text("[Посох] "+label+" · перезарядка "+seconds+" сек.",NamedTextColor.LIGHT_PURPLE));}
 @EventHandler(priority=EventPriority.HIGH)
 public void interact(PlayerInteractEvent event){
  if(event.getHand()!=EquipmentSlot.HAND||event.getPlayer().getGameMode()==GameMode.SPECTATOR||!event.getAction().isRightClick()||!p.items.is(event.getItem(),"lord_staff"))return;
  if(event.getAction()==Action.RIGHT_CLICK_BLOCK && event.useItemInHand()==Event.Result.DENY)return;event.setCancelled(true);Player player=event.getPlayer();
  if(player.isSneaking())teleport(player);else shoot(player);
 }
 @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
 public void swap(PlayerSwapHandItemsEvent event){Player player=event.getPlayer();if(!p.items.is(player.getInventory().getItemInMainHand(),"lord_staff"))return;event.setCancelled(true);summon(player);}
 private void shoot(Player player){
  if(!ready(player,"shot"))return;Vector direction=player.getEyeLocation().getDirection().normalize();Location start=player.getEyeLocation().add(direction.clone().multiply(.8));
  ShulkerBullet bullet=player.getWorld().spawn(start,ShulkerBullet.class,e->{e.setShooter(player);e.setVelocity(direction.clone().multiply(.8));e.setPersistent(false);});
  if(!bullet.isValid()){player.sendMessage(Component.text("[Посох] Снаряд не создан.",NamedTextColor.RED));return;}
  bullets.put(bullet.getUniqueId(),new Bullet(player.getUniqueId(),direction,p.tick+80));player.getWorld().playSound(start,Sound.ENTITY_SHULKER_SHOOT,1,1);used(player,"shot",15,"Снаряд");
 }
 private void teleport(Player player){
  if(!ready(player,"teleport"))return;Location eye=player.getEyeLocation();RayTraceResult hit=player.getWorld().rayTraceBlocks(eye,eye.getDirection(),64,FluidCollisionMode.NEVER,true);
  if(hit==null||hit.getHitBlock()==null||hit.getHitBlockFace()==null){player.sendMessage(Component.text("[Посох] Посмотри на безопасное место в пределах 64 блоков.",NamedTextColor.RED));return;}
  Block adjacent=hit.getHitBlock().getRelative(hit.getHitBlockFace());Location destination=null;
  for(int offset=1;offset>=-3;offset--){Block feet=adjacent.getRelative(0,offset,0),floor=feet.getRelative(0,-1,0);if(feet.getY()<=player.getWorld().getMinHeight()||feet.getY()+1>=player.getWorld().getMaxHeight())continue;
   if(!feet.isPassable()||!feet.getRelative(0,1,0).isPassable()||!floor.getType().isSolid()||feet.isLiquid()||floor.isLiquid()||danger(floor.getType()))continue;
   Location at=feet.getLocation().add(.5,0,.5);if(!player.getWorld().getWorldBorder().isInside(at))continue;at.setYaw(player.getYaw());at.setPitch(player.getPitch());destination=at;break;
  }
  if(destination==null){player.sendMessage(Component.text("[Посох] Здесь нет безопасного места для телепортации.",NamedTextColor.RED));return;}
  Location old=player.getLocation();if(!player.teleport(destination,PlayerTeleportEvent.TeleportCause.PLUGIN)){player.sendMessage(Component.text("[Посох] Телепортация отменена.",NamedTextColor.RED));return;}
  old.getWorld().spawnParticle(Particle.PORTAL,old.add(0,1,0),30,.4,.5,.4,.3);player.getWorld().spawnParticle(Particle.PORTAL,destination.clone().add(0,1,0),30,.4,.5,.4,.3);player.getWorld().playSound(destination,Sound.ENTITY_ENDERMAN_TELEPORT,1,1);used(player,"teleport",20,"Телепортация");
 }
 private boolean danger(Material m){return m==Material.MAGMA_BLOCK||m==Material.CAMPFIRE||m==Material.SOUL_CAMPFIRE||m==Material.CACTUS||m==Material.SWEET_BERRY_BUSH||m==Material.POWDER_SNOW;}
 private void summon(Player player){
  if(!ready(player,"summon"))return;
  List<Enderman> made=new ArrayList<>();Location origin=player.getLocation();
  for(int i=0;i<3;i++){double angle=2*Math.PI*i/3;Location at=origin.clone().add(Math.cos(angle)*2,0,Math.sin(angle)*2);Block feet=at.getBlock();if(!feet.isPassable()||!feet.getRelative(0,1,0).isPassable()||!feet.getRelative(0,2,0).isPassable()||!feet.getRelative(0,-1,0).getType().isSolid())continue;
   Enderman defender=player.getWorld().spawn(at,Enderman.class,e->{e.setPersistent(false);e.setRemoveWhenFarAway(true);e.setCanPickupItems(false);e.setCustomNameVisible(false);});
   if(defender.isValid()){defenders.put(defender.getUniqueId(),new Defender(player.getUniqueId(),p.tick+1200));made.add(defender);}
  }
  if(made.size()!=3){for(Enderman enderman:made){defenders.remove(enderman.getUniqueId());enderman.remove();}player.sendMessage(Component.text("[Посох] Нужно свободное место для трёх защитников.",NamedTextColor.RED));return;}
  player.getWorld().spawnParticle(Particle.PORTAL,origin.clone().add(0,1,0),80,1,.8,1,.5);player.getWorld().playSound(origin,Sound.ENTITY_ENDERMAN_TELEPORT,1,.8f);used(player,"summon",25,"Защитники");
 }
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
 public void target(EntityTargetLivingEntityEvent e){Defender d=defenders.get(e.getEntity().getUniqueId());if(d!=null&&(!(e.getTarget() instanceof Player target)||target.getUniqueId().equals(d.owner)))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
 public void damage(EntityDamageByEntityEvent e){Defender d=defenders.get(e.getDamager().getUniqueId());if(d!=null&&e.getEntity().getUniqueId().equals(d.owner))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void change(EntityChangeBlockEvent e){if(defenders.containsKey(e.getEntity().getUniqueId()))e.setCancelled(true);}
 @EventHandler public void death(EntityDeathEvent e){if(defenders.remove(e.getEntity().getUniqueId())!=null){e.getDrops().clear();e.setDroppedExp(0);}}
 @EventHandler(ignoreCancelled=true) public void hit(ProjectileHitEvent e){bullets.remove(e.getEntity().getUniqueId());}
 @EventHandler public void quit(PlayerQuitEvent e){removeOwner(e.getPlayer().getUniqueId());}
 @EventHandler public void deathPlayer(PlayerDeathEvent e){removeOwner(e.getEntity().getUniqueId());}
 private void removeOwner(UUID owner){for(var entry:List.copyOf(defenders.entrySet()))if(entry.getValue().owner.equals(owner)){Entity entity=Bukkit.getEntity(entry.getKey());if(entity!=null)entity.remove();defenders.remove(entry.getKey());}}
 public void tick(){
  for(var entry:List.copyOf(bullets.entrySet())){Entity entity=Bukkit.getEntity(entry.getKey());Bullet data=entry.getValue();if(!(entity instanceof ShulkerBullet)||!entity.isValid()||p.tick>=data.expires){if(entity!=null)entity.remove();bullets.remove(entry.getKey());continue;}entity.setVelocity(data.direction.clone().multiply(.8));}
  if(p.tick%10==0)for(var entry:List.copyOf(defenders.entrySet())){Entity entity=Bukkit.getEntity(entry.getKey());Defender d=entry.getValue();Player owner=Bukkit.getPlayer(d.owner);if(!(entity instanceof Enderman enderman)||!entity.isValid()||owner==null||owner.isDead()||p.tick>=d.expires||!owner.getWorld().equals(entity.getWorld())||owner.getLocation().distanceSquared(entity.getLocation())>1600){if(entity!=null)entity.remove();defenders.remove(entry.getKey());continue;}
   Player closest=owner.getWorld().getPlayers().stream().filter(t->!t.equals(owner)&&!t.isDead()&&t.getGameMode()!=GameMode.CREATIVE&&t.getGameMode()!=GameMode.SPECTATOR&&t.getLocation().distanceSquared(enderman.getLocation())<=1024).min(Comparator.comparingDouble(t->t.getLocation().distanceSquared(enderman.getLocation()))).orElse(null);if(!Objects.equals(enderman.getTarget(),closest))enderman.setTarget(closest);
  }
  if(p.tick%10==0)for(Player player:Bukkit.getOnlinePlayers())if(p.items.is(player.getInventory().getItemInMainHand(),"lord_staff")){
   String line="♥♥  Снаряд "+status(player,"shot")+"  |  Телепорт "+status(player,"teleport")+"  |  Защитники "+status(player,"summon");player.sendActionBar(Component.text(line,NamedTextColor.LIGHT_PURPLE));
  }
 }
 private String status(Player player,String id){long ms=remaining(player,id);return ms<=0?"✓":((ms+999)/1000)+"с";}
 @Override public void close(){for(UUID id:List.copyOf(defenders.keySet())){Entity entity=Bukkit.getEntity(id);if(entity!=null)entity.remove();}for(UUID id:List.copyOf(bullets.keySet())){Entity entity=Bukkit.getEntity(id);if(entity!=null)entity.remove();}defenders.clear();bullets.clear();}
}
