package dev.zbook;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

final class CastManager implements Listener {
 private record Armed(String spell,String bookId,UUID world,int slot,long expires){}
 private final ZBookPlugin plugin;private final Map<UUID,Armed> armed=new HashMap<>();
 CastManager(ZBookPlugin plugin){this.plugin=plugin;Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1L,20L);}
 private NamespacedKey key(Spell s){return new NamespacedKey(plugin,"cooldown_"+s.id());}
 private long remaining(Player p,Spell s){Long until=p.getPersistentDataContainer().get(key(s),PersistentDataType.LONG);return Math.max(0,(until==null?0:until)-System.currentTimeMillis());}
 private void cooldownMessage(Player p,long millis){p.sendActionBar(plugin.text().format("messages.cooldown","seconds",(millis+999)/1000));}
 void select(Player p,Spell spell,String expectedBookId){
  ItemStack held=p.getInventory().getItemInMainHand();String id=plugin.items().id(held);
  if(id==null||!id.equals(expectedBookId)||!plugin.items().learned(held).contains(spell.id())){plugin.text().send(p,"stale");return;}
  long left=remaining(p,spell);if(left>0){cooldownMessage(p,left);return;}
  long seconds=Math.clamp(plugin.getConfig().getLong("arming.timeout-seconds",15),1,300);
  armed.put(p.getUniqueId(),new Armed(spell.id(),id,p.getWorld().getUID(),p.getInventory().getHeldItemSlot(),System.currentTimeMillis()+seconds*1000));
  p.closeInventory();p.sendActionBar(plugin.text().get("messages.armed"));
 }
 private boolean valid(Player p,Armed state){ItemStack held=p.getInventory().getItemInMainHand();return state.expires()>System.currentTimeMillis()&&state.world().equals(p.getWorld().getUID())&&state.slot()==p.getInventory().getHeldItemSlot()&&state.bookId().equals(plugin.items().id(held))&&plugin.items().learned(held).contains(state.spell());}
 private void tick(){for(var entry:new ArrayList<>(armed.entrySet())){Player p=Bukkit.getPlayer(entry.getKey());if(p==null||!valid(p,entry.getValue())){armed.remove(entry.getKey());if(p!=null)plugin.text().send(p,"expired");}else p.sendActionBar(plugin.text().get("messages.armed"));}}
 @EventHandler(priority=EventPriority.HIGH) public void interact(PlayerInteractEvent e){
  if(e.getHand()!=EquipmentSlot.HAND||(e.getAction()!=Action.RIGHT_CLICK_AIR&&e.getAction()!=Action.RIGHT_CLICK_BLOCK))return;
  if(e.getAction()==Action.RIGHT_CLICK_BLOCK&&e.useItemInHand()==Event.Result.DENY)return;
  Player p=e.getPlayer();ItemStack book=p.getInventory().getItemInMainHand();if(!plugin.items().isBook(book))return;
  e.setCancelled(true);Armed state=armed.remove(p.getUniqueId());
  if(state==null){plugin.ui().open(p,book);return;}
  if(!valid(p,state)){plugin.text().send(p,"expired");return;}
  Spell spell=plugin.registry().get(state.spell());if(spell==null)return;long left=remaining(p,spell);if(left>0){cooldownMessage(p,left);return;}
  if(spell.cast(p,plugin)){p.getPersistentDataContainer().set(key(spell),PersistentDataType.LONG,System.currentTimeMillis()+spell.cooldownMillis(plugin));plugin.text().send(p,"cast");}
 }
 @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void slot(PlayerItemHeldEvent e){if(e.getPreviousSlot()!=e.getNewSlot())armed.remove(e.getPlayer().getUniqueId());}
 @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void drop(PlayerDropItemEvent e){Armed a=armed.get(e.getPlayer().getUniqueId());if(a!=null&&a.bookId().equals(plugin.items().id(e.getItemDrop().getItemStack())))armed.remove(e.getPlayer().getUniqueId());}
 @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void swap(PlayerSwapHandItemsEvent e){armed.remove(e.getPlayer().getUniqueId());}
 @EventHandler public void death(PlayerDeathEvent e){armed.remove(e.getEntity().getUniqueId());}
 @EventHandler public void world(PlayerChangedWorldEvent e){armed.remove(e.getPlayer().getUniqueId());}
 @EventHandler public void quit(PlayerQuitEvent e){armed.remove(e.getPlayer().getUniqueId());}
 private boolean protects(Player p,LightningStrike bolt){String id=bolt.getPersistentDataContainer().get(plugin.protectedCasterKey(),PersistentDataType.STRING);return p.getUniqueId().toString().equals(id);}
 @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void lightning(EntityDamageByEntityEvent e){if(e.getEntity() instanceof Player p&&e.getDamager() instanceof LightningStrike bolt&&protects(p,bolt))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void ignite(EntityCombustByEntityEvent e){if(e.getEntity() instanceof Player p&&e.getCombuster() instanceof LightningStrike bolt&&protects(p,bolt))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void fireBlock(BlockIgniteEvent e){
  if(e.getCause()!=BlockIgniteEvent.IgniteCause.LIGHTNING||!(e.getIgnitingEntity() instanceof LightningStrike bolt))return;
  String id=bolt.getPersistentDataContainer().get(plugin.protectedCasterKey(),PersistentDataType.STRING);if(id==null)return;
  try{Player p=Bukkit.getPlayer(UUID.fromString(id));var b=e.getBlock();if(p!=null&&p.getWorld().equals(b.getWorld())&&p.getBoundingBox().overlaps(new BoundingBox(b.getX(),b.getY(),b.getZ(),b.getX()+1,b.getY()+1,b.getZ()+1)))e.setCancelled(true);}catch(IllegalArgumentException ignored){}
 }
}
