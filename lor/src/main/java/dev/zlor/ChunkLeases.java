package dev.zlor;

import java.util.*;
import org.bukkit.*;

/** Plugin tickets are unique per plugin/chunk: share them between subsystems. */
public final class ChunkLeases implements AutoCloseable {
    private record Region(UUID world,int x,int z){}
    private final ZLorPlugin p;
    interface TicketOps {void set(World world,int x,int z,boolean present);}
    private final TicketOps operations;
    private final Map<Region,Set<Object>> owners=new HashMap<>();
    public ChunkLeases(ZLorPlugin p){this(p,(world,x,z,present)->{if(present)world.addPluginChunkTicket(x,z,p);else world.removePluginChunkTicket(x,z,p);});}
    ChunkLeases(ZLorPlugin p,TicketOps operations){this.p=p;this.operations=operations;}
    public void acquire(Object owner,World world,int x,int z){
        var key=new Region(world.getUID(),x,z);var set=owners.computeIfAbsent(key,ignored->Collections.newSetFromMap(new IdentityHashMap<>()));
        if(set.isEmpty())operations.set(world,x,z,true);set.add(owner);
    }
    public void release(Object owner,World world,int x,int z){
        var key=new Region(world.getUID(),x,z);var set=owners.get(key);if(set==null)return;
        set.remove(owner);if(set.isEmpty()){owners.remove(key);operations.set(world,x,z,false);}
    }
    public void release(Object owner){for(var region:new ArrayList<>(owners.keySet())){World world=Bukkit.getWorld(region.world);if(world!=null)release(owner,world,region.x,region.z);}}
    public int size(){return owners.size();}
    @Override public void close(){for(var region:owners.keySet()){World world=Bukkit.getWorld(region.world);if(world!=null)operations.set(world,region.x,region.z,false);}owners.clear();}
}
