package dev.zlor.rules;

import java.util.*;
import java.util.random.RandomGenerator;

/** Validated immutable settings. Heights are absolute, intervals are real seconds. */
public record EndSettings(int schema, Flash single, Series series, List<Stage> sky, Gate gate, Flight flight) {
    public static final int VOID_COLOR = 0xD55CFF;
    public EndSettings {
        if (schema != 1 || single == null || series == null || sky == null || gate == null || flight == null)
            throw new IllegalArgumentException("Неподдерживаемые настройки Энда.");
        if (sky.size() > 32) throw new IllegalArgumentException("Не больше 32 этапов неба.");
        sky = sky.stream().sorted(Comparator.comparingDouble(Stage::y)).toList();
        for (int i=1;i<sky.size();i++) if (sky.get(i-1).y == sky.get(i).y) throw new IllegalArgumentException("Высоты этапов должны различаться.");
    }
    public record Flash(String shape, String x, String y, String z, int color, double brightness, int sound, double volume, double seconds) {
        public Flash { EndSettings.shape(shape); angle(x); angle(y); angle(z); EndSettings.color(color); range(brightness,0,1,"яркость"); range(sound,1,3,"звук"); range(volume,0,1,"громкость"); range(seconds,0.1,30,"длительность"); }
        public Flash resolved(RandomGenerator r) { return new Flash(shape, resolved(x,r),resolved(y,r),resolved(z,r),color,brightness,sound,volume,seconds); }
        private static String resolved(String a,RandomGenerator r) { return a.equals("~") ? java.math.BigDecimal.valueOf(r.nextDouble(360)).setScale(10,java.math.RoundingMode.DOWN).stripTrailingZeros().toPlainString() : a; }
    }
    public record Series(boolean running, double min, double max, Flash style, int colorTo, double brightnessTo) {
        public Series { range(min,1,86400,"минимальный интервал"); range(max,min,86400,"максимальный интервал"); if(style==null) throw new IllegalArgumentException("Нет оформления серии."); color(colorTo); range(brightnessTo,style.brightness,1,"максимальная яркость"); }
        public Series running(boolean value) { return new Series(value,min,max,style,colorTo,brightnessTo); }
        public Flash next(RandomGenerator r) { return new Flash(style.shape,"~","~","~",mix(style.color,colorTo,r.nextDouble()),style.brightness+(brightnessTo-style.brightness)*r.nextDouble(),style.sound,style.volume,style.seconds).resolved(r); }
    }
    public record Stage(double y,int color) { public Stage { height(y); EndSettings.color(color); } }
    public record Gate(boolean enabled,double y) { public Gate { height(y); } public boolean blocks(double voidY) { return enabled && voidY>y; } }
    public record Flight(boolean enabled,double startY,double severeY,double startSeconds,double severeSeconds) {
        public Flight { height(startY); height(severeY); if(severeY<=startY) throw new IllegalArgumentException("Высота сильных сбоев должна быть выше начала."); range(startSeconds,0.5,3600,"пауза начала"); range(severeSeconds,0.5,startSeconds,"пауза сильных сбоев"); }
        public double interval(double y) { if(!enabled || y<startY) return Double.POSITIVE_INFINITY; double t=Math.clamp((y-startY)/(severeY-startY),0,1); return startSeconds+(severeSeconds-startSeconds)*t; }
    }
    public static EndSettings defaults() { var f=new Flash("horizontal","~","~","~",VOID_COLOR,0.8,1,0.5,2); return new EndSettings(1,f,new Series(false,20,60,f,0xF18ADE,1),List.of(),new Gate(false,50),new Flight(false,30,60,30,3)); }
    public EndSettings flash(Flash f) { return new EndSettings(1,f,series,sky,gate,flight); }
    public EndSettings series(Series s) { return new EndSettings(1,single,s,sky,gate,flight); }
    public EndSettings sky(List<Stage> s) { return new EndSettings(1,single,series,s,gate,flight); }
    public EndSettings gate(Gate g) { return new EndSettings(1,single,series,sky,g,flight); }
    public EndSettings flight(Flight f) { return new EndSettings(1,single,series,sky,gate,f); }
    public int skyColor(double y) { if(sky.isEmpty()) return 0xFFFFFF; if(y<=sky.getFirst().y) return sky.getFirst().color; for(int i=1;i<sky.size();i++) { var a=sky.get(i-1);var b=sky.get(i);if(y<=b.y)return mix(a.color,b.color,(y-a.y)/(b.y-a.y)); } return sky.getLast().color; }
    public static int mix(int a,int b,double t) { t=Math.clamp(t,0,1); int v=0; for(int shift:new int[]{16,8,0}) v|=((int)Math.round(((a>>shift)&255)*(1-t)+((b>>shift)&255)*t))<<shift; return v; }
    public static double angle(String text) { if(text==null) throw new IllegalArgumentException("Укажите угол или ~."); if(text.equals("~"))return Double.NaN; double d=DimensionVoid.parse(text); return ((d%360)+360)%360; }
    public static int parseColor(String s) { if(s==null || !s.matches("#?[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Цвет: шесть символов RGB, например D55CFF."); return Integer.parseInt(s.replace("#",""),16); }
    private static void color(int c) { if(c<0 || c>0xFFFFFF)throw new IllegalArgumentException("Неверный цвет."); }
    private static void shape(String s) { if(!Set.of("horizontal","vertical").contains(s))throw new IllegalArgumentException("Вариант: horizontal или vertical."); }
    private static void height(double d) { range(d,-DimensionVoid.LIMIT,DimensionVoid.LIMIT,"высота"); }
    public static void range(double d,double min,double max,String label) { if(!Double.isFinite(d)||d<min||d>max)throw new IllegalArgumentException(label+": от "+min+" до "+max+"."); }
}
