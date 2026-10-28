package dev.zbanya;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.block.data.*;
import org.bukkit.block.data.type.*;

/** Local front is NORTH (-Z). Every block is generated in code, no schematic. */
public final class Blueprint {
    public record Cell(int x,int y,int z){}
    public record Decor(int x,int y,int z,BlockFace face){}
    public static final int MIN_X=-6,MAX_X=6,MIN_Z=-8,MAX_Z=7,MIN_Y=-2,MAX_Y=8;
    public static final List<Decor> DECOR=List.of(new Decor(-4,3,1,BlockFace.EAST),new Decor(-4,3,3,BlockFace.EAST),new Decor(-4,3,5,BlockFace.EAST));
    public static Map<Cell,String> blocks(){
        var map=new LinkedHashMap<Cell,String>();
        for(int x=-5;x<=5;x++)for(int z=-5;z<=6;z++){
            put(map,x,-2,z,"stone_bricks");put(map,x,-1,z,"stone_bricks");put(map,x,0,z,(x+z)%7==0?"stripped_spruce_log[axis=x]":"spruce_planks");
        }
        // Covered porch, posts, railings, warm lamps and broad entry steps.
        for(int x=-3;x<=3;x++)for(int z=-7;z<=-6;z++){put(map,x,-1,z,"stone_bricks");put(map,x,0,z,"spruce_planks");}
        for(int x=-2;x<=2;x++){put(map,x,-2,-8,"stone_bricks");put(map,x,-1,-8,"stone_bricks");put(map,x,0,-8,"spruce_stairs[facing=south]");}
        for(int x:new int[]{-3,3}){for(int y=1;y<=3;y++)put(map,x,y,-6,"spruce_log[axis=y]");put(map,x,1,-7,"spruce_fence");put(map,x,2,-7,"lantern");}
        for(int x=-5;x<=5;x++)for(int z=-5;z<=6;z++){
            if(x==-5||x==5||z==-5||z==6)for(int y=1;y<=4;y++)put(map,x,y,z,(Math.abs(x)==5&&(z==-5||z==6))?"spruce_log[axis=y]":"spruce_planks");
            put(map,x,5,z,"spruce_planks");
        }
        for(int z:new int[]{-3,0,4})for(int x:new int[]{-5,5}){put(map,x,2,z,"glass");put(map,x,3,z,"glass");}
        for(int x:new int[]{-3,3})put(map,x,2,6,"glass");
        // Pitched roof: layered stair slopes and a solid gable, overhanging eaves.
        for(int z=-6;z<=7;z++){
            for(int x=-6;x<=6;x++){
                int y=5+(6-Math.abs(x))/2;
                put(map,x,y,z,x==0?"spruce_slab[type=bottom]":Math.abs(x)%2==1?"spruce_slab[type=top]":"spruce_stairs[facing="+(x<0?"east":"west")+"]");
                if(z==-5||z==6)for(int fill=5;fill<y;fill++)put(map,x,fill,z,"spruce_planks");
            }
        }
        for(int z=-7;z<=-6;z++)for(int x=-3;x<=3;x++)put(map,x,4,z,"spruce_slab[type=top]");
        door(map,0,1,-5); // Front door.
        for(int x=-4;x<=4;x++)for(int y=1;y<=4;y++)put(map,x,y,-1,"spruce_planks");
        door(map,1,1,-1); // Vestibule/steam room partition.
        // Rest room: bench, oak-topped table, two barrels, rugs and shelves.
        for(int x=-4;x<=-2;x++)put(map,x,1,-4,"spruce_stairs[facing=south]");
        put(map,-3,1,-3,"spruce_fence");put(map,-3,2,-3,"oak_pressure_plate");
        put(map,4,1,-4,"barrel[facing=up]");put(map,4,1,-3,"barrel[facing=up]");
        put(map,3,1,-4,"spruce_planks");put(map,3,2,-4,"lantern");
        put(map,0,1,-3,"brown_carpet");put(map,0,1,-2,"brown_carpet");
        // L-shaped two-level benches; aisle and safe enclosed stone heater.
        for(int z=1;z<=5;z++){
            put(map,-4,1,z,"spruce_planks");put(map,-4,2,z,"spruce_slab[type=bottom]");
            put(map,-3,1,z,"spruce_stairs[facing=west]");
        }
        for(int x=-2;x<=1;x++){put(map,x,1,5,"spruce_planks");put(map,x,2,5,"spruce_slab[type=bottom]");put(map,x,1,4,"spruce_stairs[facing=south]");}
        for(int x=2;x<=4;x++)for(int z=3;z<=5;z++)put(map,x,1,z,"stone_bricks");
        put(map,3,2,4,"magma_block");put(map,2,2,4,"cobbled_deepslate_slab[type=bottom]");put(map,4,2,4,"cobbled_deepslate_slab[type=bottom]");
        put(map,3,2,5,"cobbled_deepslate_wall");put(map,3,1,3,"blast_furnace[facing=north]");
        put(map,4,1,1,"water_cauldron[level=3]");put(map,3,1,1,"spruce_slab[type=bottom]");
        for(int y=3;y<=8;y++)put(map,4,y,5,"brick_wall");
        put(map,4,7,5,"bricks");put(map,4,8,5,"campfire[lit=true,signal_fire=false]");
        for(int[] p:new int[][]{{0,4,-3},{0,4,2}})put(map,p[0],p[1],p[2],"lantern[hanging=true]");
        for(int x=-2;x<=1;x++)for(int z=1;z<=3;z++)put(map,x,0,z,(x+z)%2==0?"dark_prismarine":"spruce_planks");
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
