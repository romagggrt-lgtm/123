package dev.zlor.rules;

/** Pure combat and geometry rules; no registry or world initialization required. */
public final class DragonRules {
    public static final float HEALTH=500, SCALE=1.15f;
    public static final int PALE=0xE7BACD, DARK=0x21122F;
    // Base blocks on the vanilla podium rim; query cells match vanilla crystal hitboxes.
    public static final int[][] CRYSTAL_BASES={{0,-3},{3,0},{0,3},{-3,0},{2,-2},{2,2},{-2,2},{-2,-2}};
    public static final int[][] CRYSTALS={{0,-2},{2,0},{0,2},{-2,0},{2,-2},{2,2},{-2,2},{-2,-2}};
    private DragonRules(){}
    public static boolean pauseNative(boolean stopped,boolean allowedEmpty,boolean hasStage,boolean hasRitual,boolean restoring){return stopped&&allowedEmpty&&!hasStage&&!hasRitual&&!restoring;}
    public static double spawnY(double islandY,double voidY,int minY,int maxY){return Math.clamp(Math.max(islandY+40,voidY+28),minY+24,maxY-16);}
    public static int hits(int old){return Math.min(7,Math.max(0,old)+1);}
    public static int lost(int hits){return Math.min(6,Math.max(0,hits));}
    public static float damage(float health,int hit,float voidDamage){
        if(!Float.isFinite(health)||!Float.isFinite(voidDamage)||voidDamage<0)return 0;
        float amount=voidDamage*.5f;
        return hit<7?Math.max(0,Math.min(amount,health-1)):amount;
    }
    public static boolean damagePulse(int active,int duration,int every){return active>=0&&active<duration&&every>0&&active%every==0;}
    public static boolean onBeam(double px,double py,double pz,double ax,double ay,double az,double bx,double by,double bz,double radius){
        double dx=bx-ax,dy=by-ay,dz=bz-az,len=dx*dx+dy*dy+dz*dz;
        if(!Double.isFinite(len)||len<.0001)return false;
        double t=Math.clamp(((px-ax)*dx+(py-ay)*dy+(pz-az)*dz)/len,0,1);
        double x=px-ax-t*dx,y=py-ay-t*dy,z=pz-az-t*dz;
        return x*x+y*y+z*z<=radius*radius;
    }
}
