package dev.zlor.rules;

import java.util.*;

/** Identical protection and boundary rules on Paper and Fabric; no world access. */
public final class Domes {
    public enum Kind { ABSORBER, LOOT, ENTRY }
    public record Sphere(double x,double y,double z,double radius,Kind kind) {
        public Sphere {if(!Double.isFinite(x+y+z+radius)||radius<=0||radius>128||kind==null)throw new IllegalArgumentException("Invalid dome");}
        public double distance2(double px,double py,double pz){return (px-x)*(px-x)+(py-y)*(py-y)+(pz-z)*(pz-z);}
        public boolean contains(double px,double py,double pz){return distance2(px,py,pz)<=radius*radius;}
    }
    public record Push(double x,double y,double z){public static final Push NONE=new Push(0,0,0);}
    public static double damageFactor(Collection<Sphere> spheres,double x,double y,double z){
        double factor=1;for(var s:spheres)if(s.contains(x,y,z)){if(s.kind!=Kind.LOOT)return 0;factor=.125;}return factor;
    }
    public static double flightInterval(double interval,boolean absorber){return Double.isFinite(interval)&&absorber?interval*4:interval;}
    public static Push push(Collection<Sphere> spheres,double x,double y,double z,double vx,double vy,double vz){
        // Only crossing outward. Never sum forces; another dome covering the next point removes the internal wall.
        for(var other:spheres)if(other.contains(x+vx*2,y+vy*2,z+vz*2))return Push.NONE;
        Sphere nearest=null;double gap=Double.MAX_VALUE;
        for(var s:spheres){double distance=Math.sqrt(s.distance2(x,y,z)),delta=Math.abs(distance-s.radius);
            if(delta<=.65&&distance>1e-6&&((x-s.x)*vx+(y-s.y)*vy+(z-s.z)*vz)>0&&delta<gap){gap=delta;nearest=s;}}
        if(nearest==null)return Push.NONE;double d=Math.sqrt(nearest.distance2(x,y,z)),strength=.055;
        return new Push((nearest.x-x)/d*strength,(nearest.y-y)/d*strength,(nearest.z-z)/d*strength);
    }
    /** Grid broad phase: queries allocate nothing and do not iterate every device. */
    public static final class Index {
        private final Map<Long,List<Sphere>> cells=new HashMap<>();
        private static int cell(double v){return (int)Math.floor(v/64);}
        private static long key(int x,int z){return (long)x<<32|(z&0xffffffffL);}
        public Index(Collection<Sphere> spheres){for(var s:spheres)for(int x=cell(s.x-s.radius);x<=cell(s.x+s.radius);x++)for(int z=cell(s.z-s.radius);z<=cell(s.z+s.radius);z++)cells.computeIfAbsent(key(x,z),k->new ArrayList<>()).add(s);}
        public List<Sphere> near(double x,double z){return cells.getOrDefault(key(cell(x),cell(z)),List.of());}
    }
    private Domes(){}
}
