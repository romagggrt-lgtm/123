package dev.ztest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record Rift(UUID id, Point source, Point destination) {
    public Rift { if(id==null||source==null||destination==null||source.equals(destination))throw new IllegalArgumentException("Некорректный разлом"); }
    public Map<String,Object> save(){var m=new LinkedHashMap<String,Object>();m.put("id",id.toString());m.put("source",source.save());m.put("destination",destination.save());return m;}
    public static Rift read(Object value){if(!(value instanceof Map<?,?> m)||!(m.get("id") instanceof String s))throw new IllegalArgumentException("Некорректный разлом в state.yml");return new Rift(UUID.fromString(s),Point.read(m.get("source")),Point.read(m.get("destination")));}
}
