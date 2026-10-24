package dev.zpantera;

import java.util.UUID;
import org.bukkit.*;

/** The YAML record owns identity; Bukkit entity UUIDs are transient, never respawned twice. */
final class PanteraState {
    final UUID id;
    UUID world, owner;
    double x, y, z, health;
    float yaw;
    long expiresAt;
    PanteraState(UUID id, Location at, double health) {
        this.id=id; this.health=health; position(at);
    }
    void position(Location at) { if(at.getWorld()!=null) world=at.getWorld().getUID(); x=at.getX(); y=at.getY(); z=at.getZ(); yaw=at.getYaw(); }
    Location location() { World w=Bukkit.getWorld(world); return w==null ? null : new Location(w,x,y,z,yaw,0); }
    boolean tame(UUID player, long now, long lifetime) {
        if (owner!=null) return false;
        owner=player; expiresAt=Math.addExact(now,lifetime); return true;
    }
    boolean expired(long now) { return owner!=null && now>=expiresAt; }
}
