package dev.zverity;

/** Translation only: player yaw/pitch never enters the follow destination. */
final class FollowMath {
    record Step(double x, double z) {}
    static Step step(double x, double z, double goalX, double goalZ, double stop, double speed) {
        double dx = goalX - x, dz = goalZ - z;
        double distance = Math.hypot(dx, dz);
        if (!Double.isFinite(distance) || distance <= Math.max(0, stop) + .015) return new Step(0, 0);
        double travel = Math.min(Math.max(0, speed), Math.min(distance - Math.max(0, stop), distance * .18));
        return new Step(dx / distance * travel, dz / distance * travel);
    }
    static float faceYaw(double dx, double dz) {
        // The model face points along -Z, while Bukkit yaw=0 points along +Z.
        return (float) Math.toDegrees(Math.atan2(-dx, dz)) + 180;
    }
    static float turn(float current, float desired, float maxChange) {
        float delta = ((desired - current) % 360 + 540) % 360 - 180;
        return current + Math.max(-maxChange, Math.min(maxChange, delta));
    }
}
