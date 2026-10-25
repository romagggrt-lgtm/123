package dev.zlor.rules;

/** Height of the exit portal is the command target; the entry platform follows above it. */
public final class EndStructureHeights {
    public static final int ENTRY_OFFSET = 10;
    private EndStructureHeights() {}

    public static int entryY(int portalY) { return Math.addExact(portalY, ENTRY_OFFSET); }
    public static int minimumPortalY(double voidY, int worldMinY) {
        return Math.max((int)Math.floor(voidY) + 1, worldMinY + 2);
    }
    public static int maximumPortalY(int worldMaxY) {
        return worldMaxY - ENTRY_OFFSET - 8;
    }
}
