package dev.zverity;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Server-side Verity box/sphere companion. It uses ItemDisplay only; no Forge entity is required. */
public final class ZVerityPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private enum Kind { BOX, SPHERE }

    private final Map<UUID, Thing> things = new HashMap<>();
    private final Set<UUID> packLoaded = new HashSet<>();
    private org.bukkit.NamespacedKey itemKey;
    private org.bukkit.NamespacedKey thingKey;
    private long tick;

    private final class Thing {
        final ItemDisplay display;
        final Kind kind;
        int stage;
        Player target;
        int age;

        Thing(ItemDisplay display, Kind kind) {
            this.display = display;
            this.kind = kind;
        }
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        itemKey = new org.bukkit.NamespacedKey(this, "item");
        thingKey = new org.bukkit.NamespacedKey(this, "thing");
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("zverity")).setExecutor(this);
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) cleanup(entity);
        }
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
    }

    @Override public void onDisable() {
        for (Thing thing : List.copyOf(things.values())) remove(thing);
        things.clear();
    }

    private String itemType(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return "";
        String value = stack.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        return value == null ? "" : value;
    }

    private ItemStack verityItem(int amount) {
        ItemStack stack = new ItemStack(Material.PAPER, amount);
        stack.editMeta(meta -> {
            meta.displayName(Component.text("Verity"));
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "verity");
            meta.setItemModel(new org.bukkit.NamespacedKey("z_verity", "verity_egg"));
            meta.lore(List.of(Component.text("ПКМ по блоку — призвать Verity")));
        });
        return stack;
    }

    private boolean give(CommandSender sender, String[] args) {
        if (args.length < 1 || !args[0].equalsIgnoreCase("verity")) return false;
        if (sender instanceof Player player) {
            if (!player.isOp() && !player.hasPermission("zverity.give")) {
                sender.sendMessage(Component.text("Выдача Verity доступна только оператору."));
                return true;
            }
        } else if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(Component.text("Эту выдачу может выполнить только консоль или оператор."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Использование: /give <игрок> verity [количество]"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Игрок не найден: нужен точный ник онлайн-игрока."));
            return true;
        }
        int amount = 1;
        if (args.length >= 3) {
            try { amount = Integer.parseInt(args[2]); } catch (NumberFormatException ignored) { amount = 0; }
        }
        if (amount < 1 || amount > 2304 || args.length > 3) {
            sender.sendMessage(Component.text("Количество Verity: от 1 до 2304."));
            return true;
        }
        while (amount > 0) {
            int count = Math.min(64, amount);
            amount -= count;
            for (ItemStack leftover : target.getInventory().addItem(verityItem(count)).values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), leftover);
            }
        }
        sender.sendMessage(Component.text("Выдано Verity игроку " + target.getName() + "."));
        return true;
    }

    private boolean interceptGive(CommandSender sender, String command) {
        String[] tokens = command.trim().replaceFirst("^/", "").split("\\s+");
        if (tokens.length < 3 || !tokens[0].equalsIgnoreCase("give")) return false;
        return give(sender, new String[] { tokens[2], tokens[1], tokens.length >= 4 ? tokens[3] : "1" });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void playerCommand(PlayerCommandPreprocessEvent event) {
        if (interceptGive(event.getPlayer(), event.getMessage())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void serverCommand(ServerCommandEvent event) {
        if (interceptGive(event.getSender(), event.getCommand())) event.setCancelled(true);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 2 && args[0].equalsIgnoreCase("give")) {
            return give(sender, new String[] { "verity", args[1], args.length >= 3 ? args[2] : "1" });
        }
        sender.sendMessage(Component.text("/zverity give <игрок> [количество]"));
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interact(PlayerInteractEvent event) {
        if (!event.getPlayer().isOnline() || event.getHand() != EquipmentSlot.HAND) return;
        if (!itemType(event.getItem()).equals("verity")) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        event.setCancelled(true);
        Location location = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(.5, .5, .5);
        if (!clear(location)) {
            event.getPlayer().sendMessage(Component.text("Для коробки Verity нет свободного места."));
            return;
        }
        spawn(location, Kind.BOX);
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.getItem().setAmount(event.getItem().getAmount() - 1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void interactEntity(PlayerInteractEntityEvent event) {
        Thing thing = things.get(event.getRightClicked().getUniqueId());
        if (thing == null || event.getHand() != EquipmentSlot.HAND) return;
        event.setCancelled(true);
        if (thing.kind == Kind.BOX) {
            Location location = thing.display.getLocation();
            remove(thing);
            Thing sphere = spawn(location.clone().add(0, .65, 0), Kind.SPHERE);
            location.getWorld().playSound(location, "z_verity:verity", SoundCategory.HOSTILE,
                (float) number("verity.sound-volume", .8, 0, 4), 1f);
            return;
        }
        activateOrKill(thing, event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void damage(EntityDamageEvent event) {
        Thing thing = things.get(event.getEntity().getUniqueId());
        if (thing == null) return;
        event.setCancelled(true);
        if (event instanceof EntityDamageByEntityEvent by && by.getDamager() instanceof Player player && thing.kind == Kind.SPHERE) {
            activateOrKill(thing, player);
        }
    }

    private void activateOrKill(Thing thing, Player player) {
        if (thing.stage == 0) {
            thing.stage = 1;
            thing.target = player;
            player.chat("Hello!");
            return;
        }
        kill(player, thing);
    }

    private void kill(Player player, Thing thing) {
        Location death = player.getLocation().clone();
        String coords = String.format(Locale.ROOT, "%.1f, %.1f, %.1f", death.getX(), death.getY(), death.getZ());
        player.chat("Hello, i am verity. Я убил его и оставил все его вещи (координаты смерти: " + coords + ")");
        if (getConfig().getBoolean("verity.drop-inventory-on-kill", true)) dropInventory(player, death);
        remove(thing);
        player.setHealth(0.0);
    }

    private void dropInventory(Player player, Location location) {
        List<ItemStack> stacks = new ArrayList<>();
        stacks.addAll(Arrays.asList(player.getInventory().getContents()));
        stacks.addAll(Arrays.asList(player.getInventory().getArmorContents()));
        stacks.add(player.getInventory().getItemInOffHand());
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.getType().isAir()) location.getWorld().dropItemNaturally(location, stack.clone());
        }
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
    }

    private Thing spawn(Location location, Kind kind) {
        String model = kind == Kind.BOX ? "verity_box" : "verity_sphere";
        ItemDisplay display = location.getWorld().spawn(location, ItemDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.setItemStack(displayItem(model));
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            float scale = kind == Kind.BOX ? 1.1f : 1.35f;
            entity.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale), new Quaternionf()));
            entity.setTeleportDuration(1);
            entity.setInterpolationDuration(1);
            entity.getPersistentDataContainer().set(thingKey, PersistentDataType.STRING, kind.name());
        });
        Thing thing = new Thing(display, kind);
        things.put(display.getUniqueId(), thing);
        return thing;
    }

    private ItemStack displayItem(String model) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(new org.bukkit.NamespacedKey("z_verity", model));
        stack.setItemMeta(meta);
        return stack;
    }

    private boolean clear(Location location) {
        return location.getWorld().getBlockAt(location).isPassable()
            && location.getWorld().getBlockAt(location.clone().add(0, 1, 0)).isPassable();
    }

    private void tick() {
        tick++;
        for (Thing thing : List.copyOf(things.values())) {
            if (!thing.display.isValid()) { things.remove(thing.display.getUniqueId()); continue; }
            if (thing.kind != Kind.SPHERE || thing.stage == 0 || thing.target == null || !thing.target.isOnline()) continue;
            Location target = thing.target.getLocation().clone();
            Vector behind = target.getDirection().setY(0).normalize().multiply(-number("verity.follow-distance", 1.7, .2, 8));
            Location desired = target.add(behind).add(0, number("verity.follow-height", 1.1, .2, 4), 0);
            Location current = thing.display.getLocation();
            Vector delta = desired.toVector().subtract(current.toVector());
            double factor = number("verity.follow-speed", .18, .01, 1);
            Location next = current.add(delta.multiply(factor));
            next.setYaw(target.getYaw());
            thing.age++;
            thing.display.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateZ((float) (thing.age * .12)), new Vector3f(1.35f), new Quaternionf()));
            thing.display.setTeleportDuration(1);
            thing.display.teleport(next);
            if (tick % 38 == 0) current.getWorld().playSound(current, "z_verity:verity", SoundCategory.HOSTILE,
                (float) number("verity.sound-volume", .8, 0, 4), 1f);
        }
    }

    private double number(String key, double fallback, double min, double max) {
        double value = getConfig().getDouble(key, fallback);
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }

    private void cleanup(Entity entity) {
        if (entity.getPersistentDataContainer().has(thingKey, PersistentDataType.STRING)) entity.remove();
    }

    @EventHandler public void chunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) cleanup(entity);
    }

    @EventHandler public void worldUnload(WorldUnloadEvent event) {
        for (Thing thing : List.copyOf(things.values())) if (thing.display.getWorld() == event.getWorld()) remove(thing);
    }

    private void remove(Thing thing) {
        things.remove(thing.display.getUniqueId());
        if (thing.display.isValid()) thing.display.remove();
    }

    @EventHandler public void quit(PlayerQuitEvent event) { }

    @EventHandler public void join(PlayerJoinEvent event) {
        if (!getConfig().getBoolean("resource-pack.enabled", true)) return;
        if (!getConfig().getBoolean("resource-pack.prompt-on-join", true)) return;
        Bukkit.getScheduler().runTaskLater(this, () -> sendPack(event.getPlayer()), 60L);
    }

    private void sendPack(Player player) {
        String url = getConfig().getString("resource-pack.url", "").trim();
        String sha1 = getConfig().getString("resource-pack.sha1", "").trim();
        if (url.isEmpty()) return;
        try {
            byte[] hash = sha1.isEmpty() ? null : java.util.HexFormat.of().parseHex(sha1);
            player.setResourcePack(url, hash, Component.text("Для Verity требуется ресурс-пак."));
        } catch (IllegalArgumentException ex) {
            getLogger().warning("Неверный resource-pack.sha1: " + ex.getMessage());
        }
    }

    @EventHandler public void packStatus(PlayerResourcePackStatusEvent event) {
        if (event.getStatus() == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD) {
            getLogger().warning("Игрок " + event.getPlayer().getName() + " не смог загрузить ресурс-пак Verity.");
        }
    }
}
