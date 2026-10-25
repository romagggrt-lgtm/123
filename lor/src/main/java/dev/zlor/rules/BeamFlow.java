package dev.zlor.rules;

/** Deterministic render-time flow. No entity spawning and no dependence on frame count. */
public final class BeamFlow {
    private BeamFlow(){}
    public static double smooth(double value){double t=Math.clamp(value,0,1);return t*t*(3-2*t);}
    public static double envelope(double progress){return smooth(progress/.12)*smooth((1-progress)/.18);}
    public static double height(double bottom,double top,double phase,double seconds,double speed){
        double span=top-bottom;if(span<=0)return bottom;
        double travel=phase+Math.max(0,seconds)*speed/span;travel-=Math.floor(travel);
        return top-travel*span;
    }
}
