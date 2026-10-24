package dev.zpantera;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;

/** Conservative dry-land teleport check. Never loads chunks and checks the whole hitbox. */
final class SafePosition {
    private static final Set<Material> HAZARDS=EnumSet.of(Material.LAVA,Material.WATER,Material.FIRE,Material.SOUL_FIRE,
        Material.CACTUS,Material.MAGMA_BLOCK,Material.CAMPFIRE,Material.SOUL_CAMPFIRE,Material.SWEET_BERRY_BUSH,
        Material.POWDER_SNOW,Material.WITHER_ROSE,Material.NETHER_PORTAL,Material.END_PORTAL);
    static boolean loaded(Location p) { return p!=null&&p.getWorld()!=null&&p.getWorld().isChunkLoaded(p.getBlockX()>>4,p.getBlockZ()>>4); }
    static boolean safe(Location p) {
        if (!loaded(p)) return false;
        World w=p.getWorld();
        WorldBorder border=w.getWorldBorder();Location center=border.getCenter();double half=border.getSize()/2;
        if(Math.abs(p.getX()-center.getX())+.46>=half||Math.abs(p.getZ()-center.getZ())+.46>=half
            ||p.getY()<w.getMinHeight()+1||p.getY()+1.5>=w.getMaxHeight())return false;
        for (double dx:new double[]{-.46,0,.46}) for (double dz:new double[]{-.46,0,.46}) {
            int x=(int)Math.floor(p.getX()+dx), z=(int)Math.floor(p.getZ()+dz);
            if (!w.isChunkLoaded(x>>4,z>>4)) return false;
            Block floor=w.getBlockAt(x,(int)Math.floor(p.getY()-.01),z);
            if (HAZARDS.contains(floor.getType())||!floor.getType().isSolid()
                ||Math.abs(floor.getBoundingBox().getMaxY()-p.getY())>.015) return false;
            for(int y=(int)Math.floor(p.getY()+.01);y<=(int)Math.floor(p.getY()+1.45);y++) {
                Block b=w.getBlockAt(x,y,z);
                if (HAZARDS.contains(b.getType())||b.isLiquid()||!b.isPassable()) return false;
            }
        }
        return true;
    }
    static Location nearby(Location center) {
        // Independent of the owner's yaw. Prefer rings 2–3 blocks from the player.
        for(int r:new int[]{2,3,4}) for(int dy:new int[]{0,1,-1,2,-2})
            for(int i=0;i<16;i++) {
                double a=i*Math.PI/8;
                Location p=new Location(center.getWorld(),Math.floor(center.getX()+Math.cos(a)*r)+.5,
                    center.getBlockY()+dy,Math.floor(center.getZ()+Math.sin(a)*r)+.5,center.getYaw(),0);
                if (safe(p)) return p;
            }
        return null;
    }
}
