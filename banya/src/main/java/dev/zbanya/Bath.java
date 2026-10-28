package dev.zbanya;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;

public final class Bath {
    public record Saved(int x,int y,int z,String before,String after){}
    public int schema=1;
    public UUID id=UUID.randomUUID(),owner,world;
    public int x,y,z,turn;
    public String phase="BUILDING";
    public boolean hasFood,restore=true;
    public int foodAmount=1;
    public List<Saved> cells=new ArrayList<>();
    public int[] point(int lx,int ly,int lz){var r=Blueprint.rotate(lx,lz,turn);return new int[]{x+r[0],y+ly,z+r[1]};}
    public Location location(World w,int lx,int ly,int lz){var p=point(lx,ly,lz);return new Location(w,p[0]+.5,p[1]+.5,p[2]+.5);}
    public int[] local(double wx,double wz){return Blueprint.rotate((int)Math.floor(wx)-x,(int)Math.floor(wz)-z,4-turn);}
    public boolean contains(Location p){if(!world.equals(p.getWorld().getUID()))return false;var q=local(p.getX(),p.getZ());return q[0]>=-4&&q[0]<=4&&q[1]>=-4&&q[1]<=5&&p.getY()>=y+.5&&p.getY()<y+5;}
    public boolean region(Block b){if(!world.equals(b.getWorld().getUID()))return false;var q=local(b.getX(),b.getZ());return q[0]>=Blueprint.MIN_X&&q[0]<=Blueprint.MAX_X&&q[1]>=Blueprint.MIN_Z&&q[1]<=Blueprint.MAX_Z&&b.getY()>=y+Blueprint.MIN_Y&&b.getY()<=y+Blueprint.MAX_Y;}
    public boolean intersects(Bath other,int margin){if(!world.equals(other.world))return false;var a=bounds();var b=other.bounds();return a[0]-margin<=b[3]&&a[3]+margin>=b[0]&&a[2]-margin<=b[5]&&a[5]+margin>=b[2]&&a[1]<=b[4]&&a[4]>=b[1];}
    public int[] bounds(){var a=point(Blueprint.MIN_X,Blueprint.MIN_Y,Blueprint.MIN_Z);var b=point(Blueprint.MAX_X,Blueprint.MAX_Y,Blueprint.MAX_Z);return new int[]{Math.min(a[0],b[0]),a[1],Math.min(a[2],b[2]),Math.max(a[0],b[0]),b[1],Math.max(a[2],b[2])};}
    public void validate(){
        if(schema!=1||id==null||owner==null||world==null||turn<0||turn>3||!Set.of("BUILDING","ACTIVE","REMOVING").contains(phase)||cells==null||cells.isEmpty()||cells.size()>4000||foodAmount<1||foodAmount>16)throw new IllegalArgumentException("Неверный журнал бани");
        var seen=new HashSet<String>();var box=bounds();for(var c:cells){if(c==null||c.before==null||c.after==null||c.x<box[0]||c.x>box[3]||c.y<box[1]||c.y>box[4]||c.z<box[2]||c.z>box[5]||!seen.add(c.x+":"+c.y+":"+c.z))throw new IllegalArgumentException("Неверная ячейка журнала");Bukkit.createBlockData(c.before);Bukkit.createBlockData(c.after);}
    }
}
