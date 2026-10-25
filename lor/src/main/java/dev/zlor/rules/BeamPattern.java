package dev.zlor.rules;

import java.util.*;

/** Shared, fixed sixfold lattice. Units and radii are BLOCKS, never diameters. */
public final class BeamPattern {
    public record Column(double x,double z,double radius,int color,int edge) {}
    public static final double SPACING=18.5, ARENA_RADIUS=50;
    public static final List<Column> COLUMNS=create();
    private BeamPattern(){}
    private static List<Column> create(){
        var points=new LinkedHashMap<String,Column>();
        for(int q=-5;q<=5;q++)for(int r=-5;r<=5;r++){
            add(points,q,r,5);add(points,q+1d/3,r+1d/3,3);add(points,q+2d/3,r+2d/3,3);
            add(points,q+.5,r,1.5);add(points,q,r+.5,1.5);add(points,q+.5,r-.5,1.5);
        }
        return points.values().stream().sorted(Comparator.comparingDouble(Column::radius).reversed().thenComparingDouble(Column::z).thenComparingDouble(Column::x)).toList();
    }
    private static void add(Map<String,Column> points,double q,double r,double radius){
        double x=SPACING*(q+r/2),z=SPACING*Math.sqrt(3)/2*r;
        if(Math.hypot(x,z)+radius>ARENA_RADIUS)return;
        x=Math.round(x*1e6)/1e6;z=Math.round(z*1e6)/1e6;
        int color=radius==5?0x35134F:radius==3?0x704391:0xB491D3;
        int edge=radius==5?0x804BAA:radius==3?0xB17CD5:0xDFC7F0;
        points.put(x+":"+z,new Column(x,z,radius,color,edge));
    }
    /** Circle vs player's horizontal bounding rectangle, so glow never expands damage. */
    public static boolean hits(double minX,double minZ,double maxX,double maxZ,double originX,double originZ){
        for(var c:COLUMNS){double x=originX+c.x,z=originZ+c.z;
            double dx=x-Math.clamp(x,minX,maxX),dz=z-Math.clamp(z,minZ,maxZ);
            if(dx*dx+dz*dz<=c.radius*c.radius)return true;
        }return false;
    }
}
