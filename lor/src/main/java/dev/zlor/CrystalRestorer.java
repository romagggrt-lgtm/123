package dev.zlor;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;

/** Restores missing tower entities, never terrain, explosions or ritual crystals. */
public final class CrystalRestorer implements AutoCloseable {
    private record Region(int x,int z){}
    private static final class Job {List<Location> targets;final Set<Region> leased=new HashSet<>(),loading=new HashSet<>();long started,retry;boolean saving;}
    private final ZLorPlugin p;
    private final Map<String,Job> jobs=new HashMap<>();
    private final Set<String> warned=new HashSet<>();
    private boolean closed;
    public CrystalRestorer(ZLorPlugin p){this.p=p;}
    protected List<Location> targets(World world)throws ReflectiveOperationException{return NativeSupport.towers(world);}
    protected boolean ticking(World world,int x,int z){return NativeSupport.entityTicking(world,x,z);}
    public static int restore(World world,List<Location> positions,int limit){
        int added=0;
        for(var raw:positions){
            var at=raw.clone().add(.5,0,.5);var base=at.clone().subtract(0,1,0).getBlock();
            if(base.getType()!=Material.BEDROCK)continue;
            Material one=at.getBlock().getType(),two=at.clone().add(0,1,0).getBlock().getType();
            if(!(one.isAir()||one==Material.FIRE)||!two.isAir())continue;
            if(!world.getNearbyEntities(at,.8,1.5,.8,e->e instanceof EnderCrystal&&!e.isDead()).isEmpty())continue;
            world.spawn(at,EnderCrystal.class,e->{e.setInvulnerable(false);e.setBeamTarget(null);});
            if(++added>=limit)break;
        }return added;
    }
    public void tick(){
        if(closed||p.tick%5!=0)return;
        for(var key:new HashSet<>(jobs.keySet()))if(!p.state().crystalRepairs.contains(key))release(key);
        for(String key:p.state().crystalRepairs){
            World world=p.world(key);if(world==null||world.getEnvironment()!=World.Environment.THE_END)continue;
            var job=jobs.computeIfAbsent(key,ignored->{var j=new Job();j.started=p.tick;return j;});if(job.saving||job.retry>p.tick)continue;
            try{
                if(job.targets==null)job.targets=targets(world);
                if(p.tick-job.started>=600){p.tickets.release(job);job.leased.clear();job.retry=p.tick+100;job.started=job.retry;continue;}
                boolean ready=true;
                for(var target:job.targets){
                    int x=target.getBlockX()>>4,z=target.getBlockZ()>>4;var region=new Region(x,z);
                    if(!job.leased.contains(region)){
                        ready=false;if(job.loading.size()<8&&job.loading.add(region))world.getChunkAtAsync(x,z,true).whenComplete((chunk,error)->{
                            if(closed||!p.isEnabled())return;Bukkit.getScheduler().runTask(p,()->{
                                job.loading.remove(region);if(closed||jobs.get(key)!=job||error!=null||!p.state().crystalRepairs.contains(key))return;
                                p.tickets.acquire(job,world,x,z);job.leased.add(region);
                            });
                        });
                    }else if(!ticking(world,x,z))ready=false;
                }
                if(!ready)continue;
                int added=restore(world,job.targets,Integer.MAX_VALUE);job.saving=true;
                p.store.change(s->s.crystalRepairs.remove(key),s->{p.getLogger().info("Восстановлено башенных кристаллов: "+added+" ("+key+")");release(key);},error->job.saving=false);
            }catch(ReflectiveOperationException error){if(warned.add(key))p.getLogger().warning("Восстановление кристаллов приостановлено: "+error);job.retry=p.tick+100;}
        }
    }
    public int one(World world){
        try{var list=targets(world);for(var at:list)if(!ticking(world,at.getBlockX()>>4,at.getBlockZ()>>4))return -1;return restore(world,list,1);}
        catch(ReflectiveOperationException error){if(warned.add(p.worldKey(world)))p.getLogger().warning("Восстановление кристалла Царя: "+error);return -1;}
    }
    private void release(String key){var job=jobs.remove(key);if(job!=null)p.tickets.release(job);}
    @Override public void close(){closed=true;for(String key:new HashSet<>(jobs.keySet()))release(key);}
}
