package dev.zlor.rules;

/** Shared visual timing for absorber domes; protection never depends on animation state. */
public final class DomeAnimation {
  public static final long OPEN_MS = 3_000;
  public static final long DISSOLVE_MS = 30_000;

  private DomeAnimation() {}

  public static boolean visible(
      int cell, double latitudeY, long seed, long ageMs, long remainingMs, boolean noReserves) {
    if (remainingMs <= 0) return false;
    if (ageMs < openingDelay(latitudeY)) return false;
    return !noReserves
        || remainingMs >= DISSOLVE_MS
        || remainingMs > disappearanceAt(cell, seed);
  }

  public static long openingDelay(double latitudeY) {
    return Math.round((1 - Math.clamp(latitudeY, -1, 1)) * OPEN_MS / 2d);
  }

  public static long disappearanceAt(int cell, long seed) {
    long z = seed ^ (0x9e3779b97f4a7c15L * (cell + 1L));
    z ^= z >>> 30;
    z *= 0xbf58476d1ce4e5b9L;
    z ^= z >>> 27;
    z *= 0x94d049bb133111ebL;
    z ^= z >>> 31;
    return Math.min(DISSOLVE_MS - 1, (long) ((z >>> 11) * 0x1.0p-53 * DISSOLVE_MS));
  }

  public static int surfaceAlpha(long remainingMs, boolean noReserves) {
    if (remainingMs <= 0) return 0;
    if (!noReserves || remainingMs >= DISSOLVE_MS) return 22;
    return (int) Math.round(22 * remainingMs / (double) DISSOLVE_MS);
  }
}
