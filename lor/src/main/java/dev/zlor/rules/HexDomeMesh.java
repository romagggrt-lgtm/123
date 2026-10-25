package dev.zlor.rules;

import java.util.*;

/** Closed geodesic honeycomb: 630 hexagonal cells and twelve pentagonal caps. */
public final class HexDomeMesh {
    /** Each ribbon borders two neighbouring hexagons (or pentagons). */
    public record Segment(float[] quad,int first,int second,float firstY,float secondY){}
    private record V(double x,double y,double z) {
        V add(V p){return new V(x+p.x,y+p.y,z+p.z);}
        V sub(V p){return new V(x-p.x,y-p.y,z-p.z);}
        V mul(double f){return new V(x*f,y*f,z*f);}
        V unit(){return mul(1/Math.sqrt(x*x+y*y+z*z));}
        V cross(V p){return new V(y*p.z-z*p.y,z*p.x-x*p.z,x*p.y-y*p.x);}
    }
    private static long edge(int a,int b){return (long)Math.min(a,b)<<32 | (Math.max(a,b)&0xffffffffL);}
    private static int midpoint(List<V> v,Map<Long,Integer> mids,int a,int b){return mids.computeIfAbsent(edge(a,b),k->{v.add(v.get(a).add(v.get(b)).unit());return v.size()-1;});}
    public static List<float[]> grid(double radius){return segments(radius).stream().map(Segment::quad).toList();}
    public static List<Segment> segments(double radius){
        double t=(1+Math.sqrt(5))/2;
        var v=new ArrayList<V>();
        for(var p:new double[][]{{-1,t,0},{1,t,0},{-1,-t,0},{1,-t,0},{0,-1,t},{0,1,t},{0,-1,-t},{0,1,-t},{t,0,-1},{t,0,1},{-t,0,-1},{-t,0,1}})v.add(new V(p[0],p[1],p[2]).unit());
        List<int[]> faces=new ArrayList<>(List.of(new int[]{0,11,5},new int[]{0,5,1},new int[]{0,1,7},new int[]{0,7,10},new int[]{0,10,11},new int[]{1,5,9},new int[]{5,11,4},new int[]{11,10,2},new int[]{10,7,6},new int[]{7,1,8},new int[]{3,9,4},new int[]{3,4,2},new int[]{3,2,6},new int[]{3,6,8},new int[]{3,8,9},new int[]{4,9,5},new int[]{2,4,11},new int[]{6,2,10},new int[]{8,6,7},new int[]{9,8,1}));
        for(int level=0;level<3;level++){
            var mids=new HashMap<Long,Integer>();var next=new ArrayList<int[]>();
            for(var f:faces){int a=midpoint(v,mids,f[0],f[1]),b=midpoint(v,mids,f[1],f[2]),c=midpoint(v,mids,f[2],f[0]);next.add(new int[]{f[0],a,c});next.add(new int[]{f[1],b,a});next.add(new int[]{f[2],c,b});next.add(new int[]{a,b,c});}faces=next;
        }
        var neighbours=new TreeMap<Long,V>();var quads=new ArrayList<Segment>();
        for(var f:faces){var centre=v.get(f[0]).add(v.get(f[1])).add(v.get(f[2])).unit();
            for(int i=0;i<3;i++){long key=edge(f[i],f[(i+1)%3]);var other=neighbours.remove(key);if(other==null)neighbours.put(key,centre);else{int first=(int)(key>>>32),second=(int)key;quads.add(new Segment(ribbon(other,centre,radius),first,second,(float)v.get(first).y,(float)v.get(second).y));}}
        }
        if(!neighbours.isEmpty())throw new IllegalStateException("Open dome grid");
        return List.copyOf(quads);
    }
    private static float[] ribbon(V a,V b,double r){
        var tangent=b.sub(a);var normal=a.add(b).unit();var side=normal.cross(tangent).unit().mul(.055/r);
        V[] corners={a.sub(side).unit().mul(r),b.sub(side).unit().mul(r),b.add(side).unit().mul(r),a.add(side).unit().mul(r)};
        var q=new float[12];for(int i=0;i<4;i++){q[i*3]=(float)corners[i].x;q[i*3+1]=(float)corners[i].y;q[i*3+2]=(float)corners[i].z;}return q;
    }
    private HexDomeMesh(){}
}
