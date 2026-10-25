package dev.zlor;

import dev.zlor.rules.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.util.Vector;

/** Journal both objects as one operation, before changing any world block. */
public final class Structures implements Listener {
    private final ZLorPlugin p;
    private final Set<String> busy=new HashSet<>(),failed=new HashSet<>();
    public Structures(ZLorPlugin p){this.p=p;}
    public State.Structures current(World world){
        var saved=p.state().structures.get(p.worldKey(world));if(saved!=null)return saved;
        var result=new State.Structures();result.world=p.worldKey(world);result.entryY=48;
        var battle=world.getEnvironment()==World.Environment.THE_END?world.getEnderDragonBattle():null;Location portal=battle==null?null:battle.getEndPortalLocation();
        if(portal!=null){result.portalX=portal.getBlockX();result.portalY=portal.getBlockY();result.portalZ=portal.getBlockZ();result.initialized=true;}
        return result;
    }
    public boolean protects(Location at){
        if(at.getWorld()==null||at.getWorld().getEnvironment()!=World.Environment.THE_END)return false;
        var s=current(at.getWorld());if(!entryReady(at.getWorld(),s))return false;
        double entry=s.move==null?s.entryY:s.move.oldEntryY;
        double dx=at.getX()-s.entryX-.5,dy=at.getY()-entry-1,dz=at.getZ()-s.entryZ-.5;
        return dx*dx+dy*dy+dz*dz<=Math.pow(Math.sqrt(12.5)+1,2);
    }
    public boolean entryReady(World world,State.Structures s){return s.initialized&&world.isChunkLoaded(s.entryX>>4,s.entryZ>>4)&&world.getBlockAt(s.entryX,s.entryY,s.entryZ).getType()==Material.OBSIDIAN;}
    public Location origin(World world){var s=current(world);return new Location(world,s.portalX+.5,s.portalY,s.portalZ+.5);}
    public void command(Player player,String[] args){
        World world=player.getWorld();if(world.getEnvironment()!=World.Environment.THE_END)throw new IllegalArgumentException("Эта команда выполняется в Энде");
        if(args.length==0||args.length==1&&args[0].equals("status")){status(player);return;}
        if(args.length==2&&args[0].equals("raise")){raise(player,integer(args[1]));return;}
        if(args.length==3&&args[0].equals("rule")){
            double threshold=DimensionVoid.parse(args[1]);int target=integer(args[2]);validateHeight(world,target);
            p.change(player,"Правило переноса: Войд "+threshold+" → портал "+target,s->{var value=s.structures.computeIfAbsent(p.worldKey(world),k->current(world));value.rule=true;value.threshold=threshold;value.target=target;s.touch(p.worldKey(world));},s->player.sendMessage("Одноразовое правило переноса сохранено"));return;
        }
        if(args.length==1&&args[0].equals("stop")){
            p.change(player,"Остановлено правило переноса",s->{var value=s.structures.computeIfAbsent(p.worldKey(world),k->current(world));value.rule=false;s.touch(p.worldKey(world));},s->player.sendMessage("Правило остановлено; высоты сохранены"));return;
        }
        if(args.length==1&&args[0].equals("recover")){failed.remove(p.worldKey(world));recover(world);return;}
        throw new IllegalArgumentException("/lore end [status|raise Y|rule voidY targetY|stop|recover]");
    }
    public static int integer(String raw){double value=DimensionVoid.parse(raw);if(value!=Math.rint(value))throw new IllegalArgumentException("Высота сооружения должна быть целым числом");return (int)value;}
    private void validateHeight(World world,int y){int min=EndStructureHeights.minimumPortalY(p.voids.height(world),world.getMinHeight()),max=EndStructureHeights.maximumPortalY(world.getMaxHeight());if(y<min||y>max)throw new IllegalArgumentException("Безопасный диапазон портала: "+min+"…"+max+"; площадка Y+10");}
    public void status(Player player){
        var s=current(player.getWorld());player.sendMessage("Портал Y="+s.portalY+" · вход Y="+s.entryY+" · "+(s.rule?"Правило: Войд "+s.threshold+" → "+s.target:"Правило выключено")+
                " · диапазон "+EndStructureHeights.minimumPortalY(p.voids.height(player.getWorld()),player.getWorld().getMinHeight())+"…"+EndStructureHeights.maximumPortalY(player.getWorld().getMaxHeight())+(s.move!=null?" · Ожидает восстановления":""));
    }
    public void raise(Player player,int y){p.roles.require(player);raise(player.getWorld(),y,player);}
    private void raise(World world,int y,Player player){
        String key=p.worldKey(world);validateHeight(world,y);
        if(busy.contains(key)||current(world).move!=null||failed.contains(key))throw new IllegalArgumentException("Сначала завершите восстановление переноса");
        if(p.dragons.active(world))throw new IllegalArgumentException("Сначала завершите бой или ритуал дракона");
        State.Structures previous=current(world);if(!previous.initialized)throw new IllegalArgumentException("Центральный портал ещё не найден");
        if(previous.portalY==y&&previous.entryY==y+10){if(player!=null)player.sendMessage("Оба сооружения уже на нужной высоте");return;}
        var move=plan(world,previous,y);busy.add(key);
        p.store.change(s->{
            if(player!=null)p.roles.require(player);
            var structure=s.structures.computeIfAbsent(key,k->StateStore.JSON.fromJson(StateStore.JSON.toJson(previous),State.Structures.class));
            if(structure.move!=null)throw new IllegalArgumentException("Перенос уже записан");structure.move=move;
            if(player!=null)p.roles.audit(s,player,"команда","Портал Y="+y+", площадка Y="+(y+10),key);s.touch(key);
        },s->{busy.remove(key);try{apply(world,move,previous);if(player!=null)player.sendMessage("Портал перенесён на Y="+y+", вход на Y="+(y+10));}catch(Exception e){failed.add(key);p.getLogger().severe("Перенос приостановлен, журнал сохранён: "+e);if(player!=null)p.error(player,e);}},e->{busy.remove(key);if(player!=null)p.error(player,e);});
    }
    public State.Move plan(World world,State.Structures old,int y){
        var source=new LinkedHashMap<State.Pos,String>();var layout=new LinkedHashMap<State.Pos,String>();
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
            Block block=world.getBlockAt(old.entryX+dx,old.entryY,old.entryZ+dz);
            if(block.getType()!=Material.OBSIDIAN)throw new IllegalArgumentException("Площадка входа повреждена: "+block.getX()+" "+block.getY()+" "+block.getZ());
            source.put(p.devices.pos(block),block.getBlockData().getAsString());
            layout.put(new State.Pos(p.worldKey(world),old.entryX+dx,y+10,old.entryZ+dz),Material.OBSIDIAN.createBlockData().getAsString());
            for(int h=1;h<=3;h++)layout.put(new State.Pos(p.worldKey(world),old.entryX+dx,y+10+h,old.entryZ+dz),Material.AIR.createBlockData().getAsString());
        }
        boolean bedrock=false;
        for(int dx=-3;dx<=3;dx++)for(int dy=-1;dy<=4;dy++)for(int dz=-3;dz<=3;dz++){
            Block block=world.getBlockAt(old.portalX+dx,old.portalY+dy,old.portalZ+dz);
            if(!Set.of(Material.BEDROCK,Material.END_PORTAL,Material.TORCH,Material.WALL_TORCH).contains(block.getType()))continue;
            if(block.getType()==Material.BEDROCK)bedrock=true;
            source.put(p.devices.pos(block),block.getBlockData().getAsString());layout.put(new State.Pos(p.worldKey(world),block.getX(),y+dy,block.getZ()),block.getBlockData().getAsString());
        }
        if(!bedrock)throw new IllegalArgumentException("Основание портала не найдено");
        for(var entry:layout.entrySet()){
            var coordinate=entry.getKey();Block block=world.getBlockAt(coordinate.x(),coordinate.y(),coordinate.z());
            if(!block.getType().isAir()&&!source.containsKey(coordinate)&&!block.getBlockData().getAsString().equals(entry.getValue()))throw new IllegalArgumentException("Целевая область занята: "+coordinate.x()+" "+coordinate.y()+" "+coordinate.z());
        }
        var changed=new LinkedHashMap<State.Pos,String>();source.keySet().forEach(coordinate->changed.put(coordinate,Material.AIR.createBlockData().getAsString()));changed.putAll(layout);
        var result=new State.Move();result.oldPortalY=old.portalY;result.newPortalY=y;result.oldEntryY=old.entryY;result.newEntryY=y+10;
        changed.forEach((coordinate,after)->{
            String before=world.getBlockAt(coordinate.x(),coordinate.y(),coordinate.z()).getBlockData().getAsString();
            if(!before.equals(after)){result.before.add(new State.BlockImage(coordinate,before));result.after.add(new State.BlockImage(coordinate,after));}
        });return result;
    }
    private void apply(World world,State.Move move,State.Structures previous)throws ReflectiveOperationException{
        if(move.before.size()!=move.after.size()||move.before.size()>2048)throw new IllegalArgumentException("Некорректный журнал переноса");
        for(int i=0;i<move.before.size();i++){
            var before=move.before.get(i);var after=move.after.get(i);Block block=world.getBlockAt(before.pos().x(),before.pos().y(),before.pos().z());String actual=block.getBlockData().getAsString();
            if(!actual.equals(before.data())&&!actual.equals(after.data()))throw new IllegalArgumentException("Блок изменился во время переноса: "+before.pos());
        }
        // Validate the native origin adapter before changing blocks in a real CraftWorld.
        if(world.getClass().getName().contains("craftbukkit"))NativeSupport.origin(world,new Location(world,previous.portalX,move.newPortalY,previous.portalZ));
        for(var image:move.after)world.getBlockAt(image.pos().x(),image.pos().y(),image.pos().z()).setBlockData(Bukkit.createBlockData(image.data()),false);
        for(Player player:world.getPlayers())if(Math.abs(player.getX()-previous.entryX-.5)<=2.9&&Math.abs(player.getZ()-previous.entryZ-.5)<=2.9&&player.getY()>=move.oldEntryY&&player.getY()<=move.oldEntryY+3){var location=player.getLocation();location.setY(move.newEntryY+1);player.teleport(location);player.setVelocity(new Vector());}
        p.store.change(s->{var structure=s.structures.get(p.worldKey(world));if(structure!=null&&structure.move!=null&&structure.move.token.equals(move.token)){
            structure.portalY=move.newPortalY;structure.entryY=move.newEntryY;structure.rule=false;structure.initialized=true;structure.move=null;
        }},s->p.dragons.moved(world,origin(world)),e->p.getLogger().severe("Завершение переноса не сохранено; журнал остаётся: "+e));
    }
    public void recover(World world){String key=p.worldKey(world);var structure=p.state().structures.get(key);if(structure==null||structure.move==null||busy.contains(key)||failed.contains(key))return;
        busy.add(key);try{apply(world,structure.move,structure);}catch(Exception e){failed.add(key);p.getLogger().severe("Не восстановлен перенос "+key+": "+e);}finally{busy.remove(key);}}
    public void hidePortal(World world,boolean hidden){
        var structure=current(world);if(!structure.initialized)return;
        if(hidden){
            if(structure.portalHidden)return;
            var cells=new ArrayList<State.BlockImage>();
            for(int dx=-3;dx<=3;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-3;dz<=3;dz++){
                Block block=world.getBlockAt(structure.portalX+dx,structure.portalY+dy,structure.portalZ+dz);if(block.getType()==Material.END_PORTAL)cells.add(new State.BlockImage(p.devices.pos(block),block.getBlockData().getAsString()));
            }
            // Vanilla's four-crystal ritual may already have switched the fountain
            // to its inactive AIR layout. Journal only the known basin of an intact
            // bedrock fountain so stop can reopen it without regenerating terrain.
            if(cells.isEmpty()&&world.getBlockAt(structure.portalX,structure.portalY,structure.portalZ).getType()==Material.BEDROCK&&
                    world.getBlockAt(structure.portalX+3,structure.portalY,structure.portalZ).getType()==Material.BEDROCK&&
                    world.getBlockAt(structure.portalX-3,structure.portalY,structure.portalZ).getType()==Material.BEDROCK&&
                    world.getBlockAt(structure.portalX,structure.portalY,structure.portalZ+3).getType()==Material.BEDROCK&&
                    world.getBlockAt(structure.portalX,structure.portalY,structure.portalZ-3).getType()==Material.BEDROCK){
                for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)if(dx*dx+dz*dz>0&&dx*dx+dz*dz<6.25){
                    Block block=world.getBlockAt(structure.portalX+dx,structure.portalY,structure.portalZ+dz);
                    if(block.getType().isAir())cells.add(new State.BlockImage(p.devices.pos(block),Material.END_PORTAL.createBlockData().getAsString()));
                }
            }
            p.store.change(s->{var current=s.structures.computeIfAbsent(p.worldKey(world),k->structure);current.portalHidden=true;current.portalCells=cells;},s->cells.forEach(image->{var coordinate=image.pos();Block block=world.getBlockAt(coordinate.x(),coordinate.y(),coordinate.z());if(block.getType()==Material.END_PORTAL)block.setType(Material.AIR,false);}),e->p.getLogger().warning("Не скрыт портал: "+e));
        }else if(structure.portalHidden){
            var cells=List.copyOf(structure.portalCells);
            for(var image:cells){var coordinate=image.pos();Block block=world.getBlockAt(coordinate.x(),coordinate.y(),coordinate.z());if(block.getType().isAir())block.setBlockData(Bukkit.createBlockData(image.data()),false);}
            p.store.change(s->{var current=s.structures.get(p.worldKey(world));if(current!=null){current.portalHidden=false;current.portalCells.clear();}});
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void entry(PlayerPortalEvent event){
        if(event.getTo()==null||event.getTo().getWorld().getEnvironment()!=World.Environment.THE_END)return;
        var s=current(event.getTo().getWorld());if(s.move!=null){event.setCancelled(true);event.getPlayer().sendMessage("Вход временно закрыт: восстанавливается перенос площадки");return;}
        if(s.initialized){event.setTo(new Location(event.getTo().getWorld(),s.entryX+.5,s.entryY+1,s.entryZ+.5,event.getTo().getYaw(),event.getTo().getPitch()));event.setCanCreatePortal(false);}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void platform(PortalCreateEvent event){if(event.getReason()==PortalCreateEvent.CreateReason.END_PLATFORM&&current(event.getWorld()).initialized)event.setCancelled(true);}
    public void tick(){if(p.tick%20!=0)return;
        for(World world:Bukkit.getWorlds())if(world.getEnvironment()==World.Environment.THE_END){
            recover(world);var structure=current(world);
            if(structure.rule&&structure.move==null&&!busy.contains(p.worldKey(world))&&p.voids.height(world)>=structure.threshold){
                try{raise(world,structure.target,null);}catch(IllegalArgumentException e){if(failed.add(p.worldKey(world)))p.getLogger().warning("Автоперенос ожидает безопасных условий: "+e.getMessage());}
            }
        }
    }
}
