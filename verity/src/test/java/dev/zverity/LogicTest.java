package dev.zverity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.zverity.VerityState.Action.*;
import static dev.zverity.VerityState.Stage.*;

class LogicTest {
    @Test void boxOpensBeforeGreetingAndKilling() {
        var state = new VerityState();
        assertEquals(OPEN, state.click(0, false));
        assertEquals(NONE, state.click(8, false));
        state.finishOpening();
        assertEquals(GREET, state.click(20, false));
        assertEquals(FOLLOWING, state.stage());
        assertEquals(KILL, state.click(30, false));
        assertEquals(REMOVED, state.stage());
        assertEquals(NONE, state.click(100, false));
    }
    @Test void duplicatePacketsCannotKillAfterGreeting() {
        var state = new VerityState();
        state.click(0, false); state.finishOpening();
        assertEquals(GREET, state.click(20, false));
        assertEquals(NONE, state.click(20, false));
        assertEquals(NONE, state.click(21, true));
        assertEquals(NONE, state.click(25, false));
        assertEquals(KILL, state.click(26, true));
    }
    @Test void unopenedBoxCanBeBrokenOnce() {
        var state = new VerityState();
        assertEquals(BREAK_BOX, state.click(0, true));
        assertEquals(NONE, state.click(10, true));
        state.finishOpening(); assertEquals(REMOVED, state.stage());
    }
    @Test void removedThingCannotBeReactivated() {
        var state = new VerityState(); state.remove(); state.finishOpening();
        assertEquals(NONE, state.click(100, false));
    }
    @Test void followerStopsAtDistanceWithoutOrbiting() {
        double x = 8, z = -4;
        for (int tick = 0; tick < 500; tick++) {
            var step = FollowMath.step(x, z, 0, 0, 1.7, .18);
            assertTrue(Math.hypot(step.x(), step.z()) <= .180001);
            x += step.x(); z += step.z();
        }
        assertEquals(1.7, Math.hypot(x, z), .016);
        // Turning any number of times supplies the same player position.
        for (int yaw = -720; yaw <= 720; yaw++) {
            var step = FollowMath.step(x, z, 0, 0, 1.7, .18);
            assertEquals(0, step.x()); assertEquals(0, step.z());
        }
    }
    @Test void followerMovesTowardTranslationWithoutOvershoot() {
        var step = FollowMath.step(0, 0, 1.71, 0, 1.7, .18);
        assertEquals(0, step.x(), .015);
        step = FollowMath.step(0, 0, 100, -100, 1.7, .18);
        assertTrue(step.x() > 0 && step.z() < 0);
        assertEquals(.18, Math.hypot(step.x(), step.z()), 1e-9);
    }
    @Test void zeroOrInvalidDistanceNeverProducesNaN() {
        assertEquals(new FollowMath.Step(0, 0), FollowMath.step(0, 0, 0, 0, 0, .18));
        assertEquals(new FollowMath.Step(0, 0), FollowMath.step(0, 0, Double.NaN, 0, 1.7, .18));
    }
    @Test void faceTurnsOnShortestArcAndAlwaysUpright() {
        assertEquals(361, FollowMath.turn(359, 1, 8), 1e-6);
        assertEquals(82, FollowMath.turn(90, 0, 8), 1e-6);
        assertEquals(180, FollowMath.faceYaw(0, 1), 1e-6);
        assertEquals(0, FollowMath.faceYaw(0, -1) % 360, 1e-6);
    }
}
