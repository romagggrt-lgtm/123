package dev.zbook;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;

final class CraftHandler implements Listener {
 private record Match(int bookSlot,int pageSlot,ItemStack result,Spell spell){}
 private final ZBookPlugin plugin;private final NamespacedKey recipeKey;
 CraftHandler(ZBookPlugin plugin){this.plugin=plugin;recipeKey=new NamespacedKey(plugin,"learn_spell");ShapelessRecipe recipe=new ShapelessRecipe(recipeKey,plugin.items().book());recipe.addIngredient(new RecipeChoice.MaterialChoice(Material.BOOK));recipe.addIngredient(new RecipeChoice.MaterialChoice(Material.PAPER));if(!Bukkit.addRecipe(recipe))throw new IllegalStateException("Unable to register spell recipe");}
 private boolean ours(Recipe recipe){return recipe instanceof Keyed k&&recipeKey.equals(k.getKey());}
 private Match match(ItemStack[] matrix){int b=-1,p=-1;Spell spell=null;for(int i=0;i<matrix.length;i++){
  ItemStack item=matrix[i];if(item==null||item.getType().isAir())continue;
  if(plugin.items().isBook(item)){if(b!=-1||item.getAmount()!=1||plugin.items().id(item)==null)return null;b=i;}
  else {String id=plugin.items().pageSpell(item);if(id==null||p!=-1||item.getAmount()<1)return null;p=i;spell=plugin.registry().get(id);}
 }
 if(b<0||p<0||spell==null)return null;Set<String> known=plugin.items().learned(matrix[b]);if(!known.add(spell.id()))return null;return new Match(b,p,plugin.items().update(matrix[b],known),spell);
 }
 @EventHandler public void prepare(PrepareItemCraftEvent e){if(!ours(e.getRecipe()))return;Match m=match(e.getInventory().getMatrix());e.getInventory().setResult(m==null?null:m.result());}
 @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void craft(CraftItemEvent e){
  if(!ours(e.getRecipe()))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player player))return;
  CraftingInventory inventory=e.getInventory();ItemStack[] before=inventory.getMatrix();Match m=match(before);if(m==null||e.getCurrentItem()==null||!m.result().isSimilar(e.getCurrentItem())){inventory.setResult(null);return;}
  ItemStack oldCursor=e.getView().getCursor();ClickType click=e.getClick();int slot=-1;boolean cursor=false,offhand=false;
  if(click==ClickType.LEFT||click==ClickType.RIGHT){if(oldCursor!=null&&!oldCursor.getType().isAir())return;cursor=true;}
  else if(click==ClickType.SHIFT_LEFT||click==ClickType.SHIFT_RIGHT){ItemStack[] storage=player.getInventory().getStorageContents();for(int i=0;i<storage.length;i++)if(storage[i]==null||storage[i].getType().isAir()){slot=i;break;}if(slot<0){plugin.text().send(player,"no-space");return;}}
  else if(click==ClickType.NUMBER_KEY){slot=e.getHotbarButton();if(slot<0||slot>8)return;ItemStack existing=player.getInventory().getItem(slot);if(existing!=null&&!existing.getType().isAir())return;}
  else if(click==ClickType.SWAP_OFFHAND){ItemStack existing=player.getInventory().getItemInOffHand();if(existing!=null&&!existing.getType().isAir())return;offhand=true;}
  else return;
  ItemStack[] after=before.clone();after[m.bookSlot()]=null;ItemStack page=before[m.pageSlot()].clone();page.setAmount(page.getAmount()-1);after[m.pageSlot()]=page.getAmount()>0?page:null;
  try{inventory.setMatrix(after);inventory.setResult(null);if(cursor)e.getView().setCursor(m.result());else if(offhand)player.getInventory().setItemInOffHand(m.result());else player.getInventory().setItem(slot,m.result());}
  catch(RuntimeException ex){inventory.setMatrix(before);if(cursor)e.getView().setCursor(oldCursor);else if(offhand)player.getInventory().setItemInOffHand(null);else if(slot>=0)player.getInventory().setItem(slot,null);plugin.getLogger().severe("Craft transaction rolled back: "+ex.getMessage());return;}
  player.playSound(player.getLocation(),Sound.BLOCK_ENCHANTMENT_TABLE_USE,0.8f,1.1f);plugin.text().send(player,"learned","spell",m.spell().name(plugin.text()));Bukkit.getScheduler().runTask(plugin,player::updateInventory);
 }
}
