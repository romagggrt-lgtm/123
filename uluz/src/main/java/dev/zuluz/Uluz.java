package dev.zuluz;

import java.util.*;
import java.util.regex.*;
import com.github.retrooper.packetevents.PacketEvents;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class Uluz extends JavaPlugin implements CommandExecutor,TabCompleter {
 private Hallucinations hallucinations;private Capture capture;
 @Override public void onEnable(){hallucinations=new Hallucinations(this);capture=new Capture(this);getServer().getPluginManager().registerEvents(hallucinations,this);getServer().getPluginManager().registerEvents(capture,this);PacketEvents.getAPI().getEventManager().registerListener(capture);Objects.requireNonNull(getCommand("gal")).setExecutor(this);Objects.requireNonNull(getCommand("zahvat")).setExecutor(this);getCommand("gal").setTabCompleter(this);getCommand("zahvat").setTabCompleter(this);for(Player player:Bukkit.getOnlinePlayers())capture.recover(player);Bukkit.getScheduler().runTaskTimer(this,()->{hallucinations.tick();capture.tick();},1,1);}
 @Override public void onDisable(){if(capture!=null){capture.close();PacketEvents.getAPI().getEventManager().unregisterListener(capture);}if(hallucinations!=null)hallucinations.close();}
 @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
  if(command.getName().equalsIgnoreCase("gal")){if(!sender.hasPermission("zuluz.hallucinate")){sender.sendMessage("§c[Z_Uluz] Недостаточно прав.");return true;}if(args.length!=2||(!args[1].equalsIgnoreCase("on")&&!args[1].equalsIgnoreCase("off"))){sender.sendMessage("§e[Z_Uluz] Использование: /gal <ник> <on|off>");return true;}Player target=Bukkit.getPlayerExact(args[0]);if(target==null){sender.sendMessage("§c[Z_Uluz] Игрок не найден в сети.");return true;}boolean enabled=args[1].equalsIgnoreCase("on");hallucinations.set(target,enabled);sender.sendMessage("§d[Z_Uluz] Галлюцинации для "+target.getName()+(enabled?" включены.":" выключены."));return true;}
  if(!sender.hasPermission("zuluz.capture")){sender.sendMessage("§c[Z_Uluz] Недостаточно прав.");return true;}if(!(sender instanceof Player operator)){sender.sendMessage("§c[Z_Uluz] Команда доступна только игроку.");return true;}
  if(args.length==1&&args[0].equalsIgnoreCase("stop")){if(!capture.stop(operator))operator.sendMessage("§e[Z_Uluz] Вы не участвуете в захвате.");return true;}
  if(args.length!=2){operator.sendMessage("§e[Z_Uluz] Использование: /zahvat <ник> <1sec|1min> или /zahvat stop");return true;}Player target=Bukkit.getPlayerExact(args[0]);if(target==null){operator.sendMessage("§c[Z_Uluz] Игрок не найден в сети.");return true;}int seconds=duration(args[1]);if(seconds<1||seconds>300){operator.sendMessage("§c[Z_Uluz] Укажите время от 1sec до 5min.");return true;}String error=capture.start(operator,target,seconds);if(error!=null)operator.sendMessage("§c[Z_Uluz] "+error);return true;
 }
 static int duration(String input){Matcher m=Pattern.compile("(?i)^(\\d{1,4})(sec|s|min|m)$").matcher(input);if(!m.matches())return -1;int n=Integer.parseInt(m.group(1));return m.group(2).toLowerCase(Locale.ROOT).startsWith("m")?n*60:n;}
 @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){List<String> options=new ArrayList<>();if(args.length==1){for(Player p:Bukkit.getOnlinePlayers())options.add(p.getName());if(command.getName().equalsIgnoreCase("zahvat"))options.add("stop");}else if(args.length==2){if(command.getName().equalsIgnoreCase("gal"))options.addAll(List.of("on","off"));else options.addAll(List.of("30sec","1min","5min"));}if(args.length>2)return List.of();String prefix=args[args.length-1].toLowerCase(Locale.ROOT);return options.stream().filter(s->s.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();}
}
