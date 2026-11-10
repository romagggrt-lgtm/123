package dev.zbook.qa;
import java.nio.file.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class BookVisual extends JavaPlugin implements Listener {
 @EventHandler(ignoreCancelled=true) public void strike(LightningStrikeEvent e){try{Files.writeString(Bukkit.getWorldContainer().toPath().resolve("book-lightning"),"strike");}catch(Exception ex){getLogger().warning(ex.toString());}}
 @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);}
 @EventHandler public void join(PlayerJoinEvent e){Player p=e.getPlayer();if(!p.getName().equals("BookVisual"))return;Bukkit.getScheduler().runTaskLater(this,()->{
  if(!p.isOnline())return;World world=p.getWorld();for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)world.getBlockAt(x,70,z).setType(Material.STONE);p.teleport(new Location(world,0.5,71,0.5,0,60));p.getInventory().clear();p.getInventory().setHeldItemSlot(0);p.performCommand("zbook give book");p.performCommand("zbook unlock");
  try{Files.writeString(Bukkit.getWorldContainer().toPath().resolve("book-ready"),"ready");}catch(Exception ex){getLogger().warning(ex.toString());}
 },30L);}
}
