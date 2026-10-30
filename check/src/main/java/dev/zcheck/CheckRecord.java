package dev.zcheck;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/** Immutable journal entry. UUID remains stable when the displayed name changes. */
record CheckRecord(UUID player, String name, UUID moderator, UUID world, double x, double y, double z, long started) {
    CheckRecord {
        if (player == null || world == null || name == null || !name.matches("[A-Za-z0-9_]{1,16}")) throw new IllegalArgumentException("Invalid identity");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || Math.abs(x)>30_000_000 || Math.abs(z)>30_000_000 || Math.abs(y)>4096 || started<=0) throw new IllegalArgumentException("Invalid location");
    }
    static CheckRecord create(org.bukkit.entity.Player player, UUID moderator) {
        Location loc=player.getLocation();
        return new CheckRecord(player.getUniqueId(),player.getName(),moderator,loc.getWorld().getUID(),loc.getX(),loc.getY(),loc.getZ(),System.currentTimeMillis());
    }
    Location at(float yaw,float pitch) {
        World w=Bukkit.getWorld(world);
        return w==null ? null : new Location(w,x,y,z,yaw,pitch);
    }
    CheckRecord move(Location loc) {return new CheckRecord(player,name,moderator,loc.getWorld().getUID(),loc.getX(),loc.getY(),loc.getZ(),started);}
}
