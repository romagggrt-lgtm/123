package dev.zlor.rules;

import java.util.*;

/** One warning OR active volley per arena. Selection rotates among eligible dragons. */
public final class BeamArbiter {
    private final Map<String,UUID> owners=new HashMap<>(),last=new HashMap<>();
    public UUID owner(String arena){return owners.get(arena);}
    public UUID select(String arena,Collection<UUID> ready){
        if(owners.containsKey(arena))return null;
        var sorted=ready.stream().distinct().sorted().toList();if(sorted.isEmpty())return null;
        UUID previous=last.get(arena),chosen=sorted.getFirst();
        if(previous!=null)chosen=sorted.stream().filter(id->id.compareTo(previous)>0).findFirst().orElse(chosen);
        owners.put(arena,chosen);last.put(arena,chosen);return chosen;
    }
    public void release(String arena,UUID expected){if(Objects.equals(owners.get(arena),expected))owners.remove(arena);}
    public void clear(){owners.clear();last.clear();}
}
