package dev.ztest;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

public record Point(String world,int x,int y,int z) {
    public Point { if(world==null||world.isBlank()||Math.abs((long)x)>29999984||Math.abs((long)z)>29999984||y < -2048||y>2048)throw new IllegalArgumentException("Некорректная точка"); }
    public static Point of(Block b){return new Point(b.getWorld().getName(),b.getX(),b.getY(),b.getZ());}
    public Location center(){World w=Bukkit.getWorld(world);return w==null?null:new Location(w,x+.5,y,z+.5);}
    public Map<String,Object> save(){var m=new LinkedHashMap<String,Object>();m.put("world",world);m.put("x",x);m.put("y",y);m.put("z",z);return m;}
    public static Point read(Object value) {
        if(!(value instanceof Map<?,?> m)||!(m.get("world") instanceof String w)||!(m.get("x") instanceof Number x)||!(m.get("y") instanceof Number y)||!(m.get("z") instanceof Number z))throw new IllegalArgumentException("Некорректные координаты в state.yml");
        return new Point(w,x.intValue(),y.intValue(),z.intValue());
    }
    public double distanceSquared(Location l){if(l.getWorld()==null||!world.equals(l.getWorld().getName()))return Double.POSITIVE_INFINITY;double dx=x+.5-l.getX(),dy=y-l.getY(),dz=z+.5-l.getZ();return dx*dx+dy*dy+dz*dz;}
}
