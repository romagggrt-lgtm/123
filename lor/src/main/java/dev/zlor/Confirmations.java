package dev.zlor;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;

public final class Confirmations {
    public record Pending(UUID token,UUID owner,String world,long revision,long restrictionsRevision,long expires,String kind,String description,Runnable apply){}
    private final ZLorPlugin p;
    private final Map<UUID,Pending> pending=new HashMap<>();
    public Confirmations(ZLorPlugin p){this.p=p;}
    public Pending ask(Player player,String kind,String description,long lifetime,Runnable apply){
        p.roles.require(player);String world=p.worldKey(player.getWorld());
        var value=new Pending(UUID.randomUUID(),player.getUniqueId(),world,p.state().dimensionRevisions.getOrDefault(world,0L),
                p.state().restrictionsRevision,p.now()+lifetime,kind,description,apply);
        pending.put(player.getUniqueId(),value);
        String base=kind.equals("default")?"/lore default":"/lore void";
        player.sendMessage(Component.text(description+"\n").append(
                Component.text("[Подтвердить]").color(net.kyori.adventure.text.format.NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand(base+" confirm "+value.token())))
                .append(Component.text("  [Отмена]").color(net.kyori.adventure.text.format.NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand(base+" cancel "+value.token()))));
        return value;
    }
    public void finish(Player player,String token,boolean apply){
        p.roles.require(player);var value=pending.get(player.getUniqueId());
        if(value==null||!value.token.toString().equals(token))throw new IllegalArgumentException("Подтверждение не найдено или принадлежит другому игроку");
        pending.remove(player.getUniqueId());
        if(!apply){player.sendMessage("Изменение отменено");return;}
        if(p.now()>value.expires||!value.world.equals(p.worldKey(player.getWorld()))||
                value.revision!=p.state().dimensionRevisions.getOrDefault(value.world,0L)||value.restrictionsRevision!=p.state().restrictionsRevision)
            throw new IllegalArgumentException("Подтверждение устарело: откройте форму заново");
        value.apply.run();
    }
    public Pending get(UUID id){return pending.get(id);}
    public void clear(UUID id){pending.remove(id);}
    public void expire(){pending.values().removeIf(v->p.now()>v.expires);}
}
