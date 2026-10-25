package dev.zlor;

import dev.zlor.rules.*;
import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;

public final class Restrictions implements Listener {
    private final ZLorPlugin p;
    private final FlightFailures flights=new FlightFailures();
    private final Map<UUID,Long> refusals=new HashMap<>();
    public Restrictions(ZLorPlugin p){this.p=p;}
    public boolean end(World world){return world.getEnvironment()==World.Environment.THE_END;}
    public boolean blocked(World world){return end(world)&&p.state().restrictions.gate().blocks(p.voids.height(world));}
    public double interval(Player player){double value=p.state().restrictions.flight().interval(p.voids.height(player.getWorld()));return p.devices.protectedAt(player.getLocation())?value*4:value;}
    private void refused(Player player,Location from){
        if(p.now()-refusals.getOrDefault(player.getUniqueId(),-2000L)<2000)return;refusals.put(player.getUniqueId(),p.now());
        player.sendActionBar(Component.text("Врата молчат"));var gate=p.state().restrictions.gate();p.audio.refusal(player,from,gate.sound(),gate.volume());
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void teleport(PlayerTeleportEvent event){
        if(event.getCause()==PlayerTeleportEvent.TeleportCause.END_GATEWAY&&blocked(event.getFrom().getWorld())){event.setCancelled(true);refused(event.getPlayer(),event.getFrom());}
        if(!event.isCancelled()&&event.getTo()!=null&&!event.getTo().getWorld().equals(event.getFrom().getWorld()))p.clearPersonal(event.getPlayer());
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void teleport(EntityTeleportEvent event){
        if(!blocked(event.getFrom().getWorld()))return;
        boolean gateway=false;Location from=event.getFrom();
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++)if(from.getBlock().getRelative(dx,dy,dz).getType()==Material.END_GATEWAY)gateway=true;
        if(!gateway)return;event.setCancelled(true);
        if(event.getEntity() instanceof EnderPearl pearl&&pearl.getShooter() instanceof Player player)refused(player,from);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void glide(EntityToggleGlideEvent event){
        if(!(event.getEntity() instanceof Player player)||!event.isGliding()||!eligible(player))return;
        if(p.state().restrictions.flight().blocked(p.voids.height(player.getWorld())))event.setCancelled(true);
    }
    private boolean eligible(Player player){return end(player.getWorld())&&player.getGameMode()!=GameMode.CREATIVE&&player.getGameMode()!=GameMode.SPECTATOR&&!player.isDead();}
    public void tick(){
        for(Player player:Bukkit.getOnlinePlayers()){
            if(!eligible(player)){flights.remove(player.getUniqueId());continue;}
            var settings=p.state().restrictions.flight();double y=p.voids.height(player.getWorld());
            if(settings.blocked(y)){if(player.isGliding())player.setGliding(false);flights.remove(player.getUniqueId());continue;}
            if(flights.fold(player.getUniqueId(),player.isGliding()&&settings.enabled(),interval(player),p.now()))player.setGliding(false);
        }
    }
    public void gate(Player player,RestrictionSettings.Gate value,boolean confirmed){
        p.roles.require(player);
        if(!confirmed){p.confirmations.ask(player,"restrictions","Дальние врата: "+(value.enabled()?"включить от Y > "+VoidForecast.number(value.y()):"остановить правило"),30_000,()->gate(player,value,true));return;}
        p.change(player,"Изменены блокировки дальних врат",s->{s.restrictions=s.restrictions.gate(value);s.restrictionsRevision++;},s->player.sendMessage("Настройки дальних врат сохранены"));
    }
    public void flight(Player player,RestrictionSettings.Flight value,boolean confirmed){
        p.roles.require(player);
        if(!confirmed){p.confirmations.ask(player,"restrictions","Элитры: "+(value.enabled()?"включить сбои и сохранить пороги":"остановить правило"),30_000,()->flight(player,value,true));return;}
        p.change(player,"Изменены блокировки элитр",s->{s.restrictions=s.restrictions.flight(value);s.restrictionsRevision++;},s->{flights.clear();player.sendMessage("Настройки элитр сохранены");});
    }
    public void reset(Player player,boolean confirmed){
        p.roles.require(player);
        if(!confirmed){p.confirmations.ask(player,"default","Сбросить границы и блокировки? Роли, иммунитеты, вещи и топливо сохраняются.",60_000,()->reset(player,true));return;}
        p.change(player,"Частичный сброс границ и блокировок",s->{s.dimensions.clear();s.dimensionRevisions.replaceAll((key,rev)->rev+1);s.restrictions=RestrictionSettings.defaults();s.restrictionsRevision++;},s->{
            flights.clear();Bukkit.getWorlds().forEach(p.nativeBoundary::update);player.sendMessage("Границы и блокировки сброшены. Предыдущее состояние сохранено в state.json.bak");
        });
    }
    public void leave(UUID id){flights.remove(id);refusals.remove(id);}
}
