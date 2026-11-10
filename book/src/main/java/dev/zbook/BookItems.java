package dev.zbook;
import java.util.*;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

final class BookItems {
 final NamespacedKey type,bookId,spells,page;
 private final ZBookPlugin plugin;
 BookItems(ZBookPlugin plugin){this.plugin=plugin;type=new NamespacedKey(plugin,"type");bookId=new NamespacedKey(plugin,"book_id");spells=new NamespacedKey(plugin,"spells");page=new NamespacedKey(plugin,"page");}
 boolean isBook(ItemStack item){return tagged(item,Material.BOOK,"book");}
 boolean tagged(ItemStack item,Material material,String expected){if(item==null||item.getType()!=material||!item.hasItemMeta())return false;return expected.equals(item.getItemMeta().getPersistentDataContainer().get(type,PersistentDataType.STRING));}
 String id(ItemStack item){if(!isBook(item))return null;return item.getItemMeta().getPersistentDataContainer().get(bookId,PersistentDataType.STRING);}
 String pageSpell(ItemStack item){if(!tagged(item,Material.PAPER,"page"))return null;String id=item.getItemMeta().getPersistentDataContainer().get(page,PersistentDataType.STRING);Spell spell=plugin.registry().get(id);return spell==null?null:spell.id();}
 Set<String> learned(ItemStack item){if(!isBook(item))return new LinkedHashSet<>();String raw=item.getItemMeta().getPersistentDataContainer().get(spells,PersistentDataType.STRING);if(raw==null||raw.isBlank())return new LinkedHashSet<>();return Arrays.stream(raw.split(",")).map(SpellRegistry::normalize).filter(s->s.matches("[a-z0-9_]{1,48}")).collect(Collectors.toCollection(LinkedHashSet::new));}
 ItemStack book(){ItemStack result=new ItemStack(Material.BOOK);result.editMeta(m->{m.getPersistentDataContainer().set(type,PersistentDataType.STRING,"book");m.getPersistentDataContainer().set(bookId,PersistentDataType.STRING,UUID.randomUUID().toString());m.setMaxStackSize(1);});return update(result,Set.of());}
 ItemStack update(ItemStack original,Collection<String> learned){ItemStack result=original.clone();result.setAmount(1);LinkedHashSet<String> distinct=new LinkedHashSet<>();for(String s:learned)if(s!=null&&s.matches("[a-z0-9_]{1,48}"))distinct.add(s);result.editMeta(m->{m.displayName(plugin.text().get("book.name"));m.setMaxStackSize(1);m.getPersistentDataContainer().set(spells,PersistentDataType.STRING,String.join(",",distinct));List<Component> lore=plugin.text().lines("book.lore").stream().map(line->plugin.text().component(plugin.text().replace(line,"count",distinct.size()))).toList();m.lore(lore);});return result;}
 ItemStack page(Spell spell,int amount){ItemStack result=new ItemStack(Material.PAPER,amount);result.editMeta(m->{m.displayName(plugin.text().get(spell.configPath()+".page-name"));m.lore(plugin.text().lines(spell.configPath()+".page-lore").stream().map(plugin.text()::component).toList());m.setItemModel(new NamespacedKey("zbook",spell.id()));m.getPersistentDataContainer().set(type,PersistentDataType.STRING,"page");m.getPersistentDataContainer().set(page,PersistentDataType.STRING,spell.id());});return result;}
}
