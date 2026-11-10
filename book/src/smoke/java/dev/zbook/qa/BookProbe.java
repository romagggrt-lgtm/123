package dev.zbook.qa;
import java.nio.file.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class BookProbe extends JavaPlugin implements Listener {
 private int strikes;
 @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);}
 private NamespacedKey key(String name){return new NamespacedKey("z_book",name);}
 @EventHandler(ignoreCancelled=true) public void lightning(LightningStrikeEvent e){strikes++;}
 @EventHandler(priority=EventPriority.LOWEST) public void command(PlayerCommandPreprocessEvent e){if(!e.getMessage().equalsIgnoreCase("/bookprobe"))return;e.setCancelled(true);Player p=e.getPlayer();int books=0,pages=0,learned=0;String model="none",ids="";
  for(ItemStack i:p.getInventory().getStorageContents()){if(i==null||!i.hasItemMeta())continue;var meta=i.getItemMeta();var d=meta.getPersistentDataContainer();String type=d.get(key("type"),PersistentDataType.STRING);if("book".equals(type)){books++;String spells=d.get(key("spells"),PersistentDataType.STRING);if(spells!=null&&spells.contains("ekspanor_fanfiction"))learned++;ids+=d.get(key("book_id"),PersistentDataType.STRING)+",";}else if("page".equals(type)){pages+=i.getAmount();if(meta.getItemModel()!=null)model=meta.getItemModel().toString();}}
  Long until=p.getPersistentDataContainer().get(key("cooldown_ekspanor_fanfiction"),PersistentDataType.LONG);String report="PROBE books="+books+" pages="+pages+" learned="+learned+" model="+model+" cooldown="+(until!=null&&until>System.currentTimeMillis())+" strikes="+strikes+" ids="+ids;p.sendMessage(report);
 }
}
