package dev.zlor;

import dev.zlor.rules.RelaySession;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.*;
import org.bukkit.block.data.type.RedstoneWire;
import org.bukkit.event.*;
import org.bukkit.event.block.*;

public final class Redstone implements Listener,AutoCloseable {
    private record Cached(int power,long tick){}
    private record Region(String world,int x,int z){}
    private final ZLorPlugin p;
    private final Map<UUID,Cached> inputs=new HashMap<>();
    private final Map<String,RelaySession> sessions=new HashMap<>();
    private final Set<Region> leases=new HashSet<>(),loading=new HashSet<>();
    private boolean warned;
    private boolean closed;
    public Redstone(ZLorPlugin p){this.p=p;}
    public int externalInput(State.Device device){
        if(!p.devices.loaded(device))return 0;
        var old=inputs.get(device.id);if(old!=null&&p.tick-old.tick<20)return old.power;
        var excluded=new HashSet<State.Pos>();excluded.add(device.pos);
        if(device.peer!=null){var peer=p.state().devices.get(device.peer);if(peer!=null)excluded.add(peer.pos);}
        int power;
        try{power=NativeSupport.redstone(p.devices.block(device),excluded);}
        catch(ReflectiveOperationException error){
            power=fallback(p.devices.block(device),excluded);
            if(!warned){warned=true;p.getLogger().warning("Нативное чтение редстоуна недоступно; включён ограниченный Bukkit fallback: "+error.getClass().getSimpleName());}
        }
        inputs.put(device.id,new Cached(power,p.tick));return power;
    }
    /** Test/compatibility path: reconstruct dust from independent powered sources. */
    private int fallback(Block root,Set<State.Pos> excluded){
        record Node(Block block,int loss){}
        var queue=new ArrayDeque<Node>();var seen=new HashSet<State.Pos>();queue.add(new Node(root,0));int best=0;
        while(!queue.isEmpty()&&seen.size()<2048){
            var node=queue.removeFirst();if(node.loss>14||!seen.add(p.devices.pos(node.block)))continue;
            for(BlockFace face:List.of(BlockFace.UP,BlockFace.DOWN,BlockFace.NORTH,BlockFace.SOUTH,BlockFace.EAST,BlockFace.WEST)){
                Block neighbor=node.block.getRelative(face);if(!neighbor.getWorld().isChunkLoaded(neighbor.getX()>>4,neighbor.getZ()>>4)||excluded.contains(p.devices.pos(neighbor)))continue;
                var data=neighbor.getBlockData();var custom=p.devices.at(neighbor);
                if(data instanceof RedstoneWire){queue.add(new Node(neighbor,node.loss+(node.block.equals(root)?0:1)));continue;}
                int value=custom!=null?p.devices.power(custom.id):data instanceof Powerable powered&&powered.isPowered()?15:data instanceof AnaloguePowerable analog?analog.getPower():neighbor.getType()==Material.REDSTONE_BLOCK?15:0;
                best=Math.max(best,value-node.loss);
                if(neighbor.getType().isSolid()&&node.loss==0)for(BlockFace other:List.of(BlockFace.UP,BlockFace.DOWN,BlockFace.NORTH,BlockFace.SOUTH,BlockFace.EAST,BlockFace.WEST)){
                    Block source=neighbor.getRelative(other);if(excluded.contains(p.devices.pos(source)))continue;var sourceData=source.getBlockData();
                    if(sourceData instanceof RedstoneWire)queue.add(new Node(source,0));
                    else if(sourceData instanceof Powerable powered&&powered.isPowered())best=15;
                }
            }
        }
        return Math.clamp(best,0,15);
    }
    @EventHandler(priority=EventPriority.MONITOR)public void change(BlockRedstoneEvent e){invalidate(e.getBlock());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void physics(BlockPhysicsEvent e){invalidate(e.getBlock());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void place(BlockPlaceEvent e){invalidate(e.getBlock());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void breakBlock(BlockBreakEvent e){invalidate(e.getBlock());}
    private void invalidate(Block block){
        String world=p.worldKey(block.getWorld());
        for(var d:p.state().devices.values())if(d.pos.world().equals(world)&&Math.abs(d.pos.x()-block.getX())<=17&&Math.abs(d.pos.z()-block.getZ())<=17&&Math.abs(d.pos.y()-block.getY())<=17)inputs.remove(d.id);
    }
    public void removed(UUID id){inputs.remove(id);sessions.keySet().removeIf(k->k.startsWith(id+">")||k.endsWith(">"+id));}
    public void tick(){
        var wanted=new HashSet<Region>();var seen=new HashSet<UUID>();
        for(var a:p.state().devices.values()){
            if(!a.kind.equals("magnetite_core")||a.linkPending||a.peer==null||!seen.add(a.id))continue;
            var b=p.state().devices.get(a.peer);if(b==null||b.linkPending||!Objects.equals(b.peer,a.id)||!a.pos.world().equals(b.pos.world()))continue;seen.add(b.id);
            direction(a,b,wanted);direction(b,a,wanted);
        }
        for(var region:new HashSet<>(leases))if(!wanted.contains(region)){World world=p.world(region.world);if(world!=null)world.removePluginChunkTicket(region.x,region.z,p);leases.remove(region);}
        for(var region:wanted)if(!leases.contains(region)&&loading.add(region)){
            World world=p.world(region.world);if(world==null){loading.remove(region);continue;}
            world.getChunkAtAsync(region.x,region.z,true).whenComplete((chunk,error)->{if(closed||!p.isEnabled())return;p.getServer().getScheduler().runTask(p,()->{
                loading.remove(region);if(error!=null||!p.isEnabled()||sessions.isEmpty())return;
                world.addPluginChunkTicket(region.x,region.z,p);leases.add(region);
            });});
        }
    }
    private void direction(State.Device from,State.Device to,Set<Region> wanted){
        String id=from.id+">"+to.id;Integer value=p.devices.loaded(from)&&NativeSupport.entityTicking(p.world(from.pos.world()),from.pos.x()>>4,from.pos.z()>>4)?externalInput(from):null;
        var session=sessions.get(id);if(session==null){if(value==null||value==0){p.devices.output(to,0);return;}session=new RelaySession();sessions.put(id,session);}
        boolean ready=p.devices.loaded(to)&&NativeSupport.entityTicking(p.world(to.pos.world()),to.pos.x()>>4,to.pos.z()>>4);
        var step=session.tick(value,ready);if(p.devices.loaded(to))p.devices.output(to,step.output());
        if(step.ticket()){
            int x=to.pos.x()>>4,z=to.pos.z()>>4;
            for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)if(wanted.size()<196)wanted.add(new Region(to.pos.world(),x+dx,z+dz));
        }
        if(step.timedOut())p.getLogger().warning("Приёмник ядра не готов за 600 тиков; очередь остановлена, связь сохранена");
        if(session.idle())sessions.remove(id);
    }
    public int leasedChunks(){return leases.size();}
    public boolean nativeReaderAvailable(){return !warned;}
    @Override public void close(){
        closed=true;sessions.clear();for(var region:leases){World world=p.world(region.world);if(world!=null)world.removePluginChunkTicket(region.x,region.z,p);}leases.clear();inputs.clear();
        for(var d:p.devices.loaded())if(d.kind.equals("magnetite_core"))p.devices.output(d,0);
    }
}
