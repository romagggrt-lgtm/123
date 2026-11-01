package dev.zap.qa;
import java.nio.file.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import java.util.*;
import org.bukkit.plugin.java.JavaPlugin;
/** Test-only automation; not included in the distributed plugin. */
public final class VisualProbe extends JavaPlugin implements Listener {
 private final Set<UUID> started=new HashSet<>();
 @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);}
 private void mark(String name,String value){try{Files.writeString(Bukkit.getWorldContainer().toPath().resolve("visual-"+name),value);}catch(Exception e){getLogger().warning(e.toString());}}
 private void start(org.bukkit.entity.Player p){if(!p.getName().equals("ZapVisual")||!started.add(p.getUniqueId()))return;mark("pack","loaded");Bukkit.getScheduler().runTaskLater(this,()->{if(p.isOnline())p.performCommand("ap");},25);}
 @EventHandler(priority=EventPriority.MONITOR) public void join(PlayerJoinEvent e){if(!e.getPlayer().getName().equals("ZapVisual"))return;Bukkit.getScheduler().runTask(this,()->{var p=e.getPlayer();getLogger().info("Visual join pack status: "+p.getResourcePackStatus());if(p.getResourcePackStatus()==PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED)start(p);});}
 @EventHandler(priority=EventPriority.MONITOR) public void pack(PlayerResourcePackStatusEvent e){if(!e.getPlayer().getName().equals("ZapVisual"))return;getLogger().info("Visual pack status: "+e.getStatus());if(e.getStatus()==PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED)start(e.getPlayer());}
 @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void mode(PlayerGameModeChangeEvent e){if(e.getPlayer().getName().equals("ZapVisual"))mark("mode",e.getNewGameMode().name());}
 @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void screen(InventoryOpenEvent e){if(!e.getPlayer().getName().equals("ZapVisual"))return;String type=e.getInventory().getType().name();String title=PlainTextComponentSerializer.plainText().serialize(e.getView().title());getLogger().info("Visual screen: "+type);if(type.equals("ANVIL")){mark("anvil",type);if(title.indexOf(0xe140)>=0)mark("anvil-themed","yes");}if(type.equals("CHEST")){if(title.indexOf(0xe120)>=0)mark("home-themed","yes");try{Path p=Bukkit.getWorldContainer().toPath().resolve("visual-chest-count");int count=Files.exists(p)?Integer.parseInt(Files.readString(p)):0;Files.writeString(p,Integer.toString(count+1));}catch(Exception ex){getLogger().warning(ex.toString());}}}
}
