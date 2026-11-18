package dev.abilitiesprobe;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
public final class Probe extends JavaPlugin implements Listener {
 public void onEnable(){getServer().getPluginManager().registerEvents(this,this);getCommand("disableuluz").setExecutor((sender,cmd,label,args)->{var plugin=getServer().getPluginManager().getPlugin("Z_Uluz");if(plugin!=null)getServer().getPluginManager().disablePlugin(plugin);return true;});}
 @EventHandler(priority=EventPriority.MONITOR) public void interact(PlayerInteractEvent e){if(e.getPlayer().getName().equals("Operator"))getLogger().info("INTERACT="+e.getAction()+" hand="+e.getHand()+" sneak="+e.getPlayer().isSneaking()+" block="+e.useInteractedBlock()+" item="+e.useItemInHand()+" cancelled="+e.isCancelled()+" held="+e.getPlayer().getInventory().getItemInMainHand().getType());}
 @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void place(BlockPlaceEvent e){getLogger().info("PLACE_ACTOR="+e.getPlayer().getName());}
 @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void broken(BlockBreakEvent e){getLogger().info("BREAK_ACTOR="+e.getPlayer().getName());}
}
