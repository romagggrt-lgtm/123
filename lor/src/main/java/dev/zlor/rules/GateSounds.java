package dev.zlor.rules;

import java.util.List;

/** Gateway-only choices; the eight siren tracks are intentionally separate. */
public final class GateSounds {
    public static final String DEFAULT="portal";
    public record Entry(String key,String name){}
    public static final List<Entry> ENTRIES=List.of(new Entry("enderman","Эхо Энда"),new Entry("portal","Отклик врат"),new Entry("anchor","Резонанс якоря"));
    private GateSounds(){}
    public static boolean valid(String key){return key!=null&&(ENTRIES.stream().anyMatch(e->e.key.equals(key))||key.matches("custom:[0-9a-f]{64}"));}
    public static String normalized(String key){return valid(key)?key:DEFAULT;}
    public static String name(String key){return ENTRIES.stream().filter(e->e.key.equals(key)).map(Entry::name).findFirst().orElse("Загруженный звук");}
}
