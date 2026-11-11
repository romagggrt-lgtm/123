package dev.cjbqa;

import java.nio.file.Files;
import java.nio.file.Path;
import de.boondocksulfur.customjukebox.CustomJukebox;
import de.boondocksulfur.customjukebox.api.events.CustomSoundPlayEvent;
import de.boondocksulfur.customjukebox.api.events.CustomSoundStopEvent;
import de.boondocksulfur.customjukebox.api.events.DiscPlaybackStartEvent;
import org.bukkit.*;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class CompatibilityProbe extends JavaPlugin implements Listener {
 private Path marker(String name){return Bukkit.getWorldContainer().toPath().resolve("cjbqa-"+name);}
 private void mark(String name,String value){try{Files.writeString(marker(name),value);}catch(Exception e){getLogger().severe(e.toString());}}
 @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);}
 @EventHandler public void onJoin(PlayerJoinEvent e){Player p=e.getPlayer();if(!p.getName().equals("CjbTester"))return;
  Bukkit.getScheduler().runTaskLater(this,()->{
   if(!p.isOnline())return;
   PlayerResourcePackStatusEvent.Status status=p.getResourcePackStatus();mark("pack-status",String.valueOf(status));
   World world=p.getWorld();for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)world.getBlockAt(x,70,z).setType(Material.STONE);
   world.getBlockAt(0,72,3).setType(Material.JUKEBOX);p.teleport(new Location(world,0.5,71,0.5,0,0));p.getInventory().clear();
   boolean sent=Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"cjb give CjbTester epic_journey");mark("give-command",String.valueOf(sent));
   Bukkit.getScheduler().runTaskLater(this,()->{
    ItemStack found=null;for(ItemStack it:p.getInventory().getStorageContents())if(it!=null&&it.getType()==Material.MUSIC_DISC_13){found=it;break;}
    if(found==null){mark("setup-error","Custom disc was not given");return;}
    var disc=CustomJukebox.getInstance().getDiscManager().getDiscFromItem(found);
    if(disc==null){mark("setup-error","Given item is not recognized as a custom disc");return;}
    p.getInventory().clear();p.getInventory().setItem(0,found);p.getInventory().setHeldItemSlot(0);p.setOp(false);
    boolean guiCommand=p.performCommand("cjb gui");boolean guiOpened=p.getOpenInventory().getTopInventory().getType()!=org.bukkit.event.inventory.InventoryType.CRAFTING;
    mark("gui",guiCommand+":"+guiOpened);p.closeInventory();
    var pm=Bukkit.getPluginManager();StringBuilder statusPlugins=new StringBuilder();for(String name:new String[]{"CustomJukebox","Z_Book","Z_AP","Z_Banya","Z_Nick","Z_Check","InventoryCache"})statusPlugins.append(name).append('=').append(pm.isPluginEnabled(name)).append('\n');mark("plugins",statusPlugins.toString());
    mark("ready","disc="+disc.getId()+";model="+found.getItemMeta().getCustomModelDataComponent().getFloats()+";sound="+disc.getSoundKey());
   },20L);
  },25L);
 }
 @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void onStart(DiscPlaybackStartEvent e){if(!e.getDisc().getId().equals("epic_journey"))return;var b=e.getLocation().getBlock();boolean inserted=b.getState() instanceof Jukebox j&&j.getRecord().getType()==Material.MUSIC_DISC_13;mark("play-start","disc="+e.getDisc().getId()+";inserted="+inserted);}
 @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void onSound(CustomSoundPlayEvent e){if(e.getPlayer().getName().equals("CjbTester")&&e.getDisc().getId().equals("epic_journey"))mark("sound","sound="+e.getDisc().getSoundKey()+";source="+e.getSource()+";volume="+e.getVolume());}
 @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void onStop(CustomSoundStopEvent e){if(e.getPlayer().getName().equals("CjbTester")&&e.getDisc().getId().equals("epic_journey"))mark("stop","sound="+e.getDisc().getSoundKey()+";source="+e.getSource());}
}
