package dev.zlor;

import dev.zlor.rules.*;
import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.damage.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;

public final class Voids implements Listener {
    private record Measurement(String world,EquipmentSlot hand,long started){}
    private final ZLorPlugin p;
    private final Map<UUID,Measurement> measures=new HashMap<>();
    private final Map<UUID,Long> lastMeasure=new HashMap<>(),lastBottle=new HashMap<>();
    private final Set<String> clamping=new HashSet<>();
    public Voids(ZLorPlugin p){this.p=p;}
    public double nativeY(World world){return world.getMinHeight()-64d;}
    public double ceiling(World world){return Math.min(world.getMaxHeight(),p.getConfig().getDouble("safety.max-height."+p.worldKey(world).replace('.','_'),world.getMaxHeight()));}
    public DimensionVoid state(World world){return p.state().dimensions.getOrDefault(p.worldKey(world),DimensionVoid.vanilla(nativeY(world),p.now()));}
    public double height(World world){return Math.min(state(world).height(p.now()),ceiling(world));}
    public void command(Player player,String[] args){
        if(args.length==0){info(player,null);return;}
        if(args.length==1){
            if(args[0].equals("stop")){set(player,"stop",0,false,false,false);return;}
            if(args[0].equals("view")){p.visuals.toggleVoid(player);return;}
            info(player,DimensionVoid.parse(args[0]));return;
        }
        if(args.length==2&&(args[0].equals("confirm")||args[0].equals("cancel"))){p.confirmations.finish(player,args[1],args[0].equals("confirm"));return;}
        if(args.length<3||args.length>4)throw new IllegalArgumentException("/lore void height|speed set|vector [minute] <число>");
        boolean speed=args[0].equals("speed"),height=args[0].equals("height"),vector=args[1].equals("vector");
        if((!speed&&!height)||(!vector&&!args[1].equals("set")))throw new IllegalArgumentException("Нужны height|speed и set|vector");
        boolean minute=args.length==4;
        if(minute&&(!speed||!args[2].equals("minute")))throw new IllegalArgumentException("Режим minute применяется только к скорости");
        set(player,speed?"speed":"height",DimensionVoid.parse(args[args.length-1]),vector,minute,false);
    }
    public void set(Player player,String operation,double amount,boolean vector,boolean minute,boolean confirmed){
        p.roles.require(player);World world=player.getWorld();String key=p.worldKey(world);
        if(!p.nativeBoundary.ready(world))throw new IllegalArgumentException("Нативная граница пока недоступна: "+p.nativeBoundary.status(world));
        DimensionVoid old=state(world), next=next(old,world,operation,amount,vector,minute);
        if(next.baseY()>ceiling(world))throw new IllegalArgumentException("В этом измерении предел высоты: "+VoidForecast.number(ceiling(world)));
        if(!confirmed&&operation.equals("height")&&Math.abs(next.height(p.now())-height(world))>=p.getConfig().getDouble("safety.confirmation-height-delta",32)){
            p.confirmations.ask(player,"void","Изменить границу на "+VoidForecast.number(next.height(p.now()))+"?",30_000,
                    ()->set(player,operation,amount,vector,minute,true));return;
        }
        p.change(player,"Войд: "+operation+" "+(vector?"vector ":"set ")+VoidForecast.input(amount)+(minute?" за минуту":""),s->{
            DimensionVoid current=s.dimensions.getOrDefault(key,DimensionVoid.vanilla(nativeY(world),p.now()));
            DimensionVoid value=next(current,world,operation,amount,vector,minute);
            if(value.baseY()>ceiling(world))throw new IllegalArgumentException("Превышен предел высоты");
            s.dimensions.put(key,value);s.touch(key);
        },s->{p.nativeBoundary.update(world);player.sendMessage("Войд: Y "+VoidForecast.number(height(world))+", "+VoidForecast.number(state(world).perFourHours()/240)+" бл/мин");});
    }
    private DimensionVoid next(DimensionVoid current,World world,String op,double value,boolean vector,boolean minute){
        current=current.limited(ceiling(world),p.now());
        return switch(op){
            case "stop"->DimensionVoid.vanilla(nativeY(world),p.now());
            case "height"->vector?current.vector(value,p.now()):current.set(value,p.now());
            case "speed"->vector?current.speedVector(value,minute,p.now()):current.speed(value,minute,p.now());
            default->throw new IllegalArgumentException("Неизвестная операция");
        };
    }
    public void info(Player player,Double target){
        var state=state(player.getWorld());double y=height(player.getWorld());
        player.sendMessage("Войд · "+p.worldKey(player.getWorld())+" · Y "+VoidForecast.number(y)+" · "+VoidForecast.number(state.perFourHours()/240)+" бл/мин · "+VoidForecast.number(state.perFourHours()/4)+" бл/ч");
        if(target!=null)player.sendMessage("Прогноз до "+VoidForecast.number(target)+": "+VoidForecast.target(y,state.perFourHours(),target,365));
        else for(int step:new int[]{10,50}){double level=VoidForecast.nextLevel(y,state.perFourHours(),step);player.sendMessage("До Y "+VoidForecast.number(level)+": "+VoidForecast.target(y,state.perFourHours(),level,365));}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void damage(EntityDamageEvent event){
        if(event.getCause()!=EntityDamageEvent.DamageCause.VOID)return;
        Entity entity=event.getEntity();
        if(p.protectedAt(entity)){event.setCancelled(true);return;}
        if(entity instanceof Player player&&p.loot.inShield(player.getLocation()))event.setDamage(event.getDamage()/8);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void use(PlayerInteractEvent event){
        if(event.getHand()==null||(!event.getAction().isRightClick())||event.getPlayer().getGameMode()==GameMode.SPECTATOR)return;
        Player player=event.getPlayer();String id=p.items.id(event.getItem());
        if("voidometer".equals(id)){
            event.setCancelled(true);long last=lastMeasure.getOrDefault(player.getUniqueId(),Long.MIN_VALUE/2);
            if(p.tick-last<60||measures.containsKey(player.getUniqueId()))return;
            lastMeasure.put(player.getUniqueId(),p.tick);measures.put(player.getUniqueId(),new Measurement(p.worldKey(player.getWorld()),event.getHand(),p.tick));
            player.sendActionBar(Component.text("Войдометр ⟳"));return;
        }
        if(event.getItem()==null||event.getItem().getType()!=Material.GLASS_BOTTLE)return;
        if(p.tick-lastBottle.getOrDefault(player.getUniqueId(),-100L)<5)return;
        if(p.dragons.collectBreath(player,event.getHand())){event.setCancelled(true);lastBottle.put(player.getUniqueId(),p.tick);return;}
        if(collectVoid(player,event.getHand())){event.setCancelled(true);lastBottle.put(player.getUniqueId(),p.tick);}
    }
    public boolean collectVoid(Player player,EquipmentSlot hand){
        World world=player.getWorld();double y=height(world);var eye=player.getEyeLocation();var direction=eye.getDirection();
        if(y<=nativeY(world)||p.devices.protectedAt(player.getLocation()))return false;
        var attribute=player.getAttribute(org.bukkit.attribute.Attribute.BLOCK_INTERACTION_RANGE);
        double reach=attribute==null?(player.getGameMode()==GameMode.CREATIVE?5:4.5):attribute.getValue();
        Location target;
        if(eye.getY()<=y)target=eye.clone();
        else{
            if(direction.getY()>=-1e-7)return false;double distance=(y-eye.getY())/direction.getY();
            if(distance<0||distance>reach)return false;target=eye.clone().add(direction.multiply(distance));
            var hit=world.rayTraceBlocks(eye,eye.getDirection(),distance,FluidCollisionMode.NEVER,true);
            if(hit!=null)return false;
        }
        if(p.devices.protectedAt(target))return false;
        return giveBottle(player,hand,p.items.make("liquid_void"));
    }
    public boolean giveBottle(Player player,EquipmentSlot hand,org.bukkit.inventory.ItemStack result){
        var current=player.getInventory().getItem(hand);if(current.getType()!=Material.GLASS_BOTTLE)return false;
        boolean creative=player.getGameMode()==GameMode.CREATIVE;
        if(!creative&&current.getAmount()==1){player.getInventory().setItem(hand,result);return true;}
        if(!p.items.capacity(player.getInventory(),result))return false;
        if(!creative)current.subtract(1);player.getInventory().addItem(result);return true;
    }
    public void tick(){
        for(var it=measures.entrySet().iterator();it.hasNext();){
            var entry=it.next();var player=Bukkit.getPlayer(entry.getKey());var measure=entry.getValue();
            if(player==null||player.isDead()||!measure.world.equals(p.worldKey(player.getWorld()))||!p.items.is(player.getInventory().getItem(measure.hand),"voidometer")){it.remove();continue;}
            long elapsed=p.tick-measure.started;
            if(elapsed<20){if(elapsed%4==0)player.playSound(player.getLocation(),"smplore:meter_click",SoundCategory.PLAYERS,.5f,1);}
            else{
                double y=height(player.getWorld());var state=state(player.getWorld());
                player.sendActionBar(Component.text(!state.active()&&Math.abs(y-nativeY(player.getWorld()))<1e-7?"Изменений не обнаружено":"Высота Войда: "+VoidForecast.number(y)));
                it.remove();
            }
        }
        if(p.tick%20==0)for(World world:Bukkit.getWorlds()){
            var current=state(world);var limited=current.limited(ceiling(world),p.now());String key=p.worldKey(world);
            if(current.perFourHours()!=limited.perFourHours()&&clamping.add(key))p.store.change(s->{s.dimensions.put(key,limited);s.touch(key);},s->clamping.remove(key),e->clamping.remove(key));
            p.nativeBoundary.update(world);
        }
    }
    public void leave(UUID player){measures.remove(player);lastBottle.remove(player);lastMeasure.remove(player);}
}
