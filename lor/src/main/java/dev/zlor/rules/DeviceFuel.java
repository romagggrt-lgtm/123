package dev.zlor.rules;

import dev.zlor.rules.AbsorberTiming;

/** Real-time paid fuel; unused reserves are never consumed retrospectively. */
public final class DeviceFuel {
    private long until;
    public long remaining(long now) { return Math.clamp(until-now,0,AbsorberTiming.LEGACY_MAX_MILLIS); }
    public boolean active(long now) { return remaining(now)>0; }
    public void load(long deadline) { until=Math.max(0,deadline); }
    public long deadline() { return until; }
    public boolean start(long now) { if(active(now))return false;until=now+AbsorberTiming.BOTTLE_MILLIS;return true; }
    public void restore(long remaining,long now) { until=now+Math.clamp(remaining,0,AbsorberTiming.LEGACY_MAX_MILLIS); }
}
