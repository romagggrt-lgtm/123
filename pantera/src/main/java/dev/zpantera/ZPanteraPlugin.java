package dev.zpantera;

import java.io.IOException;
import java.util.*;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;

public class ZPanteraPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    Settings settings;
    Rig rig;
    Navigation navigation=new Navigation.Paper();
    LongSupplier clock=System::currentTimeMillis;
    final Map<UUID,PanteraState> states=new LinkedHashMap<>();
    final Map<UUID,PanteraRuntime> active=new HashMap<>();
    private final Map<UUID,UUID> index=new HashMap<>();
    private final Map<String,ItemStack> models=new HashMap<>();
    private final Map<UUID,Long> eggTicks=new HashMap<>();
    private NamespacedKey entityKey, eggKey;
    private PanteraStore store;
    private BukkitTask task;
    private long tick, startupUntil;
    private boolean writable, dirty, closing;

    @Override public void onEnable() {
        saveDefaultConfig(); settings=Settings.read(getConfig()); closing=false;writable=false; tick=0;
        entityKey=new NamespacedKey("z_pantera","entity");eggKey=new NamespacedKey("z_pantera","spawn_egg");
        states.clear();active.clear();index.clear();models.clear();eggTicks.clear();
        try {
            rig=new Rig(getResource("rig.json"));
            store=new PanteraStore(getDataFolder().toPath().resolve("panteras.yml"));store.load(states);writable=true;
        } catch(Exception e) {
            getLogger().log(Level.SEVERE,"Cannot read Pantera state/model. Original save is preserved; plugin disabled.",e);
            getServer().getPluginManager().disablePlugin(this);return;
        }
        var command=Objects.requireNonNull(getCommand("zpantera"));command.setExecutor(this);command.setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this,this);
        // A crash can leave legacy tagged entities in already loaded chunks. Only this namespace is cleaned.
        for(World world:Bukkit.getWorlds()) for(Entity entity:new ArrayList<>(world.getEntities()))
            if(tagged(entity)) entity.remove();
        startupUntil=clock.getAsLong()+settings.startupGraceMillis();
        for(PanteraState state:new ArrayList<>(states.values())) reconcile(state);
        task=Bukkit.getScheduler().runTaskTimer(this,this::pulse,2,2);
        getLogger().info("Z_Pantera 1.0.0 enabled. Uses the single shared resource pack from server.properties; sends no packs.");
    }
    @Override public void onDisable() {
        closing=true;if(task!=null)task.cancel();
        for(PanteraRuntime runtime:active.values()) runtime.snapshot();
        if(writable) save();
        for(PanteraRuntime runtime:new ArrayList<>(active.values())) runtime.remove(this);
        active.clear();index.clear();
    }
    void mark(Entity e,UUID id) {
        e.getPersistentDataContainer().set(entityKey,PersistentDataType.STRING,id.toString());
        e.setPersistent(false);index.put(e.getUniqueId(),id);
        if(!(e instanceof Wolf)) { e.setInvulnerable(true);e.setGravity(false);e.setSilent(true); }
    }
    void unindex(Entity e) { index.remove(e.getUniqueId()); }
    private boolean tagged(Entity e) { return e.getPersistentDataContainer().has(entityKey,PersistentDataType.STRING); }
    private PanteraRuntime runtime(Entity e) { UUID id=index.get(e.getUniqueId());return id==null?null:active.get(id); }
    ItemStack model(String name) {
        return models.computeIfAbsent(name,n->{
            ItemStack item=new ItemStack(Material.PAPER);
            item.editMeta(m->m.setItemModel(new NamespacedKey("z_pantera",n)));return item;
        }).clone();
    }
    ItemStack egg(int amount) {
        ItemStack item=new ItemStack(Material.CAT_SPAWN_EGG,amount);
        item.editMeta(m->{m.displayName(Component.text("Яйцо призыва Пантеры",NamedTextColor.LIGHT_PURPLE));
            m.setItemModel(eggKey);m.getPersistentDataContainer().set(eggKey,PersistentDataType.BYTE,(byte)1);});
        return item;
    }
    private boolean isEgg(ItemStack item) {
        return item!=null&&!item.isEmpty()&&item.getType()==Material.CAT_SPAWN_EGG&&item.hasItemMeta()
            &&item.getItemMeta().getPersistentDataContainer().has(eggKey,PersistentDataType.BYTE);
    }
    private boolean use(Player p) { return p.hasPermission("zpantera.use")||p.hasPermission("zpantera.admin"); }
    private void message(CommandSender p,String text) { p.sendMessage(Component.text(text,NamedTextColor.LIGHT_PURPLE)); }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void eggUse(PlayerInteractEvent e) {
        if(!isEgg(e.getItem())) return;
        e.setCancelled(true);
        if(e.getAction()!=Action.RIGHT_CLICK_BLOCK||e.getHand()==null||!use(e.getPlayer())
            ||e.getPlayer().getGameMode()==GameMode.SPECTATOR) return;
        if(Objects.equals(eggTicks.get(e.getPlayer().getUniqueId()),tick))return;
        Location at=Objects.requireNonNull(e.getClickedBlock()).getRelative(e.getBlockFace()).getLocation().add(.5,0,.5);
        at.setYaw(e.getPlayer().getYaw());
        if(!SafePosition.safe(at)) { message(e.getPlayer(),"Здесь недостаточно безопасного места для пантеры.");return; }
        if(spawn(at)==null) { message(e.getPlayer(),"Не удалось призвать пантеру. Проверьте консоль сервера.");return; }
        eggTicks.put(e.getPlayer().getUniqueId(),tick);
        if(e.getPlayer().getGameMode()!=GameMode.CREATIVE) {
            ItemStack held=e.getPlayer().getInventory().getItem(e.getHand()); held.subtract(1);
            e.getPlayer().getInventory().setItem(e.getHand(),held);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void dispense(BlockDispenseEvent e) { if(isEgg(e.getItem()))e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void rightClick(PlayerInteractEntityEvent e) {
        PanteraRuntime r=runtime(e.getRightClicked());if(r==null)return;
        boolean permitted=!e.isCancelled();e.setCancelled(true);
        if(permitted&&e.getHand()==EquipmentSlot.HAND) interact(e.getPlayer(),r);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void rightClickAt(PlayerInteractAtEntityEvent e) {
        PanteraRuntime r=runtime(e.getRightClicked());if(r==null)return;
        boolean permitted=!e.isCancelled();e.setCancelled(true);
        if(permitted&&e.getHand()==EquipmentSlot.HAND) interact(e.getPlayer(),r);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void leftClick(PrePlayerAttackEntityEvent e) {
        PanteraRuntime r=runtime(e.getAttacked());if(r==null)return;
        // Paper marks a non-damageable Interaction as already cancelled (willAttack=false).
        // That is the normal left-click signal for our hitbox, not a protection cancellation.
        boolean permitted=!e.willAttack()||!e.isCancelled();e.setCancelled(true);
        if(permitted)interact(e.getPlayer(),r);
    }
    void interact(Player player,PanteraRuntime r) {
        if(!use(player)||player.getGameMode()==GameMode.SPECTATOR||player.isDead()
            ||player.getWorld()!=r.base.getWorld()||player.getLocation().distanceSquared(r.base.getLocation())>36)return;
        long now=clock.getAsLong();
        if(r.lastInteraction!=Long.MIN_VALUE&&now-r.lastInteraction<settings.cooldownMillis())return;
        r.lastInteraction=now;
        if(r.state.tame(player.getUniqueId(),now,settings.lifetimeMillis())) { dirty=true;save(); }
        effects(r,true);r.nextHeart=tick+settings.heartTicks();
    }
    private void effects(PanteraRuntime r,boolean burst) {
        Location at=r.base.getLocation();double radius=Math.max(settings.heartRadius(),settings.soundRadius());
        for(Player p:at.getWorld().getPlayers()) {
            double d=p.getLocation().distanceSquared(at);if(d>radius*radius)continue;
            if(burst&&d<=settings.soundRadius()*settings.soundRadius())
                p.playSound(at,"z_pantera:pantera",SoundCategory.NEUTRAL,settings.volume(),1f);
            if(d<=settings.heartRadius()*settings.heartRadius()) {
                int n=burst?settings.burstHearts():settings.idleHearts();
                p.spawnParticle(Particle.HEART,at.clone().add(0,.85,0),n/2,.55,.25,.6,0);
                p.spawnParticle(Particle.HEART,at.clone().add(0,1.55,0),n-n/2,.32,.16,.32,0);
            }
        }
    }
    private PanteraRuntime spawn(Location at) {
        if(states.size()>=settings.maxCount())return null;
        PanteraState p=new PanteraState(UUID.randomUUID(),at,settings.health());states.put(p.id,p);
        PanteraRuntime r=attach(p,at);if(r==null)states.remove(p.id);dirty=true;save();return r;
    }
    private PanteraRuntime attach(PanteraState p,Location at) {
        if(active.containsKey(p.id)||!SafePosition.loaded(at))return active.get(p.id);
        try {
            PanteraRuntime r=new PanteraRuntime(this,p,at);
            if(!r.base.isValid()||r.base.isDead()) { r.remove(this);return null; }
            active.put(p.id,r);return r;
        } catch(RuntimeException ex) { getLogger().log(Level.SEVERE,"Failed to create panther "+p.id,ex);return null; }
    }
    private void remove(UUID id) {
        PanteraRuntime r=active.remove(id);if(r!=null)r.remove(this);
        if(states.remove(id)!=null)dirty=true;
    }
    private void suspend(PanteraRuntime r) {
        active.remove(r.state.id);
        // EntitiesUnload may already mark the navigator invalid. Its location/HP are still
        // available here; do not fall back to a position from the previous periodic save.
        r.state.position(r.base.getLocation());double health=r.base.getHealth();
        if(health>0)r.state.health=health;else states.remove(r.state.id);
        r.remove(this);dirty=true;
    }
    private void save() {
        if(!writable)return;
        for(PanteraRuntime r:active.values())r.snapshot();
        try { store.save(states);dirty=false; }
        catch(IOException e) { dirty=true;getLogger().log(Level.SEVERE,"Failed to save panteras.yml",e); }
    }
    private void reconcile(PanteraState p) {
        long now=clock.getAsLong();
        if(p.expired(now)) { remove(p.id);return; }
        Player owner=p.owner==null?null:Bukkit.getPlayer(p.owner);
        if(p.owner!=null&&(owner==null||!owner.isOnline())) {
            if(now>=startupUntil)remove(p.id);return;
        }
        Location at=p.location();
        if(owner!=null&&(at==null||at.getWorld()!=owner.getWorld()||!SafePosition.loaded(at)
            ||at.distanceSquared(owner.getLocation())>settings.teleportDistance()*settings.teleportDistance())) {
            at=SafePosition.nearby(owner.getLocation());
            if(at!=null) {p.position(at);dirty=true;}
        }
        if(SafePosition.loaded(at)) {
            // A wall may have been built while a stored mob's chunk was unloaded.
            if(!SafePosition.safe(at))at=SafePosition.nearby(at);
            if(at!=null)attach(p,at);
        }
    }
    private void pulse() {
        if(closing||Bukkit.isStopping())return;
        tick+=2;
        if(tick%20==0) for(PanteraState p:new ArrayList<>(states.values())) {
            if(p.expired(clock.getAsLong())||(p.owner!=null&&clock.getAsLong()>=startupUntil&&Bukkit.getPlayer(p.owner)==null))remove(p.id);
            else if(!active.containsKey(p.id))reconcile(p);
        }
        for(PanteraRuntime r:new ArrayList<>(active.values())) {
            if(!r.base.isValid()||r.base.isDead()) {remove(r.state.id);continue;}
            if(tick%10==0)follow(r);
            if(!active.containsKey(r.state.id))continue;
            r.frame(this,tick);
            if(r.state.owner!=null&&tick>=r.nextHeart) {
                effects(r,false);r.nextHeart=tick+(r.run>.5?Math.max(40,settings.heartTicks()-10):settings.heartTicks());
            }
        }
        if(tick%200==0&&(dirty||!active.isEmpty()))save();
        else if(dirty&&tick%20==0)save();
    }
    private void follow(PanteraRuntime r) {
        if(r.state.owner==null)return;
        Player player=Bukkit.getPlayer(r.state.owner);
        if(player==null||!player.isOnline()) {remove(r.state.id);return;}
        if(player.isDead()||player.getGameMode()==GameMode.SPECTATOR) {navigation.stop(r.base);r.moving=false;return;}
        Location at=r.base.getLocation(), target=player.getLocation();
        if(at.getWorld()!=target.getWorld()) {teleport(r,target);return;}
        double distance=at.distanceSquared(target);
        if(distance>settings.teleportDistance()*settings.teleportDistance()) {teleport(r,target);return;}
        if(distance<=settings.stopDistance()*settings.stopDistance()) {
            if(r.moving)navigation.stop(r.base);r.moving=false;r.stuck=0;r.progress=at;return;
        }
        if(!r.moving&&distance<settings.startDistance()*settings.startDistance())return;
        r.moving=true;
        boolean sprint=player.isSprinting()||distance>64;
        navigation.move(r.base,target.clone(),sprint?settings.runSpeed():settings.walkSpeed());
        if(r.progress.getWorld()==at.getWorld()&&at.distanceSquared(r.progress)<.04)r.stuck+=10;
        else r.stuck=0;
        r.progress=at;
        if(r.stuck>=settings.stuckTicks()&&distance>12.25)teleport(r,target);
    }
    private void teleport(PanteraRuntime r,Location owner) {
        if(tick<r.nextTeleport)return;
        r.nextTeleport=tick+60;
        Location safe=SafePosition.nearby(owner);if(safe==null)return;
        navigation.stop(r.base);
        // Other plugins may cancel or redirect EntityTeleportEvent. Cancel unsafe redirects below.
        if(r.base.teleport(safe)) {
            r.state.position(r.base.getLocation());r.progress=r.base.getLocation();r.last=r.base.getLocation();
            r.stuck=0;r.moving=false;dirty=true;
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void safeTeleport(EntityTeleportEvent e) {
        PanteraRuntime r=runtime(e.getEntity());
        if(r!=null&&e.getEntity()==r.base&&(e.getTo()==null||!SafePosition.safe(e.getTo())))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void damage(EntityDamageEvent e) {
        PanteraRuntime r=runtime(e.getEntity());if(r==null)return;
        if(e.getEntity()!=r.base) {e.setCancelled(true);return;}
        if(e instanceof EntityDamageByEntityEvent hit&&hit.getDamager() instanceof Player player) {
            boolean permitted=!e.isCancelled();e.setCancelled(true);if(permitted)interact(player,r);return;
        }
        if(settings.invulnerable())e.setCancelled(true);
        else if(!e.isCancelled())e.setDamage(e.getDamage()*settings.damageMultiplier());
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void outgoingDamage(EntityDamageByEntityEvent e) {
        Entity attacker=e.getDamager();
        if(runtime(attacker)!=null||(attacker instanceof Projectile p&&p.getShooter() instanceof Entity source&&runtime(source)!=null))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void targeting(EntityTargetLivingEntityEvent e) {if(runtime(e.getEntity())!=null)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void breed(EntityBreedEvent e) {if(runtime(e.getMother())!=null||runtime(e.getFather())!=null)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void pickup(EntityPickupItemEvent e) {if(runtime(e.getEntity())!=null)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void blocks(EntityChangeBlockEvent e) {if(runtime(e.getEntity())!=null)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void death(EntityDeathEvent e) {
        PanteraRuntime r=runtime(e.getEntity());if(r==null||e.getEntity()!=r.base)return;
        e.getDrops().clear();e.setDroppedExp(0);e.setShouldPlayDeathSound(false);
        Bukkit.getScheduler().runTask(this,()->{
            if(!e.isCancelled()&&(r.base.isDead()||!r.base.isValid())) {remove(r.state.id);save();}
        });
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void quit(PlayerQuitEvent e) {
        eggTicks.remove(e.getPlayer().getUniqueId());
        // Paper emits quit events during /stop too: retain those pets for onDisable's save.
        if(Bukkit.isStopping())return;
        for(PanteraState p:new ArrayList<>(states.values()))if(e.getPlayer().getUniqueId().equals(p.owner))remove(p.id);
        if(dirty)save();
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void join(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTask(this,()->{
            if(!e.getPlayer().isOnline())return;
            for(PanteraState p:new ArrayList<>(states.values()))if(e.getPlayer().getUniqueId().equals(p.owner)&&!active.containsKey(p.id))reconcile(p);
        });
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void unload(EntitiesUnloadEvent e) {
        if(closing)return;
        Set<UUID> ids=new HashSet<>();for(Entity entity:e.getEntities()){UUID id=index.get(entity.getUniqueId());if(id!=null)ids.add(id);}
        for(UUID id:ids) {PanteraRuntime r=active.get(id);if(r!=null)suspend(r);}
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void load(EntitiesLoadEvent e) {
        for(Entity entity:e.getEntities())if(tagged(entity)&&!index.containsKey(entity.getUniqueId()))entity.remove();
        Bukkit.getScheduler().runTask(this,()->{
            for(PanteraState p:new ArrayList<>(states.values()))
                if(p.world.equals(e.getWorld().getUID())&&((int)Math.floor(p.x)>>4)==e.getChunk().getX()
                    &&((int)Math.floor(p.z)>>4)==e.getChunk().getZ()&&!active.containsKey(p.id))reconcile(p);
        });
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(!sender.hasPermission("zpantera.admin")) {message(sender,"Недостаточно прав.");return true;}
        if(args.length==0) {message(sender,"/zpantera give <ник> [1–64] | spawn | remove | removeall | reload");return true;}
        switch(args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> {
                if(args.length<2||args.length>3) {message(sender,"/zpantera give <ник> [1–64]");break;}
                Player p=Bukkit.getPlayerExact(args[1]);if(p==null) {message(sender,"Игрок не найден.");break;}
                int n=1;try {if(args.length==3)n=Integer.parseInt(args[2]);}catch(NumberFormatException ex){n=0;}
                if(n<1||n>64) {message(sender,"Количество: от 1 до 64.");break;}
                for(ItemStack rest:p.getInventory().addItem(egg(n)).values())p.getWorld().dropItemNaturally(p.getLocation(),rest);
                message(sender,"Яйцо Пантеры выдано игроку "+p.getName()+".");
            }
            case "spawn" -> {
                if(!(sender instanceof Player p)) {message(sender,"Эта команда доступна в игре.");break;}
                Location at=SafePosition.nearby(p.getLocation());
                message(sender,at!=null&&spawn(at)!=null?"Пантера призвана.":"Не удалось призвать: нет безопасного места или достигнут предел.");
            }
            case "remove" -> {
                if(!(sender instanceof Player p)) {message(sender,"Эта команда доступна в игре.");break;}
                PanteraRuntime nearest=active.values().stream().filter(r->r.base.getWorld()==p.getWorld())
                    .filter(r->r.base.getLocation().distanceSquared(p.getLocation())<=256)
                    .min(Comparator.comparingDouble(r->r.base.getLocation().distanceSquared(p.getLocation()))).orElse(null);
                if(nearest==null)message(sender,"Пантера в радиусе 16 блоков не найдена.");
                else {remove(nearest.state.id);save();message(sender,"Пантера удалена.");}
            }
            case "removeall" -> {int n=states.size();for(UUID id:new ArrayList<>(states.keySet()))remove(id);save();message(sender,"Удалено пантер: "+n);}
            case "reload" -> {
                reloadConfig();settings=Settings.read(getConfig());
                for(PanteraRuntime r:active.values()) {
                    r.base.getAttribute(Attribute.MAX_HEALTH).setBaseValue(settings.health());
                    r.base.setHealth(Math.min(r.base.getHealth(),settings.health()));r.base.setInvulnerable(settings.invulnerable());r.updateLabel(this);
                }
                save();message(sender,"Настройки Z_Pantera обновлены.");
            }
            default -> message(sender,"Неизвестная команда. /zpantera");
        }
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command c,String label,String[] args) {
        if(!sender.hasPermission("zpantera.admin"))return List.of();
        List<String> options=args.length==1?List.of("give","spawn","remove","removeall","reload"):
            args.length==2&&args[0].equalsIgnoreCase("give")?Bukkit.getOnlinePlayers().stream().map(Player::getName).toList():
            args.length==3&&args[0].equalsIgnoreCase("give")?List.of("1","8","64"):List.of();
        String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);
        return options.stream().filter(s->s.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
