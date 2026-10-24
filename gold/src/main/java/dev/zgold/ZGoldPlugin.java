package dev.zgold;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Main-thread gameplay; one ItemDisplay and one Interaction per loaded Golda. No NMS. */
public class ZGoldPlugin extends JavaPlugin implements Listener, TabExecutor {
    private static final double HALF_LENGTH = Math.hypot(.875, .4375) + .015, HALF_HEIGHT = .26;
    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("z_gold:pack:1.05_Gold".getBytes(StandardCharsets.UTF_8));
    private final Map<UUID, Golda> goldas = new LinkedHashMap<>();
    private final Map<UUID, Pair> active = new HashMap<>();
    private final Map<UUID, UUID> entityOwners = new HashMap<>();
    private final Map<ChunkKey, Set<UUID>> chunks = new HashMap<>();
    private final Map<UUID, InventorySnapshot> deaths = new HashMap<>();
    private final Map<UUID, UUID> packAttempts = new HashMap<>();
    private final Set<UUID> killing = new HashSet<>(), warned = new HashSet<>(), packed = new HashSet<>();
    private NamespacedKey itemKey, entityKey;
    private GoldStore store;
    private Settings settings;
    private String bundledHash;
    private long tick;
    private boolean loaded, failed;

    @Override public void onEnable() {
        try {
            saveDefaultConfig();
            try (InputStream input = getResource("pack.sha1")) {
                bundledHash = input == null ? "" : new String(input.readAllBytes(), StandardCharsets.US_ASCII).trim();
            }
            settings = Settings.read(getConfig(), bundledHash);
            itemKey = new NamespacedKey(this, "item"); entityKey = new NamespacedKey(this, "golda");
            store = new GoldStore(getDataFolder().toPath().resolve("golda-state.yml"));
            store.load(goldas, deaths); loaded = true;
            goldas.values().forEach(this::index);
            Bukkit.getPluginManager().registerEvents(this, this);
            Objects.requireNonNull(getCommand("zgold")).setExecutor(this);
            getCommand("zgold").setTabCompleter(this);
            // One startup pass, never a per-tick scan of world entities.
            for (World world : Bukkit.getWorlds()) for (Entity entity : world.getEntities()) clean(entity);
            for (Golda g : goldas.values()) activate(g);
            Bukkit.getScheduler().runTaskTimer(this, this::step, 1, 1);
            for (Player p : Bukkit.getOnlinePlayers()) { recover(p); schedulePack(p); }
            getLogger().info("Z_Gold " + getPluginMeta().getVersion() + ": loaded " + goldas.size() + " Golda. Resource pack: 1.05 + Gold, no SmpLore.");
            if (serverPack() != null) {
                getLogger().info("Resource pack is managed by server.properties; Z_Gold will not send an additional pack.");
                checkOtherPackSenders();
            }
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Cannot enable Z_Gold. Original state has not been overwritten.", ex);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }
    @Override public void onDisable() {
        for (Golda g : List.copyOf(goldas.values())) if (g.removing) { goldas.remove(g.id); unindex(g); }
        if (loaded && !failed) try { store.save(goldas, deaths); }
        catch (IOException ex) { getLogger().log(Level.SEVERE, "Could not save Golda state", ex); }
        for (UUID id : List.copyOf(active.keySet())) detach(id);
    }
    private boolean commit() {
        if (failed) return false;
        try { store.save(goldas, deaths); return true; }
        catch (IOException | RuntimeException ex) {
            // Stop the economy on disk failure instead of continuing with unsaved counters.
            failed = true; getLogger().log(Level.SEVERE, "Z_Gold suspended: state could not be committed. Fix disk access and restart.", ex);
            return false;
        }
    }
    private Component message(String key, String... replacements) {
        TagResolver.Builder tags = TagResolver.builder();
        for (int i = 0; i + 1 < replacements.length; i += 2) tags.resolver(Placeholder.unparsed(replacements[i], replacements[i + 1]));
        return MiniMessage.miniMessage().deserialize(settings.messages().getOrDefault(key, key), tags.build());
    }
    private void tell(CommandSender to, String key, String... replacements) { to.sendMessage(message(key, replacements)); }
    private boolean use(Player p) { return p.isOp() || p.hasPermission("zgold.use"); }
    private boolean admin(CommandSender s) { return s.isOp() || s.hasPermission("zgold.admin"); }
    private boolean alive(Player p) { return p.isOnline() && !p.isDead() && p.getHealth() > 0 && p.getGameMode() != GameMode.SPECTATOR; }

    private ItemStack egg(int count) {
        ItemStack egg = new ItemStack(Material.EGG, count);
        egg.editMeta(meta -> {
            meta.displayName(message("egg-name")); meta.lore(List.of(message("egg-lore")));
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "koch_golda");
            meta.setItemModel(new NamespacedKey("z_gold", "koch_golda"));
            meta.setMaxStackSize(64);
        });
        return egg;
    }
    private boolean isEgg(ItemStack item) {
        return item != null && item.hasItemMeta() && "koch_golda".equals(item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING));
    }
    private ItemStack model() {
        ItemStack model = new ItemStack(Material.PAPER);
        model.editMeta(meta -> meta.setItemModel(new NamespacedKey("z_gold", "golda")));
        return model;
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void place(PlayerInteractEvent e) {
        if (!isEgg(e.getItem())) return;
        e.setCancelled(true); // In particular, never throw the base vanilla egg.
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null || e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (!use(p)) { tell(p, "no-permission"); return; }
        if (!alive(p)) return;
        if (failed) { tell(p, "error"); return; }
        if (goldas.size() >= settings.maxActive()) { tell(p, "limit"); return; }
        Block base = e.getClickedBlock();
        Location ground = base.getLocation().add(.5, 1, .5);
        if (!base.getType().isSolid() || p.getEyeLocation().distanceSquared(ground) > settings.reach() * settings.reach()
            || !free(ground, settings.height() + settings.amplitude())) { tell(p, "blocked"); return; }
        Golda g = new Golda(UUID.randomUUID(), base.getWorld().getUID(), ground.getX(), ground.getY(), ground.getZ(),
            p.getYaw(), settings.height(), settings.amplitude(), settings.riseTicks(), settings.periodTicks(), settings.batch(), settings.clicks());
        goldas.put(g.id, g); index(g);
        if (!commit()) { unindex(g); goldas.remove(g.id); tell(p, "error"); return; }
        if (!activate(g)) {
            goldas.remove(g.id); unindex(g); commit(); tell(p, "blocked"); return;
        }
        if (p.getGameMode() != GameMode.CREATIVE) e.getItem().setAmount(e.getItem().getAmount() - 1);
        warnPack(p);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void dispense(org.bukkit.event.block.BlockDispenseEvent e) { if (isEgg(e.getItem())) e.setCancelled(true); }
    private boolean free(Location base, double height) {
        World w = base.getWorld();
        BoundingBox volume = new BoundingBox(base.getX() - HALF_LENGTH, base.getY() + .02, base.getZ() - HALF_LENGTH,
            base.getX() + HALF_LENGTH, base.getY() + height + HALF_HEIGHT + .02, base.getZ() + HALF_LENGTH);
        if (volume.getMaxY() >= w.getMaxHeight() || volume.getMinY() < w.getMinHeight()) return false;
        for (int x = (int)Math.floor(volume.getMinX()); x <= (int)Math.floor(volume.getMaxX()); x++)
            for (int z = (int)Math.floor(volume.getMinZ()); z <= (int)Math.floor(volume.getMaxZ()); z++) {
                if (!w.isChunkLoaded(x >> 4, z >> 4)) return false;
                for (int y = (int)Math.floor(volume.getMinY()); y <= (int)Math.floor(volume.getMaxY()); y++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (!b.isPassable() && b.getBoundingBox().overlaps(volume)) return false;
                }
            }
        return goldas.values().stream().noneMatch(g -> g.world.equals(w.getUID())
            && Math.abs(g.x - base.getX()) < HALF_LENGTH * 2 && Math.abs(g.z - base.getZ()) < HALF_LENGTH * 2
            && Math.abs(g.groundY - base.getY()) < height + 1);
    }
    private void index(Golda g) { chunks.computeIfAbsent(ChunkKey.of(g), k -> new HashSet<>()).add(g.id); }
    private void unindex(Golda g) {
        Set<UUID> ids = chunks.get(ChunkKey.of(g));
        if (ids != null) { ids.remove(g.id); if (ids.isEmpty()) chunks.remove(ChunkKey.of(g)); }
    }
    private Location center(Golda g) { return new Location(Bukkit.getWorld(g.world), g.x, g.centerY(), g.z, g.yaw, 0); }
    private void mark(Entity e, Golda g) {
        e.setPersistent(false); e.setInvulnerable(true); e.setGravity(false); e.setSilent(true);
        e.getPersistentDataContainer().set(entityKey, PersistentDataType.STRING, g.id.toString());
    }
    private boolean activate(Golda g) {
        if (g.removing || active.containsKey(g.id) || failed) return active.containsKey(g.id);
        World w = Bukkit.getWorld(g.world);
        if (w == null || !w.isChunkLoaded(ChunkKey.of(g).x, ChunkKey.of(g).z)) return false;
        ItemDisplay display = null; Interaction hit = null;
        try {
            Location at = center(g);
            display = w.spawn(at, ItemDisplay.class, e -> {
                mark(e, g); e.setItemStack(model()); e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                e.setTeleportDuration(2); e.setInterpolationDuration(2); e.setDisplayWidth(2.2f); e.setDisplayHeight(1.0f);
                e.setBrightness(new Display.Brightness(15, 15)); e.setViewRange(1.5f);
            });
            hit = w.spawn(at.clone().subtract(0, HALF_HEIGHT, 0), Interaction.class, e -> {
                mark(e, g); e.setInteractionWidth((float)(HALF_LENGTH * 2)); e.setInteractionHeight((float)(HALF_HEIGHT * 2)); e.setResponsive(true);
            });
            if (!display.isValid() || !hit.isValid()) throw new IllegalStateException("Golda spawn was cancelled");
            active.put(g.id, new Pair(display, hit)); entityOwners.put(display.getUniqueId(), g.id); entityOwners.put(hit.getUniqueId(), g.id);
            return true;
        } catch (RuntimeException ex) {
            if (display != null) display.remove(); if (hit != null) hit.remove();
            getLogger().log(Level.WARNING, "Could not create visual/interaction for Golda " + g.id, ex); return false;
        }
    }
    private void detach(UUID id) {
        Pair pair = active.remove(id);
        if (pair != null) {
            entityOwners.remove(pair.display.getUniqueId()); entityOwners.remove(pair.hit.getUniqueId());
            pair.display.remove(); pair.hit.remove();
        }
    }
    private void clean(Entity e) {
        if (e.getPersistentDataContainer().has(entityKey) && !entityOwners.containsKey(e.getUniqueId())) e.remove();
    }
    private void step() {
        tick++;
        if (failed) return;
        for (Map.Entry<UUID, Pair> entry : List.copyOf(active.entrySet())) {
            Golda g = goldas.get(entry.getKey()); Pair pair = entry.getValue();
            if (g == null) { detach(entry.getKey()); continue; }
            if (!pair.display.isValid() || !pair.hit.isValid()) {
                detach(g.id);
                if (g.removing) { goldas.remove(g.id); unindex(g); commit(); } else activate(g);
                continue;
            }
            if (g.removing) {
                if (++g.removeAge >= settings.disappearTicks()) {
                    goldas.remove(g.id); unindex(g); detach(g.id); commit(); continue;
                }
                if (tick % 2 == 0) {
                    float scale = 1 - (float)g.removeAge / settings.disappearTicks();
                    pair.display.setTransformation(new Transformation(new Vector3f(0, g.removeAge * .015f, 0),
                        new Quaternionf(), new Vector3f(scale), new Quaternionf()));
                    pair.display.setInterpolationDelay(0);
                }
            } else {
                g.age++;
                if (tick % 2 == 0) {
                    Location at = center(g); pair.display.teleport(at); pair.hit.teleport(at.clone().subtract(0, HALF_HEIGHT, 0));
                }
            }
        }
        if (tick % settings.saveTicks() == 0) commit();
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void click(PlayerInteractEntityEvent e) { interact(e); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void clickAt(PlayerInteractAtEntityEvent e) { interact(e); }
    private void interact(PlayerInteractEntityEvent e) {
        UUID id = entityOwners.get(e.getRightClicked().getUniqueId()); if (id == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        Golda g = goldas.get(id); Player p = e.getPlayer();
        if (g == null || g.removing || !alive(p)) return;
        if (!use(p)) { tell(p, "no-permission"); return; }
        if (failed) { tell(p, "error"); return; }
        if (!g.world.equals(p.getWorld().getUID()) || p.getEyeLocation().distanceSquared(center(g)) > settings.reach() * settings.reach()) return;
        if (!g.ready()) { tell(p, "rising"); return; }
        if (g.waiting()) {
            // Both interact-at and interact packets can arrive for one physical click.
            if (g.lastClick != Long.MIN_VALUE && tick - g.lastClick < settings.cooldown()) return;
            g.lastClick = tick;
            if (g.respondent.equals(p.getUniqueId())) question(p, g); else tell(p, "waiting-other");
            return;
        }
        if (!g.canClick(tick, settings.cooldown())) return;
        int old = g.clicks; UUID oldRespondent = g.respondent, oldToken = g.token;
        g.claim(p.getUniqueId(), tick);
        // Commit before spawning the item: restarts cannot reset an already-issued batch.
        if (!commit()) {
            g.clicks = old; g.respondent = oldRespondent; g.token = oldToken; tell(p, "error"); return;
        }
        Location drop = center(g).subtract(0, .35, 0);
        Item item = drop.getWorld().dropItem(drop, new ItemStack(Material.GOLD_INGOT, g.batch));
        if (!item.isValid()) {
            g.clicks = old; g.respondent = oldRespondent; g.token = oldToken; commit(); tell(p, "error"); return;
        }
        item.setPickupDelay(10);
        // Rewards are intentionally silent. Even a legacy messages.reward entry
        // from 1.0.0 must not reveal the counter or maximum to the player.
        if (g.waiting()) question(p, g);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void damage(EntityDamageEvent e) { if (entityOwners.containsKey(e.getEntity().getUniqueId())) e.setCancelled(true); }
    private void question(Player p, Golda g) {
        tell(p, "question");
        String command = "/zgold answer " + g.id + " " + g.token + " ";
        Component yes = Component.text("[" + settings.messages().getOrDefault("answer-yes", "Да") + "]", NamedTextColor.GREEN)
            .clickEvent(ClickEvent.runCommand(command + "yes")).hoverEvent(HoverEvent.showText(message("answer-hover")));
        Component no = Component.text("[" + settings.messages().getOrDefault("answer-no", "Нет") + "]", NamedTextColor.RED)
            .clickEvent(ClickEvent.runCommand(command + "no")).hoverEvent(HoverEvent.showText(message("answer-hover")));
        p.sendMessage(yes.append(Component.text(" ")).append(no));
    }
    private void answer(Player p, String[] args) {
        if (!use(p)) { tell(p, "no-permission"); return; }
        if (failed) { tell(p, "error"); return; }
        Golda g;
        try {
            g = goldas.get(UUID.fromString(args[1]));
            if (g == null || !g.accepts(p.getUniqueId(), UUID.fromString(args[2])) || !Set.of("yes", "no").contains(args[3]))
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) { tell(p, "invalid-answer"); return; }
        if (!alive(p) || deaths.containsKey(p.getUniqueId()) || !killing.add(p.getUniqueId())) { tell(p, "invalid-answer"); return; }
        InventorySnapshot snapshot = new InventorySnapshot(p);
        double previousHealth = p.getHealth();
        deaths.put(p.getUniqueId(), snapshot); g.removing = true;
        if (!commit()) {
            deaths.remove(p.getUniqueId()); killing.remove(p.getUniqueId()); g.removing = false; tell(p, "error"); return;
        }
        try {
            // Removing the cursor first prevents vanilla from throwing it on the ground on death.
            p.setItemOnCursor(null);
            p.setHealth(0); // Direct server death, including creative; no damage/totem bypass loophole.
            if (!snapshot.confirmed) {
                if (p.getHealth() <= 0) p.setHealth(previousHealth);
                snapshot.restore(p); deaths.remove(p.getUniqueId()); g.removing = false; g.token = UUID.randomUUID();
                commit(); tell(p, "death-cancelled");
            }
        } catch (RuntimeException ex) {
            if (!snapshot.confirmed) {
                if (p.getHealth() <= 0 && !p.isDead()) p.setHealth(previousHealth);
                snapshot.restore(p); deaths.remove(p.getUniqueId()); g.removing = false; g.token = UUID.randomUUID(); commit();
            }
            getLogger().log(Level.SEVERE, "Could not finish Golda death; snapshot was preserved where needed", ex); tell(p, "error");
        } finally { killing.remove(p.getUniqueId()); }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void preserve(PlayerDeathEvent e) {
        if (!killing.contains(e.getEntity().getUniqueId())) return;
        InventorySnapshot s = deaths.get(e.getEntity().getUniqueId()); if (s == null) return;
        e.getDrops().clear(); e.getItemsToKeep().clear(); e.setKeepInventory(true); e.setKeepLevel(true);
        e.setDroppedExp(0); e.setShouldDropExperience(false); e.setNewLevel(s.level); e.setNewTotalExp(s.total);
        e.deathMessage(null); e.setShowDeathMessages(false);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void deathObserved(PlayerDeathEvent e) {
        UUID id = e.getEntity().getUniqueId();
        if (!killing.contains(id)) return;
        InventorySnapshot s = deaths.get(id);
        if (s != null && !s.confirmed) {
            s.confirmed = true; commit();
            Component text = message("death", "player", e.getEntity().getName());
            for (Player listener : Bukkit.getOnlinePlayers()) listener.sendMessage(text);
            Bukkit.getConsoleSender().sendMessage(text);
        }
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void respawn(PlayerRespawnEvent e) {
        Bukkit.getScheduler().runTask(this, () -> recover(e.getPlayer()));
    }
    private void recover(Player p) {
        InventorySnapshot snapshot = deaths.get(p.getUniqueId());
        if (snapshot == null || p.isDead() || p.getHealth() <= 0) return;
        // Also restores an ARMED snapshot if a crash occurred between the disk commit and death.
        snapshot.restore(p);
        // Flush player.dat before clearing the journal; a second crash must not lose restored items.
        p.saveData();
        deaths.remove(p.getUniqueId());
        if (!commit()) deaths.put(p.getUniqueId(), snapshot);
        else tell(p, "restored");
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void join(PlayerJoinEvent e) {
        schedulePack(e.getPlayer());
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Player p = e.getPlayer(); if (!p.isOnline()) return; recover(p);
            if (alive(p)) for (Golda g : goldas.values()) if (g.waiting() && p.getUniqueId().equals(g.respondent)) question(p, g);
        }, 20);
    }
    @EventHandler public void quit(PlayerQuitEvent e) {
        warned.remove(e.getPlayer().getUniqueId()); packed.remove(e.getPlayer().getUniqueId());
        packAttempts.remove(e.getPlayer().getUniqueId());
    }
    private org.bukkit.packs.ResourcePack serverPack() {
        org.bukkit.packs.ResourcePack pack = getServer().getServerResourcePack();
        return pack == null || pack.getUrl().isBlank() ? null : pack;
    }
    private void checkOtherPackSenders() {
        // Read-only diagnostics: never edit another plugin's config or JAR.
        for (String name : List.of("Shahed", "Z_Verity")) {
            var plugin = getServer().getPluginManager().getPlugin(name);
            if (plugin instanceof JavaPlugin other && other.isEnabled()
                && other.getConfig().getBoolean("resource-pack.enabled", true)
                && other.getConfig().getBoolean("resource-pack.prompt-on-join", true)) {
                getLogger().warning("Duplicate resource-pack sender: " + name
                    + ". Set resource-pack.enabled and resource-pack.prompt-on-join to false in "
                    + other.getDataFolder() + "/config.yml, then restart the server. Keep the common pack in server.properties.");
            }
        }
    }
    private void schedulePack(Player p) {
        // server.properties is the single owner whenever it defines a pack.
        if (!settings.packEnabled() || serverPack() != null || settings.packUrl().isEmpty()) return;
        UUID id = p.getUniqueId(), attempt = UUID.randomUUID();
        if (packAttempts.putIfAbsent(id, attempt) != null) return;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            // A delayed task from an earlier login may not send to a new session.
            if (!attempt.equals(packAttempts.get(id)) || !p.isOnline()) return;
            // Recheck at send time, too: settings can change while the task waits.
            if (!settings.packEnabled() || serverPack() != null || settings.packUrl().isEmpty()) {
                packAttempts.remove(id, attempt); return;
            }
            byte[] hash = settings.packHash().isEmpty() ? null : HexFormat.of().parseHex(settings.packHash());
            // Exactly one attempt per session; statuses never start download loops.
            p.addResourcePack(PACK_ID, settings.packUrl(), hash, settings.packPrompt(), settings.packRequired());
        }, Math.clamp(settings.packDelay(), 1, 1200));
    }
    @EventHandler public void packStatus(PlayerResourcePackStatusEvent e) {
        var server = serverPack();
        if (!e.getID().equals(PACK_ID) && (server == null || !e.getID().equals(server.getId()))) return;
        if (e.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) packed.add(e.getPlayer().getUniqueId());
        else if (Set.of(PlayerResourcePackStatusEvent.Status.DECLINED, PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD,
            PlayerResourcePackStatusEvent.Status.FAILED_RELOAD, PlayerResourcePackStatusEvent.Status.INVALID_URL).contains(e.getStatus())) {
            packed.remove(e.getPlayer().getUniqueId()); tell(e.getPlayer(), "pack-warning");
        }
    }
    private void warnPack(Player p) {
        if (settings.packEnabled() && !packed.contains(p.getUniqueId()) && warned.add(p.getUniqueId())) tell(p, "pack-warning");
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void chunkLoad(ChunkLoadEvent e) {
        ChunkKey key = new ChunkKey(e.getWorld().getUID(), e.getChunk().getX(), e.getChunk().getZ());
        Bukkit.getScheduler().runTask(this, () -> activateChunk(key));
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void entityLoad(EntitiesLoadEvent e) {
        e.getEntities().forEach(this::clean);
        activateChunk(new ChunkKey(e.getWorld().getUID(), e.getChunk().getX(), e.getChunk().getZ()));
    }
    private void activateChunk(ChunkKey key) {
        for (UUID id : Set.copyOf(chunks.getOrDefault(key, Set.of()))) { Golda g = goldas.get(id); if (g != null) activate(g); }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void chunkUnload(ChunkUnloadEvent e) {
        ChunkKey key = new ChunkKey(e.getWorld().getUID(), e.getChunk().getX(), e.getChunk().getZ());
        for (UUID id : Set.copyOf(chunks.getOrDefault(key, Set.of()))) {
            detach(id); Golda g = goldas.get(id);
            if (g != null && g.removing) { goldas.remove(id); unindex(g); }
        }
        if (loaded && !failed) commit();
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void worldUnload(WorldUnloadEvent e) {
        for (Golda g : List.copyOf(goldas.values())) if (g.world.equals(e.getWorld().getUID())) {
            detach(g.id); if (g.removing) { goldas.remove(g.id); unindex(g); }
        }
        if (loaded && !failed) commit();
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void worldLoad(WorldLoadEvent e) {
        Bukkit.getScheduler().runTask(this, () -> {
            for (Golda g : goldas.values()) if (g.world.equals(e.getWorld().getUID())) activate(g);
        });
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (args.length == 4 && args[0].equalsIgnoreCase("answer") && sender instanceof Player p) { answer(p, args); return true; }
            if (!admin(sender)) { tell(sender, "no-permission"); return true; }
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig(); Settings next = Settings.read(getConfig(), bundledHash); settings = next; tell(sender, "reloaded"); return true;
            }
            if (failed) { tell(sender, "error"); return true; }
            if ((args.length == 2 || args.length == 3) && args[0].equalsIgnoreCase("give")) {
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { tell(sender, "player-not-found"); return true; }
                int count;
                try { count = args.length == 3 ? Integer.parseInt(args[2]) : 1; } catch (NumberFormatException ex) { count = 0; }
                if (count < 1 || count > 2304) { tell(sender, "amount"); return true; }
                int left = count;
                while (left > 0) {
                    int n = Math.min(64, left); left -= n;
                    target.getInventory().addItem(egg(n)).values().forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));
                }
                tell(sender, "given", "amount", "" + count, "player", target.getName()); return true;
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("remove")) {
                if (!(sender instanceof Player p)) { tell(sender, "player-only"); return true; }
                Golda nearest = goldas.values().stream().filter(g -> g.world.equals(p.getWorld().getUID()))
                    .filter(g -> center(g).distanceSquared(p.getLocation()) <= settings.removeRadius() * settings.removeRadius())
                    .min(Comparator.comparingDouble(g -> center(g).distanceSquared(p.getLocation()))).orElse(null);
                if (nearest == null) tell(sender, "none-nearby"); else remove(sender, List.of(nearest)); return true;
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("removeall")) { remove(sender, List.copyOf(goldas.values())); return true; }
            tell(sender, "usage"); return true;
        } catch (Exception ex) { getLogger().log(Level.WARNING, "Z_Gold command failed", ex); tell(sender, "error"); return true; }
    }
    private void remove(CommandSender sender, List<Golda> records) {
        records.forEach(g -> goldas.remove(g.id));
        if (!commit()) { records.forEach(g -> goldas.put(g.id, g)); tell(sender, "error"); return; }
        for (Golda g : records) { unindex(g); detach(g.id); }
        tell(sender, "removed", "amount", "" + records.size());
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command c, String alias, String[] a) {
        if (!admin(sender)) return List.of();
        List<String> choices = a.length == 1 ? List.of("give", "reload", "remove", "removeall")
            : a.length == 2 && a[0].equalsIgnoreCase("give") ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList() : List.of();
        String prefix = a[a.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
    private record Pair(ItemDisplay display, Interaction hit) { }
    private record ChunkKey(UUID world, int x, int z) {
        static ChunkKey of(Golda g) { return new ChunkKey(g.world, (int)Math.floor(g.x) >> 4, (int)Math.floor(g.z) >> 4); }
    }
}
