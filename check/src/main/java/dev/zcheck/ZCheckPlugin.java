package dev.zcheck;

import java.time.Duration;
import java.util.*;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public class ZCheckPlugin extends JavaPlugin implements Listener,CommandExecutor,TabCompleter,ZCheckApi {
    private final MiniMessage mm=MiniMessage.miniMessage();
    private Map<UUID,CheckRecord> checks=Map.of();
    private final Set<UUID> allowedTeleport=new HashSet<>();
    private final Map<UUID,Long> blockedMessages=new HashMap<>();
    private CheckStore store; private long ticks;
    @Override public void onEnable(){
        try{
            saveDefaultConfig();store=createStore();checks=store.load();
            Objects.requireNonNull(getCommand("zcheck")).setExecutor(this);getCommand("zcheck").setTabCompleter(this);
            Bukkit.getPluginManager().registerEvents(this,this);
            Bukkit.getServicesManager().register(ZCheckApi.class,this,this,ServicePriority.Normal);
            for(Player p:Bukkit.getOnlinePlayers())if(isChecked(p.getUniqueId()))restore(p);
            Bukkit.getScheduler().runTaskTimer(this,this::pulse,1,1);
            getLogger().info("Загружено активных проверок: "+checks.size());
        }catch(Exception e){getLogger().log(Level.SEVERE,"Не удалось загрузить проверки; данные не изменены",e);Bukkit.getPluginManager().disablePlugin(this);}
    }
    protected CheckStore createStore(){return new CheckStore(getDataFolder().toPath());}
    @Override public void onDisable(){
        Bukkit.getServicesManager().unregisterAll(this);
        for(Player p:Bukkit.getOnlinePlayers())if(isChecked(p.getUniqueId()))p.sendActionBar(Component.empty());
        allowedTeleport.clear();blockedMessages.clear();
    }
    @Override public boolean isChecked(UUID id){return checks.containsKey(id);}
    private boolean checked(Player p){return isChecked(p.getUniqueId());}
    private Component format(String path, String fallback,String name){
        String raw=getConfig().getString(path,fallback);
        try{return mm.deserialize(raw,Placeholder.unparsed("player",name==null?"":name));}
        catch(IllegalArgumentException e){getLogger().warning("Некорректное сообщение "+path+": "+e.getMessage());return Component.text(fallback);}
    }
    private void message(CommandSender sender,String key,String name){
        Component pre=format("messages.prefix","[Z_Check] ",name);
        sender.sendMessage(pre.append(format("messages."+key,key,name)));
    }
    private boolean replace(Map<UUID,CheckRecord> next){
        try{store.save(next);checks=Map.copyOf(next);return true;}
        catch(Exception e){getLogger().log(Level.SEVERE,"Не удалось сохранить checks.yml; состояние не изменено",e);return false;}
    }
    private void audit(String kind,CheckRecord record){try{store.audit(kind,record);}catch(Exception e){getLogger().log(Level.SEVERE,"Не удалось записать audit.log",e);}}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!sender.hasPermission("zcheck.use")){message(sender,"no-permission",null);return true;}
        if(args.length!=1){message(sender,"usage",null);return true;}
        Player online=Bukkit.getPlayerExact(args[0]);
        CheckRecord existing=online==null?checks.values().stream().filter(r->r.name().equalsIgnoreCase(args[0])||r.player().toString().equalsIgnoreCase(args[0])).findFirst().orElse(null):checks.get(online.getUniqueId());
        if(existing!=null){
            Map<UUID,CheckRecord> next=new HashMap<>(checks);next.remove(existing.player());
            if(!replace(next)){message(sender,"save-error",null);return true;}
            audit("END",existing);blockedMessages.remove(existing.player());Player p=Bukkit.getPlayer(existing.player());
            if(p!=null){p.sendActionBar(Component.empty());showTitle(p,false);message(p,"ended-player",null);}
            message(sender,"ended-moderator",p==null?existing.name():p.getName());return true;
        }
        if(online==null){message(sender,"player-not-found",null);return true;}
        if(sender instanceof Player p&&p.getUniqueId().equals(online.getUniqueId())&&!getConfig().getBoolean("allow-self-check",false)){message(sender,"self",null);return true;}
        if(online.hasPermission("zcheck.bypass")){message(sender,"bypass",null);return true;}
        if(online.isInsideVehicle()&&!online.leaveVehicle()){message(sender,"vehicle",null);return true;}
        UUID mod=sender instanceof Player p?p.getUniqueId():null;CheckRecord r=CheckRecord.create(online,mod);
        Map<UUID,CheckRecord> next=new HashMap<>(checks);next.put(r.player(),r);
        if(!replace(next)){message(sender,"save-error",null);return true;}
        audit("START",r);online.closeInventory();online.setVelocity(new Vector(0,0,0));announce(online);message(sender,"started-moderator",online.getName());return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command cmd,String alias,String[] args){
        if(!sender.hasPermission("zcheck.use")||args.length!=1)return List.of();
        var names=new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);Bukkit.getOnlinePlayers().forEach(p->names.add(p.getName()));checks.values().forEach(r->names.add(r.name()));
        String prefix=args[0].toLowerCase(Locale.ROOT);return names.stream().filter(n->n.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
    private Duration duration(String key,int fallback){return Duration.ofMillis(Math.clamp(getConfig().getLong("title."+key,fallback),0,1200)*50);}
    private void showTitle(Player p,boolean start){
        String key=start?"start":"end";
        var times=Title.Times.times(duration("fade-in-ticks",10),duration("stay-ticks",70),duration("fade-out-ticks",15));
        p.showTitle(Title.title(format("title."+key,key,null),format("title."+key+"-subtitle","",null),times));
    }
    private void announce(Player p){
        showTitle(p,true);message(p,"started-player",null);p.sendActionBar(format("messages.reminder","Проверка",null));
        if(getConfig().getBoolean("effects.enabled",true))try{
            Sound sound=Sound.valueOf(getConfig().getString("effects.sound","ENTITY_EXPERIENCE_ORB_PICKUP").toUpperCase(Locale.ROOT));
            p.playSound(p.getLocation(),sound,(float)Math.clamp(getConfig().getDouble("effects.volume",.8),0,2),(float)Math.clamp(getConfig().getDouble("effects.pitch",.7),.5,2));
            Particle particle=Particle.valueOf(getConfig().getString("effects.particle","CLOUD").toUpperCase(Locale.ROOT));
            p.spawnParticle(particle,p.getLocation().add(0,1,0),Math.clamp(getConfig().getInt("effects.particle-count",16),0,100),.5,.6,.5,.02);
        }catch(IllegalArgumentException e){getLogger().warning("Неподдерживаемый звук или частица в config.yml: "+e.getMessage());}
        UUID id=p.getUniqueId();CheckRecord initial=checks.get(id);for(int delay:getConfig().getIntegerList("title.repeat-delays-ticks"))if(delay>0&&delay<=1200)Bukkit.getScheduler().runTaskLater(this,()->{Player live=Bukkit.getPlayer(id);if(live!=null&&initial.equals(checks.get(id)))showTitle(live,true);},delay);
    }
    private void pulse(){
        ticks++;int interval=Math.clamp(getConfig().getInt("reminder-interval-ticks",40),20,1200);
        if(ticks%interval!=0)return;
        for(UUID id:checks.keySet()){Player p=Bukkit.getPlayer(id);if(p!=null)p.sendActionBar(format("messages.reminder","Проверка",null));}
    }
    private void restore(Player p){
        CheckRecord r=checks.get(p.getUniqueId());if(r==null)return;
        p.closeInventory();if(p.isInsideVehicle())p.leaveVehicle();
        Location anchor=r.at(p.getYaw(),p.getPitch());
        if(anchor!=null){allowedTeleport.add(p.getUniqueId());try{if(!p.teleport(anchor))getLogger().warning("Телепортация при восстановлении проверки отменена: "+p.getName());}finally{allowedTeleport.remove(p.getUniqueId());}}
        else getLogger().warning("Мир проверки ещё не загружен для "+p.getName()+": "+r.world());
        p.setVelocity(new Vector(0,0,0));announce(p);
    }
    @Override public boolean moveCheckedPlayer(Player player,Location to){
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("ZCheckApi must be called on main thread");
        CheckRecord old=checks.get(player.getUniqueId());if(old==null||to==null||to.getWorld()==null||!to.getWorld().getWorldBorder().isInside(to))return false;
        CheckRecord replacement;try{replacement=old.move(to);}catch(IllegalArgumentException e){return false;}
        Map<UUID,CheckRecord> next=new HashMap<>(checks);next.put(old.player(),replacement);if(!replace(next))return false;
        allowedTeleport.add(old.player());boolean ok;
        try{ok=player.teleport(to);}finally{allowedTeleport.remove(old.player());}
        if(!ok){var rollback=new HashMap<>(checks);rollback.put(old.player(),old);if(!replace(rollback))getLogger().severe("Не удалось восстановить старую точку проверки "+old.player());return false;}
        audit("MOVE",replacement);return true;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void teleport(PlayerTeleportEvent e){if(checked(e.getPlayer())&&!allowedTeleport.contains(e.getPlayer().getUniqueId()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void move(PlayerMoveEvent e){
        if(e instanceof PlayerTeleportEvent||!checked(e.getPlayer())||e.getTo()==null)return;
        Location from=e.getFrom(),to=e.getTo();if(from.getX()==to.getX()&&from.getY()==to.getY()&&from.getZ()==to.getZ())return;
        Location anchor=checks.get(e.getPlayer().getUniqueId()).at(to.getYaw(),to.getPitch());
        if(anchor==null||!anchor.getWorld().equals(from.getWorld()))anchor=new Location(from.getWorld(),from.getX(),from.getY(),from.getZ(),to.getYaw(),to.getPitch());
        e.setTo(anchor);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void velocity(PlayerVelocityEvent e){if(checked(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void vehicle(VehicleEnterEvent e){if(e.getEntered() instanceof Player p&&checked(p))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void breakBlock(BlockBreakEvent e){if(block(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void place(BlockPlaceEvent e){if(block(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void interact(PlayerInteractEvent e){if(block(e.getPlayer())){e.setUseInteractedBlock(Event.Result.DENY);e.setUseItemInHand(Event.Result.DENY);e.setCancelled(true);}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void interactEntity(PlayerInteractEntityEvent e){if(block(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void inventory(InventoryClickEvent e){if(e.getWhoClicked() instanceof Player p&&block(p))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void drag(InventoryDragEvent e){if(e.getWhoClicked() instanceof Player p&&block(p))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void open(InventoryOpenEvent e){if(e.getPlayer() instanceof Player p&&block(p))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void drop(PlayerDropItemEvent e){if(block(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void pickup(EntityPickupItemEvent e){if(e.getEntity() instanceof Player p&&block(p))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void consume(PlayerItemConsumeEvent e){if(block(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void swap(PlayerSwapHandItemsEvent e){if(block(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void bucketFill(PlayerBucketFillEvent e){if(block(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void bucketEmpty(PlayerBucketEmptyEvent e){if(block(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void hanging(HangingBreakByEntityEvent e){if(e.getRemover() instanceof Player p&&checked(p))e.setCancelled(true);}
    private boolean block(Player p){return checked(p)&&getConfig().getBoolean("block-interactions",true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void damage(EntityDamageEvent e){
        if(e.getEntity() instanceof Player p&&checked(p)&&getConfig().getBoolean("protect-from-damage",true))e.setCancelled(true);
        if(e instanceof EntityDamageByEntityEvent by){Entity cause=by.getDamager();Player attacker=cause instanceof Player p?p:cause instanceof Projectile projectile&&projectile.getShooter() instanceof Player p?p:cause instanceof TNTPrimed t&&t.getSource() instanceof Player p?p:null;if(attacker!=null&&checked(attacker))e.setCancelled(true);}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void command(PlayerCommandPreprocessEvent e){
        Player p=e.getPlayer();if(!checked(p))return;String[] words=e.getMessage().substring(1).split("\\s+",2);String base=words[0].toLowerCase(Locale.ROOT);
        boolean allowed=getConfig().getStringList("allowed-commands").stream().map(s->s.replaceFirst("^/","").toLowerCase(Locale.ROOT)).anyMatch(base::equals);
        if(!allowed){e.setCancelled(true);long now=System.currentTimeMillis(),last=blockedMessages.getOrDefault(p.getUniqueId(),0L);if(now-last>=3000){message(p,"command-blocked",null);blockedMessages.put(p.getUniqueId(),now);}}
    }
    @EventHandler(priority=EventPriority.MONITOR) public void join(PlayerJoinEvent e){UUID id=e.getPlayer().getUniqueId();if(isChecked(id))Bukkit.getScheduler().runTask(this,()->{Player p=Bukkit.getPlayer(id);if(p!=null&&isChecked(id))restore(p);});}
    @EventHandler(priority=EventPriority.MONITOR) public void quit(PlayerQuitEvent e){
        Player p=e.getPlayer();CheckRecord r=checks.get(p.getUniqueId());blockedMessages.remove(p.getUniqueId());if(r==null)return;
        audit("QUIT",r);Set<UUID> notified=new HashSet<>();Component text=format("messages.prefix","[Z_Check] ",null).append(format("messages.logout","Игрок <player> вышел с проверки.",p.getName()));
        if(getConfig().getBoolean("public-logout-notice",false)){for(Player viewer:Bukkit.getOnlinePlayers())if(!viewer.getUniqueId().equals(p.getUniqueId()))viewer.sendMessage(text);}
        else for(Player mod:Bukkit.getOnlinePlayers())if(!mod.getUniqueId().equals(p.getUniqueId())&&((getConfig().getBoolean("notify-initiator",true)&&mod.getUniqueId().equals(r.moderator()))||(getConfig().getBoolean("notify-moderators",true)&&mod.hasPermission("zcheck.notify")))&&notified.add(mod.getUniqueId()))mod.sendMessage(text);
        Bukkit.getConsoleSender().sendMessage(text);
    }
    @EventHandler(priority=EventPriority.MONITOR) public void respawn(PlayerRespawnEvent e){UUID id=e.getPlayer().getUniqueId();if(isChecked(id))Bukkit.getScheduler().runTask(this,()->{Player p=Bukkit.getPlayer(id);if(p!=null&&isChecked(id))restore(p);});}
}
