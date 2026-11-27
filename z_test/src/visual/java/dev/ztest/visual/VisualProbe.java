package dev.ztest.visual;
import dev.ztest.*;
import java.nio.file.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import io.papermc.paper.entity.TeleportFlag;

public final class VisualProbe extends JavaPlugin implements Listener {
 int age=0; Allay soul; Location fixed; Player viewer; int attacks=0;
 @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);Bukkit.getScheduler().runTaskTimer(this,this::tick,20,1);}
 @EventHandler(priority=EventPriority.MONITOR) public void swing(PlayerAnimationEvent e){if(e.getPlayer().equals(viewer))try{Files.writeString(Path.of("visual-swing.txt"),e.getAnimationType().toString());}catch(Exception ignored){}}
 @EventHandler(priority=EventPriority.MONITOR) public void death(EntityDeathEvent e){if(e.getEntity().equals(soul))try{ZTestPlugin p=(ZTestPlugin)Bukkit.getPluginManager().getPlugin("Z_Test");Files.writeString(Path.of("visual-death.txt"),"shard="+e.getDrops().stream().anyMatch(i->p.getDetector().is(i,"soul_shard")));}catch(Exception ignored){}}
 @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=false) public void damage(EntityDamageByEntityEvent e){if(e.getDamager() instanceof Player&&e.getEntity().equals(soul)){attacks++;try{Files.writeString(Path.of("visual-damage.txt"),"cancelled="+e.isCancelled()+" damage="+e.getFinalDamage());}catch(Exception ignored){}}}
 void tick(){try{
   if(viewer==null){viewer=Bukkit.getPlayerExact("LorObserver");if(viewer==null)return;viewer.setOp(true);viewer.setGameMode(GameMode.SURVIVAL);viewer.setInvulnerable(true);viewer.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));viewer.setAllowFlight(true);viewer.setFlying(true);}
   age++; ZTestPlugin plugin=(ZTestPlugin)Bukkit.getPluginManager().getPlugin("Z_Test");World world=plugin.getPurgatory();
   if(age==30){
     fixed=new Location(world,4.5,66.0,8.5,0,0);world.getChunkAt(fixed).load();
     for(int x=2;x<=6;x++)for(int z=3;z<=14;z++)for(int y=65;y<=72;y++)world.getBlockAt(x,y,z).setType(Material.AIR);
     soul=world.spawn(fixed,Allay.class,CreatureSpawnEvent.SpawnReason.CUSTOM,v->{v.getPersistentDataContainer().set(new NamespacedKey(plugin,"soul"),PersistentDataType.BYTE,(byte)1);});
     Location camera=new Location(world,4.5,65.0,11.0,180,0);viewer.teleport(camera);viewer.setAllowFlight(true);viewer.setFlying(true);
   }
   if(soul!=null&&soul.isValid()){soul.teleport(fixed,TeleportCause.PLUGIN,TeleportFlag.EntityState.RETAIN_PASSENGERS);}
   if(age==350){Files.writeString(Path.of("visual-ready.txt"),"viewer="+viewer.getName()+" soul="+(soul!=null&&soul.isValid())+" passengers="+(soul==null?0:soul.getPassengers().size()));}
 }catch(Exception e){getLogger().severe("Visual probe: "+e);try{Files.writeString(Path.of("visual-error.txt"),e.toString());}catch(Exception ignored){}}}
}
