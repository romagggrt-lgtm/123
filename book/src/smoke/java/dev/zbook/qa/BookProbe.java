package dev.zbook.qa;
import java.nio.file.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class BookProbe extends JavaPlugin implements Listener {
 private int strikes;private boolean blockLightning;
 @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);}
 private NamespacedKey key(String name){return new NamespacedKey("z_book",name);}
 @EventHandler(priority=EventPriority.MONITOR) public void damage(EntityDamageEvent e){if(e.getEntity() instanceof Player p&&p.getName().equals("BookTester")){String damager=e instanceof EntityDamageByEntityEvent by?by.getDamager().getType().name():"none";getLogger().info("PROBE damage cause="+e.getCause()+" damager="+damager+" cancelled="+e.isCancelled()+" health="+p.getHealth()+" damage="+e.getFinalDamage());}}
 @EventHandler public void lightning(LightningStrikeEvent e){if(blockLightning)e.setCancelled(true);else if(!e.isCancelled())strikes++;}
 @EventHandler(priority=EventPriority.LOWEST) public void command(PlayerCommandPreprocessEvent e){if(!e.getMessage().toLowerCase().startsWith("/bookprobe"))return;e.setCancelled(true);Player p=e.getPlayer();if(e.getMessage().equalsIgnoreCase("/bookprobe block")){blockLightning=true;return;}if(e.getMessage().equalsIgnoreCase("/bookprobe unblock")){blockLightning=false;return;}int books=0,pages=0,learned=0;String model="none",ids="";
  for(ItemStack i:p.getInventory().getStorageContents()){if(i==null||!i.hasItemMeta())continue;var meta=i.getItemMeta();var d=meta.getPersistentDataContainer();String type=d.get(key("type"),PersistentDataType.STRING);if("book".equals(type)){books++;String spells=d.get(key("spells"),PersistentDataType.STRING);if(spells!=null&&spells.contains("ekspanor_fanfiction"))learned++;ids+=d.get(key("book_id"),PersistentDataType.STRING)+",";}else if("page".equals(type)){pages+=i.getAmount();if(meta.getItemModel()!=null)model=meta.getItemModel().toString();}}
  Long until=p.getPersistentDataContainer().get(key("cooldown_ekspanor_fanfiction"),PersistentDataType.LONG);String report="PROBE books="+books+" pages="+pages+" learned="+learned+" model="+model+" cooldown="+(until!=null&&until>System.currentTimeMillis())+" strikes="+strikes+" health="+p.getHealth()+" fire="+p.getFireTicks()+" ids="+ids;p.sendMessage(report);
 }
}
