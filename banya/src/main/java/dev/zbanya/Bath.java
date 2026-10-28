package dev.zbanya;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;

public final class Bath {
    public record Saved(int x,int y,int z,String before,String after){}
    public int schema=1;
    public String name;
    public boolean areaCaptured,modified,rescueCaptured,rescueDelivered;
    public record Rescued(int x,int y,int z,int slot,String data){}
    public List<Rescued> rescued=new ArrayList<>();
    public static String canonical(String name){return java.text.Normalizer.normalize(name,java.text.Normalizer.Form.NFC).toLowerCase(Locale.ROOT);}
    public static boolean validName(String name){return name!=null&&name.matches("[\\p{L}\\p{N}_-]{1,32}");}
    /** Old journals did not record empty interior cells; their original clearance was air. */
    public boolean captureLegacyClearance(){
        if(areaCaptured)return false;
        var known=new HashSet<String>();for(var c:cells)known.add(c.x+":"+c.y+":"+c.z);
        var box=bounds();for(int px=box[0];px<=box[3];px++)for(int py=y;py<=box[4];py++)for(int pz=box[2];pz<=box[5];pz++)if(!known.contains(px+":"+py+":"+pz))cells.add(new Saved(px,py,pz,"minecraft:air","minecraft:air"));
        areaCaptured=true;return true;
    }
    public boolean operational(World w){if(!modified)return true;int[] p=layout==0?point(3,2,4):point(1,2,2);return w!=null&&w.isChunkLoaded(p[0]>>4,p[2]>>4)&&w.getBlockAt(p[0],p[1],p[2]).getType()==Material.MAGMA_BLOCK;}

    /** Missing in 1.0.0 journals: zero must keep the original large layout. */
    public int layout=0;
    public UUID id=UUID.randomUUID(),owner,world;
    public int x,y,z,turn;
    public String phase="BUILDING";
    public boolean hasFood,restore=true;
    public int foodAmount=1;
    public List<Saved> cells=new ArrayList<>();
    public int[] point(int lx,int ly,int lz){var r=Blueprint.rotate(lx,lz,turn);return new int[]{x+r[0],y+ly,z+r[1]};}
    public Location location(World w,int lx,int ly,int lz){var p=point(lx,ly,lz);return new Location(w,p[0]+.5,p[1]+.5,p[2]+.5);}
    public int[] local(double wx,double wz){return Blueprint.rotate((int)Math.floor(wx)-x,(int)Math.floor(wz)-z,4-turn);}
    public int[] extents(){return layout==0?new int[]{-6,-2,-8,6,8,7}:new int[]{Blueprint.MIN_X,Blueprint.MIN_Y,Blueprint.MIN_Z,Blueprint.MAX_X,Blueprint.MAX_Y,Blueprint.MAX_Z};}
    public List<Blueprint.Decor> decor(){return layout==0?Blueprint.LEGACY_DECOR:Blueprint.DECOR;}
    public int[] foodPoint(){return layout==0?point(4,1,-4):point(2,1,-2);}
    public Location steam(World w){return layout==0?location(w,3,3,4):location(w,1,3,2);}
    public boolean contains(Location p){if(p.getWorld()==null||!world.equals(p.getWorld().getUID()))return false;var q=local(p.getX(),p.getZ());return layout==0?q[0]>=-4&&q[0]<=4&&q[1]>=-4&&q[1]<=5&&p.getY()>=y+.5&&p.getY()<y+5:q[0]>=-2&&q[0]<=2&&q[1]>=-2&&q[1]<=2&&p.getY()>=y+.5&&p.getY()<y+4.5;}
    public boolean region(Block b){if(!world.equals(b.getWorld().getUID()))return false;var q=local(b.getX(),b.getZ());var a=extents();return q[0]>=a[0]&&q[0]<=a[3]&&q[1]>=a[2]&&q[1]<=a[5]&&b.getY()>=y+a[1]&&b.getY()<=y+a[4];}
    public boolean intersects(Bath other,int margin){if(!world.equals(other.world))return false;var a=bounds();var b=other.bounds();return a[0]-margin<=b[3]&&a[3]+margin>=b[0]&&a[2]-margin<=b[5]&&a[5]+margin>=b[2]&&a[1]<=b[4]&&a[4]>=b[1];}
    public int[] bounds(){var e=extents();var a=point(e[0],e[1],e[2]);var b=point(e[3],e[4],e[5]);return new int[]{Math.min(a[0],b[0]),a[1],Math.min(a[2],b[2]),Math.max(a[0],b[0]),b[1],Math.max(a[2],b[2])};}
    public void validate(){
        if((name!=null&&!validName(name))||rescued==null||rescued.size()>32768||schema!=1||layout<0||layout>1||id==null||owner==null||world==null||turn<0||turn>3||!Set.of("BUILDING","ACTIVE","REMOVING").contains(phase)||cells==null||cells.isEmpty()||cells.size()>4000||foodAmount<1||foodAmount>16)throw new IllegalArgumentException("Неверный журнал бани");
        for(var item:rescued)if(item==null||item.data==null||item.data.length()>1_500_000||item.slot<0||item.slot>255)throw new IllegalArgumentException("Неверные сохранённые вещи");
        var seen=new HashSet<String>();var box=bounds();for(var c:cells){if(c==null||c.before==null||c.after==null||c.x<box[0]||c.x>box[3]||c.y<box[1]||c.y>box[4]||c.z<box[2]||c.z>box[5]||!seen.add(c.x+":"+c.y+":"+c.z))throw new IllegalArgumentException("Неверная ячейка журнала");Bukkit.createBlockData(c.before);Bukkit.createBlockData(c.after);}
    }
}
