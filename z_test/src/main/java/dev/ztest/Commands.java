package dev.ztest;

import java.util.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class Commands implements CommandExecutor,TabCompleter {
    private final ZTestPlugin p;
    Commands(ZTestPlugin p){this.p=p;}
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){
        if(args.length>0&&args[0].equalsIgnoreCase("return")){
            if(args.length!=1){sender.sendMessage(ZTestPlugin.msg("§eИспользование: /ztest return"));return true;}
            if(!(sender instanceof Player player)){sender.sendMessage(ZTestPlugin.msg("§cКоманда доступна игроку."));return true;}p.rifts.emergencyReturn(player);return true;
        }
        if(!sender.hasPermission("ztest.admin")){sender.sendMessage(ZTestPlugin.msg("§cНет доступа."));return true;}
        if(args.length==0||args[0].equalsIgnoreCase("help")){help(sender);return true;}
        try{
            if(args[0].equalsIgnoreCase("souls")){
                if(args.length!=2)throw new IllegalArgumentException("Используйте /ztest souls status|spawn");
                Player player=player(sender);
                switch(args[1].toLowerCase(Locale.ROOT)){
                    case "status"->sender.sendMessage(ZTestPlugin.msg(p.souls.status(player)));
                    case "spawn"->{int n=p.souls.spawnNear(player,true);sender.sendMessage(ZTestPlugin.msg(n==6?"§aСоздана группа из шести душ перед тобой.":"§cНе удалось создать группу: проверь лимит душ, место и запреты других плагинов."));}
                    default->throw new IllegalArgumentException("Используйте /ztest souls status|spawn");
                }return true;
            }
            if(args[0].equalsIgnoreCase("rift")){
                if(args.length!=2){sender.sendMessage(ZTestPlugin.msg("§eИспользование: /ztest rift create|close|list"));return true;}
                switch(args[1].toLowerCase(Locale.ROOT)){
                    case "create"->{Player player=player(sender);Rift r=p.rifts.create(player);sender.sendMessage(ZTestPlugin.msg("§aРазлом открыт: §f"+format(r.source())+" §7↔ §fЧистилище "+r.destination().x()+", "+r.destination().y()+", "+r.destination().z()));}
                    case "close"->{Player player=player(sender);Rift r=p.rifts.nearest(player.getLocation(),12);if(r==null)throw new IllegalArgumentException("Рядом нет разлома (радиус 12 блоков)");p.rifts.remove(r);sender.sendMessage(ZTestPlugin.msg("§dРазлом закрыт с обеих сторон."));}
                    case "list"->{if(p.state.rifts().isEmpty())sender.sendMessage(ZTestPlugin.msg("§7Открытых разломов нет."));else{sender.sendMessage(ZTestPlugin.msg("§dОткрытые разломы: "+p.state.rifts().size()));for(Rift r:p.state.rifts())sender.sendMessage("§7• §f"+format(r.source())+" §8→ §d"+format(r.destination()));}}
                    default->throw new IllegalArgumentException("Используйте /ztest rift create|close|list");
                }return true;
            }
            if(args[0].equalsIgnoreCase("give")){
                if(args.length<2||args.length>3)throw new IllegalArgumentException("Используйте /ztest give shard|detector [количество]");
                Player player=player(sender);ItemStack item=switch(args[1].toLowerCase(Locale.ROOT)){case "shard"->p.detector.shard();case "detector"->p.detector.detector();default->throw new IllegalArgumentException("Предмет: shard или detector");};
                int amount=1;if(args.length==3)try{amount=Integer.parseInt(args[2]);}catch(NumberFormatException ex){throw new IllegalArgumentException("Количество должно быть от 1 до 64");}
                if(amount<1||amount>64)throw new IllegalArgumentException("Количество должно быть от 1 до 64");item.setAmount(amount);
                var left=player.getInventory().addItem(item);for(ItemStack remaining:left.values())player.getWorld().dropItemNaturally(player.getLocation(),remaining);
                sender.sendMessage(ZTestPlugin.msg("§aПредмет выдан: §f"+args[1]+" × "+amount));return true;
            }
            throw new IllegalArgumentException("Неизвестная команда. Используйте /ztest help");
        }catch(IllegalArgumentException ex){sender.sendMessage(ZTestPlugin.msg("§c"+ex.getMessage()));}
        catch(java.io.IOException ex){p.failed(ex);sender.sendMessage(ZTestPlugin.msg("§cДанные не сохранены. Проверьте журнал сервера."));}
        return true;
    }
    private static Player player(CommandSender sender){if(sender instanceof Player p)return p;throw new IllegalArgumentException("Эту команду нужно выполнить в игре");}
    private static String format(Point p){return p.world()+" "+p.x()+", "+p.y()+", "+p.z();}
    private static void help(CommandSender s){s.sendMessage(ZTestPlugin.msg("§dКоманды:"));s.sendMessage("§f/ztest rift create §7— создать парный разлом перед собой");s.sendMessage("§f/ztest rift close §7— закрыть ближайший разлом");s.sendMessage("§f/ztest rift list §7— показать активные разломы");s.sendMessage("§f/ztest souls status|spawn §7— проверить или призвать души");s.sendMessage("§f/ztest give shard|detector [количество] §7— выдать предмет себе");s.sendMessage("§f/ztest return §7— аварийный выход, если ваш разлом закрыт");}
    @Override public List<String> onTabComplete(CommandSender sender,Command cmd,String alias,String[] args){
        if(args.length==1)return matches(sender.hasPermission("ztest.admin")?List.of("help","rift","give","souls","return"):List.of("return"),args[0]);
        if(!sender.hasPermission("ztest.admin"))return List.of();
        if(args.length==2&&args[0].equalsIgnoreCase("souls"))return matches(List.of("status","spawn"),args[1]);
        if(args.length==2&&args[0].equalsIgnoreCase("rift"))return matches(List.of("create","close","list"),args[1]);
        if(args.length==2&&args[0].equalsIgnoreCase("give"))return matches(List.of("shard","detector"),args[1]);
        if(args.length==3&&args[0].equalsIgnoreCase("give"))return matches(List.of("1","4","16","64"),args[2]);
        return List.of();
    }
    private static List<String> matches(List<String> values,String prefix){return values.stream().filter(v->v.startsWith(prefix.toLowerCase(Locale.ROOT))).toList();}
}
