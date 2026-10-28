package dev.zbanya;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;

public final class Sites {
    private static final Set<Material> GROUND=Set.of(Material.GRASS_BLOCK,Material.DIRT,Material.COARSE_DIRT,Material.ROOTED_DIRT,Material.PODZOL,Material.MYCELIUM,Material.MOSS_BLOCK,Material.SAND,Material.RED_SAND,Material.GRAVEL,Material.SNOW_BLOCK);
    public static boolean replaceable(Material m){return m.isAir()||m==Material.SHORT_GRASS||m==Material.TALL_GRASS||m==Material.FERN||m==Material.LARGE_FERN||m==Material.SNOW;}
    public static List<int[]> candidates(int min,int max){
        var list=new ArrayList<int[]>();for(int x=-max;x<=max;x++)for(int z=-max;z<=max;z++){double d=Math.hypot(x,z);if(d>=min&&d<=max)list.add(new int[]{x,z});}
        list.sort(java.util.Comparator.comparingDouble(a->a[0]*a[0]+a[1]*a[1]));return list;
    }
    public static Bath find(Player player,Collection<Bath> all,int min,int max,boolean overlap){
        int count=0;for(var c:candidates(min,max)){var b=probe(player.getLocation(),player.getUniqueId(),all,c,min,max,overlap);if(b!=null)return b;}return null;
    }
    public static Bath probe(Location at,UUID owner,Collection<Bath> all,int[] c,int min,int max,boolean overlap){
        World w=at.getWorld();if(w.getEnvironment()!=World.Environment.NORMAL)return null;
            int x=at.getBlockX()+c[0],z=at.getBlockZ()+c[1];if(!w.isChunkLoaded(x>>4,z>>4))return null;
            var bath=new Bath();bath.layout=1;bath.owner=owner;bath.world=w.getUID();bath.x=x;bath.z=z;bath.turn=Blueprint.rotation(at.getX()-x,at.getZ()-z);
            int surface=surface(w,x,z);if(surface==Integer.MIN_VALUE)return null;bath.y=surface+1;
            double distance=bath.location(w,0,1,0).distanceSquared(at);if(distance<min*min||distance>max*max)return null;
            var box=bath.bounds();if(bath.y-2<w.getMinHeight()||bath.y+Blueprint.MAX_Y+1>=w.getMaxHeight())return null;
            if(at.getX()>=box[0]-1&&at.getX()<=box[3]+2&&at.getZ()>=box[2]-1&&at.getZ()<=box[5]+2)return null;
            if(all.stream().anyMatch(b->bath.intersects(b,overlap?0:1)))return null;
            boolean valid=true;
            for(int px=box[0];px<=box[3]&&valid;px++)for(int pz=box[2];pz<=box[5]&&valid;pz++){
                if(!w.isChunkLoaded(px>>4,pz>>4)||!w.getWorldBorder().isInside(new Location(w,px+.5,bath.y,pz+.5))){valid=false;break;}
                int ground=surface(w,px,pz);if(ground<surface-1||ground>surface){valid=false;break;}
                for(int py=bath.y;py<=bath.y+Blueprint.MAX_Y;py++)if(!replaceable(w.getBlockAt(px,py,pz).getType())){valid=false;break;}
                // Never bury a container, mechanism or player structure below a thin lawn.
                for(int py=bath.y-2;py<bath.y;py++){var b=w.getBlockAt(px,py,pz);if(!GROUND.contains(b.getType())&&!b.getType().isAir()){valid=false;break;}}
            }
            if(!valid)return null;
            for(var e:Blueprint.blocks().entrySet()){
                var p=bath.point(e.getKey().x(),e.getKey().y(),e.getKey().z());var b=w.getBlockAt(p[0],p[1],p[2]);String after=Blueprint.orient(e.getValue(),bath.turn);
                if(!b.getBlockData().getAsString().equals(after))bath.cells.add(new Bath.Saved(p[0],p[1],p[2],b.getBlockData().getAsString(),after));
            }
            // Preserve plants from the interior/clearance as well.
            for(int px=box[0];px<=box[3];px++)for(int pz=box[2];pz<=box[5];pz++)for(int py=bath.y+1;py<=bath.y+Blueprint.MAX_Y;py++){
                final int ax=px,ay=py,az=pz;var b=w.getBlockAt(px,py,pz);if(!b.getType().isAir()&&replaceable(b.getType())&&bath.cells.stream().noneMatch(q->q.x()==ax&&q.y()==ay&&q.z()==az))bath.cells.add(new Bath.Saved(px,py,pz,b.getBlockData().getAsString(),"minecraft:air"));
            }
            if(w.getNearbyEntities(new Location(w,(box[0]+box[3]+1)/2d,bath.y+3,(box[2]+box[5]+1)/2d),(box[3]-box[0]+1)/2d+1,6,(box[5]-box[2]+1)/2d+1,e->true).isEmpty())return bath;
        return null;
    }
    private static int surface(World w,int x,int z){int y=w.getHighestBlockYAt(x,z);for(int i=0;i<3&&y>w.getMinHeight()&&replaceable(w.getBlockAt(x,y,z).getType());i++)y--;return GROUND.contains(w.getBlockAt(x,y,z).getType())?y:Integer.MIN_VALUE;}
}
