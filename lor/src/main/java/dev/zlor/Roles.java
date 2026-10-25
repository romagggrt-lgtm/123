package dev.zlor;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class Roles {
    private final ZLorPlugin p;
    private static final UUID CONSOLE=new UUID(0,0);
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("dd.MM HH:mm:ss").withZone(ZoneId.of("Europe/Moscow"));
    public Roles(ZLorPlugin plugin){p=plugin;}
    public boolean scen(Player player){return p.state().roles.containsKey(player.getUniqueId());}
    public Player require(CommandSender sender){
        if(!(sender instanceof Player player))throw new IllegalArgumentException("Эта команда требует игрока и его измерение");
        if(!scen(player))throw new IllegalArgumentException("Нужна роль сценариста: /scen add <ник>");return player;
    }
    public boolean admin(CommandSender sender){return !(sender instanceof Player)||sender.isOp()||sender.hasPermission("zlor.admin");}
    public UUID known(String name){
        var online=Bukkit.getPlayerExact(name);if(online!=null)return online.getUniqueId();
        return p.state().names.entrySet().stream().filter(e->e.getValue().equalsIgnoreCase(name)).map(Map.Entry::getKey).findFirst().orElse(null);
    }
    public void audit(State state,CommandSender source,String action,String detail,String world){
        var id=source instanceof Player player?player.getUniqueId():CONSOLE;
        state.actions.put(id,new State.Audit(action,detail,world,p.now()));
    }
    public void command(CommandSender sender,String[] args){
        if(args.length==0){list(sender);return;}
        boolean allowed=admin(sender)||(sender instanceof Player player&&scen(player));
        if(!allowed)throw new IllegalArgumentException("Назначать роли может OP, консоль или сценарист");
        if(args.length!=2||!Set.of("add","delete","remove").contains(args[0]))throw new IllegalArgumentException("/scen [add|delete] <ник>");
        var target=known(args[1]);if(target==null)throw new IllegalArgumentException("Первое назначение требует присутствия игрока на сервере");
        boolean add=args[0].equals("add");String name=p.state().names.getOrDefault(target,args[1]);
        p.store.change(s->{
            if(!admin(sender)&&(!(sender instanceof Player player)||!s.roles.containsKey(player.getUniqueId())))throw new IllegalArgumentException("Роль больше не действует");
            if(add){if(s.roles.containsKey(target))throw new IllegalArgumentException("Этот игрок уже сценарист");s.roles.put(target,new State.Role(name,p.now()));}
            else if(s.roles.remove(target)==null)throw new IllegalArgumentException("У этого игрока нет роли сценариста");
            s.names.put(target,name);audit(s,sender,"команда",(add?"Назначен ":"Снят ")+name,"");
        },s->{
            Player online=Bukkit.getPlayer(target);
            if(online!=null){if(!add)p.clearPersonal(online);online.updateCommands();}
            sender.sendMessage(add?"Роль сценариста назначена: "+name:"Роль сценариста снята: "+name);
        },e->p.error(sender,e));
    }
    public void list(CommandSender sender){
        if(!admin(sender)&&(!(sender instanceof Player player)||!scen(player)))throw new IllegalArgumentException("Нет доступа к списку сценаристов");
        sender.sendMessage("Сценаристы:");
        p.state().roles.forEach((id,role)->{
            var online=Bukkit.getPlayer(id);var action=p.state().actions.get(id);
            sender.sendMessage((online==null?"○ ":"● ")+p.state().names.getOrDefault(id,role.lastName())+
                    (action==null?" — изменений нет":" — "+action.detail()+" · "+action.dimension()+" · "+TIME.format(Instant.ofEpochMilli(action.at()))+" МСК"));
        });
        var console=p.state().actions.get(CONSOLE);if(console!=null)sender.sendMessage("Консоль — "+console.detail()+" · "+TIME.format(Instant.ofEpochMilli(console.at()))+" МСК");
    }
}
