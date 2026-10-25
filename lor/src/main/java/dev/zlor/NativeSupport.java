package dev.zlor;

import java.lang.reflect.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;

/** Small version-specific adapter; each caller exposes failure in diagnostics. */
public final class NativeSupport {
    private NativeSupport(){}
    public static Object handle(Object craft)throws ReflectiveOperationException{return craft.getClass().getMethod("getHandle").invoke(craft);}
    public static Object invoke(Object instance,String name,Object...args)throws ReflectiveOperationException{
        for(Method method:instance.getClass().getMethods()){
            if(!method.getName().equals(name)||method.getParameterCount()!=args.length)continue;
            Class<?>[] types=method.getParameterTypes();boolean match=true;
            for(int i=0;i<types.length;i++)if(args[i]!=null&&!boxed(types[i]).isInstance(args[i])){match=false;break;}
            if(match)return method.invoke(instance,args);
        }
        throw new NoSuchMethodException(instance.getClass().getName()+"."+name+"/"+args.length);
    }
    private static Class<?> boxed(Class<?> type){return type==int.class?Integer.class:type==long.class?Long.class:type==boolean.class?Boolean.class:type==float.class?Float.class:type==double.class?Double.class:type;}
    public static Object pos(int x,int y,int z)throws ReflectiveOperationException{return Class.forName("net.minecraft.core.BlockPos").getConstructor(int.class,int.class,int.class).newInstance(x,y,z);}
    public static void charge(EnderDragon dragon,Location target)throws ReflectiveOperationException{
        Object manager=invoke(handle(dragon),"getPhaseManager");
        Object phase=invoke(manager,"getCurrentPhase");
        Object vector=Class.forName("net.minecraft.world.phys.Vec3").getConstructor(double.class,double.class,double.class).newInstance(target.getX(),target.getY(),target.getZ());
        invoke(phase,"setTarget",vector);
    }
    public static double miningRatio(Player player,Block block,double hardness){
        try{
            Object level=handle(block.getWorld()),coordinate=pos(block.getX(),block.getY(),block.getZ());
            Object stone=Class.forName("net.minecraft.world.level.block.Blocks").getField("STONE").get(null);stone=invoke(stone,"defaultBlockState");
            Object carrier=invoke(level,"getBlockState",coordinate),actor=handle(player);
            double wanted=((Number)invoke(stone,"getDestroyProgress",actor,level,coordinate)).doubleValue()*1.5/hardness;
            double current=((Number)invoke(carrier,"getDestroyProgress",actor,level,coordinate)).doubleValue();
            return current>0?Math.clamp(wanted/current,.00001,1024):1;
        }catch(ReflectiveOperationException error){
            // The fallback is only for non-Craft test servers. Real Paper is diagnosed separately.
            double speed=switch(player.getInventory().getItemInMainHand().getType()){
                case WOODEN_PICKAXE->2;case STONE_PICKAXE->4;case IRON_PICKAXE->6;case DIAMOND_PICKAXE->8;case NETHERITE_PICKAXE->9;case GOLDEN_PICKAXE->12;default->1;
            };
            return Math.max(.00001,.5/hardness*speed);
        }
    }
    public static boolean entityTicking(World world,int x,int z){
        if(!world.isChunkLoaded(x,z))return false;
        try{
            Object chunk=Class.forName("net.minecraft.world.level.ChunkPos").getConstructor(int.class,int.class).newInstance(x,z);
            return (boolean)invoke(handle(world),"areEntitiesActuallyLoadedAndTicking",chunk);
        }catch(ReflectiveOperationException e){return world.getChunkAt(x,z).isEntitiesLoaded();}
    }
    public static void identity(Entity entity,UUID id)throws ReflectiveOperationException{invoke(handle(entity),"setUUID",id);}
    public static void detach(EnderDragon dragon)throws ReflectiveOperationException{
        Object handle=handle(dragon);handle.getClass().getMethod("setDragonFight",Class.forName("net.minecraft.world.level.dimension.end.EndDragonFight")).invoke(handle,new Object[]{null});
    }
    public static void duration(AreaEffectCloud cloud)throws ReflectiveOperationException{invoke(handle(cloud),"setPotionDurationScale",1f);}
    public static void origin(World world,Location portal)throws ReflectiveOperationException{
        Object fight=invoke(handle(world),"getDragonFight");if(fight==null)return;
        Object target=pos(portal.getBlockX(),portal.getBlockY(),portal.getBlockZ());
        var portalField=fight.getClass().getDeclaredField("portalLocation");var originField=fight.getClass().getDeclaredField("origin");
        portalField.setAccessible(true);originField.setAccessible(true);portalField.set(fight,target);originField.set(fight,target);
    }
    public static void stopNativeFight(World world)throws ReflectiveOperationException{
        Object fight=invoke(handle(world),"getDragonFight");if(fight==null)return;
        Map<String,Object> changes=new LinkedHashMap<>();changes.put("respawnStage",null);changes.put("respawnCrystals",null);changes.put("respawnTime",0);
        changes.put("dragonUUID",null);changes.put("dragonKilled",true);changes.put("previouslyKilled",true);changes.put("needsStateScanning",false);
        var fields=new LinkedHashMap<Field,Object>();for(var entry:changes.entrySet()){var field=fight.getClass().getDeclaredField(entry.getKey());field.setAccessible(true);fields.put(field,entry.getValue());}
        for(var entry:fields.entrySet())entry.getKey().set(fight,entry.getValue());
        var battle=world.getEnderDragonBattle();if(battle!=null){battle.getBossBar().removeAll();battle.getBossBar().setVisible(false);}
    }
    public static List<Location> towers(World world)throws ReflectiveOperationException{
        var feature=Class.forName("net.minecraft.world.level.levelgen.feature.SpikeFeature");
        Method method=feature.getMethod("getSpikesForLevel",Class.forName("net.minecraft.world.level.WorldGenLevel"));
        List<Location> result=new ArrayList<>();
        for(Object spike:(List<?>)method.invoke(null,handle(world)))result.add(new Location(world,((Number)invoke(spike,"getCenterX")).intValue(),((Number)invoke(spike,"getHeight")).intValue()+1,((Number)invoke(spike,"getCenterZ")).intValue()));
        return result;
    }

