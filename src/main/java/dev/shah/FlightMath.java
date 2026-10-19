package dev.shah;

/** Pure flight math, also exercised independently of the unavailable server runtime. */
public final class FlightMath {
    private FlightMath() {}
    public static float turn(float current, float desired, float limit) {
        float delta = ((desired-current)%360+540)%360-180;
        return current+Math.max(-limit,Math.min(limit,delta));
    }
    public static double step(double speed, double travelled, double range) {
        return Math.max(0,Math.min(speed,range-travelled));
    }
    public static double smooth(double progress) {
        double t=Math.max(0,Math.min(1,progress)); return t*t*(3-2*t);
    }
}
