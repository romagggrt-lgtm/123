package dev.ztest;

/** Deterministic cell-based layout; uses world coordinates across chunk boundaries. */
public final class Terrain {
    public static final int GROUND = 64, SKY = 112, TREE_CELL = 20;
    private Terrain() {}
    public record Tree(int x, int z, int height) {}
    private static long mix(long x) {
        x ^= x >>> 30; x *= 0xbf58476d1ce4e5b9L;
        x ^= x >>> 27; x *= 0x94d049bb133111ebL;
        return x ^ (x >>> 31);
    }
    public static Tree tree(long seed, int cx, int cz) {
        long h = mix(seed ^ ((long)cx * 0x9e3779b97f4a7c15L) ^ ((long)cz * 0xc2b2ae3d27d4eb4fL));
        if (Math.floorMod(h, 10) < 3) return null;
        int x = cx * TREE_CELL + 3 + (int)Math.floorMod(h >>> 8, 14);
        int z = cz * TREE_CELL + 3 + (int)Math.floorMod(h >>> 25, 14);
        if (Math.abs((long)x) < 10 && Math.abs((long)z) < 10) return null;
        return new Tree(x, z, 4 + (int)Math.floorMod(h >>> 44, 3));
    }
    public static boolean sun(int x, int z) {
        int dx = Math.floorMod(x + 128, 256) - 128;
        int dz = Math.floorMod(z + 128, 256) - 128;
        return dx * dx + dz * dz <= 13 * 13;
    }
    public static boolean light(int x, int z) {
        return Math.floorMod(x, 9) == 0 && Math.floorMod(z, 9) == 0;
    }
}
