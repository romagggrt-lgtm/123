package dev.zpantera;

import org.bukkit.*;
import org.bukkit.entity.Wolf;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.event.entity.EntityInteractEvent;
import com.destroystokyo.paper.entity.ai.VanillaGoal;

/** Native Paper pathfinding is isolated so server-event tests need not fake a physics engine. */
interface Navigation {
    void prepare(Wolf wolf);
    boolean move(Wolf wolf, Location destination, double speed);
    void stop(Wolf wolf);
    final class Paper implements Navigation {
        @Override public void prepare(Wolf w) {
            var goals=Bukkit.getMobGoals(); var swim=goals.getGoal(w,VanillaGoal.FLOAT);
            goals.removeAllGoals(w);
            if (swim!=null) goals.addGoal(w,0,swim);
            w.setAware(true); w.setTarget(null); w.setAngry(false);
            w.getPathfinder().setCanFloat(true); w.getPathfinder().setCanPassDoors(true); w.getPathfinder().setCanOpenDoors(true);
        }
        @Override public boolean move(Wolf w,Location at,double speed) {
            // Pathfinder can plan through doors, but wolves do not have a vanilla OpenDoorGoal.
            // Open only an adjacent wooden door in the travel direction; never break any block.
            Location here=w.getLocation();double dx=at.getX()-here.getX(),dz=at.getZ()-here.getZ(),length=Math.hypot(dx,dz);
            if(length>.1)for(double step:new double[]{.5,1}) {
                Location near=here.clone().add(dx/length*step,0,dz/length*step);
                if(!SafePosition.loaded(near))continue;
                Block b=near.getBlock();
                if(b.getType()!=Material.IRON_DOOR&&b.getBlockData() instanceof Door door&&!door.isOpen()) {
                    var event=new EntityInteractEvent(w,b);Bukkit.getPluginManager().callEvent(event);
                    if(!event.isCancelled()) {
                        door.setOpen(true);b.setBlockData(door,true);
                        Block other=b.getRelative(0,door.getHalf()==Bisected.Half.BOTTOM?1:-1,0);
                        if(other.getType()==b.getType()&&other.getBlockData() instanceof Door half) {half.setOpen(true);other.setBlockData(half,true);}
                    }
                }
            }
            return w.getPathfinder().moveTo(at,speed);
        }
        @Override public void stop(Wolf w) { w.getPathfinder().stopPathfinding(); }
    }
}
