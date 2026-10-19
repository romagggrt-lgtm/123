package dev.shah;

/** Run with tools/check_math.sh; uses no fake Bukkit classes. */
public final class FlightMathChecks {
    private static int passed;
    private static void near(double expected,double actual) {
        if(Math.abs(expected-actual)>1e-6) throw new AssertionError(expected+" != "+actual);
        passed++;
    }
    public static void main(String[] args) {
        near(360,FlightMath.turn(359,1,1));
        near(0,FlightMath.turn(1,359,1));
        near(-361,FlightMath.turn(-360,-370,1));
        near(.1,FlightMath.step(16,149.9,150));
        near(0,FlightMath.step(2,151,150));
        double distance=0;
        for(int ticks=0;ticks<100;ticks++) distance+=FlightMath.step(16,distance,150);
        near(150,distance);
        near(0,FlightMath.smooth(-.5)); near(1,FlightMath.smooth(2));
        double previous=0;
        for(int i=0;i<=80;i++) {
            double t=FlightMath.smooth(i/80.0);
            if(t<previous || t<0 || t>1) throw new AssertionError("Takeoff reverses or overshoots");
            previous=t;
        }
        passed++;
        System.out.println(passed+" flight math checks passed; these are not server integration tests.");
    }
}
