package dev.zbanya;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.*;

/** Compact single steam room; local front is NORTH. No lounge or large porch. */
public final class Blueprint {
    public record Cell(int x,int y,int z){}
    public record Decor(int x,int y,int z,BlockFace face){}
    public static final int MIN_X=-4,MAX_X=4,MIN_Z=-4,MAX_Z=4,MIN_Y=-2,MAX_Y=7;
    public static final List<Decor> DECOR=List.of(new Decor(-2,3,-1,BlockFace.EAST),new Decor(-2,3,1,BlockFace.EAST),new Decor(0,3,2,BlockFace.NORTH));
    public static final List<Decor> LEGACY_DECOR=List.of(new Decor(-4,3,1,BlockFace.EAST),new Decor(-4,3,3,BlockFace.EAST),new Decor(-4,3,5,BlockFace.EAST));
    public static Map<Cell,String> blocks(){
        var map=new LinkedHashMap<Cell,String>();
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){
            put(map,x,-2,z,"stone_bricks");put(map,x,-1,z,"stone_bricks");
            put(map,x,0,z,(x+z)%5==0?"stripped_spruce_log[axis=x]":"spruce_planks");
            if(Math.abs(x)==3||Math.abs(z)==3)for(int y=1;y<=3;y++)put(map,x,y,z,Math.abs(x)==3&&Math.abs(z)==3?"spruce_log[axis=y]":"spruce_planks");
            // Half-block ceiling leaves standing headroom over the upper bench.
            put(map,x,4,z,"spruce_slab[type=top]");
        }
        door(map,0,1,-3);
        for(int x=-1;x<=1;x++){put(map,x,-2,-4,"stone_bricks");put(map,x,-1,-4,"stone_bricks");put(map,x,0,-4,"spruce_stairs[facing=south]");}
        for(int x:new int[]{-2,2}){put(map,x,0,-4,"stone_bricks");put(map,x,1,-4,"spruce_fence");put(map,x,2,-4,"lantern");}
        put(map,-3,2,0,"glass");put(map,3,2,-1,"glass");put(map,-1,2,3,"glass");
        for(int z=-4;z<=4;z++)for(int x=-4;x<=4;x++){
            int y=4+(4-Math.abs(x))/2;
            put(map,x,y,z,x==0?"spruce_slab[type=bottom]":Math.abs(x)%2==1?"spruce_slab[type=top]":"spruce_stairs[facing="+(x<0?"east":"west")+"]");
            if(Math.abs(z)==3)for(int fill=4;fill<y;fill++)put(map,x,fill,z,"spruce_planks");
        }
        // Two tiers along two walls, with a clear central aisle.
        for(int z=-1;z<=2;z++){put(map,-2,1,z,"spruce_planks");put(map,-2,2,z,"spruce_slab[type=bottom]");}
        for(int z=-1;z<=1;z++)put(map,-1,1,z,"spruce_stairs[facing=west]");
        for(int x=-1;x<=0;x++){put(map,x,1,2,"spruce_planks");put(map,x,2,2,"spruce_slab[type=bottom]");}
        put(map,0,1,1,"spruce_stairs[facing=south]");
        for(int x=1;x<=2;x++)for(int z=1;z<=2;z++)put(map,x,1,z,"stone_bricks");
        put(map,1,1,1,"blast_furnace[facing=north]");put(map,1,2,2,"magma_block");put(map,2,2,2,"chiseled_stone_bricks");
        put(map,2,1,0,"water_cauldron[level=3]");
        for(int y=3;y<=5;y++)put(map,2,y,2,"brick_wall");
        put(map,2,6,2,"bricks");put(map,2,7,2,"campfire[lit=true,signal_fire=false]");
        // The rare food stays in a small barrel by the entrance.
        put(map,2,1,-2,"barrel[facing=up]");
        put(map,0,4,0,"spruce_planks");put(map,2,4,-2,"spruce_planks");
        put(map,0,3,0,"lantern[hanging=true]");put(map,2,3,-2,"lantern[hanging=true]");
        put(map,0,0,0,"dark_prismarine");put(map,1,0,0,"dark_prismarine");
        return Collections.unmodifiableMap(map);
    }
    private static void put(Map<Cell,String> m,int x,int y,int z,String block){m.put(new Cell(x,y,z),"minecraft:"+block);}
    private static void door(Map<Cell,String> m,int x,int y,int z){put(m,x,y,z,"spruce_door[facing=north,half=lower,hinge=left]");put(m,x,y+1,z,"spruce_door[facing=north,half=upper,hinge=left]");}
    public static int[] rotate(int x,int z,int turns){return switch(Math.floorMod(turns,4)){case 0->new int[]{x,z};case 1->new int[]{-z,x};case 2->new int[]{-x,-z};default->new int[]{z,-x};};}
    public static int rotation(double dx,double dz){return Math.abs(dx)>Math.abs(dz)?(dx>0?1:3):(dz>0?2:0);}
    public static BlockFace facing(BlockFace face,int turn){var v=rotate(face.getModX(),face.getModZ(),turn);return Arrays.stream(new BlockFace[]{BlockFace.NORTH,BlockFace.EAST,BlockFace.SOUTH,BlockFace.WEST}).filter(f->f.getModX()==v[0]&&f.getModZ()==v[1]).findFirst().orElse(face);}
    public static String orient(String raw,int turn){
        BlockData d=Bukkit.createBlockData(raw);
        if(d instanceof Directional f)f.setFacing(facing(f.getFacing(),turn));
        if(d instanceof Orientable axis&&turn%2!=0&&axis.getAxis()!=Axis.Y)axis.setAxis(axis.getAxis()==Axis.X?Axis.Z:Axis.X);
        if(d instanceof MultipleFacing faces){var old=new HashSet<>(faces.getFaces());for(BlockFace f:faces.getAllowedFaces())faces.setFace(f,false);for(BlockFace f:old)faces.setFace(facing(f,turn),true);}
        return d.getAsString();
    }
}
