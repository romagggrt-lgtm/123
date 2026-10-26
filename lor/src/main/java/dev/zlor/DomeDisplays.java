package dev.zlor;

import dev.zlor.rules.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import org.joml.Quaternionf;

/** One display per sphere: the resource pack composites 642 real 3D facets.
 * Per-cell visibility removes internal overlap; floats animate reveal/dissolve. */
public final class DomeDisplays implements AutoCloseable {
    public static final List<double[]> CELLS=HexDomeMesh.centres();
    private static final class Frame {
        ItemDisplay display;ItemStack last;long union=Long.MIN_VALUE;List<Boolean> mask;
    }
    private final ZLorPlugin p;
    private final Map<UUID,Frame> frames=new HashMap<>();
    public DomeDisplays(ZLorPlugin p){this.p=p;}
    public int size(){return frames.size();}
    public static List<Boolean> mask(Visuals.Dome dome,List<Visuals.Dome> all){
        var nearby=all.stream().filter(other->other!=dome&&other.radius()==50&&dome.radius()==50&&other.center().getWorld().equals(dome.center().getWorld())&&other.center().distanceSquared(dome.center())<100*100).toList();
        var result=new ArrayList<Boolean>(CELLS.size());
        for(double[] cell:CELLS){var point=dome.center().clone().add(cell[0]*dome.radius(),cell[1]*dome.radius(),cell[2]*dome.radius());result.add(nearby.stream().noneMatch(other->other.center().distanceSquared(point)<2499.9));}
        return List.copyOf(result);
    }
    public void sync(List<Visuals.Dome> all){
        if(!p.getConfig().getBoolean("effects.dome-mesh",true)){close();return;}
        int cap=Math.clamp(p.getConfig().getInt("effects.max-mesh-domes",32),1,64);double range=Math.clamp(p.getConfig().getDouble("effects.range",96),32,192);
        var wanted=new HashSet<UUID>();
        var visible=all.stream().filter(d->d.center().getWorld().getPlayers().stream().anyMatch(player->player.getLocation().distanceSquared(d.center())<=Math.pow(d.radius()+range,2))).sorted(Comparator.comparingDouble(d->d.center().getWorld().getPlayers().stream().mapToDouble(player->Math.abs(player.getLocation().distance(d.center())-d.radius())).min().orElse(Double.MAX_VALUE))).limit(cap).toList();
        for(var dome:visible){
            wanted.add(dome.id());var frame=frames.computeIfAbsent(dome.id(),key->new Frame());
            long union=all.stream().filter(other->other.radius()==50&&other.center().getWorld().equals(dome.center().getWorld())).mapToLong(other->other.id().getMostSignificantBits()^other.id().getLeastSignificantBits()).reduce(0,(a,b)->a^b);
            if(frame.mask==null||frame.union!=union){frame.mask=mask(dome,all);frame.union=union;}
            if(frame.display==null||!frame.display.isValid()){
                frame.display=dome.center().getWorld().spawn(dome.center(),ItemDisplay.class,e->{
                    e.setPersistent(false);e.setInvulnerable(true);e.setGravity(false);e.setSilent(true);e.setBrightness(new Display.Brightness(15,15));
                    e.setViewRange(4);e.setDisplayWidth((float)(dome.radius()*2.1));e.setDisplayHeight((float)(dome.radius()*2.1));
                    e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);e.setInterpolationDuration(4);e.setInterpolationDelay(0);
                    e.setTransformation(new Transformation(new Vector3f(),new Quaternionf(),new Vector3f((float)dome.radius()),new Quaternionf()));
                });frame.last=null;
            }
            String color=dome.radius()==50?"purple":dome.color().equals(Color.YELLOW)?"yellow":"green";
            float opening=(float)Math.clamp(dome.age()/3000d,0,1),fade=dome.reserves()?1:(float)Math.clamp(dome.remaining()/30000d,0,1);
            var item=new ItemStack(Material.PAPER);item.editMeta(meta->{
                meta.setItemModel(new NamespacedKey("z_lor","dome_"+color));var data=meta.getCustomModelDataComponent();
                data.setFloats(List.of(opening,fade));data.setFlags(frame.mask);meta.setCustomModelDataComponent(data);
            });
            if(!item.equals(frame.last)){frame.display.setItemStack(item);frame.last=item;}
        }
        for(var id:new HashSet<>(frames.keySet()))if(!wanted.contains(id)){frames.remove(id).display.remove();}
    }
    @Override public void close(){for(var frame:frames.values())if(frame.display!=null)frame.display.remove();frames.clear();}
}
