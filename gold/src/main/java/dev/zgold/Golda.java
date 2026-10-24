package dev.zgold;

import java.util.UUID;

/** Persisted gameplay state. Entity UUIDs are deliberately not authoritative. */
final class Golda {
    final UUID id, world;
    final double x, groundY, z, height, amplitude;
    final float yaw;
    final int riseTicks, periodTicks, batch, limit;
    long age;
    int clicks;
    UUID respondent, token;
    boolean removing;
    long lastClick = Long.MIN_VALUE;
    int removeAge;

    Golda(UUID id, UUID world, double x, double groundY, double z, float yaw,
          double height, double amplitude, int riseTicks, int periodTicks, int batch, int limit) {
        if (id == null || world == null || !Double.isFinite(x) || !Double.isFinite(groundY)
            || !Double.isFinite(z) || !Float.isFinite(yaw) || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000
            || Math.abs(groundY) > 4096 || !Double.isFinite(height) || height < 0.5 || height > 8
            || !Double.isFinite(amplitude) || amplitude < 0 || amplitude > .2
            || riseTicks < 1 || riseTicks > 400 || periodTicks < 20 || periodTicks > 1200
            || batch < 1 || batch > 64 || limit < 1 || limit > 64) throw new IllegalArgumentException("Invalid Golda record");
        this.id = id; this.world = world; this.x = x; this.groundY = groundY; this.z = z; this.yaw = yaw;
        this.height = height; this.amplitude = amplitude; this.riseTicks = riseTicks;
        this.periodTicks = periodTicks; this.batch = batch; this.limit = limit;
    }

    boolean ready() { return age >= riseTicks && !removing; }
    boolean waiting() { return clicks == limit && respondent != null && token != null && !removing; }
    double centerY() {
        if (age < riseTicks) {
            double t = (double) age / riseTicks;
            return groundY + .32 + (height - .32) * t * t * (3 - 2 * t);
        }
        // A cosine that begins at zero displacement and zero vertical velocity.
        return groundY + height + amplitude * .5 * (1 - Math.cos(2 * Math.PI * (age - riseTicks) / periodTicks));
    }
    boolean canClick(long tick, int cooldown) {
        return ready() && clicks < limit && (lastClick == Long.MIN_VALUE || tick - lastClick >= cooldown);
    }
    void claim(UUID player, long tick) {
        if (!ready() || clicks >= limit) throw new IllegalStateException("Golda is not ready");
        clicks++; lastClick = tick;
        if (clicks == limit) { respondent = player; token = UUID.randomUUID(); }
    }
    boolean accepts(UUID player, UUID answerToken) {
        return waiting() && respondent.equals(player) && token.equals(answerToken);
    }
}
