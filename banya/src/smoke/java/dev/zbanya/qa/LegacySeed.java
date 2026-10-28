package dev.zbanya.qa;

import dev.zbanya.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Runs against the unmodified release 1.0.0 JAR, before the update is installed. */
public final class LegacySeed extends JavaPlugin {
    ZBanyaPlugin p;Player player;int stage,ticks;boolean done;
    @Override public void onEnable(){Bukkit.getScheduler().runTaskTimer(this,this::tick,1,1);}
    void tick(){if(done)return;ticks++;try{
        if(ticks>4000)throw new IllegalStateException("Legacy seed timeout");
        if(stage==0){player=Bukkit.getPlayerExact("BanyaObserver");if(player==null||Bukkit.getOnlinePlayers().size()<3)return;p=(ZBanyaPlugin)Bukkit.getPluginManager().getPlugin("Z_Banya");if(!p.getPluginMeta().getVersion().equals("1.0.0"))throw new IllegalStateException("Seed must use original release JAR");var w=player.getWorld();w.setGameRule(GameRule.DO_MOB_SPAWNING,false);for(Entity e:w.getEntities())if(e instanceof LivingEntity&&!(e instanceof Player))e.remove();player.setOp(true);player.setGameMode(GameMode.CREATIVE);player.teleport(new Location(w,80.5,65,80.5));for(Player other:Bukkit.getOnlinePlayers())if(!other.equals(player))other.teleport(new Location(w,40.5,65,40.5));p.getConfig().set("vkusnyashka.spawn-chance",0.0);stage=1;ticks=0;return;}
        if(stage==1&&ticks>=40){Bukkit.dispatchCommand(player,"zbanya set");stage=2;return;}
        if(stage==2&&p.idle()){var b=p.all().stream().findFirst().orElseThrow();p.foodContainer(b).getInventory().setItem(3,new ItemStack(Material.DIAMOND,7));var result=new LinkedHashMap<String,Object>();result.put("status","UPGRADE");result.put("plugin_version",p.getPluginMeta().getVersion());result.put("bath",b);Files.writeString(Path.of("banya-legacy-seed.json"),Journal.JSON.toJson(result));finish(result);}
    }catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"Legacy seed failed",e);finish(Map.of("status","FAIL","failure",e.toString()));}}
    void finish(Map<String,?> result){if(done)return;done=true;try{Files.writeString(Path.of("banya-native.json"),Journal.JSON.toJson(result));}catch(Exception e){throw new RuntimeException(e);}Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);}
}
