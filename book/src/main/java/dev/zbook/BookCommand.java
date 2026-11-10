package dev.zbook;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class BookCommand implements CommandExecutor,TabCompleter {
 private final ZBookPlugin plugin;BookCommand(ZBookPlugin plugin){this.plugin=plugin;}
 @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
  if(args.length>0&&args[0].equalsIgnoreCase("cast")){
   if(!(sender instanceof Player p)){plugin.text().send(sender,"player-only");return true;}
   Spell s=args.length==2?plugin.registry().get(args[1]):null;ItemStack held=p.getInventory().getItemInMainHand();String bookId=plugin.items().id(held);
   if(s==null||bookId==null){plugin.text().send(p,"stale");return true;}plugin.casts().select(p,s,bookId);return true;
  }
  if(!sender.hasPermission("zbook.admin")){plugin.text().send(sender,"no-permission");return true;}
  if(args.length==0){plugin.text().send(sender,"usage");return true;}
  if(args[0].equalsIgnoreCase("give")){give(sender,args);return true;}
  if(args[0].equalsIgnoreCase("unlock")||args[0].equalsIgnoreCase("reset")){
   if(!(sender instanceof Player p)){plugin.text().send(sender,"player-only");return true;}ItemStack held=p.getInventory().getItemInMainHand();if(!plugin.items().isBook(held)){plugin.text().send(p,"hold-book");return true;}
   Set<String> known=args[0].equalsIgnoreCase("reset")?new LinkedHashSet<>():plugin.items().learned(held);if(args[0].equalsIgnoreCase("unlock"))for(Spell s:plugin.registry().all())known.add(s.id());p.getInventory().setItemInMainHand(plugin.items().update(held,known));plugin.text().send(p,args[0].equalsIgnoreCase("reset")?"reset":"unlocked");return true;
  }
  plugin.text().send(sender,"usage");return true;
 }
 private void give(CommandSender sender,String[] args){
  if(args.length<2||args.length>4){plugin.text().send(sender,"usage");return;}boolean book=args[1].equalsIgnoreCase("book");Spell spell=plugin.registry().get(args[1]);if(!book&&spell==null){plugin.text().send(sender,"invalid-item");return;}
  Player target;if(args.length>=3){target=Bukkit.getPlayerExact(args[2]);if(target==null){plugin.text().send(sender,"invalid-player");return;}}else if(sender instanceof Player p)target=p;else{plugin.text().send(sender,"player-only");return;}
  int count=1;if(args.length==4)try{count=Integer.parseInt(args[3]);if(count<1||count>64)throw new NumberFormatException();}catch(NumberFormatException ex){plugin.text().send(sender,"invalid-amount");return;}
  ItemStack[] storage=target.getInventory().getStorageContents();if(book){int free=0;for(ItemStack s:storage)if(s==null||s.getType().isAir())free++;if(free<count){plugin.text().send(sender,"no-space");return;}for(int i=0;i<count;i++)target.getInventory().addItem(plugin.items().book());}
  else {ItemStack prototype=plugin.items().page(spell,1);int room=0;for(ItemStack s:storage){if(s==null||s.getType().isAir())room+=prototype.getMaxStackSize();else if(s.isSimilar(prototype))room+=Math.max(0,s.getMaxStackSize()-s.getAmount());}if(room<count){plugin.text().send(sender,"no-space");return;}target.getInventory().addItem(plugin.items().page(spell,count));}
  String name=book?plugin.text().raw("book.name"):plugin.text().raw(spell.configPath()+".page-name");plugin.text().send(sender,"gave","item",name,"amount",count,"player",target.getName());if(!target.equals(sender))plugin.text().send(target,"received","item",name,"amount",count);
 }
 @Override public List<String> onTabComplete(CommandSender sender,Command c,String alias,String[] args){if(!sender.hasPermission("zbook.admin"))return List.of();List<String> options=new ArrayList<>();if(args.length==1)options.addAll(List.of("give","unlock","reset"));else if(args.length==2&&args[0].equalsIgnoreCase("give")){options.add("book");for(Spell s:plugin.registry().all())options.add(s.id());}else if(args.length==3&&args[0].equalsIgnoreCase("give"))for(Player p:Bukkit.getOnlinePlayers())options.add(p.getName());else if(args.length==4&&args[0].equalsIgnoreCase("give"))options.addAll(List.of("1","16","64"));String prefix=args[args.length-1].toLowerCase(Locale.ROOT);return options.stream().filter(x->x.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();}
}
