package dev.zlor.rules;

import java.util.*;

/** External sphere patches, clipped by radical planes of equal-radius neighbours. */
public final class SphereUnion {
    public record Point(double x,double y,double z){
        public Point subtract(Point p){return new Point(x-p.x,y-p.y,z-p.z);}
        public double dot(Point p){return x*p.x+y*p.y+z*p.z;}
        Point cross(Point p){return new Point(y*p.z-z*p.y,z*p.x-x*p.z,x*p.y-y*p.x);}
        public double length(){return Math.sqrt(dot(this));}
        Point radius(double r){double k=r/length();return new Point(x*k,y*k,z*k);}
        Point lerp(Point p,double t){return new Point(x+(p.x-x)*t,y+(p.y-y)*t,z+(p.z-z)*t);}
    }
    public record Mesh(Point centre,List<float[]> surface,List<float[]> grid,List<HexDomeMesh.Segment> segments){}
    private record Plane(Point direction,double limit) {double value(Point p){return p.dot(direction)-limit;}}
    private static final List<float[]> SURFACE=SphereMesh.surface();
    private SphereUnion(){}
    public static List<Mesh> build(List<Point> input){
        var centres=input.stream().distinct().toList();var result=new ArrayList<Mesh>();
        for(var centre:centres){var neighbours=centres.stream().filter(p->!p.equals(centre)).map(p->p.subtract(centre)).filter(p->p.dot(p)<10_000).toList();
            var animated=segments(neighbours);
            result.add(new Mesh(centre,patches(SURFACE,neighbours,50),animated.stream().map(HexDomeMesh.Segment::quad).toList(),animated));
        }
        return List.copyOf(result);
    }
    private static List<HexDomeMesh.Segment> segments(List<Point> neighbours){
        if(neighbours.isEmpty())return SphereMesh.segments();
        var result=new ArrayList<HexDomeMesh.Segment>();
        var planes=planes(neighbours,50.015);
        for(var source:SphereMesh.segments()){
            var clipped=new ArrayList<float[]>();append(clipped,source.quad(),planes,50.015);
            for(var quad:clipped)
                result.add(new HexDomeMesh.Segment(quad,source.first(),source.second(),source.firstY(),source.secondY()));
        }
        return List.copyOf(result);
    }
    private static List<Plane> planes(List<Point> neighbours,double radius){
        return neighbours.stream().map(d->new Plane(d,(d.dot(d)+radius*radius-2500)/2)).toList();
    }
    private static List<float[]> patches(List<float[]> original,List<Point> neighbours,double radius){
        if(neighbours.isEmpty())return original;
        var planes=planes(neighbours,radius);
        var output=new ArrayList<float[]>();
        for(var quad:original)append(output,quad,planes,radius);
        return List.copyOf(output);
    }
    private static void append(List<float[]> output,float[] quad,List<Plane> planes,double radius){
        var poly=new ArrayList<Point>();for(int i=0;i<12;i+=3)poly.add(new Point(quad[i],quad[i+1],quad[i+2]).radius(radius));
        var visited=new ArrayList<Plane>();
        for(var plane:planes){poly=clip(poly,plane,radius,visited);visited.add(plane);if(poly.isEmpty())break;}
        if(poly.size()==4)output.add(quad(poly.get(0),poly.get(1),poly.get(2),poly.get(3)));
        else for(int i=1;i+1<poly.size();i++)output.add(quad(poly.get(0),poly.get(i),poly.get(i+1),poly.get(i+1)));
    }
    private static ArrayList<Point> clip(List<Point> polygon,Plane plane,double radius,List<Plane> visited){
        var out=new ArrayList<Point>();if(polygon.isEmpty())return out;var a=polygon.getLast();boolean outsideA=plane.value(a)<=1e-8;
        for(var b:polygon){boolean outsideB=plane.value(b)<=1e-8;if(outsideA!=outsideB)out.add(intersection(a,b,plane,radius,visited));if(outsideB)out.add(b);a=b;outsideA=outsideB;}
        return out;
    }
    private static Point intersection(Point a,Point b,Plane plane,double radius,List<Plane> visited){
        // A previous cut creates a small-circle edge, not a great-circle edge.
        // Preserve its plane when intersecting a third sphere; normalising a chord
        // would otherwise move the new vertex back inside an already clipped dome.
        for(var previous:visited)if(Math.abs(previous.value(a))<1e-5&&Math.abs(previous.value(b))<1e-5){
            var u=plane.direction;var v=previous.direction;
            double uu=u.dot(u),vv=v.dot(v),uv=u.dot(v),det=uu*vv-uv*uv;
            if(det<1e-10)continue;
            double alpha=(plane.limit*vv-previous.limit*uv)/det,beta=(previous.limit*uu-plane.limit*uv)/det;
            var base=new Point(alpha*u.x+beta*v.x,alpha*u.y+beta*v.y,alpha*u.z+beta*v.z);
            double remaining=radius*radius-base.dot(base);if(remaining<0)continue;
            var offset=u.cross(v).radius(Math.sqrt(remaining));
            var first=new Point(base.x+offset.x,base.y+offset.y,base.z+offset.z);
            var second=new Point(base.x-offset.x,base.y-offset.y,base.z-offset.z);
            var middle=a.lerp(b,.5);
            return first.subtract(middle).length()<second.subtract(middle).length()?first:second;
        }
        double low=0,high=1;boolean start=plane.value(a)<=0;
        for(int i=0;i<30;i++){double t=(low+high)/2;var p=a.lerp(b,t).radius(radius);if((plane.value(p)<=0)==start)low=t;else high=t;}
        return a.lerp(b,(low+high)/2).radius(radius);
    }
    private static float[] quad(Point a,Point b,Point c,Point d){return new float[]{(float)a.x,(float)a.y,(float)a.z,(float)b.x,(float)b.y,(float)b.z,(float)c.x,(float)c.y,(float)c.z,(float)d.x,(float)d.y,(float)d.z};}
}
