package dev.ztest;

import java.util.Random;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

public final class PurgatoryGenerator extends ChunkGenerator {
    @Override public void generateNoise(WorldInfo info, Random random, int cx, int cz, ChunkData data) {
        int min=Math.max(info.getMinHeight(),data.getMinHeight()), top=Math.min(info.getMaxHeight(),data.getMaxHeight());
        if (min >= Terrain.GROUND || top <= Terrain.SKY) return;
        data.setRegion(0,min,0,16,min+1,16,Material.BEDROCK);
        if(min+1<60)data.setRegion(0,min+1,0,16,60,16,Material.GRAY_CONCRETE);
        data.setRegion(0,Math.max(min+1,60),0,16,Terrain.GROUND,16,Material.BROWN_CONCRETE);
        data.setRegion(0,Terrain.GROUND,0,16,Terrain.GROUND+1,16,Material.GREEN_CONCRETE);
        int baseX=cx*16,baseZ=cz*16;
        for(int x=0;x<16;x++) for(int z=0;z<16;z++) {
            int wx=baseX+x,wz=baseZ+z;
            data.setBlock(x,Terrain.SKY,z,Terrain.sun(wx,wz)?Material.YELLOW_CONCRETE:Material.LIGHT_BLUE_CONCRETE);
            if(Terrain.light(wx,wz))data.setBlock(x,Terrain.GROUND+1,z,Material.LIGHT);
        }
        int minCellX=Math.floorDiv(baseX-3,Terrain.TREE_CELL),maxCellX=Math.floorDiv(baseX+18,Terrain.TREE_CELL);
        int minCellZ=Math.floorDiv(baseZ-3,Terrain.TREE_CELL),maxCellZ=Math.floorDiv(baseZ+18,Terrain.TREE_CELL);
        for(int gx=minCellX;gx<=maxCellX;gx++)for(int gz=minCellZ;gz<=maxCellZ;gz++) {
            var tree=Terrain.tree(info.getSeed(),gx,gz);if(tree==null)continue;
            int lx=tree.x()-baseX,lz=tree.z()-baseZ;
            for(int y=1;y<=tree.height();y++)put(data,lx,Terrain.GROUND+y,lz,Material.BROWN_CONCRETE);
            int crown=Terrain.GROUND+tree.height();
            for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int dy=-1;dy<=1;dy++) {
                if(dx==0&&dz==0&&dy<=0)continue;
                if(Math.abs(dx)==2&&Math.abs(dz)==2)continue;
                if(dy==1&&(Math.abs(dx)==2||Math.abs(dz)==2))continue;
                put(data,lx+dx,crown+dy,lz+dz,Material.LIME_CONCRETE);
            }
        }
    }
    private static void put(ChunkData d,int x,int y,int z,Material m){if(x>=0&&x<16&&z>=0&&z<16&&y>=d.getMinHeight()&&y<d.getMaxHeight())d.setBlock(x,y,z,m);}
    @Override public Location getFixedSpawnLocation(World world,Random random){return new Location(world,.5,Terrain.GROUND+1,.5);}
    @Override public boolean canSpawn(World world,int x,int z){return true;}
    @Override public boolean shouldGenerateNoise(){return false;}
    @Override public boolean shouldGenerateSurface(){return false;}
    @Override public boolean shouldGenerateCaves(){return false;}
    @Override public boolean shouldGenerateDecorations(){return false;}
    @Override public boolean shouldGenerateMobs(){return false;}
    @Override public boolean shouldGenerateStructures(){return false;}
    @Override public boolean isParallelCapable(){return true;}
}
