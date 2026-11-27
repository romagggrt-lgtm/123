package dev.ztest.qa;

import com.google.gson.Gson;
import dev.ztest.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataType;

/** Run only on a disposable Paper server. Never included in the distributed plugin. */
public final class NativeSmoke extends JavaPlugin {
    ZTestPlugin p;Player player;World normal;int stage,ticks,entered;boolean second,finished;Rift rift;Point blockPoint;UUID trackedSoul;Location soulStart;final Map<String,String> checks=new LinkedHashMap<>();
    void check(String name,boolean value){if(!value)throw new IllegalStateException(name);checks.put(name,"PASS");}
    void next(){stage++;entered=ticks;}
    int age(){return ticks-entered;}
    long crystalCount(){World w=Bukkit.getWorld(blockPoint.world());Location loc=blockPoint.center().add(0,1.25,0);return w.getNearbyEntitiesByType(ItemDisplay.class,loc,1,d->Arrays.equals(d.getPersistentDataContainer().get(new NamespacedKey(p,"detector_display"),PersistentDataType.INTEGER_ARRAY),new int[]{blockPoint.x(),blockPoint.y(),blockPoint.z()})).size();}
    @Override public void onEnable(){second=Files.exists(Path.of("ztest-phase1.ok"));Bukkit.getScheduler().runTaskTimer(this,(Runnable)this::tick,1,1);}
    void report(String status,String failure){try{Files.writeString(Path.of("ztest-native-report.json"),new Gson().toJson(Map.of("status",status,"phase",second?2:1,"checks",checks,"failure",failure)));}catch(Exception ex){getLogger().severe(ex.toString());}finished=true;}
    void tick(){if(finished)return;ticks++;try{
        if(ticks>3000)throw new IllegalStateException("Timeout stage="+stage+" age="+age());
        if(stage==0){
            player=Bukkit.getPlayerExact("ZTestPlayer");if(player==null)return;
            p=(ZTestPlugin)Bukkit.getPluginManager().getPlugin("Z_Test");check("plugin_enabled",p!=null&&p.isEnabled());World w=p.getPurgatory();check("world_created_with_generator",w!=null&&w.getGenerator() instanceof PurgatoryGenerator);
            check("concrete_plain",w.getBlockAt(0,Terrain.GROUND,0).getType()==Material.GREEN_CONCRETE&&w.getBlockAt(0,Terrain.GROUND-1,0).getType()==Material.BROWN_CONCRETE);
            check("concrete_sun_and_sky",w.getBlockAt(0,Terrain.SKY,0).getType()==Material.YELLOW_CONCRETE&&w.getBlockAt(60,Terrain.SKY,60).getType()==Material.LIGHT_BLUE_CONCRETE);
            check("invisible_lighting",w.getBlockAt(0,Terrain.GROUND+1,0).getType()==Material.LIGHT);
            Terrain.Tree tree=null;for(int cx=-12;cx<=12&&tree==null;cx++)for(int cz=-12;cz<=12;cz++){var t=Terrain.tree(w.getSeed(),cx,cz);if(t!=null&&Math.floorMod(t.x(),16)>=14){tree=t;break;}}
            check("tree_test_found",tree!=null);check("concrete_tree_cross_chunk",w.getBlockAt(tree.x(),Terrain.GROUND+1,tree.z()).getType()==Material.BROWN_CONCRETE&&w.getBlockAt(tree.x()+2,Terrain.GROUND+tree.height(),tree.z()).getType()==Material.LIME_CONCRETE);
            check("different_world_not_replaced",Bukkit.getWorlds().stream().anyMatch(other->!other.equals(w)&&!(other.getGenerator() instanceof PurgatoryGenerator)));
            player.setOp(true);player.setInvulnerable(true);normal=Bukkit.getWorlds().stream().filter(other->!other.equals(w)&&other.getEnvironment()==World.Environment.NORMAL).findFirst().orElseThrow();
            if(second){check("rift_persisted_after_restart",p.getState().rifts().size()==1);check("detector_persisted_after_restart",p.getState().detectorCount()==1);rift=p.getState().rifts().iterator().next();blockPoint=p.getState().detectors().iterator().next();check("detector_block_persisted",Bukkit.getWorld(blockPoint.world()).getBlockAt(blockPoint.x(),blockPoint.y(),blockPoint.z()).getType()==Material.LODESTONE);next();return;}
            check("fresh_state",p.getState().rifts().isEmpty()&&p.getState().detectorCount()==0);
            check("zlor_enabled",Bukkit.getPluginManager().isPluginEnabled("Z_Lor"));
            check("crown_give_command",player.performCommand("zlore "+player.getName()+" give"));
            ItemStack shard=p.getDetector().shard(),red=new ItemStack(Material.REDSTONE),lodestone=new ItemStack(Material.LODESTONE);
            ItemStack[] grid={red,shard,red,shard,lodestone,shard,red,shard,red};ItemStack crafted=Bukkit.craftItem(grid,w,player);
            check("craft_correct_detector",p.getDetector().is(crafted,"detector"));
            grid[1]=p.getDetector().legacyShard();check("old_shard_still_crafts",p.getDetector().is(Bukkit.craftItem(grid,w,player),"detector"));
            grid[1]=new ItemStack(Material.AMETHYST_SHARD);ItemStack fake=Bukkit.craftItem(grid,w,player);check("ordinary_amethyst_rejected",fake==null||fake.getType().isAir());
            int floor=normal.getHighestBlockYAt(0,0);Location start=new Location(normal,.5,floor+1,.5,0,0);player.teleport(start);player.setVelocity(new org.bukkit.util.Vector());
            check("rift_create_command",player.performCommand("ztest rift create")&&p.getState().rifts().size()==1);rift=p.getState().rifts().iterator().next();
            Block block=normal.getBlockAt(8,floor+1,0);BlockState old=block.getState();block.setType(Material.LODESTONE);var place=new BlockPlaceEvent(block,old,block.getRelative(BlockFace.DOWN),p.getDetector().detector(),player,true,EquipmentSlot.HAND);Bukkit.getPluginManager().callEvent(place);
            blockPoint=Point.of(block);check("detector_placement",!place.isCancelled()&&p.getState().hasDetector(blockPoint));check("detector_finds_rift",rift.source().equals(p.getRifts().nearestEndpoint(block.getLocation())));
            var interact=new PlayerInteractEvent(player,Action.RIGHT_CLICK_BLOCK,null,block,BlockFace.UP,EquipmentSlot.HAND);Bukkit.getPluginManager().callEvent(interact);check("detector_click_handled",interact.isCancelled());
            next();return;
        }
        if(!second){
            if(stage==1&&age()>100){
                ItemStack crown=Arrays.stream(player.getInventory().getContents()).filter(Objects::nonNull).filter(i->i.hasItemMeta()&&new NamespacedKey("z_lor","eneit_crown").equals(i.getItemMeta().getItemModel())).findFirst().orElse(null);
                check("crown_received",crown!=null);check("crown_no_pumpkin_overlay",crown.getItemMeta().getEquippable().getCameraOverlay()==null);
                player.getInventory().setHelmet(crown);check("crown_equips",player.getInventory().getHelmet()!=null&&player.getInventory().getHelmet().isSimilar(crown));
                check("detector_crystal_created",crystalCount()==1);Location from=rift.source().center().clone().add(2,0,0),to=rift.source().center();Bukkit.getPluginManager().callEvent(new PlayerMoveEvent(player,from,to));check("portal_enters_dimension",player.getWorld().equals(p.getPurgatory()));next();return;}
            if(stage==2){var souls=p.getPurgatory().getEntitiesByClass(Vex.class).stream().filter(p.getSouls()::isSoul).toList();if(souls.size()<6)return;check("soul_group_of_six",souls.size()==6);check("souls_passive",souls.stream().allMatch(v->!v.hasAI()&&v.getTarget()==null));check("souls_have_models",souls.stream().allMatch(v->v.isInvisible()&&v.getPassengers().stream().anyMatch(e->e instanceof ItemDisplay)));trackedSoul=souls.get(0).getUniqueId();soulStart=souls.get(0).getLocation();next();return;}
            if(stage==3&&age()>60){Entity found=Bukkit.getEntity(trackedSoul);check("soul_still_alive",found instanceof Vex);Vex soul=(Vex)found;Location at=soul.getLocation();check("soul_wanders",at.distanceSquared(soulStart)>.0025);check("soul_at_head_height",at.getY()>=Terrain.GROUND+1&&at.getY()<=Terrain.GROUND+4);soul.setHealth(0);Bukkit.getScheduler().runTaskLater(this,()->{try{check("soul_drops_shard",p.getPurgatory().getNearbyEntitiesByType(Item.class,at,4).stream().anyMatch(i->p.getDetector().is(i.getItemStack(),"soul_shard")));next();}catch(Exception e){report("FAIL",e.toString());}},2);stage=20;return;}
            if(stage==21&&age()>120){Location from=rift.destination().center().clone().add(2,0,0),to=rift.destination().center();Bukkit.getPluginManager().callEvent(new PlayerMoveEvent(player,from,to));check("portal_returns_to_origin_world",player.getWorld().equals(normal));Files.writeString(Path.of("ztest-phase1.ok"),"phase1\n");report("PASS","");return;}
        }else{
            if(stage==1&&age()>10){check("detector_crystal_restored_after_restart",crystalCount()==1);Location loc=rift.source().center().clone().add(3,0,0);player.teleport(loc);check("rift_close_command",player.performCommand("ztest rift close")&&p.getState().rifts().isEmpty());Block block=Bukkit.getWorld(blockPoint.world()).getBlockAt(blockPoint.x(),blockPoint.y(),blockPoint.z());var e=new BlockBreakEvent(block,player);Bukkit.getPluginManager().callEvent(e);check("detector_break_intercepted",!e.isCancelled()&&!e.isDropItems()&&!p.getState().hasDetector(blockPoint));block.setType(Material.AIR);next();return;}
            if(stage==2&&age()>5){World w=Bukkit.getWorld(blockPoint.world());Location at=blockPoint.center();check("detector_drops_itself",w.getNearbyEntitiesByType(Item.class,at,4).stream().anyMatch(i->p.getDetector().is(i.getItemStack(),"detector")));check("detector_crystal_removed",crystalCount()==0);report("PASS","");return;}
        }
    }catch(Throwable ex){getLogger().severe("Z_Test QA: "+ex);report("FAIL",ex.toString());}}
}
