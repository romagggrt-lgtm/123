package dev.zlor.rules;

/** Immutable, timestamp-based dimension setting. Negative speed descends. */
public record DimensionVoid(boolean active, double baseY, double perFourHours, long anchor, boolean minuteMode) {
    public static final long PERIOD = 14_400_000L;
    public static final double LIMIT = 30_000_000;
    public DimensionVoid {
        bounded(baseY); bounded(perFourHours);
        if (anchor < 0) throw new IllegalArgumentException("Некорректная метка времени.");
    }
    public static double bounded(double value) {
        if (!Double.isFinite(value) || Math.abs(value) > LIMIT)
            throw new IllegalArgumentException("Нужно конечное число от -30000000 до 30000000.");
        return value;
    }
    public static double parse(String text) {
        if (text == null || !text.matches("[+-]?(?:[0-9]+(?:[.,][0-9]{1,10})?|[.,][0-9]{1,10})"))
            throw new IllegalArgumentException("Нужно число с точкой/запятой, до 10 знаков после неё; например -1,25.");
        try { return bounded(Double.parseDouble(text.replace(',', '.'))); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Некорректное число."); }
    }
    public static DimensionVoid vanilla(double nativeY, long now) { return new DimensionVoid(false, nativeY, 0, now, false); }
    public double height(long now) {
        double delta = active ? Math.max(0d, (double) now - anchor) / PERIOD * perFourHours : 0;
        return Math.clamp(baseY + delta, -LIMIT, LIMIT);
    }
    public DimensionVoid set(double y, long now) { return new DimensionVoid(true, bounded(y), perFourHours, now, minuteMode); }
    public DimensionVoid vector(double amount, long now) { return set(bounded(height(now) + bounded(amount)), now); }
    public DimensionVoid speed(double value, boolean minute, long now) {
        bounded(value);
        double canonical = bounded(minute ? value * 240 : value);
        return new DimensionVoid(active || canonical != 0, height(now), canonical, now, minute);
    }
    public DimensionVoid speedVector(double delta, boolean minute, long now) {
        bounded(delta);
        double current = minute ? perFourHours / 240 : perFourHours;
        return speed(bounded(current + delta), minute, now);
    }
    public DimensionVoid limited(double ceiling, long now) {
        bounded(ceiling);
        double y = Math.min(height(now), ceiling);
        double rate = perFourHours;
        if ((rate > 0 && y >= ceiling) || (rate < 0 && y <= -LIMIT)) rate = 0;
        return new DimensionVoid(active, y, rate, now, minuteMode);
    }
    public double displaySpeed() { return minuteMode ? perFourHours / 240 : perFourHours; }
    /** The original comparison remains y' < nativeY; only its sampled Y is translated. */
    public static double sampledY(double y, double nativeY, double targetY) { return y - (targetY - nativeY); }
}
