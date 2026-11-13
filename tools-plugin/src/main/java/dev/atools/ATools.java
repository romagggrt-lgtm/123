package dev.atools;

import java.util.*;
import java.util.stream.Collectors;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.plugin.java.JavaPlugin;

/** Administrative enchantments and a persistent, vanilla librarian trade. */
public final class ATools extends JavaPlugin implements CommandExecutor,TabCompleter {
 private static final Map<String,String> ALIASES=Map.ofEntries(
  Map.entry("острота","sharpness"),Map.entry("прочность","unbreaking"),Map.entry("починка","mending"),
  Map.entry("эффективность","efficiency"),Map.entry("удача","fortune"),Map.entry("добыча","looting"),
  Map.entry("защита","protection"),Map.entry("сила","power"),Map.entry("шелковое_касание","silk_touch"));
 @Override public void onEnable(){saveDefaultConfig();Objects.requireNonNull(getCommand("z_tools")).setExecutor(this);getCommand("z_tools").setTabCompleter(this);}
 private Enchantment enchantment(String input){String id=input.toLowerCase(Locale.ROOT);id=ALIASES.getOrDefault(id,id);NamespacedKey key=NamespacedKey.fromString(id.contains(":")?id:"minecraft:"+id);return key==null?null:Registry.ENCHANTMENT.get(key);}
 private void say(CommandSender sender,String message){sender.sendMessage("§8[§bA_Tools§8] §r"+message);}
 @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
  if(!sender.hasPermission("a_tools.admin")){say(sender,"§cНедостаточно прав.");return true;}
  if(!(sender instanceof Player p)){say(sender,"§cКоманда доступна только игроку.");return true;}
  if(args.length==0){say(p,"§e/zt set <чары> [уровень], /zt enchanted <чары> [уровень], /zt reset");return true;}
  if(args[0].equalsIgnoreCase("reset")){
   ItemStack item=p.getInventory().getItemInMainHand();if(item.getType().isAir()){say(p,"§cВозьмите предмет в руку.");return true;}
   for(Enchantment enchant:List.copyOf(item.getEnchantments().keySet()))item.removeEnchantment(enchant);
   if(item.getItemMeta() instanceof EnchantmentStorageMeta meta){for(Enchantment enchant:List.copyOf(meta.getStoredEnchants().keySet()))meta.removeStoredEnchant(enchant);item.setItemMeta(meta);}
   say(p,"§aЗачарования предмета удалены.");return true;
  }
  if(args.length<2||args.length>3||!(args[0].equalsIgnoreCase("set")||args[0].equalsIgnoreCase("enchanted"))){say(p,"§cИспользуйте /zt <set|enchanted> <чары> [уровень].");return true;}
  Enchantment enchant=enchantment(args[1]);if(enchant==null){say(p,"§cНеизвестное зачарование. Используйте Tab для списка.");return true;}
  int level=enchant.getMaxLevel();try{if(args.length==3)level=Integer.parseInt(args[2]);}catch(NumberFormatException ex){level=0;}
  if(level<1||level>32767){say(p,"§cУровень должен быть от 1 до 32767.");return true;}
  if(args[0].equalsIgnoreCase("set")){
   if(level<enchant.getStartLevel()||level>enchant.getMaxLevel()){say(p,"§cЖитель продаёт только обычные уровни: "+enchant.getStartLevel()+"–"+enchant.getMaxLevel()+".");return true;}
   Block target=p.getTargetBlockExact(5);if(target==null||!target.getRelative(0,1,0).isPassable()||!target.getRelative(0,2,0).isPassable()||!target.getType().isSolid()){say(p,"§cПосмотрите на свободный твёрдый блок в пределах пяти блоков.");return true;}
   ItemStack book=new ItemStack(Material.ENCHANTED_BOOK);EnchantmentStorageMeta meta=(EnchantmentStorageMeta)book.getItemMeta();meta.addStoredEnchant(enchant,level,true);book.setItemMeta(meta);
   MerchantRecipe trade=new MerchantRecipe(book,Math.clamp(getConfig().getInt("trade.max-uses",999999),1,1000000));trade.addIngredient(new ItemStack(Material.EMERALD,Math.clamp(getConfig().getInt("trade.emeralds",20),1,64)));trade.addIngredient(new ItemStack(Material.BOOK));trade.setExperienceReward(false);trade.setPriceMultiplier(0);
   Villager villager=p.getWorld().spawn(target.getLocation().add(.5,1,.5),Villager.class,v->{v.setProfession(Villager.Profession.LIBRARIAN);v.setVillagerLevel(5);v.setPersistent(true);v.setRemoveWhenFarAway(false);});villager.setRecipes(List.of(trade));say(p,"§aЖитель создан: "+enchant.getKey().asString()+" "+level+".");return true;
  }
  ItemStack item=p.getInventory().getItemInMainHand();if(item.getType().isAir()){say(p,"§cВозьмите предмет в основную руку.");return true;}
  try{if(item.getItemMeta() instanceof EnchantmentStorageMeta meta){meta.addStoredEnchant(enchant,level,true);item.setItemMeta(meta);}else item.addUnsafeEnchantment(enchant,level);say(p,"§aДобавлено: "+enchant.getKey().asString()+" "+level+".");}catch(IllegalArgumentException ex){say(p,"§cНе удалось зачаровать этот предмет.");}return true;
 }
 @Override public List<String> onTabComplete(CommandSender sender,Command cmd,String alias,String[] args){
  if(!sender.hasPermission("a_tools.admin"))return List.of();String prefix=args[args.length-1].toLowerCase(Locale.ROOT);
  if(args.length==1)return List.of("set","enchanted","reset").stream().filter(s->s.startsWith(prefix)).toList();
  if(args.length==2&&(args[0].equalsIgnoreCase("set")||args[0].equalsIgnoreCase("enchanted"))){List<String> all=new ArrayList<>(ALIASES.keySet());for(Enchantment e:Registry.ENCHANTMENT)all.add(e.getKey().getKey());return all.stream().distinct().filter(s->s.startsWith(prefix)).sorted().collect(Collectors.toList());}
  if(args.length==3){Enchantment e=enchantment(args[1]);if(e==null)return List.of();int max=args[0].equalsIgnoreCase("set")?e.getMaxLevel():Math.max(e.getMaxLevel(),100);return List.of("1",Integer.toString(e.getMaxLevel()),Integer.toString(max)).stream().distinct().filter(s->s.startsWith(prefix)).toList();}return List.of();
 }
}
