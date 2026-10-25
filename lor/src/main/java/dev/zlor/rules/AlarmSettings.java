package dev.zlor.rules;

/** Distance is measured vertically from the Void surface to the device centre. */
public record AlarmSettings(int start, int danger) {
    public static final int SOUND=0, ANALOG=1, PULSE=2;
    public AlarmSettings {
        if(start<1||start>512||danger<0||danger>=start)throw new IllegalArgumentException("0 ≤ максимум < начало ≤ 512");
    }
    public double intensity(double distance){return Math.clamp((start-distance)/(start-(double)danger),0,1);}
    public int strength(double distance){return (int)Math.ceil(15*intensity(distance));}
    public long interval(double distance){return Math.round(10_000-9_800*intensity(distance));}
    public boolean warning(double distance){return distance<start;}
    public static boolean lowFuel(long remaining,int reserves){return remaining>0&&remaining<=300_000&&reserves==0;}
    public static int outputMode(int saved){return saved==PULSE?PULSE:ANALOG;}
    public static String modeName(int mode){return mode==PULSE?"импульсы":"сигнал 0–15";}
}
