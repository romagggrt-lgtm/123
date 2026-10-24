package dev.zpantera;

import org.bukkit.configuration.file.FileConfiguration;
import net.kyori.adventure.text.format.TextColor;

record Settings(double health, boolean invulnerable, double damageMultiplier, long lifetimeMillis,
                int maxCount, long startupGraceMillis, boolean nameVisible, String name, TextColor nameColor,
                double stopDistance, double startDistance, double walkSpeed, double runSpeed,
                double teleportDistance, int stuckTicks, float volume, int soundRadius,
                long cooldownMillis, int heartRadius, int heartTicks, int burstHearts, int idleHearts) {
    static Settings read(FileConfiguration c) {
        double stop = number(c, "follow.stop-distance", 2.3, 1.8, 5);
        TextColor color = TextColor.fromHexString(c.getString("pantera.name-color", "#E9A9E9"));
        return new Settings(number(c,"pantera.health",30,1,1024), c.getBoolean("pantera.invulnerable",false),
            number(c,"pantera.damage-multiplier",.25,0,1), (long)(number(c,"pantera.lifetime-seconds",300,1,86400)*1000),
            (int)number(c,"pantera.max-count",32,1,128), (long)(number(c,"pantera.startup-owner-grace-seconds",60,0,120)*1000), c.getBoolean("pantera.name-visible",true),
            c.getString("pantera.name","❤ Kazan Pantera ❤"), color == null ? TextColor.color(0xE9A9E9) : color,
            stop, number(c,"follow.start-distance",3,stop+.2,8), number(c,"follow.walk-speed",1,.2,2),
            number(c,"follow.run-speed",1.55,.2,3), number(c,"follow.teleport-distance",24,12,80),
            (int)(number(c,"follow.stuck-seconds",8,3,60)*20), (float)number(c,"effects.sound-volume",1,.01,1),
            (int)number(c,"effects.sound-radius",24,8,48), (long)(number(c,"effects.sound-cooldown-seconds",2,2,60)*1000),
            (int)number(c,"effects.heart-radius",24,8,48), (int)number(c,"effects.heart-interval-ticks",50,40,200),
            (int)number(c,"effects.burst-hearts",28,10,64), (int)number(c,"effects.idle-hearts",5,1,12));
    }
    private static double number(FileConfiguration c, String key, double fallback, double lo, double hi) {
        double n = c.getDouble(key, fallback);
        return Double.isFinite(n) ? Math.max(lo,Math.min(hi,n)) : fallback;
    }
}