    /** Same shadow-world rule as the reference: wires and both received outputs are zero. */
    public static int redstone(Block root,Set<State.Pos> excluded)throws ReflectiveOperationException{
        Object level=handle(root.getWorld()),origin=pos(root.getX(),root.getY(),root.getZ());
        Class<?> signal=Class.forName("net.minecraft.world.level.SignalGetter");
        Object air=invoke(Class.forName("net.minecraft.world.level.block.Blocks").getField("AIR").get(null),"defaultBlockState");
        Object wire=Class.forName("net.minecraft.world.level.block.Blocks").getField("REDSTONE_WIRE").get(null);
        Object property=Class.forName("net.minecraft.world.level.block.RedStoneWireBlock").getField("POWER").get(null);
        String world=root.getWorld().getKey().asString();
        var shadow=new SignalShadow(level,origin,air,wire,property,excluded,world,root.getWorld().getMinHeight(),root.getWorld().getMaxHeight()-root.getWorld().getMinHeight());
        Object proxy=Proxy.newProxyInstance(signal.getClassLoader(),new Class<?>[]{signal},shadow);
        return shadow.read(proxy);
    }
    private static final class SignalShadow implements InvocationHandler{
        final Object level,origin,air,wire,power;
        final Set<State.Pos> excluded;final String world;final int min,height;
        final Map<Object,Object> cache=new HashMap<>();
        SignalShadow(Object level,Object origin,Object air,Object wire,Object power,Set<State.Pos> excluded,String world,int min,int height){this.level=level;this.origin=origin;this.air=air;this.wire=wire;this.power=power;this.excluded=excluded;this.world=world;this.min=min;this.height=height;}
        Object raw(Object pos)throws ReflectiveOperationException{
            Object existing=cache.get(pos);if(existing!=null)return existing;
            Object value=(boolean)NativeSupport.invoke(level,"hasChunkAt",pos)?NativeSupport.invoke(level,"getBlockState",pos):air;cache.put(pos,value);return value;
        }
        State.Pos key(Object pos)throws ReflectiveOperationException{return new State.Pos(world,((Number)NativeSupport.invoke(pos,"getX")).intValue(),((Number)NativeSupport.invoke(pos,"getY")).intValue(),((Number)NativeSupport.invoke(pos,"getZ")).intValue());}
        Object sanitized(Object pos)throws ReflectiveOperationException{
            if(excluded.contains(key(pos)))return air;
            Object state=raw(pos);return (boolean)NativeSupport.invoke(state,"is",wire)?NativeSupport.invoke(state,"setValue",power,0):state;
        }
        public Object invoke(Object proxy,Method method,Object[] args)throws Throwable{
            return switch(method.getName()){
                case "getBlockState"->sanitized(args[0]);
                case "getBlockEntity"->(boolean)NativeSupport.invoke(level,"hasChunkAt",args[0])?NativeSupport.invoke(level,"getBlockEntity",args[0]):null;
                case "getFluidState"->NativeSupport.invoke(sanitized(args[0]),"getFluidState");
                case "getMinY"->min;
                case "getHeight"->height;
                case "toString"->"Z_Lor external-signal shadow";
                case "hashCode"->System.identityHashCode(proxy);
                case "equals"->proxy==args[0];
                default->{if(method.isDefault())yield InvocationHandler.invokeDefault(proxy,method,args);throw new UnsupportedOperationException(method.toString());}
            };
        }
        int read(Object proxy)throws ReflectiveOperationException{
            Class<?> direction=Class.forName("net.minecraft.core.Direction");Object[] all=direction.getEnumConstants();
            int best=((Number)NativeSupport.invoke(proxy,"getBestNeighborSignal",origin)).intValue();if(best==15)return 15;
            record Wire(Object pos,int loss){}
            var queue=new ArrayDeque<Wire>();var seen=new HashSet<Object>();
            for(Object side:all){
                Object coordinate=NativeSupport.invoke(origin,"relative",side),state=raw(coordinate);
                if(isWire(state)&&emits(state,proxy,coordinate,side,false))queue.add(new Wire(coordinate,0));
                if(conductor(state,proxy,coordinate))for(Object inner:all){Object w=NativeSupport.invoke(coordinate,"relative",inner),ws=raw(w);if(isWire(ws)&&emits(ws,proxy,w,inner,true))queue.add(new Wire(w,0));}
            }
            while(!queue.isEmpty()&&seen.size()<2048){
                var item=queue.removeFirst();if(item.loss>14||!seen.add(item.pos))continue;
                best=Math.max(best,((Number)NativeSupport.invoke(proxy,"getBestNeighborSignal",item.pos)).intValue()-item.loss);if(best>=15)return 15;
                if(item.loss==14)continue;
                Object above=NativeSupport.invoke(item.pos,"above");boolean blocked=conductor(raw(above),proxy,above);
                for(Object side:all){String name=side.toString();if(name.equalsIgnoreCase("up")||name.equalsIgnoreCase("down"))continue;
                    Object next=NativeSupport.invoke(item.pos,"relative",side),state=raw(next);if(isWire(state))queue.add(new Wire(next,item.loss+1));
                    boolean solid=conductor(state,proxy,next);Object step=solid&&!blocked?NativeSupport.invoke(next,"above"):!solid?NativeSupport.invoke(next,"below"):null;
                    if(step!=null&&isWire(raw(step)))queue.add(new Wire(step,item.loss+1));
                }
            }
            return Math.clamp(best,0,15);
        }
        boolean isWire(Object state)throws ReflectiveOperationException{return (boolean)NativeSupport.invoke(state,"is",wire);}
        boolean conductor(Object state,Object proxy,Object pos)throws ReflectiveOperationException{return (boolean)NativeSupport.invoke(state,"isRedstoneConductor",proxy,pos);}
        boolean emits(Object state,Object proxy,Object pos,Object side,boolean direct)throws ReflectiveOperationException{return ((Number)NativeSupport.invoke(NativeSupport.invoke(state,"setValue",power,15),direct?"getDirectSignal":"getSignal",proxy,pos,side)).intValue()>0;}
    }
}
