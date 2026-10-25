package dev.zlor.rules;

/** New bottles last 15 minutes; already paid legacy fuel keeps its original deadline. */
public final class AbsorberTiming {
    public static final long BOTTLE_MILLIS = 15 * 60 * 1000L;
    public static final long LEGACY_MAX_MILLIS = 60 * 60 * 1000L;
    private AbsorberTiming() {}
}
