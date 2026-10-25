package dev.zgold;

import java.util.*;
import org.bukkit.configuration.ConfigurationSection;

record Settings(int riseTicks, double height, double amplitude, int periodTicks, int disappearTicks,
                double reach, int cooldown, int maxActive, double removeRadius, int saveTicks, int batch, int clicks,
                boolean packEnabled, String packUrl, String packHash, boolean packRequired, int packDelay,
                String packPrompt, Map<String, String> messages) {
    static Settings read(ConfigurationSection c, String bundledHash) {
        String url = c.getString("resource-pack.url", "").trim();
        String hash = c.getString("resource-pack.sha1", "bundled").trim();
        if (hash.equals("bundled")) hash = bundledHash;
        if (!url.isEmpty() && !url.startsWith("https://")) throw new IllegalArgumentException("resource-pack.url must use HTTPS");
        if (!hash.isEmpty() && !hash.matches("[a-fA-F0-9]{40}")) throw new IllegalArgumentException("resource-pack.sha1 must contain 40 hex digits");
        Map<String, String> messages = new HashMap<>();
        messages.put("inventory-changed","<light_purple>Инвентарь изменился во время сохранения. Повторите ответ.");
        ConfigurationSection msg = c.getConfigurationSection("messages");
        if (msg != null) for (String key : msg.getKeys(false)) messages.put(key, msg.getString(key, key));
        return new Settings(integer(c, "rise-duration-ticks", 50, 1, 400), number(c, "height-above-ground", 2.3, .5, 8),
            number(c, "hover-amplitude", .055, 0, .2), integer(c, "hover-period-ticks", 90, 20, 1200),
            integer(c, "disappear-duration-ticks", 16, 1, 100), number(c, "interaction-range", 5, 1, 6),
            integer(c, "click-cooldown-ticks", 6, 1, 100), integer(c, "max-active", 128, 1, 4096),
            number(c, "remove-radius", 16, 1, 128), integer(c, "save-interval-ticks", 200, 20, 1200),
            integer(c, "reward-per-click", 5, 1, 64), integer(c, "reward-clicks", 3, 1, 64),
            c.getBoolean("resource-pack.enabled", true), url, hash, c.getBoolean("resource-pack.required", true),
            c.getInt("resource-pack.delay-ticks", 40), c.getString("resource-pack.prompt", "Z_Server_Pack"), Map.copyOf(messages));
    }
    private static double number(ConfigurationSection c, String key, double fallback, double lo, double hi) {
        double value = c.getDouble("golda." + key, fallback);
        if (!Double.isFinite(value) || value < lo || value > hi) throw new IllegalArgumentException("Invalid golda." + key);
        return value;
    }
    private static int integer(ConfigurationSection c, String key, int fallback, int lo, int hi) {
        double value = number(c, key, fallback, lo, hi);
        if (value != Math.rint(value)) throw new IllegalArgumentException("golda." + key + " must be an integer");
        return (int) value;
    }
}
