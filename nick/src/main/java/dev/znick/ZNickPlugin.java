package dev.znick;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

public class ZNickPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    volatile NickState state = new NickState(Map.of());
    volatile String collision = "always";
    private Wire wire;
    private NickStore store;
    private int max;
    private NamedTextColor color;
    private boolean duplicates;

    @Override public void onEnable() {
        try {
            saveDefaultConfig();
            store = openStore(getDataFolder().toPath().resolve("nicknames.yml"));
            readSettings(getConfig());
            var loaded = store.load(max, color);
            validateDuplicates(loaded, duplicates);
            state = new NickState(loaded);
            wire = openWire();
            getServer().getPluginManager().registerEvents(this, this);
            Objects.requireNonNull(getCommand("znick")).setExecutor(this);
            getCommand("znick").setTabCompleter(this);
            for (Player p : getServer().getOnlinePlayers()) wire.attach(p);
            for (Player p : getServer().getOnlinePlayers()) { apply(p); if (state.players.containsKey(p.getUniqueId())) wire.refresh(p, null); }
            getServer().getScheduler().runTaskTimer(this, this::reconcile, 20, 20);
            getLogger().info("Z_Nick " + getPluginMeta().getVersion() + ": загружено ников " + state.players.size());
        } catch (Exception error) {
            getLogger().log(Level.SEVERE, "Не удалось включить Z_Nick. Файл ников не изменён.", error);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    protected NickStore openStore(Path file) { return new NickStore(file); }
    protected Wire openWire() throws Exception { return new NmsWire(this); }

    private void readSettings(org.bukkit.configuration.file.FileConfiguration config) {
        max = config.getInt("display.max-visible-length", 32);
        if (max < 1 || max > 64) throw new IllegalArgumentException("max-visible-length must be 1..64");
        color = NamedTextColor.NAMES.value(config.getString("display.default-color", "white").toLowerCase(Locale.ROOT));
        if (color == null) throw new IllegalArgumentException("Unknown display.default-color");
        collision = config.getString("display.collision-rule", "always").toLowerCase(Locale.ROOT);
        if (!Set.of("always", "never", "push_other_teams", "push_own_team").contains(collision))
            throw new IllegalArgumentException("Unknown collision-rule");
        int interval = config.getInt("display.reconcile-interval-ticks", 40);
        if (interval < 20 || interval > 1200) throw new IllegalArgumentException("reconcile interval must be 20..1200");
        duplicates = config.getBoolean("allow-duplicate-nicks", false);
    }

    private static void validateDuplicates(Map<UUID, Nick> values, boolean allowed) {
        if (allowed) return;
        Set<String> used = new HashSet<>();
        for (Nick n : values.values()) if (!used.add(Nick.key(n.plain())))
            throw new IllegalArgumentException("Duplicate nickname in nicknames.yml: " + n.plain());
    }

    @Override public void onDisable() {
        var previous = state;
        state = new NickState(Map.of());
        if (wire != null) {
            for (Player p : getServer().getOnlinePlayers()) if (previous.players.containsKey(p.getUniqueId())) {
                p.displayName(Component.text(p.getName())); p.playerListName(null);
                try { wire.refresh(p, previous.players.get(p.getUniqueId())); }
                catch (Exception error) { getLogger().log(Level.WARNING, "Не удалось обновить отображение при отключении", error); }
            }
            wire.close(); wire = null;
        }
    }

    private void apply(Player p) {
        Nick n = state.players.get(p.getUniqueId());
        if (n != null) { p.displayName(n.text()); p.playerListName(n.text()); }
    }

    private long tick;
    private void reconcile() {
        tick += 20;
        int interval = getConfig().getInt("display.reconcile-interval-ticks", 40);
        if (tick < interval) return;
        tick = 0;
        for (UUID id : state.players.keySet()) {
            Player p = getServer().getPlayer(id);
            if (p != null) {
                Nick n = state.players.get(id);
                if (!p.displayName().equals(n.text())) p.displayName(n.text());
                if (!p.playerListName().equals(n.text())) p.playerListName(n.text());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void enter(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        try {
            wire.attach(p);
            Nick n = state.players.get(p.getUniqueId());
            if (n != null && !n.account().equals(p.getName())) {
                var next = new HashMap<>(state.players);
                next.put(n.id(), Nick.parse(n.id(), p.getName(), n.raw(), n.wire(), max, color));
                store.save(next); state = new NickState(next);
            }
            apply(p);
        } catch (Exception error) {
            getLogger().log(Level.SEVERE, "Не удалось подключить защиту ников для " + p.getName(), error);
            p.kick(Component.text("Ошибка подключения Z_Nick. Сообщите администратору."));
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void joined(PlayerJoinEvent e) { e.joinMessage(state.rewrite(e.joinMessage())); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void leaving(PlayerQuitEvent e) { e.quitMessage(state.rewrite(e.quitMessage())); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void left(PlayerQuitEvent e) { wire.forget(e.getPlayer()); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void death(PlayerDeathEvent e) { e.deathMessage(state.rewrite(e.deathMessage())); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void chat(AsyncChatEvent e) {
        var snapshot = state;
        var renderer = e.renderer();
        e.renderer((source, name, message, viewer) -> snapshot.rewrite(renderer.render(source,
            snapshot.players.containsKey(source.getUniqueId()) ? snapshot.players.get(source.getUniqueId()).text() : name,
            message, viewer)));
    }

    private OfflinePlayer resolve(String input) {
        Player p = getServer().getPlayerExact(input);
        if (p != null) return p;
        try {
            UUID id = UUID.fromString(input);
            OfflinePlayer found = getServer().getOfflinePlayer(id);
            return found.isOnline() || found.hasPlayedBefore() || state.players.containsKey(id) ? found : null;
        } catch (IllegalArgumentException ignored) { }
        OfflinePlayer known = getServer().getOfflinePlayerIfCached(input);
        if (known != null) return known;
        return state.players.values().stream().filter(n -> n.account().equalsIgnoreCase(input)).findFirst()
            .map(n -> getServer().getOfflinePlayer(n.id())).orElse(null);
    }

    private int wireId(UUID id) {
        Nick old = state.players.get(id);
        if (old != null) return old.wire();
        Set<Integer> used = new HashSet<>(); state.players.values().forEach(n -> used.add(n.wire()));
        int token = id.hashCode() & 0x0fffffff;
        while (used.contains(token)) token = (token + 1) & 0x0fffffff;
        return token;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { message(sender, "usage", Map.of()); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!Set.of("set", "remove", "reload").contains(sub)) { message(sender, "usage", Map.of()); return true; }
        if (!sender.hasPermission("znick.admin") && !sender.hasPermission("znick." + sub)) { message(sender, "no-permission", Map.of()); return true; }
        if (sub.equals("reload")) {
            if (args.length != 1) { message(sender, "usage", Map.of()); return true; }
            reload(sender); return true;
        }
        if (args.length != (sub.equals("set") ? 3 : 2)) { message(sender, "usage", Map.of()); return true; }
        OfflinePlayer target = resolve(args[sub.equals("set") ? 2 : 1]);
        if (target == null || target.getName() == null) { message(sender, "unknown-player", Map.of()); return true; }
        UUID id = target.getUniqueId(); Nick old = state.players.get(id);
        var next = new HashMap<>(state.players);
        Nick nick = null;
        if (sub.equals("set")) {
            try { nick = Nick.parse(id, target.getName(), args[1], wireId(id), max, color); }
            catch (IllegalArgumentException bad) { message(sender, "invalid-nick", Map.of("max", Component.text(max))); return true; }
            Nick chosen = nick;
            OfflinePlayer owner = getServer().getOfflinePlayerIfCached(nick.plain());
            if (!duplicates && ((owner != null && !owner.getUniqueId().equals(id)) || getServer().getOnlinePlayers().stream().anyMatch(p -> !p.getUniqueId().equals(id) && p.getName().equalsIgnoreCase(chosen.plain()))
                || next.values().stream().anyMatch(n -> !n.id().equals(id) && Nick.key(n.plain()).equals(Nick.key(chosen.plain()))))) {
                message(sender, "duplicate", Map.of()); return true;
            }
            next.put(id, nick);
        } else {
            if (old == null) { message(sender, "not-set", Map.of()); return true; }
            next.remove(id);
        }
        try { store.save(next); }
        catch (Exception error) { getLogger().log(Level.SEVERE, "Сохранение ников не удалось", error); message(sender, "save-error", Map.of()); return true; }
        state = new NickState(next);
        Player live = target.getPlayer();
        if (live != null) {
            if (nick == null) { live.displayName(Component.text(live.getName())); live.playerListName(null); }
            else apply(live);
            try { wire.refresh(live, old); }
            catch (Exception error) { getLogger().log(Level.SEVERE, "Не удалось обновить клиентский профиль", error); live.kick(Component.text("Перезайдите для обновления ника.")); }
        }
        message(sender, nick == null ? "removed" : "set", Map.of("player", Component.text(target.getName()), "nick", nick == null ? Component.empty() : nick.text()));
        return true;
    }

    private void reload(CommandSender sender) {
        var prior = state; String oldCollision = collision;
        int oldMax = max; NamedTextColor oldColor = color; boolean oldDup = duplicates;
        try {
            YamlConfiguration candidate = new YamlConfiguration(); candidate.load(getDataFolder().toPath().resolve("config.yml").toFile());
            readSettings(candidate);
            var loaded = store.load(max, color); validateDuplicates(loaded, duplicates);
            reloadConfig(); state = new NickState(loaded);
        } catch (Exception error) {
            max = oldMax; color = oldColor; duplicates = oldDup; collision = oldCollision;
            getLogger().log(Level.WARNING, "Ошибка перезагрузки, сохранены прежние данные", error);
            message(sender, "reload-error", Map.of()); return;
        }
        for (Player p : getServer().getOnlinePlayers()) {
            Nick old = prior.players.get(p.getUniqueId());
            if (old != null && !state.players.containsKey(p.getUniqueId())) { p.displayName(Component.text(p.getName())); p.playerListName(null); }
            else apply(p);
            if (old != null || state.players.containsKey(p.getUniqueId())) try { wire.refresh(p, old); }
            catch (Exception error) { getLogger().log(Level.SEVERE, "Ошибка обновления ника при reload", error); p.kick(Component.text("Перезайдите для обновления ника.")); }
        }
        message(sender, "reloaded", Map.of());
    }

    private void message(CommandSender to, String key, Map<String, Component> values) {
        Component text = Nick.LEGACY.deserialize(getConfig().getString("messages.prefix", "&d[Z_Nick] &r")
            + getConfig().getString("messages." + key, key));
        for (var e : values.entrySet()) text = text.replaceText(b -> b.matchLiteral("{" + e.getKey() + "}").replacement(e.getValue()));
        to.sendMessage(text);
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> choices = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("set", "remove", "reload")) if (sender.hasPermission("znick.admin") || sender.hasPermission("znick." + s)) choices.add(s);
        } else if ((args.length == 3 && args[0].equalsIgnoreCase("set") && (sender.hasPermission("znick.admin") || sender.hasPermission("znick.set")))
            || (args.length == 2 && args[0].equalsIgnoreCase("remove") && (sender.hasPermission("znick.admin") || sender.hasPermission("znick.remove")))) {
            getServer().getOnlinePlayers().forEach(p -> choices.add(p.getName()));
            state.players.values().forEach(n -> choices.add(n.account()));
        }
        String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().distinct().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(typed)).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
