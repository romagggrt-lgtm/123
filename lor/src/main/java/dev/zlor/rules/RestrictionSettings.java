package dev.zlor.rules;

/** Only approved End restrictions; independent from native-void/roles and archived atmosphere. */
public record RestrictionSettings(int schema, Gate gate, Flight flight) {
    public RestrictionSettings {
        if (schema != 1 || gate == null || flight == null) throw new IllegalArgumentException("Неполные настройки блокировок.");
    }
    public record Gate(boolean enabled, double y, String sound, double volume) {
        public Gate {
            range(y, -DimensionVoid.LIMIT, DimensionVoid.LIMIT, "Высота блокировки");
            range(volume, 0, 1, "Громкость");
            if (!GateSounds.valid(sound))
                throw new IllegalArgumentException("Выберите звук из списка или загрузите файл.");
            sound=GateSounds.normalized(sound);
        }
        public boolean blocks(double voidY) { return enabled && voidY > y; }
        public Gate enabled(boolean value) { return new Gate(value, y, sound, volume); }
    }
    public record Flight(boolean enabled, double startY, double endY, double startSeconds, boolean fullBlockEnabled, double fullBlockY) {
        public Flight(boolean enabled,double startY,double endY,double startSeconds){this(enabled,startY,endY,startSeconds,false,Math.min(DimensionVoid.LIMIT,endY+40));}
        public Flight {
            range(startY, -DimensionVoid.LIMIT, DimensionVoid.LIMIT, "Высота начала");
            range(endY, -DimensionVoid.LIMIT, DimensionVoid.LIMIT, "Высота конца");
            if (endY <= startY) throw new IllegalArgumentException("Высота конца должна быть выше начала сбоев.");
            range(startSeconds, .2, 3600, "Начальный интервал, секунды");
            range(fullBlockY,-DimensionVoid.LIMIT,DimensionVoid.LIMIT,"Высота полного запрета");
            if(fullBlockEnabled&&fullBlockY<endY)throw new IllegalArgumentException("Полный запрет должен быть не ниже конца усиления сбоев.");
        }
        public double interval(double y) {
            if (!enabled || y < startY) return Double.POSITIVE_INFINITY;
            double t = Math.clamp((y - startY) / (endY - startY), 0, 1);
            return t>=1?.2:startSeconds + (.2 - startSeconds) * t;
        }
        public boolean blocked(double y){return enabled&&fullBlockEnabled&&y>=fullBlockY;}
        public Flight enabled(boolean value) { return new Flight(value, startY, endY, startSeconds,fullBlockEnabled,fullBlockY); }
    }
    public static RestrictionSettings defaults() { return new RestrictionSettings(1, new Gate(false, 50, GateSounds.DEFAULT, .5), new Flight(false, 0, 60, 120)); }
    public RestrictionSettings gate(Gate value) { return new RestrictionSettings(1, value, flight); }
    public RestrictionSettings flight(Flight value) { return new RestrictionSettings(1, gate, value); }
    public static void range(double value, double min, double max, String title) {
        if (!Double.isFinite(value) || value < min || value > max) throw new IllegalArgumentException(title + ": от " + min + " до " + max + ".");
    }
}
