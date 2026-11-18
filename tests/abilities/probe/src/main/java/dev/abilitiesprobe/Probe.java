package dev.abilitiesprobe;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.plugin.java.JavaPlugin;
public final class Probe extends JavaPlugin implements Listener {
 public void onEnable(){getServer().getPluginManager().registerEvents(this,this);}
 @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void place(BlockPlaceEvent e){getLogger().info("PLACE_ACTOR="+e.getPlayer().getName());}
 @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void broken(BlockBreakEvent e){getLogger().info("BREAK_ACTOR="+e.getPlayer().getName());}
}
