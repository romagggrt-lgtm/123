package dev.zap.qa;
import java.nio.file.*;
import org.bukkit.Bukkit;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;
/** Test-only automation; not included in the distributed plugin. */
public final class VisualProbe extends JavaPlugin implements Listener {
 @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);}
 private void mark(String name,String value){try{Files.writeString(Bukkit.getWorldContainer().toPath().resolve("visual-"+name),value);}catch(Exception e){getLogger().warning(e.toString());}}
 @EventHandler(priority=EventPriority.MONITOR) public void pack(PlayerResourcePackStatusEvent e){if(!e.getPlayer().getName().equals("ZapVisual"))return;getLogger().info("Visual pack status: "+e.getStatus());if(e.getStatus()==PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED){mark("pack","loaded");Bukkit.getScheduler().runTaskLater(this,()->{if(e.getPlayer().isOnline())e.getPlayer().performCommand("ap");},25);}}
 @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void screen(InventoryOpenEvent e){if(!e.getPlayer().getName().equals("ZapVisual"))return;String type=e.getInventory().getType().name();getLogger().info("Visual screen: "+type);if(type.equals("ANVIL"))mark("anvil",type);if(type.equals("CHEST")){try{Path p=Bukkit.getWorldContainer().toPath().resolve("visual-chest-count");int count=Files.exists(p)?Integer.parseInt(Files.readString(p)):0;Files.writeString(p,Integer.toString(count+1));}catch(Exception ex){getLogger().warning(ex.toString());}}}
}
