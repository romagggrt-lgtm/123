package dev.shah;

import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.key.Key;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.*;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.HexFormat;

/** All entity operations run on the server thread; no NMS, chunk tickets or asynchronous world access. */
public final class ShahPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final String DEFAULT_PACK_URL = "https://raw.githubusercontent.com/romagggrt-lgtm/123/main/dist/Shahed-ResourcePack.zip";
    private static final String DEFAULT_PACK_SHA1 = "4cbfd9594d57f6a8d4b1db657a9d2cd75420dfb3";
    private NamespacedKey itemKey, entityKey;
    private final Map<UUID, Flight> flights = new LinkedHashMap<>();
    private final Map<UUID, Flight> hitboxes = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Set<UUID> using = new HashSet<>();
    private final Map<UUID, Long> nextSpawn = new HashMap<>();
    private final Map<UUID, String> packStatus = new HashMap<>();
    private boolean suppressExplosionDamage;
    private long tick;
    private final Random random = new Random();

    @Override public void onEnable() {
        saveDefaultConfig();
        itemKey = new NamespacedKey(this, "item"); entityKey = new NamespacedKey(this, "managed");
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("shahed")).setExecutor(this);
        Objects.requireNonNull(getCommand("shahed")).setTabCompleter(this);
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) cleanupOrphan(e);
            nextSpawn.put(w.getUID(), interval());
        }
        Bukkit.getScheduler().runTaskTimer(this, this::update, 1, 1);
    }
    @Override public void onDisable() {
        for (Flight f : List.copyOf(flights.values())) remove(f);
        using.clear(); cooldowns.clear();
    }
    private double number(String key, double def, double min, double max) {
        double n = getConfig().getDouble(key, def);
        return Double.isFinite(n) ? Math.max(min, Math.min(max, n)) : def;
    }
    private boolean yes(String key) { return getConfig().getBoolean(key, true); }
    private long interval() { return (long) (number("shahed.natural-spawn.interval-minutes",30,0.05,1440)*1200); }
    private String type(ItemStack item) {
        return item == null || !item.hasItemMeta() ? "" : Objects.toString(item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING), "");
    }
    private boolean launcher(ItemStack item) { return type(item).equals("Shahed_3k"); }
    @SuppressWarnings("UnstableApiUsage")
    private ItemStack item(String id, int amount) {
        ItemStack item = new ItemStack(id.equals("shahed") ? Material.PHANTOM_SPAWN_EGG : Material.PAPER, amount);
        item.editMeta(meta -> {
            meta.displayName(Component.text(id.equals("shahed") ? "Shahed" : "Шахед 3000"));
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
            meta.setItemModel(new NamespacedKey("shah", id.equals("shahed") ? "egg" : "shahed"));
            meta.lore(List.of(Component.text(id.equals("shahed") ? "ПКМ по блоку — взлёт" : "Удерживайте ПКМ, затем отпустите")));
        });
        if (id.equals("Shahed_3k")) item.setData(DataComponentTypes.CONSUMABLE,
            Consumable.consumable().consumeSeconds(3600).animation(ItemUseAnimation.BOW)
                .sound(Key.key("minecraft:intentionally_empty")).hasConsumeParticles(false).build());
        return item;
    }
    private boolean customGive(CommandSender sender, String[] args) {
        if (args.length < 2 || !(args[1].equalsIgnoreCase("shahed") || args[1].equalsIgnoreCase("Shahed_3k"))) return false;
        if (sender instanceof Player p) {
            if ((yes("permissions.give-op-only") && !p.isOp()) || (!yes("permissions.give-op-only") && !p.hasPermission("shah.give"))) {
                sender.sendMessage("Выдача недоступна."); return true;
            }
        } else if (!(sender instanceof ConsoleCommandSender) || !yes("permissions.console-give")) {
            sender.sendMessage("Выдача этому отправителю недоступна."); return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { sender.sendMessage("Игрок не найден (нужен точный ник онлайн-игрока)."); return true; }
        int count;
        try { count = args.length == 2 ? 1 : Integer.parseInt(args[2]); }
        catch (NumberFormatException ex) { count = 0; }
        if (args.length > 3 || count < 1 || count > 2304) { sender.sendMessage("Количество: 1–2304."); return true; }
        String id = args[1].equalsIgnoreCase("shahed") ? "shahed" : "Shahed_3k";
        while (count > 0) {
            int n = Math.min(count,64); count -= n;
            for (ItemStack rest : target.getInventory().addItem(item(id,n)).values()) target.getWorld().dropItemNaturally(target.getLocation(),rest);
        }
        sender.sendMessage("Выдано: " + id + " → " + target.getName()); warnPack(target);
        return true;
    }
    private boolean intercept(CommandSender sender, String command) {
        String[] a = command.trim().replaceFirst("^/", "").split("\\s+");
        // Deliberately leave namespaced minecraft:give and essentials:give untouched.
        return a.length >= 3 && a[0].equalsIgnoreCase("give") && customGive(sender, Arrays.copyOfRange(a,1,a.length));
    }
    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true) public void command(PlayerCommandPreprocessEvent e) {
        if (intercept(e.getPlayer(), e.getMessage())) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true) public void command(ServerCommandEvent e) {
        if (intercept(e.getSender(), e.getCommand())) e.setCancelled(true);
    }
    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (a.length >= 3 && a[0].equalsIgnoreCase("give") && customGive(s, Arrays.copyOfRange(a,1,a.length))) return true;
        s.sendMessage("/shahed give <player> <shahed|Shahed_3k> [amount]"); return true;
    }
    @Override public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (!s.isOp()) return List.of();
        List<String> options = a.length == 1 ? List.of("give") : a.length == 2 ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList() : a.length == 3 ? List.of("shahed", "Shahed_3k") : List.of("1", "2", "5");
        return options.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(a[a.length-1].toLowerCase(Locale.ROOT))).toList();
    }
    @EventHandler(priority=EventPriority.HIGH) public void interact(PlayerInteractEvent e) {
        if (type(e.getItem()).equals("shahed")) {
            if (e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
            boolean allowed = e.useInteractedBlock() != Event.Result.DENY && e.useItemInHand() != Event.Result.DENY;
            e.setCancelled(true);
            if (!allowed) return;
            Location at = e.getClickedBlock().getRelative(e.getBlockFace()).getLocation().add(.5,1,.5);
            if (!clear(at,2) || !capacity()) { e.getPlayer().sendMessage("Недостаточно свободного места или достигнут лимит."); return; }
            at.setYaw(e.getPlayer().getLocation().getYaw()); at.setPitch(0);
            spawn(at,false,null,null);
            if (e.getPlayer().getGameMode() != GameMode.CREATIVE) e.getItem().setAmount(e.getItem().getAmount()-1);
            warnPack(e.getPlayer()); return;
        }
        if (!launcher(e.getItem()) || e.getHand() != EquipmentSlot.HAND || !(e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)) return;
        if (e.useItemInHand() == Event.Result.DENY && e.getAction() == Action.RIGHT_CLICK_BLOCK) return;
        if (cooldowns.getOrDefault(e.getPlayer().getUniqueId(),0L) > tick) { e.setCancelled(true); return; }
        e.setUseInteractedBlock(Event.Result.DENY); e.setUseItemInHand(Event.Result.ALLOW);
        using.add(e.getPlayer().getUniqueId()); warnPack(e.getPlayer());
    }
    @EventHandler public void released(PlayerStopUsingItemEvent e) {
        Player p = e.getPlayer();
        if (!using.remove(p.getUniqueId()) || !launcher(e.getItem()) || !launcher(p.getInventory().getItemInMainHand()) || p.isDead()) return;
        if (e.getTicksHeldFor() < number("shahed-3000.charge-time-ticks",25,1,1200) || cooldowns.getOrDefault(p.getUniqueId(),0L) > tick || !capacity()) return;
        Vector direction = p.getEyeLocation().getDirection().normalize();
        Location origin = p.getEyeLocation();
        if (!loaded(origin)) return;
        spawn(origin,false,p.getUniqueId(),direction);
        cooldowns.put(p.getUniqueId(), tick+(long)(20*number("shahed-3000.cooldown-seconds",3,0,3600)));
        if (!yes("shahed-3000.unlimited-ammo") && p.getGameMode()!=GameMode.CREATIVE) {
            ItemStack hand=p.getInventory().getItemInMainHand(); hand.setAmount(hand.getAmount()-1);
        }
    }
    @EventHandler(ignoreCancelled=true) public void consume(PlayerItemConsumeEvent e) { if (launcher(e.getItem())) e.setCancelled(true); }
    @EventHandler public void held(PlayerItemHeldEvent e) { using.remove(e.getPlayer().getUniqueId()); }
    @EventHandler(ignoreCancelled=true) public void swap(PlayerSwapHandItemsEvent e) { using.remove(e.getPlayer().getUniqueId()); }
    @EventHandler public void quit(PlayerQuitEvent e) { using.remove(e.getPlayer().getUniqueId()); cooldowns.remove(e.getPlayer().getUniqueId()); packStatus.remove(e.getPlayer().getUniqueId()); }
    @EventHandler public void death(PlayerDeathEvent e) { using.remove(e.getEntity().getUniqueId()); }
    @EventHandler public void pack(PlayerResourcePackStatusEvent e) { packStatus.put(e.getPlayer().getUniqueId(),e.getStatus().name()); }
    @EventHandler public void join(PlayerJoinEvent e) {
        if (yes("resource-pack.prompt-on-join")) Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!e.getPlayer().isOnline()) return;
            sendResourcePack(e.getPlayer());
            warnPack(e.getPlayer());
        },100);
    }
    private void sendResourcePack(Player player) {
        if (!yes("resource-pack.enabled")) return;
        // Existing installations keep their old config.yml, so defaults must also
        // be supplied in code; saveDefaultConfig() never overwrites that file.
        String url=getConfig().getString("resource-pack.url", DEFAULT_PACK_URL).trim();
        String sha1=getConfig().getString("resource-pack.sha1", DEFAULT_PACK_SHA1).trim();
        if (url.isEmpty()) return;
        try {
            byte[] hash=sha1.isEmpty() ? null : HexFormat.of().parseHex(sha1);
            if (hash!=null && hash.length!=20) throw new IllegalArgumentException("resource-pack.sha1 must be 40 hex characters");
            player.setResourcePack(url, hash, Component.text("Для моделей shah примите ресурс-пак."));
        } catch (IllegalArgumentException ex) {
            getLogger().warning("Invalid resource-pack.sha1: "+ex.getMessage());
        }
    }
    private void warnPack(Player p) {
        if (yes("resource-pack.enabled") && !"SUCCESSFULLY_LOADED".equals(packStatus.get(p.getUniqueId())))
            p.sendActionBar(Component.text("Для моделей shah примите серверный ресурс-пак. Если его нет — обратитесь к администратору."));
    }

    private final class Flight {
        final UUID id = UUID.randomUUID(), shooter;
        final boolean natural, projectile;
        final ItemDisplay display;
        final int soundSlot;
        final Slime body;
        Location pos;
        Vector direction;
        int age;
        double travelled, altitude;
        float targetYaw;
        boolean dead;
        final Set<UUID> listeners = new HashSet<>();
        Flight(Location p, boolean n, UUID s, Vector d, ItemDisplay v, Slime b) {
            pos=p; natural=n; shooter=s; projectile=d!=null; display=v; body=b;
            Set<Integer> occupied = new HashSet<>();
            for (Flight flight : flights.values()) occupied.add(flight.soundSlot);
            int slot=0; while (occupied.contains(slot)) slot++; soundSlot=slot;
            direction=d==null ? p.getDirection().setY(0).normalize() : d.clone(); targetYaw=p.getYaw();
            altitude=number("shahed.natural-spawn.min-height-above-ground",35,5,120);
        }
    }
    private boolean capacity() { return flights.size() < number("limits.max-total",128,1,1024); }
    private void mark(Entity e) { e.getPersistentDataContainer().set(entityKey,PersistentDataType.BYTE,(byte)1); e.setPersistent(false); e.setGravity(false); }
    private Flight spawn(Location p, boolean natural, UUID shooter, Vector direction) {
        ItemDisplay visual=p.getWorld().spawn(p,ItemDisplay.class,e->{
            mark(e); e.setItemStack(item("Shahed_3k",1)); e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            // Vanilla item-model coordinates face -Z; our generated aircraft nose faces +Z.
            // Rotate the display so its nose follows the flight vector instead of flying backwards.
            e.setTransformation(new Transformation(new Vector3f(),new Quaternionf().rotateY((float)Math.PI),new Vector3f(2.2f),new Quaternionf()));
            e.setTeleportDuration(1); e.setInterpolationDuration(1); e.setViewRange(2); e.setDisplayWidth(5); e.setDisplayHeight(2);
        });
        Slime body=null;
        if(direction==null) body=p.getWorld().spawn(p,Slime.class,e->{
            mark(e); e.setAI(false); e.setInvisible(true); e.setSilent(true); e.setSize(2); e.setCollidable(false);
            e.setRemoveWhenFarAway(false); e.setInvulnerable(false); e.customName(Component.text("Шахед"));
        });
        Flight f=new Flight(p.clone(),natural,shooter,direction,visual,body);
        flights.put(f.id,f); if(body!=null) hitboxes.put(body.getUniqueId(),f);
        return f;
    }
    private void cleanupOrphan(Entity e) { if(e.getPersistentDataContainer().has(entityKey,PersistentDataType.BYTE)) e.remove(); }
    @EventHandler public void load(ChunkLoadEvent e) { for(Entity entity:e.getChunk().getEntities()) cleanupOrphan(entity); }
    @EventHandler(ignoreCancelled=true) public void unload(ChunkUnloadEvent e) {
        for(Flight f:List.copyOf(flights.values())) if(f.pos.getWorld()==e.getWorld() && (f.pos.getBlockX()>>4)==e.getChunk().getX() && (f.pos.getBlockZ()>>4)==e.getChunk().getZ()) remove(f);
    }
    @EventHandler(ignoreCancelled=true) public void unload(WorldUnloadEvent e) {
        for(Flight f:List.copyOf(flights.values())) if(f.pos.getWorld()==e.getWorld()) remove(f);
        nextSpawn.remove(e.getWorld().getUID());
    }
    @EventHandler public void load(WorldLoadEvent e) { nextSpawn.put(e.getWorld().getUID(),tick+interval()); }
    @EventHandler(priority=EventPriority.HIGHEST) public void damage(EntityDamageEvent e) {
        if(suppressExplosionDamage && (e.getCause()==EntityDamageEvent.DamageCause.ENTITY_EXPLOSION || e.getCause()==EntityDamageEvent.DamageCause.BLOCK_EXPLOSION)) e.setCancelled(true);
        if(e instanceof EntityDamageByEntityEvent outgoing && hitboxes.containsKey(outgoing.getDamager().getUniqueId())) e.setCancelled(true);
        Flight f=hitboxes.get(e.getEntity().getUniqueId());
        if(f==null) return;
        boolean cancelled=e.isCancelled(); e.setCancelled(true);
        if(cancelled || !(e instanceof EntityDamageByEntityEvent hit)) return;
        Entity attacker=hit.getDamager();
        Player player=attacker instanceof Player p ? p : attacker instanceof Projectile pr && pr.getShooter() instanceof Player p ? p : null;
        if(player!=null) detonate(f,player);
    }
    @EventHandler(ignoreCancelled=true) public void split(SlimeSplitEvent e) { if(e.getEntity().getPersistentDataContainer().has(entityKey,PersistentDataType.BYTE)) e.setCancelled(true); }
    @EventHandler(ignoreCancelled=true) public void prime(ExplosionPrimeEvent e) { if(hitboxes.containsKey(e.getEntity().getUniqueId())) e.setCancelled(true); }
    private void remove(Flight f) {
        if(f.dead) return; f.dead=true;
        flights.remove(f.id); if(f.body!=null) { hitboxes.remove(f.body.getUniqueId()); f.body.remove(); }
        f.display.remove();
        for(UUID id:f.listeners) { Player p=Bukkit.getPlayer(id); if(p!=null) p.stopSound("shah:engine_"+f.soundSlot,SoundCategory.AMBIENT); }
    }
    private void detonate(Flight f, Entity attacker) {
        if(f.dead) return;
        Location p=f.pos.clone(); String base=f.projectile?"shahed-3000":"shahed";
        // Native explosion events remain available to region protection plugins.
        Entity source=attacker;
        remove(f);
        boolean previous=suppressExplosionDamage;
        suppressExplosionDamage=!yes(base+".explosion.damage-entities");
        try { p.getWorld().createExplosion(p,(float)number(base+".explosion.power",f.projectile?6:4,0,16),false,yes(base+".explosion.break-blocks"),source); }
        finally { suppressExplosionDamage=previous; }
    }
    private boolean loaded(Location p) { return p.getWorld()!=null && p.getWorld().isChunkLoaded(p.getBlockX()>>4,p.getBlockZ()>>4); }
    private boolean clear(Location p, int radius) {
        World w=p.getWorld();
        if(p.getY()<w.getMinHeight()+2 || p.getY()>w.getMaxHeight()-3 || !w.getWorldBorder().isInside(p)) return false;
        for(int x=-radius;x<=radius;x++) for(int z=-radius;z<=radius;z++) {
            Location q=p.clone().add(x,0,z); if(!loaded(q)) return false;
            for(int y=-1;y<=1;y++) if(!w.getBlockAt(q.getBlockX(),q.getBlockY()+y,q.getBlockZ()).isPassable()) return false;
        }
        return true;
    }
    private void update() {
        tick++;
        for(Flight f:List.copyOf(flights.values())) {
            if(f.dead) continue;
            if(!f.display.isValid() || (f.body!=null&&!f.body.isValid()) || !loaded(f.pos)) { remove(f); continue; }
            f.age++;
            if(f.projectile) projectile(f); else peaceful(f);
            if(!f.dead) sound(f);
        }
        for(World w:Bukkit.getWorlds()) {
            if(tick < nextSpawn.getOrDefault(w.getUID(),Long.MAX_VALUE)) continue;
            nextSpawn.put(w.getUID(),tick+interval()); natural(w);
        }
        if(tick%20==0) for(UUID id:using) { Player p=Bukkit.getPlayer(id); if(p!=null) p.sendActionBar(Component.text("Шахед 3000 · удерживайте, затем отпустите ПКМ")); }
    }
    private void move(Flight f, Location to) {
        f.pos=to;
        f.display.setTeleportDuration(yes("shahed.flight.smooth-movement")||f.projectile?1:0);
        f.display.teleport(to);
        if(f.body!=null) f.body.teleport(to.clone().subtract(0,.5,0));
    }
    private void projectile(Flight f) {
        double max=number("shahed-3000.max-distance",150,1,1000);
        double step=FlightMath.step(number("shahed-3000.projectile-speed",2,.05,16),f.travelled,max);
        if(step<=0) { remove(f); return; }
        // Conservative chunk rectangle covers even a tiny diagonal corner crossing.
        Location end=f.pos.clone().add(f.direction.clone().multiply(step));
        int ax=f.pos.getBlockX()>>4, az=f.pos.getBlockZ()>>4, bx=end.getBlockX()>>4, bz=end.getBlockZ()>>4;
        for(int x=Math.min(ax,bx);x<=Math.max(ax,bx);x++) for(int z=Math.min(az,bz);z<=Math.max(az,bz);z++)
            if(!f.pos.getWorld().isChunkLoaded(x,z)) { remove(f); return; }
        World w=f.pos.getWorld();
        RayTraceResult block=w.rayTraceBlocks(f.pos,f.direction,step,FluidCollisionMode.NEVER,true);
        RayTraceResult entity=w.rayTraceEntities(f.pos,f.direction,step,.25,e->e instanceof Player p && !p.getUniqueId().equals(f.shooter) && !p.isDead() && p.getGameMode()!=GameMode.SPECTATOR);
        RayTraceResult hit=block;
        if(entity!=null && (hit==null || entity.getHitPosition().distanceSquared(f.pos.toVector())<hit.getHitPosition().distanceSquared(f.pos.toVector()))) hit=entity;
        if(hit!=null) { f.pos=hit.getHitPosition().toLocation(w); detonate(f,Bukkit.getPlayer(f.shooter)); return; }
        Location to=f.pos.clone().add(f.direction.clone().multiply(step)); to.setDirection(f.direction);
        if(!w.getWorldBorder().isInside(to) || to.getY()<w.getMinHeight() || to.getY()>=w.getMaxHeight()) { remove(f); return; }
        f.travelled+=step; move(f,to); if(f.travelled>=max) remove(f);
    }
    private void peaceful(Flight f) {
        double range=number("limits.despawn-player-distance",192,32,512);
        if(f.age>number("shahed.flight.lifetime-seconds",180,1,86400)*20 || f.pos.getWorld().getPlayers().stream().noneMatch(p->p.getLocation().distanceSquared(f.pos)<range*range)) { remove(f); return; }
        if(f.age%100==1) f.targetYaw+=random.nextFloat()*24-12;
        float yaw=FlightMath.turn(f.pos.getYaw(),f.targetYaw,.5f);
        Vector horizontal=new Location(f.pos.getWorld(),0,0,0,yaw,0).getDirection();
        double speed=number("shahed.flight.speed",.4,.03,2);
        int takeoff=(int)(number("shahed.takeoff.duration-seconds",4,1,30)*20);
        boolean rising=!f.natural && f.age<takeoff;
        double progress=Math.min(1,(double)f.age/takeoff);
        double easing=yes("shahed.takeoff.smooth-animation") ? FlightMath.smooth(progress) : progress;
        Location to=f.pos.clone().add(horizontal.multiply(speed*(rising?Math.max(.1,easing):1)));
        if(!loaded(to)) { remove(f); return; }
        int surface=to.getWorld().getHighestBlockYAt(to.getBlockX(),to.getBlockZ(),HeightMap.MOTION_BLOCKING_NO_LEAVES);
        double desired=Math.min(to.getWorld().getMaxHeight()-6,surface+f.altitude);
        double climb=rising ? Math.max(.1,(desired-f.pos.getY())/Math.max(1,takeoff-f.age)) : Math.max(-.25,Math.min(.45,desired-f.pos.getY()));
        to.setY(f.pos.getY()+climb); to.setYaw(yaw); to.setPitch(0);
        if(yes("shahed.flight.avoid-obstacles")) {
            Location ahead=to.clone().add(to.getDirection().multiply(5));
            if(!clear(ahead,2)) { f.targetYaw=yaw+45; to=f.pos.clone().add(0,.4,0); to.setYaw(yaw); }
        }
        if(!clear(to,1)) { remove(f); return; }
        move(f,to);
    }
    private void natural(World w) {
        if(!yes("shahed.natural-spawn.enabled") || w.getEnvironment()!=World.Environment.NORMAL || !getConfig().getStringList("shahed.natural-spawn.worlds").contains(w.getName()) || !capacity()) return;
        if(flights.values().stream().filter(f->f.natural&&f.pos.getWorld()==w).count()>=number("shahed.natural-spawn.max-active",2,0,128) || random.nextDouble()>=number("shahed.natural-spawn.chance",.5,0,1)) return;
        List<Player> players=w.getPlayers().stream().filter(p->p.getGameMode()!=GameMode.SPECTATOR).toList(); if(players.isEmpty()) return;
        double min=number("shahed.natural-spawn.min-distance-from-player",60,1,256), max=number("shahed.natural-spawn.max-distance-from-player",100,min,512);
        for(int attempt=0;attempt<12;attempt++) {
            Player anchor=players.get(random.nextInt(players.size())); double angle=random.nextDouble()*Math.PI*2, distance=min+random.nextDouble()*(max-min);
            Location p=anchor.getLocation().clone().add(Math.cos(angle)*distance,0,Math.sin(angle)*distance);
            if(!loaded(p)) continue;
            double low=number("shahed.natural-spawn.min-height-above-ground",35,5,120), high=number("shahed.natural-spawn.max-height-above-ground",55,low,160);
            double altitude=low+random.nextDouble()*(high-low);
            int surface=w.getHighestBlockYAt(p.getBlockX(),p.getBlockZ(),HeightMap.MOTION_BLOCKING_NO_LEAVES);
            p.setY(surface+altitude); p.setYaw(random.nextFloat()*360); p.setPitch(0);
            double nearest=players.stream().mapToDouble(player->{Location l=player.getLocation(); double dx=l.getX()-p.getX(), dz=l.getZ()-p.getZ(); return Math.sqrt(dx*dx+dz*dz);}).min().orElse(0);
            if(nearest<min || nearest>max || !clear(p,3)) continue;
            Flight f=spawn(p,true,null,null); f.altitude=altitude; return;
        }
    }
    private void sound(Flight f) {
        if(!yes("shahed.sound.enabled")) return;
        double max=number("shahed.sound.max-distance",45,1,128);
        // A one-second authored sample is overlapped at 18 ticks; positional volume also attenuates client-side.
        if(f.age%18!=1) return;
        Set<UUID> now=new HashSet<>();
        for(Player p:f.pos.getWorld().getPlayers()) {
            double distance=p.getEyeLocation().distance(f.pos); if(distance>max) continue;
            now.add(p.getUniqueId());
            float volume=(float)(number("shahed.sound.volume",.25,0,2)*Math.max(0,1-distance/max));
            double life=number("shahed.flight.lifetime-seconds",180,1,86400)*20;
            if(!f.projectile) volume*=(float)Math.min(1,Math.max(0,(life-f.age)/40));
            p.playSound(f.pos,"shah:engine_"+f.soundSlot,SoundCategory.AMBIENT,volume,1);
        }
        f.listeners.addAll(now);
    }
}
