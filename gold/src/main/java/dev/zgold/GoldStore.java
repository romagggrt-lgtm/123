package dev.zgold;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** One atomic file commits economy state and pending death recovery together. */
final class GoldStore {
    private final Path file;
    GoldStore(Path file) { this.file = file; }

    void load(Map<UUID, Golda> goldas, Map<UUID, InventorySnapshot> deaths) throws IOException, InvalidConfigurationException {
        if (!Files.exists(file)) return;
        if (Files.size(file) > 16 * 1024 * 1024) throw new IOException("Golda state exceeds 16 MiB; source preserved");
        YamlConfiguration in = new YamlConfiguration(); in.load(file.toFile());
        if (in.getInt("schema") != 1) throw new IOException("Unsupported Golda state schema; source preserved");
        try {
            ConfigurationSection list = in.getConfigurationSection("goldas");
            if (list != null) {
                if (list.getKeys(false).size() > 4096) throw new IllegalArgumentException("Too many Golda records");
                for (String key : list.getKeys(false)) {
                    ConfigurationSection s = Objects.requireNonNull(list.getConfigurationSection(key));
                    Golda g = new Golda(UUID.fromString(key), UUID.fromString(s.getString("world")),
                        s.getDouble("x"), s.getDouble("ground-y"), s.getDouble("z"), (float) s.getDouble("yaw"),
                        s.getDouble("height"), s.getDouble("amplitude"), s.getInt("rise-ticks"),
                        s.getInt("period-ticks"), s.getInt("batch"), s.getInt("limit"));
                    g.age = s.getLong("age"); g.clicks = s.getInt("clicks"); g.removing = s.getBoolean("removing");
                    if (s.contains("respondent")) g.respondent = UUID.fromString(s.getString("respondent"));
                    if (s.contains("token")) g.token = UUID.fromString(s.getString("token"));
                    if (g.age < 0 || g.age > 1_000_000_000_000L || g.clicks < 0 || g.clicks > g.limit
                        || (g.clicks == g.limit && !g.removing && (g.respondent == null || g.token == null)))
                        throw new IllegalArgumentException("Invalid reward/dialog state");
                    // A consumed answer cannot be replayed after a crash during its fade-out.
                    if (!g.removing) goldas.put(g.id, g);
                }
            }
            ConfigurationSection pending = in.getConfigurationSection("deaths");
            if (pending != null) for (String key : pending.getKeys(false))
                deaths.put(UUID.fromString(key), InventorySnapshot.read(Objects.requireNonNull(pending.getConfigurationSection(key))));
        } catch (RuntimeException ex) { throw new IOException("Invalid Golda state; source preserved", ex); }
    }

    void save(Map<UUID, Golda> goldas, Map<UUID, InventorySnapshot> deaths) throws IOException {
        YamlConfiguration out = new YamlConfiguration(); out.set("schema", 1);
        for (Golda g : goldas.values()) {
            ConfigurationSection s = out.createSection("goldas." + g.id);
            s.set("world", g.world.toString()); s.set("x", g.x); s.set("ground-y", g.groundY); s.set("z", g.z); s.set("yaw", g.yaw);
            s.set("height", g.height); s.set("amplitude", g.amplitude); s.set("rise-ticks", g.riseTicks); s.set("period-ticks", g.periodTicks);
            s.set("batch", g.batch); s.set("limit", g.limit); s.set("age", g.age); s.set("clicks", g.clicks); s.set("removing", g.removing);
            if (g.respondent != null) s.set("respondent", g.respondent.toString());
            if (g.token != null) s.set("token", g.token.toString());
        }
        deaths.forEach((id, snapshot) -> snapshot.write(out.createSection("deaths." + id)));
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), "golda-", ".tmp");
        try {
            Files.writeString(temp, out.saveToString(), StandardCharsets.UTF_8);
            try (var channel = java.nio.channels.FileChannel.open(temp, StandardOpenOption.WRITE)) { channel.force(true); }
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
