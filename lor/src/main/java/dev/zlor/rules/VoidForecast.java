package dev.zlor.rules;

import java.util.Locale;

/** Pure estimates at constant speed; no world scans or unbounded date arithmetic. */
public final class VoidForecast {
    private VoidForecast() {}
    public static String number(double value) {
        if (Math.abs(value) < 0.00005) value = 0;
        return String.format(Locale.ROOT, "%.4f", value).replace('.', ',');
    }
    public static String input(double value) {
        return java.math.BigDecimal.valueOf(value).setScale(10, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
    public static String duration(double minutes, int horizonDays) {
        if (minutes <= 0) return "0";
        if (!Double.isFinite(minutes) || minutes > horizonDays * 1440d) return "больше " + horizonDays + " суток";
        String m = minutes < 1 ? "<1 мин" : String.format(Locale.ROOT, "%.0f мин", Math.ceil(minutes));
        String h = minutes < .6 ? "<0,01 ч" : minutes < 60 ? String.format(Locale.ROOT, "%.2f ч", minutes / 60).replace('.', ',')
                : String.format(Locale.ROOT, "%.1f ч", minutes / 60).replace('.', ',');
        return m + " (" + h + ")";
    }
    public static String target(double y, double rate, double target, int horizonDays) {
        if (target <= y) return "0 — уровень уже под границей или совпадает с ней";
        if (rate == 0) return "не достигнет: движение остановлено";
        if (rate < 0) return "не достигнет: Войд опускается";
        return duration((target - y) / rate * 240, horizonDays);
    }
    public static double nextLevel(double y, double rate, int step) {
        double result = rate < 0 ? (Math.ceil(y / step) - 1) * step : (Math.floor(y / step) + 1) * step;
        return Math.clamp(result, -DimensionVoid.LIMIT, DimensionVoid.LIMIT);
    }
}
