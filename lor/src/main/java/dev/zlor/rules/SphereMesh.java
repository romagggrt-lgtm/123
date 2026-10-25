package dev.zlor.rules;

import java.util.*;

/** Full double-sided sphere: RenderTypes.debugQuads disables culling and depth writes. */
public final class SphereMesh {
    private static final List<HexDomeMesh.Segment> SEGMENTS=HexDomeMesh.segments(50+.015);
    private static final List<float[]> GRID=SEGMENTS.stream().map(HexDomeMesh.Segment::quad).toList();
    private SphereMesh(){}
    public static List<float[]> surface(){
        var quads=new ArrayList<float[]>();
        for(int latitude=0;latitude<24;latitude++)for(int longitude=0;longitude<48;longitude++)quads.add(quad(-Math.PI/2+latitude*Math.PI/24,-Math.PI/2+(latitude+1)*Math.PI/24,longitude*Math.PI/24,(longitude+1)*Math.PI/24,50));
        return List.copyOf(quads);
    }
    public static List<float[]> grid(){return GRID;}
    public static List<HexDomeMesh.Segment> segments(){return SEGMENTS;}
    private static float[] quad(double a,double b,double c,double d,double r){
        var q=new float[12];point(q,0,a,c,r);point(q,3,b,c,r);point(q,6,b,d,r);point(q,9,a,d,r);return q;
    }
    private static void point(float[] q,int offset,double latitude,double longitude,double r){q[offset]=(float)(Math.cos(latitude)*Math.cos(longitude)*r);q[offset+1]=(float)(Math.sin(latitude)*r);q[offset+2]=(float)(Math.cos(latitude)*Math.sin(longitude)*r);}
}
