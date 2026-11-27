package dev.ztest.visual;
import dev.ztest.*;
import java.nio.file.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import io.papermc.paper.entity.TeleportFlag;

public final class VisualProbe extends JavaPlugin {
 int age=0; Vex soul; Location fixed; Player viewer;
 @Override public void onEnable(){Bukkit.getScheduler().runTaskTimer(this,this::tick,20,1);}
 void tick(){try{
   if(viewer==null){viewer=Bukkit.getPlayerExact("LorObserver");if(viewer==null)return;viewer.setOp(true);viewer.setGameMode(GameMode.CREATIVE);viewer.setAllowFlight(true);viewer.setFlying(true);}
   age++; ZTestPlugin plugin=(ZTestPlugin)Bukkit.getPluginManager().getPlugin("Z_Test");World world=plugin.getPurgatory();
   if(age==30){
     fixed=new Location(world,4.5,66.0,8.5,0,0);world.getChunkAt(fixed).load();
     for(int x=3;x<=5;x++)for(int z=7;z<=9;z++)for(int y=65;y<=69;y++)world.getBlockAt(x,y,z).setType(Material.AIR);
     soul=world.spawn(fixed,Vex.class,CreatureSpawnEvent.SpawnReason.CUSTOM,v->{v.getPersistentDataContainer().set(new NamespacedKey(plugin,"soul"),PersistentDataType.BYTE,(byte)1);});
     Location camera=new Location(world,4.5,65.0,12.5,180,0);viewer.teleport(camera);viewer.setAllowFlight(true);viewer.setFlying(true);
   }
   if(soul!=null&&soul.isValid()){soul.teleport(fixed,TeleportCause.PLUGIN,TeleportFlag.EntityState.RETAIN_PASSENGERS);}
   if(age==350){Files.writeString(Path.of("visual-ready.txt"),"viewer="+viewer.getName()+" soul="+(soul!=null&&soul.isValid())+" passengers="+(soul==null?0:soul.getPassengers().size()));}
 }catch(Exception e){getLogger().severe("Visual probe: "+e);try{Files.writeString(Path.of("visual-error.txt"),e.toString());}catch(Exception ignored){}}}
}
