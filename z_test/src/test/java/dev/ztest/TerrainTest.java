package dev.ztest;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TerrainTest {
    @Test void treesAreDeterministicAndStayInTheirGridCells(){
        int count=0;for(int x=-30;x<=30;x++)for(int z=-30;z<=30;z++){
            var a=Terrain.tree(987654321L,x,z);assertEquals(a,Terrain.tree(987654321L,x,z));if(a==null)continue;count++;
            assertEquals(x,Math.floorDiv(a.x(),Terrain.TREE_CELL));assertEquals(z,Math.floorDiv(a.z(),Terrain.TREE_CELL));assertTrue(a.height()>=4&&a.height()<=6);
            assertFalse(Math.abs(a.x())<10&&Math.abs(a.z())<10);
        }assertTrue(count>1000);
    }
    @Test void sunAndLightingRemainContinuousAcrossNegativeChunks(){
        for(int x=-300;x<300;x+=7)for(int z=-300;z<300;z+=11){assertEquals(Terrain.sun(x,z),Terrain.sun(x+256,z-256));assertEquals(Terrain.light(x,z),Terrain.light(x+9,z-9));}
        assertTrue(Terrain.sun(0,0));assertTrue(Terrain.sun(256,-256));assertFalse(Terrain.sun(128,128));
    }
}
