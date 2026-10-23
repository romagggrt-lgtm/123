package dev.zverity;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Main-thread-only visual + Interaction pair; no living mob, NMS or chunk tickets. */
public class ZVerityPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private static final String PACK_URL = "https://raw.githubusercontent.com/romagggrt-lgtm/123/main/dist/Z_Server_Pack_1.05.zip";
    private static final String PACK_SHA1 = "5c01b018a2cd391de078a216be19b1db06479bfd";
    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("z_verity:resources".getBytes(StandardCharsets.UTF_8));
    private static final float SPHERE_SCALE = 1.1f;
    private static final double RADIUS = 7.0 / 16 * SPHERE_SCALE;
    private static final int OPEN_TICKS = 20;
    private final Map<UUID, Thing> things = new LinkedHashMap<>();
    private final Map<UUID, Thing> byEntity = new HashMap<>();
    private final Set<UUID> verityDeaths = new HashSet<>();
    private NamespacedKey itemKey, thingKey;
    private long tick;

    private static final class Thing {
        final ItemDisplay display;
        final Interaction hitbox;
        final VerityState state = new VerityState();
        final Deque<Location> trail = new ArrayDeque<>();
        final Location boxCenter;
        final long birth;
        UUID owner;
        Location lastOwner;
        long openedAt, attackTimestamp = Long.MIN_VALUE;
        Thing(ItemDisplay display, Interaction hitbox, Location center, long birth) {
            this.display = display; this.hitbox = hitbox; this.boxCenter = center.clone(); this.birth = birth;
        }
    }

    @Override public void onEnable() {
        saveDefaultConfig(); migratePack();
        itemKey = new NamespacedKey(this, "item"); thingKey = new NamespacedKey(this, "thing");
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("zverity")).setExecutor(this);
        Bukkit.getWorlds().forEach(w -> w.getEntities().forEach(this::cleanStale));
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 1, 1);
        for (Player player : Bukkit.getOnlinePlayers()) schedulePack(player);
    }
    private void migratePack() {
        String url = getConfig().getString("resource-pack.url", "");
        // Only our published defaults migrate; custom hosting is preserved.
        if (url.matches("https://(?:raw\\.githubusercontent\\.com/romagggrt-lgtm/123/main/|github\\.com/romagggrt-lgtm/123/raw/refs/heads/main/)dist/Z_Server_Pack_1\\.0[345]\\.zip")) {
            getConfig().set("resource-pack.url", PACK_URL); getConfig().set("resource-pack.sha1", PACK_SHA1);
        }
        if (getConfig().getDouble("verity.follow-height", .5) == 1.1) getConfig().set("verity.follow-height", .5);
        getConfig().options().copyDefaults(true); saveConfig();
    }
    @Override public void onDisable() {
        for (Thing t : List.copyOf(things.values())) remove(t);
        things.clear(); byEntity.clear(); verityDeaths.clear();
    }
    private ItemStack egg(int amount) {
        ItemStack stack = new ItemStack(Material.PAPER, amount);
        stack.editMeta(meta -> {
            meta.displayName(Component.text("Яйцо Verity"));
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "verity");
            meta.setItemModel(new NamespacedKey("z_verity", "verity_egg"));
            meta.lore(List.of(Component.text("ПКМ по блоку — поставить коробку")));
        });
        return stack;
    }
    private boolean isEgg(ItemStack stack) {
        return stack != null && stack.hasItemMeta() && "verity".equals(stack.getItemMeta()
            .getPersistentDataContainer().get(itemKey, PersistentDataType.STRING));
    }
    private boolean canAdmin(CommandSender sender) {
        return sender instanceof ConsoleCommandSender || sender.isOp() || sender.hasPermission("zverity.give");
    }
    private void give(CommandSender sender, String[] args) {
        if (!canAdmin(sender)) { sender.sendMessage("Выдача Verity доступна оператору."); return; }
        if (args.length < 1 || args.length > 2) { sender.sendMessage("/zverity give <ник> [количество]"); return; }
        Player player = Bukkit.getPlayerExact(args[0]);
        if (player == null) { sender.sendMessage("Нужен точный ник игрока онлайн."); return; }
        int count;
        try { count = args.length == 2 ? Integer.parseInt(args[1]) : 1; }
        catch (NumberFormatException e) { count = 0; }
        if (count < 1 || count > 2304) { sender.sendMessage("Количество: 1–2304."); return; }
        int original = count;
        while (count > 0) {
            int batch = Math.min(count, 64); count -= batch;
            player.getInventory().addItem(egg(batch)).values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
        sender.sendMessage("Выдано Verity: " + original + ", игрок: " + player.getName());
    }
    private boolean interceptGive(CommandSender sender, String command) {
        String[] args = command.replaceFirst("^/", "").trim().split("\\s+");
        if (args.length < 3 || !args[0].equalsIgnoreCase("give") || !args[2].equalsIgnoreCase("verity")) return false;
        if (args.length > 4) { sender.sendMessage("/give <ник> verity [количество]"); return true; }
        give(sender, args.length == 4 ? new String[]{args[1], args[3]} : new String[]{args[1]}); return true;
    }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void playerCommand(PlayerCommandPreprocessEvent e) { if (interceptGive(e.getPlayer(), e.getMessage())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void serverCommand(ServerCommandEvent e) { if (interceptGive(e.getSender(), e.getCommand())) e.setCancelled(true); }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("give")) { give(sender, Arrays.copyOfRange(args, 1, args.length)); return true; }
        if (args.length == 1 && args[0].equalsIgnoreCase("clear") && canAdmin(sender)) {
            int count = things.size();
            for (Thing t : List.copyOf(things.values())) remove(t);
            Bukkit.getWorlds().forEach(w -> w.getEntities().forEach(this::cleanStale));
            sender.sendMessage("Удалено объектов Verity: " + count); return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("pack") && sender instanceof Player player) { sendPack(player); return true; }
        sender.sendMessage("/zverity give <ник> [количество]; /zverity clear; /zverity pack"); return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void place(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null || !isEgg(e.getItem())) return;
        e.setCancelled(true);
        if (things.size() >= number("verity.max-active", 64, 1, 256)) { e.getPlayer().sendMessage("Достигнут лимит Verity."); return; }
        Location center = e.getClickedBlock().getRelative(e.getBlockFace()).getLocation().add(.5, .5, .5);
        if (!free(center, .45)) { e.getPlayer().sendMessage("Для коробки нужно свободное место."); return; }
        spawnBox(center);
        if (e.getPlayer().getGameMode() != GameMode.CREATIVE) e.getItem().setAmount(e.getItem().getAmount() - 1);
    }
    private ItemStack visual(String model) {
        ItemStack item = new ItemStack(Material.PAPER);
        item.editMeta(meta -> meta.setItemModel(new NamespacedKey("z_verity", model))); return item;
    }
    private Thing spawnBox(Location center) {
        ItemDisplay display = center.getWorld().spawn(center, ItemDisplay.class, entity -> {
            mark(entity); entity.setInvulnerable(true); entity.setGravity(false);
            entity.setItemStack(visual("verity_box")); entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            entity.setTeleportDuration(2); entity.setInterpolationDuration(2);
            entity.setDisplayWidth(1.5f); entity.setDisplayHeight(1.5f);
        });
        Interaction hitbox;
        try {
            hitbox = center.getWorld().spawn(center.clone().subtract(0, .46, 0), Interaction.class, entity -> {
                mark(entity); entity.setInteractionWidth(.92f); entity.setInteractionHeight(.92f); entity.setResponsive(true);
            });
        } catch (RuntimeException ex) { display.remove(); throw ex; }
        Thing t = new Thing(display, hitbox, center, tick);
        things.put(display.getUniqueId(), t); byEntity.put(display.getUniqueId(), t); byEntity.put(hitbox.getUniqueId(), t); return t;
    }
    private void mark(Entity entity) {
        entity.setPersistent(false); entity.setSilent(true);
        entity.getPersistentDataContainer().set(thingKey, PersistentDataType.BYTE, (byte) 1);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void rightClick(PlayerInteractEntityEvent e) { entityClick(e); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void rightClickAt(PlayerInteractAtEntityEvent e) { entityClick(e); }
    private void entityClick(PlayerInteractEntityEvent e) {
        Thing t = byEntity.get(e.getRightClicked().getUniqueId());
        if (t == null || e.getHand() != EquipmentSlot.HAND) return;
        e.setCancelled(true); click(t, e.getPlayer(), false);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void damage(EntityDamageEvent e) {
        Thing t = byEntity.get(e.getEntity().getUniqueId()); if (t == null) return;
        e.setCancelled(true);
        if (e instanceof EntityDamageByEntityEvent by && by.getDamager() instanceof Player player) click(t, player, true);
    }
    private void click(Thing t, Player player, boolean attack) {
        if (!player.isOnline() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR || player.getWorld() != t.display.getWorld()) return;
        double range = number("verity.interaction-range", 5, 1, 6);
        if (player.getEyeLocation().distanceSquared(t.display.getLocation()) > range * range) return;
        switch (t.state.click(tick, attack)) {
            case OPEN -> {
                t.openedAt = tick; t.display.setItemStack(visual("verity_sphere"));
                t.hitbox.setInteractionWidth((float) (RADIUS * 2)); t.hitbox.setInteractionHeight((float) (RADIUS * 2));
                Location center = t.display.getLocation();
                center.setYaw(FollowMath.faceYaw(player.getX() - center.getX(), player.getZ() - center.getZ()));
                move(t, center); t.display.setTransformation(transform(.05f));
                player.getWorld().playSound(center, "z_verity:verity", SoundCategory.MASTER, (float) number("verity.sound-volume", .8, 0, 2), 1);
            }
            case GREET -> {
                t.owner = player.getUniqueId(); t.lastOwner = player.getLocation(); t.trail.add(t.lastOwner.clone());
                for (Player near : player.getWorld().getPlayers())
                    if (near.getLocation().distanceSquared(t.display.getLocation()) <= 64 * 64) near.sendMessage(Component.text("<Verity> Hello!"));
            }
            case KILL -> kill(t, player);
            case BREAK_BOX -> {
                Location center = t.display.getLocation(); remove(t);
                if (player.getGameMode() != GameMode.CREATIVE) center.getWorld().dropItemNaturally(center, egg(1));
            }
            case NONE -> { }
        }
    }
    private void kill(Thing t, Player player) {
        Location death = player.getLocation(); remove(t);
        String coords = death.getWorld().getName() + ": " + death.getBlockX() + ", " + death.getBlockY() + ", " + death.getBlockZ();
        Bukkit.broadcast(Component.text("<" + player.getName() + "> Hello, i am verity. Я убил его и оставил все его вещи (" + coords + ")"));
        verityDeaths.add(player.getUniqueId());
        try { player.setHealth(0); } finally { verityDeaths.remove(player.getUniqueId()); }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void death(PlayerDeathEvent e) {
        if (verityDeaths.contains(e.getEntity().getUniqueId()) && getConfig().getBoolean("verity.drop-inventory-on-kill", true)) {
            // All three slot groups are disjoint; getContents() must not be
            // combined with armour/offhand a second time.
            e.setKeepInventory(false); e.getDrops().clear();
            var inventory = e.getEntity().getInventory();
            for (ItemStack[] slots : List.of(inventory.getStorageContents(), inventory.getArmorContents(), inventory.getExtraContents()))
                for (ItemStack item : slots) if (item != null && !item.getType().isAir()) e.getDrops().add(item.clone());
        }
        removeFollowers(e.getEntity().getUniqueId());
    }
    private static Transformation transform(float scale) {
        return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale), new Quaternionf());
    }
    private void tick() {
        tick++;
        for (Thing t : List.copyOf(things.values())) {
            if (!t.display.isValid() || !t.hitbox.isValid() || tick - t.birth > number("verity.lifetime-seconds", 900, 10, 86400) * 20) { remove(t); continue; }
            // Some versions record Interaction attacks without ordinary damage.
            Interaction.PreviousInteraction attack = t.hitbox.getLastAttack();
            if (attack != null && attack.getTimestamp() != t.attackTimestamp) {
                t.attackTimestamp = attack.getTimestamp();
                Player player = attack.getPlayer().getPlayer(); if (player != null) click(t, player, true);
                if (t.state.stage() == VerityState.Stage.REMOVED) continue;
            }
            if (t.state.stage() == VerityState.Stage.OPENING) {
                double progress = Math.min(1, (tick - t.openedAt) / (double) OPEN_TICKS);
                double ease = progress * progress * (3 - 2 * progress);
                Location center = t.boxCenter.clone().add(0, .4 * ease, 0);
                center.setYaw(t.display.getLocation().getYaw()); move(t, center);
                t.display.setTransformation(transform((float) (.05 + (SPHERE_SCALE - .05) * ease)));
                if (progress >= 1) t.state.finishOpening();
            } else if (t.state.stage() == VerityState.Stage.FOLLOWING) follow(t);
        }
    }
    private void follow(Thing t) {
        Player owner = Bukkit.getPlayer(t.owner);
        if (owner == null || !owner.isOnline() || owner.isDead() || owner.getWorld() != t.display.getWorld()) { remove(t); return; }
        Location feet = owner.getLocation(), current = t.display.getLocation();
        if (current.distanceSquared(feet) > 48 * 48 || feet.distanceSquared(t.lastOwner) > 24 * 24) { remove(t); return; }
        if (feet.distanceSquared(t.lastOwner) >= .3 * .3) {
            t.trail.addLast(feet.clone()); t.lastOwner = feet.clone();
            while (t.trail.size() > 128) t.trail.removeFirst();
        }
        while (t.trail.size() > 1 && horizontalSquared(current, t.trail.getFirst()) < .65 * .65) t.trail.removeFirst();
        Location goal = t.trail.isEmpty() ? feet : t.trail.getFirst();
        double stop = t.trail.size() > 1 ? .15 : number("verity.follow-distance", 1.7, .8, 6);
        FollowMath.Step step = FollowMath.step(current.getX(), current.getZ(), goal.getX(), goal.getZ(), stop, number("verity.follow-speed", .18, .02, .35));
        Location next = navigate(current, step, goal.getY()); if (next == null) return;
        double dx = feet.getX() - next.getX(), dz = feet.getZ() - next.getZ();
        if (dx * dx + dz * dz > .01) next.setYaw(FollowMath.turn(current.getYaw(), FollowMath.faceYaw(dx, dz), 8));
        next.setPitch(0); move(t, next);
    }
    private static double horizontalSquared(Location a, Location b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ(); return dx * dx + dz * dz;
    }
    private Location navigate(Location current, FollowMath.Step step, double feetY) {
        for (int degrees : new int[]{0, 35, -35, 70, -70}) {
            double angle = Math.toRadians(degrees), cos = Math.cos(angle), sin = Math.sin(angle);
            Location next = current.clone().add(step.x() * cos - step.z() * sin, 0, step.x() * sin + step.z() * cos);
            double floor = floor(next, feetY); if (!Double.isFinite(floor)) continue;
            double desired = floor + Math.max(RADIUS + .012, number("verity.follow-height", .5, .5, 2));
            double dy = desired - current.getY(); if (dy > .65) continue;
            next.setY(current.getY() + Math.max(-.16, Math.min(.16, dy)));
            if (free(next, RADIUS)) return next;
            if (dy > 0 && dy <= .65) { next.setY(desired); if (free(next, RADIUS)) return next; }
        }
        return null;
    }
    private double floor(Location at, double feetY) {
        World world = at.getWorld(); int x = at.getBlockX(), z = at.getBlockZ();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return Double.NaN;
        int top = Math.min(world.getMaxHeight() - 1, (int) Math.floor(feetY + .6));
        int bottom = Math.max(world.getMinHeight(), (int) Math.floor(Math.min(feetY, at.getY())) - 3);
        for (int y = top; y >= bottom; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (!block.isPassable() && !block.isLiquid()) return block.getBoundingBox().getMaxY();
        }
        return Double.NaN;
    }
    private boolean free(Location at, double radius) {
        World world = at.getWorld(); BoundingBox body = BoundingBox.of(at, radius, radius, radius);
        if (body.getMinY() < world.getMinHeight() || body.getMaxY() >= world.getMaxHeight()) return false;
        for (int x = (int) Math.floor(body.getMinX()); x <= Math.floor(body.getMaxX()); x++)
            for (int z = (int) Math.floor(body.getMinZ()); z <= Math.floor(body.getMaxZ()); z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;
                for (int y = (int) Math.floor(body.getMinY()); y <= Math.floor(body.getMaxY()); y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!block.isPassable() && block.getBoundingBox().overlaps(body)) return false;
                }
            }
        return true;
    }
    private void move(Thing t, Location center) {
        t.display.teleport(center);
        double radius = t.state.stage() == VerityState.Stage.BOX ? .46 : RADIUS;
        t.hitbox.teleport(center.clone().subtract(0, radius, 0));
    }
    private void remove(Thing t) {
        t.state.remove(); things.remove(t.display.getUniqueId());
        byEntity.remove(t.display.getUniqueId()); byEntity.remove(t.hitbox.getUniqueId());
        t.display.remove(); t.hitbox.remove(); t.trail.clear();
    }
    private void removeFollowers(UUID owner) { for (Thing t : List.copyOf(things.values())) if (owner.equals(t.owner)) remove(t); }
    private void cleanStale(Entity e) { if (e.getPersistentDataContainer().has(thingKey) && !byEntity.containsKey(e.getUniqueId())) e.remove(); }
    @EventHandler public void chunkLoad(ChunkLoadEvent e) { for (Entity entity : e.getChunk().getEntities()) cleanStale(entity); }
    @EventHandler(ignoreCancelled = true) public void chunkUnload(ChunkUnloadEvent e) {
        for (Entity entity : e.getChunk().getEntities()) { Thing t = byEntity.get(entity.getUniqueId()); if (t != null) remove(t); }
    }
    @EventHandler(ignoreCancelled = true) public void worldUnload(WorldUnloadEvent e) {
        for (Thing t : List.copyOf(things.values())) if (t.display.getWorld() == e.getWorld()) remove(t);
    }
    @EventHandler public void quit(PlayerQuitEvent e) { removeFollowers(e.getPlayer().getUniqueId()); }
    @EventHandler public void worldChange(PlayerChangedWorldEvent e) { removeFollowers(e.getPlayer().getUniqueId()); }
    private double number(String path, double fallback, double min, double max) {
        double n = getConfig().getDouble(path, fallback); return Double.isFinite(n) ? Math.max(min, Math.min(max, n)) : fallback;
    }
    @EventHandler public void join(PlayerJoinEvent e) { schedulePack(e.getPlayer()); }
    private void schedulePack(Player player) {
        if (getConfig().getBoolean("resource-pack.enabled", true) && getConfig().getBoolean("resource-pack.prompt-on-join", true))
            Bukkit.getScheduler().runTaskLater(this, () -> { if (player.isOnline()) sendPack(player); }, 120);
    }
    private void sendPack(Player player) {
        String url = getConfig().getString("resource-pack.url", PACK_URL).trim();
        String hash = getConfig().getString("resource-pack.sha1", PACK_SHA1).trim();
        try {
            if (!url.startsWith("https://") || !hash.matches("[0-9a-fA-F]{40}")) throw new IllegalArgumentException("нужны HTTPS URL и SHA-1 из 40 символов");
            player.addResourcePack(PACK_ID, url, HexFormat.of().parseHex(hash), "Модель и звук Verity + ресурсы Shahed", false);
        } catch (IllegalArgumentException ex) { getLogger().warning("Ресурс-пак Verity: " + ex.getMessage()); }
    }
    @EventHandler public void packStatus(PlayerResourcePackStatusEvent e) {
        if (!PACK_ID.equals(e.getID())) return;
        switch (e.getStatus()) {
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DECLINED -> {
                e.getPlayer().sendMessage("Ресурс-пак Verity не загрузился. Разрешите серверные ресурсы и используйте /zverity pack.");
                getLogger().warning("Ресурс-пак у " + e.getPlayer().getName() + ": " + e.getStatus());
            }
            default -> { }
        }
    }
}
