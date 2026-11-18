package dev.zuluz;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.*;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

/** Routes interactions to the target's connection and keeps their real body at the operator's camera. */
final class Capture extends PacketListenerAbstract implements Listener,AutoCloseable {
 private record Session(UUID operator,UUID target,User operatorUser,User targetUser,long end){}
 private record Route(Session session,boolean operator){}
 private final JavaPlugin p;
 private final Map<UUID,Session> people=new ConcurrentHashMap<>();
 private final Map<Object,Route> channels=new ConcurrentHashMap<>();
 private boolean moving;private long ticks;
 private static final Set<PacketType.Play.Client> ACTIONS=EnumSet.of(PacketType.Play.Client.CLICK_WINDOW,PacketType.Play.Client.CLICK_WINDOW_BUTTON,PacketType.Play.Client.CLOSE_WINDOW,PacketType.Play.Client.INTERACT_ENTITY,PacketType.Play.Client.PLAYER_DIGGING,PacketType.Play.Client.HELD_ITEM_CHANGE,PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT,PacketType.Play.Client.USE_ITEM,PacketType.Play.Client.ANIMATION,PacketType.Play.Client.CREATIVE_INVENTORY_ACTION,PacketType.Play.Client.CRAFT_RECIPE_REQUEST,PacketType.Play.Client.SELECT_TRADE,PacketType.Play.Client.NAME_ITEM,PacketType.Play.Client.EDIT_BOOK,PacketType.Play.Client.UPDATE_SIGN,PacketType.Play.Client.SLOT_STATE_CHANGE,PacketType.Play.Client.SELECT_BUNDLE_ITEM,PacketType.Play.Client.PICK_ITEM_FROM_BLOCK,PacketType.Play.Client.PICK_ITEM_FROM_ENTITY,PacketType.Play.Client.PLAYER_INPUT,PacketType.Play.Client.ENTITY_ACTION);
 private static final Set<PacketType.Play.Client> MOVEMENT=EnumSet.of(PacketType.Play.Client.PLAYER_POSITION,PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION,PacketType.Play.Client.PLAYER_ROTATION,PacketType.Play.Client.PLAYER_FLYING,PacketType.Play.Client.VEHICLE_MOVE,PacketType.Play.Client.STEER_BOAT,PacketType.Play.Client.PLAYER_ABILITIES);
 private static final Set<PacketType.Play.Server> INVENTORY=EnumSet.of(PacketType.Play.Server.WINDOW_ITEMS,PacketType.Play.Server.SET_SLOT,PacketType.Play.Server.WINDOW_PROPERTY,PacketType.Play.Server.OPEN_WINDOW,PacketType.Play.Server.OPEN_HORSE_WINDOW,PacketType.Play.Server.CLOSE_WINDOW,PacketType.Play.Server.HELD_ITEM_CHANGE);
 Capture(JavaPlugin p){super(PacketListenerPriority.HIGHEST);this.p=p;}
 @Override public void onPacketReceive(PacketReceiveEvent e){Route route=channels.get(e.getChannel());if(route==null)return;Session s=route.session();if(!(e.getPacketType() instanceof PacketType.Play.Client type))return;
  if(route.operator()&&ACTIONS.contains(type)){e.setCancelled(true);s.targetUser().receivePacketSilently(e.getFullBufferClone());}
  else if(!route.operator()&&(ACTIONS.contains(type)||MOVEMENT.contains(type))){e.setCancelled(true);}
 }
 @Override public void onPacketSend(PacketSendEvent e){Route route=channels.get(e.getChannel());if(route==null||!(e.getPacketType() instanceof PacketType.Play.Server type)||!INVENTORY.contains(type))return;
  if(route.operator())e.setCancelled(true);else{route.session().operatorUser().sendPacketSilently(e.getFullBufferClone());if(type==PacketType.Play.Server.OPEN_WINDOW||type==PacketType.Play.Server.OPEN_HORSE_WINDOW)e.setCancelled(true);}
 }
 String start(Player operator,Player target,int seconds){
  if(operator.equals(target))return "Нельзя захватить самого себя.";if(people.containsKey(operator.getUniqueId())||people.containsKey(target.getUniqueId()))return "Один из игроков уже участвует в захвате.";
  if(operator.isDead()||target.isDead()||operator.isInsideVehicle()||target.isInsideVehicle())return "Игроки должны быть живы и не находиться в транспорте.";
  User a=PacketEvents.getAPI().getPlayerManager().getUser(operator),b=PacketEvents.getAPI().getPlayerManager().getUser(target);
  if(a==null||b==null||a.getClientVersion()!=b.getClientVersion())return "Версии клиентов игроков должны совпадать.";
  String path="recovery."+operator.getUniqueId();p.getConfig().set(path+".location",operator.getLocation());p.getConfig().set(path+".mode",operator.getGameMode().name());p.getConfig().set(path+".invulnerable",operator.isInvulnerable());p.getConfig().set(path+".collidable",operator.isCollidable());p.getConfig().set(path+".silent",operator.isSilent());p.getConfig().set(path+".allow-flight",operator.getAllowFlight());p.getConfig().set(path+".flying",operator.isFlying());p.saveConfig();
  operator.closeInventory();target.closeInventory();moving=true;boolean teleported;try{teleported=operator.teleport(target.getLocation(),PlayerTeleportEvent.TeleportCause.PLUGIN);}finally{moving=false;}
  if(!teleported){p.getConfig().set(path,null);p.saveConfig();return "Телепортация отменена другим плагином.";}
  operator.setGameMode(target.getGameMode());operator.setInvulnerable(true);operator.setCollidable(false);operator.setSilent(true);
  Session s=new Session(operator.getUniqueId(),target.getUniqueId(),a,b,System.currentTimeMillis()+seconds*1000L);people.put(s.operator(),s);people.put(s.target(),s);channels.put(a.getChannel(),new Route(s,true));channels.put(b.getChannel(),new Route(s,false));
  for(Player viewer:Bukkit.getOnlinePlayers())viewer.hidePlayer(p,operator);operator.hidePlayer(p,target);target.updateInventory();
  operator.sendMessage(Component.text("§d[Z_Uluz] Управление игроком "+target.getName()+" на "+seconds+" сек. /zahvat stop — завершить."));target.sendMessage(Component.text("§d[Z_Uluz] Сейчас вами временно управляет администратор."));return null;
 }
 void tick(){ticks++;for(Session s:new HashSet<>(people.values())){Player a=Bukkit.getPlayer(s.operator()),b=Bukkit.getPlayer(s.target());if(a==null||b==null||a.isDead()||b.isDead()||System.currentTimeMillis()>=s.end()){stop(s,"Сеанс завершён.");continue;}
  if(ticks%10==0){a.sendHealthUpdate(b.getHealth(),b.getFoodLevel(),b.getSaturation());a.sendExperienceChange(b.getExp(),b.getLevel());}
  if(!a.getWorld().equals(b.getWorld())||a.getGameMode()!=b.getGameMode()){stop(s,"Мир или режим игрока изменился.");continue;}
  Location where=a.getLocation();Location existing=b.getLocation();if(ticks%10==0||where.distanceSquared(existing)>.0001||where.getYaw()!=existing.getYaw()||where.getPitch()!=existing.getPitch()){
   moving=true;boolean ok;try{ok=b.teleport(where,PlayerTeleportEvent.TeleportCause.PLUGIN);}finally{moving=false;}if(!ok)stop(s,"Перемещение отменено другим плагином.");
  }
 }}
 boolean stop(Player person){Session s=people.get(person.getUniqueId());if(s==null)return false;stop(s,"Сеанс завершён.");return true;}
 private void stop(Session s,String message){people.remove(s.operator());people.remove(s.target());channels.remove(s.operatorUser().getChannel());channels.remove(s.targetUser().getChannel());Player a=Bukkit.getPlayer(s.operator()),b=Bukkit.getPlayer(s.target());if(a!=null){for(Player viewer:Bukkit.getOnlinePlayers())viewer.showPlayer(p,a);if(b!=null)a.showPlayer(p,b);recover(a);a.sendHealthUpdate();a.sendExperienceChange(a.getExp(),a.getLevel());a.closeInventory();a.updateInventory();if(p.isEnabled())Bukkit.getScheduler().runTaskLater(p,()->{if(a.isOnline()&&!people.containsKey(a.getUniqueId()))a.updateInventory();},2);a.sendMessage("§d[Z_Uluz] "+message);}if(b!=null){b.closeInventory();b.updateInventory();b.sendMessage("§d[Z_Uluz] "+message);}}
 void recover(Player a){String key="recovery."+a.getUniqueId();if(!p.getConfig().isConfigurationSection(key))return;Location old=p.getConfig().getLocation(key+".location");try{a.setGameMode(GameMode.valueOf(p.getConfig().getString(key+".mode","SURVIVAL")));a.setInvulnerable(p.getConfig().getBoolean(key+".invulnerable"));a.setCollidable(p.getConfig().getBoolean(key+".collidable",true));a.setSilent(p.getConfig().getBoolean(key+".silent"));a.setAllowFlight(p.getConfig().getBoolean(key+".allow-flight"));if(a.getAllowFlight())a.setFlying(p.getConfig().getBoolean(key+".flying"));if(old!=null){moving=true;try{if(!a.teleport(old,PlayerTeleportEvent.TeleportCause.PLUGIN)){a.sendMessage("§c[Z_Uluz] Не удалось вернуться на исходное место.");return;}}finally{moving=false;}}p.getConfig().set(key,null);p.saveConfig();}catch(RuntimeException e){p.getLogger().warning("Не удалось восстановить "+a.getName()+": "+e.getMessage());}}
 @EventHandler(priority=EventPriority.MONITOR) public void closeInventory(InventoryCloseEvent e){if(e.getPlayer() instanceof Player player){Session s=people.get(player.getUniqueId());if(s!=null&&s.target().equals(player.getUniqueId())&&p.isEnabled())Bukkit.getScheduler().runTaskLater(p,()->{if(player.isOnline()&&people.get(player.getUniqueId())==s)player.updateInventory();},1);}}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void pickup(EntityPickupItemEvent e){if(e.getEntity() instanceof Player player){Session s=people.get(player.getUniqueId());if(s!=null&&s.operator().equals(player.getUniqueId()))e.setCancelled(true);}}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void experience(com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent e){Session s=people.get(e.getPlayer().getUniqueId());if(s!=null&&s.operator().equals(e.getPlayer().getUniqueId()))e.setCancelled(true);}
 @EventHandler public void quit(PlayerQuitEvent e){stop(e.getPlayer());}
 @EventHandler public void join(PlayerJoinEvent e){recover(e.getPlayer());for(Session s:new HashSet<>(people.values())){Player a=Bukkit.getPlayer(s.operator());if(a!=null)e.getPlayer().hidePlayer(p,a);}}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void teleport(PlayerTeleportEvent e){if(!moving){Session s=people.get(e.getPlayer().getUniqueId());if(s!=null){if(s.operator().equals(e.getPlayer().getUniqueId()))e.setCancelled(true);stop(e.getPlayer());}}}
 @EventHandler public void death(PlayerDeathEvent e){stop(e.getEntity());}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void command(PlayerCommandPreprocessEvent e){Session s=people.get(e.getPlayer().getUniqueId());if(s!=null&&s.target().equals(e.getPlayer().getUniqueId())){e.setCancelled(true);e.getPlayer().sendMessage("§c[Z_Uluz] Во время захвата команды недоступны.");}}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void chat(io.papermc.paper.event.player.AsyncChatEvent e){Session s=people.get(e.getPlayer().getUniqueId());if(s!=null&&s.target().equals(e.getPlayer().getUniqueId()))e.setCancelled(true);}
 @Override public void close(){for(Session s:new HashSet<>(people.values()))stop(s,"Плагин выключен.");}
}
