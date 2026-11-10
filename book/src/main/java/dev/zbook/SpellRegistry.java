package dev.zbook;
import java.util.*;

public final class SpellRegistry {
 private final Map<String,Spell> spells=new LinkedHashMap<>();
 public void register(Spell spell){String id=normalize(spell.id());if(!id.matches("[a-z0-9_]{1,48}")||spells.putIfAbsent(id,spell)!=null)throw new IllegalArgumentException("Invalid or duplicate spell id: "+id);}
 public Spell get(String id){return id==null?null:spells.get(normalize(id));}
 public Collection<Spell> all(){return Collections.unmodifiableCollection(spells.values());}
 static String normalize(String id){return id.toLowerCase(Locale.ROOT);}
}
