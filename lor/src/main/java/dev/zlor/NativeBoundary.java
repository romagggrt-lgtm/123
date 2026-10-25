package dev.zlor;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import static net.bytebuddy.matcher.ElementMatchers.*;
import org.bukkit.World;
import org.bukkit.entity.Entity;

/** Wrap the existing native iteration: no extra world-wide entity scan. */
public class NativeBoundary implements AutoCloseable {
    private record Hook(World world,Object level,Field field,Object original,Object wrapper,boolean enabled,double offset){}
    private final ZLorPlugin p;
    private final Map<UUID,Hook> hooks=new HashMap<>();
    private final Map<UUID,String> unavailable=new HashMap<>();
    private static final ClassValue<Class<?>> WRAPPERS=new ClassValue<>(){
        @Override protected Class<?> computeValue(Class<?> base){
            try{
                return new ByteBuddy().subclass(base).name(base.getName()+"$ZLorTick_"+UUID.randomUUID().toString().replace("-",""))
                        .defineField("zLorHandler",InvocationHandler.class,Visibility.PUBLIC)
                        .method(isDeclaredBy(base).and(isVirtual()).and(not(isFinalizer())))
                        .intercept(InvocationHandlerAdapter.toField("zLorHandler"))
                        .make().load(base.getClassLoader(),ClassLoadingStrategy.UsingLookup.of(MethodHandles.privateLookupIn(base,MethodHandles.lookup())))
                        .getLoaded();
            }catch(Exception error){throw new IllegalStateException("Не удалось создать native ticker adapter",error);}
        }
    };
    private static final ClassValue<Method> BUKKIT_ENTITY=new ClassValue<>(){
        @Override protected Method computeValue(Class<?> type){try{return type.getMethod("getBukkitEntity");}catch(Exception e){throw new IllegalStateException(e);}}
    };
    public NativeBoundary(ZLorPlugin p){this.p=p;}
    public static Object wrap(Object original,UnaryOperator<Consumer<Object>> around)throws Exception{
        var base=original.getClass();var type=WRAPPERS.get(base);var instance=type.getDeclaredConstructor().newInstance();
        // A final/native implementation that cannot be intercepted must never
        // install an empty independent list and silently stop all entity ticks.
        type.getDeclaredMethod("forEach",Consumer.class);
        for(var method:base.getDeclaredMethods()){
            if(Modifier.isPublic(method.getModifiers())&&!Modifier.isStatic(method.getModifiers()))
                type.getDeclaredMethod(method.getName(),method.getParameterTypes());
        }
        InvocationHandler handler=(proxy,method,args)->{
            Object[] forwarded=args;
            if(method.getName().equals("forEach")&&args!=null&&args.length==1&&args[0] instanceof Consumer<?>){
                @SuppressWarnings("unchecked") var consumer=(Consumer<Object>)args[0];forwarded=new Object[]{around.apply(consumer)};
            }
            try{return method.invoke(original,forwarded);}catch(InvocationTargetException e){throw e.getCause();}
        };
        type.getField("zLorHandler").set(instance,handler);return instance;
    }
    public void attach(World world){
        if(hooks.containsKey(world.getUID())||unavailable.containsKey(world.getUID()))return;
        if(!p.getConfig().getBoolean("safety.native-boundary-hook",true)){unavailable.put(world.getUID(),"Отключено в config.yml");return;}
        try{
            Object level=world.getClass().getMethod("getHandle").invoke(world);
            Field field=level.getClass().getDeclaredField("entityTickList");field.setAccessible(true);
            Object original=field.get(level);
            boolean enabled=world.isVoidDamageEnabled();double offset=world.getVoidDamageMinBuildHeightOffset();
            Object wrapper=wrap(original,consumer->entity->{
                if(p.stopping()||!p.isEnabled()){consumer.accept(entity);return;}
                try{
                    Entity bukkit=(Entity)BUKKIT_ENTITY.get(entity.getClass()).invoke(entity);
                    boolean shield=p.protectedAt(bukkit);
                    double current=p.voids.height(world)-world.getMinHeight();
                    world.setVoidDamageMinBuildHeightOffset(current);
                    world.setVoidDamageEnabled(enabled&&!shield);
                    try{consumer.accept(entity);}finally{
                        world.setVoidDamageEnabled(enabled);
                        world.setVoidDamageMinBuildHeightOffset(p.stopping()||!p.isEnabled()?offset:p.voids.height(world)-world.getMinHeight());
                    }
                }catch(ReflectiveOperationException error){throw new IllegalStateException("Ошибка адаптера нативного тика",error);}
            });
            field.set(level,wrapper);
            if(field.get(level)!=wrapper)throw new IllegalStateException("Нативный ticker не заменён");
            hooks.put(world.getUID(),new Hook(world,level,field,original,wrapper,enabled,offset));
        }catch(Exception error){
            unavailable.put(world.getUID(),error.getClass().getSimpleName()+": "+error.getMessage());
            p.getLogger().warning("Нативная граница недоступна в "+world.getName()+": "+unavailable.get(world.getUID()));
        }
    }
    public boolean ready(World world){return hooks.containsKey(world.getUID());}
    public String status(World world){return ready(world)?"native ticker подключён":unavailable.getOrDefault(world.getUID(),"ожидание мира");}
    public void update(World world){if(ready(world))world.setVoidDamageMinBuildHeightOffset(p.voids.height(world)-world.getMinHeight());}
    public void detach(World world){
        var hook=hooks.remove(world.getUID());if(hook==null)return;
        try{
            if(hook.field.get(hook.level)==hook.wrapper)hook.field.set(hook.level,hook.original);
            world.setVoidDamageEnabled(hook.enabled);world.setVoidDamageMinBuildHeightOffset(hook.offset);
        }catch(Exception e){p.getLogger().severe("Не восстановлен нативный ticker "+world.getName()+": "+e);}
    }
    @Override public void close(){for(var hook:new ArrayList<>(hooks.values()))detach(hook.world);unavailable.clear();}
}
