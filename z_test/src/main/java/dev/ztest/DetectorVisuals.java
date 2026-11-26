package dev.ztest;

import java.util.Arrays;
import org.bukkit.*;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Ephemeral vanilla displays: registry stays authoritative across chunk unloads/restarts. */
final class DetectorVisuals implements Listener {
    private final ZTestPlugin p;private final NamespacedKey key;
    DetectorVisuals(ZTestPlugin p){this.p=p;key=new NamespacedKey(p,"detector_display");}
    void restoreLoaded(){for(Point at:p.state.detectors()){World w=Bukkit.getWorld(at.world());if(w!=null&&w.isChunkLoaded(at.x()>>4,at.z()>>4))show(at);}}
    @EventHandler public void load(ChunkLoadEvent e){String world=e.getWorld().getName();int x=e.getChunk().getX(),z=e.getChunk().getZ();Bukkit.getScheduler().runTask(p,()->{for(Point at:p.state.detectors())if(at.world().equals(world)&&(at.x()>>4)==x&&(at.z()>>4)==z)show(at);});}
    private int[] coords(Point p){return new int[]{p.x(),p.y(),p.z()};}
    private boolean matches(ItemDisplay d,Point at){return Arrays.equals(coords(at),d.getPersistentDataContainer().get(key,PersistentDataType.INTEGER_ARRAY));}
    void show(Point at){
        World w=Bukkit.getWorld(at.world());if(w==null||!w.isChunkLoaded(at.x()>>4,at.z()>>4)||w.getBlockAt(at.x(),at.y(),at.z()).getType()!=Material.LODESTONE||!p.state.hasDetector(at))return;
        Location pos=new Location(w,at.x()+.5,at.y()+1.25,at.z()+.5);
        if(w.getNearbyEntitiesByType(ItemDisplay.class,pos,1,d->matches(d,at)).size()>0)return;
        w.spawn(pos,ItemDisplay.class,d->{d.getPersistentDataContainer().set(key,PersistentDataType.INTEGER_ARRAY,coords(at));d.setPersistent(false);d.setInvulnerable(true);d.setGravity(false);d.setBillboard(Display.Billboard.CENTER);d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);d.setItemStack(new ItemStack(Material.AMETHYST_SHARD));var t=d.getTransformation();t.getScale().set(.42f,.42f,.42f);d.setTransformation(t);d.setBrightness(new Display.Brightness(15,15));});
    }
    void remove(Point at){World w=Bukkit.getWorld(at.world());if(w==null||!w.isChunkLoaded(at.x()>>4,at.z()>>4))return;Location pos=new Location(w,at.x()+.5,at.y()+1.25,at.z()+.5);w.getNearbyEntitiesByType(ItemDisplay.class,pos,1,d->matches(d,at)).forEach(ItemDisplay::remove);}
    void clearLoaded(){for(World w:Bukkit.getWorlds())for(ItemDisplay d:w.getEntitiesByClass(ItemDisplay.class))if(d.getPersistentDataContainer().has(key,PersistentDataType.INTEGER_ARRAY))d.remove();}
}
