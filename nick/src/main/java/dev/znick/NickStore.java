package dev.znick;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

class NickStore {
    private final Path file;
    NickStore(Path file) { this.file = file; }

    Map<UUID, Nick> load(int max, NamedTextColor color) throws IOException, InvalidConfigurationException {
        if (!Files.exists(file)) return Map.of();
        if (Files.size(file) > 16 * 1024 * 1024) throw new IOException("nicknames.yml exceeds 16 MiB");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file.toFile());
        if (yaml.getInt("schema", -1) != 1) throw new IOException("Unsupported nicknames.yml schema");
        var section = yaml.getConfigurationSection("players");
        if (section == null) throw new IOException("Missing players section");
        Map<UUID, Nick> result = new LinkedHashMap<>();
        Set<Integer> aliases = new HashSet<>();
        for (String key : section.getKeys(false)) {
            try {
                var p = Objects.requireNonNull(section.getConfigurationSection(key));
                UUID id = UUID.fromString(key);
                String account = Objects.requireNonNull(p.getString("account"));
                if (!account.matches("[A-Za-z0-9_]{1,16}") || !p.isInt("wire")) throw new IllegalArgumentException("Invalid account/wire");
                Nick n = Nick.parse(id, account, p.getString("nickname"), p.getInt("wire"), max, color);
                if (!aliases.add(n.wire())) throw new IllegalArgumentException("Duplicate wire identity");
                result.put(id, n);
            } catch (RuntimeException bad) { throw new IOException("Invalid nickname record: " + key, bad); }
        }
        return Map.copyOf(result);
    }

    void save(Map<UUID, Nick> values) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema", 1);
        yaml.createSection("players");
        values.values().stream().sorted(Comparator.comparing(n -> n.id().toString())).forEach(n -> {
            String p = "players." + n.id() + ".";
            yaml.set(p + "account", n.account());
            yaml.set(p + "nickname", n.raw());
            yaml.set(p + "wire", n.wire());
        });
        byte[] data = yaml.saveToString().getBytes(StandardCharsets.UTF_8);
        if (data.length > 16 * 1024 * 1024) throw new IOException("Nickname database exceeds 16 MiB");
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), "nicknames-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(data);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            // Do not replace a durable journal with a non-atomic partial write.
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(temp); }
    }
}
