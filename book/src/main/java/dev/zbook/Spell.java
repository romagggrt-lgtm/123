package dev.zbook;

import java.util.List;
import org.bukkit.entity.Player;

/** To add a spell implement this interface and register one instance at startup. */
public interface Spell {
 String id();
 default String configPath(){return "spells."+id();}
 default String name(Text text){return text.raw(configPath()+".name");}
 default List<String> description(Text text){return text.lines(configPath()+".description");}
 long cooldownMillis(ZBookPlugin plugin);
 double range(ZBookPlugin plugin);
 /** Return true only if the action actually succeeded. */
 boolean cast(Player player,ZBookPlugin plugin);
}
