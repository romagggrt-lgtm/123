package dev.zlor.rules;

import java.util.Collection;
import dev.zlor.rules.Domes;

/** Per-tick boundary drag; no attraction in the interior or internal union walls. */
public final class DomeBoundary {
    private DomeBoundary() {}
    public static Domes.Push drag(Collection<Domes.Sphere> spheres, double x, double y, double z,
            double mx, double my, double mz, double vx, double vy, double vz) {
        return drag(spheres,x,y,z,mx,my,mz,vx,vy,vz,false);
    }
    public static Domes.Push drag(Collection<Domes.Sphere> spheres, double x, double y, double z,
            double mx, double my, double mz, double vx, double vy, double vz, boolean fast) {
        if (!Double.isFinite(x+y+z+mx+my+mz+vx+vy+vz) || mx*mx+my*my+mz*mz>64) return Domes.Push.NONE;
        Domes.Sphere boundary=null; double best=Double.MAX_VALUE;
        for (var s:spheres) {
            double d=Math.sqrt(s.distance2(x,y,z)), gap=Math.abs(s.radius()-d);
            if(d<1e-6 || gap>1.25 || gap>=best) continue;
            double nx=(x-s.x())/d, ny=(y-s.y())/d, nz=(z-s.z())/d;
            if(mx*nx+my*ny+mz*nz<=.002) continue;
            boolean hidden=false;
            for(var other:spheres) if(other!=s && other.contains(s.x()+nx*(s.radius()+.1),s.y()+ny*(s.radius()+.1),s.z()+nz*(s.radius()+.1))) {hidden=true;break;}
            if(!hidden) {boundary=s;best=gap;}
        }
        if(boundary==null) return Domes.Push.NONE;
        double d=Math.sqrt(boundary.distance2(x,y,z));
        double nx=(x-boundary.x())/d,ny=(y-boundary.y())/d,nz=(z-boundary.z())/d;
        double outward=mx*nx+my*ny+mz*nz;
        double pressure=Math.clamp((1.25-(boundary.radius()-d))/.8,0,1);
        // Walking (~.216 blocks/tick) stalls; a running start or flight overcomes the drag.
        double desired=outward-.24*pressure;
        if(fast||outward>.24) desired=Math.max(fast?.045:.015,desired*.8);
        else desired=Math.max(-.035,desired);
        double correction=Math.clamp(desired-(vx*nx+vy*ny+vz*nz),-1.2,.3);
        return new Domes.Push(nx*correction,ny*correction,nz*correction);
    }
}
