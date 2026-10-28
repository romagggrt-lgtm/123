package dev.zbanya.qa;

import dev.zbanya.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;

/** Only installed in a disposable CI world. Never distributed in Z_Banya.jar. */
public final class NativeSmoke extends JavaPlugin implements Listener {
    ZBanyaPlugin p;Player observer,target,outside;World world;Bath bath;int stage,entered,ticks,consumed;boolean done,second,upgraded,finalBoot;Bath other;int legacyStage;Bath legacy;ItemDisplay display;
    Map<String,Object> checks=new LinkedHashMap<>();
    void check(String name,boolean value){if(!value)throw new IllegalStateException(name);checks.put(name,"PASS");}
    void next(){stage++;entered=ticks;}
    int age(){return ticks-entered;}
    @Override public void onEnable(){second=Files.exists(Path.of("banya-qa-phase1.json"));finalBoot=Files.exists(Path.of("banya-qa-phase2.json"));getServer().getPluginManager().registerEvents(this,this);Bukkit.getScheduler().runTaskTimer(this,this::tick,1,1);}
    void camera(int x,int y,int z,int tx,int ty,int tz){var at=bath.location(world,x,y,z);at.setDirection(bath.location(world,tx,ty,tz).toVector().subtract(at.clone().add(0,observer.getEyeHeight(),0).toVector()));observer.teleport(at);observer.setAllowFlight(true);observer.setFlying(true);}
    void tick(){
        if(done)return;ticks++;
        try{
            Files.writeString(Path.of("banya-stage.json"),Journal.JSON.toJson(Map.of("stage",stage,"age",age(),"restart",second)));
            if(ticks>6000)throw new IllegalStateException("Timeout stage="+stage);
            if(stage==0){
                observer=Bukkit.getPlayerExact("BanyaObserver");target=Bukkit.getPlayerExact("BanyaTester");outside=Bukkit.getPlayerExact("BanyaOutside");if(observer==null||target==null||outside==null)return;
                p=(ZBanyaPlugin)Bukkit.getPluginManager().getPlugin("Z_Banya");check("real_plugin_enabled",p!=null&&p.isEnabled());world=observer.getWorld();
                world.setGameRule(GameRule.DO_MOB_SPAWNING,false);world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE,false);world.setGameRule(GameRule.RANDOM_TICK_SPEED,0);world.setTime(6000);
                observer.setOp(true);observer.setGameMode(GameMode.CREATIVE);target.setGameMode(GameMode.SURVIVAL);target.setFoodLevel(20);outside.setGameMode(GameMode.CREATIVE);
                for(Entity e:world.getEntities())if(e instanceof LivingEntity&&!(e instanceof Player))e.remove();
                outside.teleport(new Location(world,40.5,65,40.5));target.teleport(new Location(world,.5,65,.5));observer.teleport(new Location(world,.5,65,.5));
                if(finalBoot){finalChecks();finish("PASS",null);return;}
                if(second){bath=p.named("1");other=p.named("2");loadBath(bath);loadBath(other);check("two_named_baths_loaded_after_restart",p.all().size()==2&&bath.phase.equals("ACTIVE")&&other.phase.equals("ACTIVE"));check("food_not_respawned_after_restart",p.foodContainer(bath).getInventory().isEmpty());check("manually_broken_frame_does_not_respawn",frames(bath)==2);check("other_bath_frames_untouched",frames(other)==3);next();return;}
                if(!upgraded&&legacyUpgrade())return;
                Bukkit.dispatchCommand(target,"zbanya set 1");check("ordinary_player_cannot_create",p.all().isEmpty());p.getConfig().set("vkusnyashka.spawn-chance",1.0);Bukkit.dispatchCommand(observer,"zbanya set 1");next();return;
            }
            check("plugin_stays_enabled",p.isEnabled());
            if(second){secondPhase();return;}
            if(stage==1){
                if(!p.idle())return;bath=p.named("1");check("native_build_complete",bath.phase.equals("ACTIVE"));check("compact_single_room_7x7",bath.layout==1&&bath.bounds()[3]-bath.bounds()[0]==8&&bath.bounds()[5]-bath.bounds()[2]==8);check("master_channel_with_legacy_config",p.getConfig().getString("sound.category","MASTER").equals("MASTER"));check("three_persistent_bucket_frames",frames()==3);
                check("exactly_one_native_food",p.foodContainer(bath).getInventory().getItem(0).getAmount()==1&&p.isFood(p.foodContainer(bath).getInventory().getItem(0)));
                check("native_food_always_edible",p.foodContainer(bath).getInventory().getItem(0).getItemMeta().getFood().canAlwaysEat());
                for(var c:bath.cells){var block=world.getBlockAt(c.x(),c.y(),c.z());check("all_native_block_states_placed",block.getBlockData().getAsString().equals(c.after()));if(block.getType()==Material.LANTERN)check("all_lanterns_have_native_support",block.getBlockData().isSupported(block));}
                world.setTime(14000);camera(-9,5,-11,0,2,0);target.teleport(bath.location(world,0,1,-1));next();return;
            }
            if(stage==2){if(age()==40)check("private_loop_enters_independently",p.listening(target.getUniqueId())&&!p.listening(outside.getUniqueId())&&!p.listening(observer.getUniqueId()));if(age()>=260){target.teleport(new Location(world,35.5,65,35.5));check("sound_stops_on_teleport",!p.listening(target.getUniqueId()));camera(0,1,-2,1,2,1);next();}return;}
            if(stage==3&&age()>=280){camera(0,1,-2,1,2,2);next();return;}
            if(stage==4&&age()==80)camera(1,1,-1,-2,2,1);
            if(stage==4&&age()>=150){
                camera(0,1,-2,0,2,-1);display=world.spawn(bath.location(world,0,2,-1),ItemDisplay.class,e->{e.setItemStack(p.food(bath.id,1));e.setBrightness(new Display.Brightness(15,15));e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);e.setPersistent(false);e.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(),new org.joml.Quaternionf().rotateY(.4f),new org.joml.Vector3f(.8f),new org.joml.Quaternionf()));});next();return;
            }
            if(stage==5&&age()>=70){
                display.remove();var food=p.foodContainer(bath).getInventory().getItem(0);p.foodContainer(bath).getInventory().clear();target.getInventory().setItemInMainHand(food);target.setFoodLevel(20);next();return;
            }
            if(stage==6){
                if(consumed==0){if(age()>200)throw new IllegalStateException("Protocol client did not finish consuming food at full hunger");return;}
                check("native_food_consumed_once",consumed==1&&(target.getInventory().getItemInMainHand().isEmpty()));check("six_extra_hearts",Math.abs(target.getAbsorptionAmount()-12)<.01);
                for(var pair:Map.of(PotionEffectType.REGENERATION,1,PotionEffectType.ABSORPTION,2,PotionEffectType.RESISTANCE,0,PotionEffectType.FIRE_RESISTANCE,0,PotionEffectType.NAUSEA,0).entrySet())check("effect_"+pair.getKey().getKey(),target.hasPotionEffect(pair.getKey())&&target.getPotionEffect(pair.getKey()).getAmplifier()==pair.getValue());
                check("no_vanilla_apple_stacking",target.getPotionEffect(PotionEffectType.REGENERATION).getDuration()<=200&&target.getAbsorptionAmount()==12);
                next();return;
            }
            if(stage==7){
                if(age()==1){target.teleport(bath.location(world,0,1,-1));var q=bath.point(-3,3,-1);var block=world.getBlockAt(q[0],q[1],q[2]);check("ordinary_player_can_really_break_bath_wall",target.breakBlock(block)&&block.getType().isAir());observer.teleport(new Location(world,60.5,65,.5));}
                if(age()==40)Bukkit.dispatchCommand(observer,"zbanya set 2");
                if(age()>=180&&p.idle()){
                    other=p.named("2");check("same_creator_has_two_named_baths",p.all().size()==2&&other!=null&&bath!=other);check("broken_decor_not_recreated",frames(bath)==2);check("new_bath_has_three_frames",frames(other)==3);
                    var data=new LinkedHashMap<String,Object>();data.put("checks",checks);data.put("bath",bath);data.put("other",other);data.put("legacy",legacy);data.put("server",Bukkit.getVersion());Files.writeString(Path.of("banya-qa-phase1.json"),Journal.JSON.toJson(data));finish("RESTART",null);
                }
            }
        }catch(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"BANYA_NATIVE_FAILED stage="+stage,error);finish("FAIL",error);}
    }
    boolean legacyUpgrade(){
        if(legacyStage==0){legacy=p.all().stream().filter(b->b.layout==0).findFirst().orElseThrow();check("original_1_0_0_journal_loaded",Math.min(legacy.bounds()[3]-legacy.bounds()[0],legacy.bounds()[5]-legacy.bounds()[2])==12&&Math.max(legacy.bounds()[3]-legacy.bounds()[0],legacy.bounds()[5]-legacy.bounds()[2])==15);check("legacy_name_assigned",legacy.name!=null&&p.named(legacy.name)==legacy);check("legacy_barrel_items_preserved",p.foodContainer(legacy).getInventory().getItem(3).getAmount()==7);p.remove(legacy,observer,false);legacyStage=1;return true;}
        if(!p.idle())return true;
        check("legacy_bath_completely_removed",p.all().isEmpty());for(var c:legacy.cells)check("legacy_entire_terrain_restored",world.getBlockAt(c.x(),c.y(),c.z()).getBlockData().getAsString().equals(c.before()));check("legacy_items_recovered_once",recovered(legacy,Material.DIAMOND)==7);upgraded=true;return false;
    }
    void loadBath(Bath b){if(b==null)throw new IllegalStateException("Named bath is missing");var box=b.bounds();for(int x=box[0]>>4;x<=box[3]>>4;x++)for(int z=box[2]>>4;z<=box[5]>>4;z++)world.getChunkAt(x,z).getEntities();}
    long frames(Bath b){return world.getEntities().stream().filter(e->e instanceof ItemFrame&&b.id.toString().equals(e.getPersistentDataContainer().get(new NamespacedKey("zbanya","decor"),org.bukkit.persistence.PersistentDataType.STRING))).count();}
    java.util.List<Item> recoveredItems(Bath b){return world.getEntities().stream().filter(e->e instanceof Item&&java.util.Optional.ofNullable(e.getPersistentDataContainer().get(new NamespacedKey("zbanya","recovery"),org.bukkit.persistence.PersistentDataType.STRING)).orElse("").startsWith(b.id+"/")).map(e->(Item)e).toList();}
    int recovered(Bath b,Material type){return recoveredItems(b).stream().map(Item::getItemStack).filter(i->i.getType()==type).mapToInt(ItemStack::getAmount).sum();}
    Bath savedBath(String path,String key)throws Exception{return Journal.JSON.fromJson(com.google.gson.JsonParser.parseString(Files.readString(Path.of(path))).getAsJsonObject().get(key),Bath.class);}
    void secondPhase()throws Exception{
        if(stage==1&&age()>=20){
            legacy=savedBath("banya-qa-phase1.json","legacy");loadBath(legacy);check("legacy_loot_survives_restart",recovered(legacy,Material.DIAMOND)==7);
            p.foodContainer(bath).getInventory().setItem(3,new ItemStack(Material.DIAMOND,7));
            int[] a=bath.point(0,1,-2),b=bath.point(1,1,-2);world.getBlockAt(a[0],a[1],a[2]).setBlockData(Bukkit.createBlockData(Blueprint.orient("minecraft:chest[facing=north,type=left]",bath.turn)),false);world.getBlockAt(b[0],b[1],b[2]).setBlockData(Bukkit.createBlockData(Blueprint.orient("minecraft:chest[facing=north,type=right]",bath.turn)),false);
            var left=(org.bukkit.block.Chest)world.getBlockAt(a[0],a[1],a[2]).getState();var right=(org.bukkit.block.Chest)world.getBlockAt(b[0],b[1],b[2]).getState();
            var named=new ItemStack(Material.DIAMOND,11);named.editMeta(m->m.displayName(net.kyori.adventure.text.Component.text("Тест сохранения")));left.getBlockInventory().setItem(0,named);right.getBlockInventory().setItem(0,new ItemStack(Material.EMERALD,9));check("native_double_chest_connected",left.getInventory().getSize()==54);
            var changed=bath.point(0,1,0);world.getBlockAt(changed[0],changed[1],changed[2]).setType(Material.DIAMOND_BLOCK);
            var pos=bath.point(7,0,7);world.getBlockAt(pos[0],pos[1],pos[2]).setType(Material.CHEST);((org.bukkit.block.Container)world.getBlockAt(pos[0],pos[1],pos[2]).getState()).getInventory().setItem(0,new ItemStack(Material.EMERALD,9));
            target.teleport(new Location(world,.5,65,.5));observer.teleport(new Location(world,300.5,65,300.5));Bukkit.dispatchCommand(observer,"zbanya remove 1");next();return;
        }
        if(stage==2&&p.idle()){
            check("named_removal_works_from_far_away",p.named("1")==null&&p.named("2")!=null);for(var c:bath.cells)check("all_changed_and_added_blocks_restored",world.getBlockAt(c.x(),c.y(),c.z()).getBlockData().getAsString().equals(c.before()));check("double_chest_items_not_duplicated",recovered(bath,Material.DIAMOND)==18&&recovered(bath,Material.EMERALD)==9);
            check("custom_item_metadata_preserved",recoveredItems(bath).stream().anyMatch(i->i.getItemStack().getAmount()==11&&i.getItemStack().getItemMeta().displayName()!=null&&net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(i.getItemStack().getItemMeta().displayName()).equals("Тест сохранения")));
            check("recovered_items_do_not_despawn_or_burn",recoveredItems(bath).stream().allMatch(i->i.isUnlimitedLifetime()&&i.isInvulnerable()&&!i.hasGravity()&&i.canPlayerPickup()&&!i.canMobPickup()));
            loadBath(other);for(var c:other.cells)check("second_bath_not_changed_by_first_removal",world.getBlockAt(c.x(),c.y(),c.z()).getBlockData().getAsString().equals(c.after()));check("other_bath_keeps_its_frames",frames(other)==3);
            var pos=bath.point(7,0,7);check("adjacent_player_chest_preserved",((org.bukkit.block.Container)world.getBlockAt(pos[0],pos[1],pos[2]).getState()).getInventory().getItem(0).getAmount()==9);Bukkit.dispatchCommand(observer,"zbanya remove 2");next();return;
        }
        if(stage==3&&p.idle()){
            check("both_named_baths_removed",p.all().isEmpty());for(var c:other.cells)check("second_bath_terrain_restored",world.getBlockAt(c.x(),c.y(),c.z()).getBlockData().getAsString().equals(c.before()));check("decorative_entities_cleaned",frames()==0);check("no_orphan_sound_sessions",!p.listening(observer.getUniqueId())&&!p.listening(target.getUniqueId()));
            Files.writeString(Path.of("banya-qa-phase2.json"),Journal.JSON.toJson(java.util.Map.of("bath",bath,"other",other,"legacy",legacy)));next();return;
        }
        if(stage==4&&age()>10)finish("RESTART",null);
    }
    void finalChecks()throws Exception{
        bath=savedBath("banya-qa-phase2.json","bath");other=savedBath("banya-qa-phase2.json","other");legacy=savedBath("banya-qa-phase2.json","legacy");loadBath(bath);loadBath(other);loadBath(legacy);
        check("no_deleted_baths_return_after_restart",p.all().isEmpty());check("no_recovered_item_duplication_after_restart",recovered(bath,Material.DIAMOND)==18&&recovered(bath,Material.EMERALD)==9&&recovered(legacy,Material.DIAMOND)==7);check("recovery_flags_persist_after_restart",recoveredItems(bath).stream().allMatch(i->i.isUnlimitedLifetime()&&i.isInvulnerable()&&i.canPlayerPickup()));
        for(var c:bath.cells)check("restored_blocks_persist_after_restart",world.getBlockAt(c.x(),c.y(),c.z()).getBlockData().getAsString().equals(c.before()));check("no_decor_respawns_after_deletion",frames()==0);
    }
    long frames(){return world.getEntities().stream().filter(e->e instanceof ItemFrame&&e.getPersistentDataContainer().has(new NamespacedKey("zbanya","decor"))).count();}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void consume(PlayerItemConsumeEvent e){if(e.getPlayer().equals(target)&&p.isFood(e.getItem()))consumed++;}
    void finish(String status,Throwable error){if(done)return;done=true;try{var result=new LinkedHashMap<String,Object>();result.put("status",status);result.put("checks",checks);result.put("server",Bukkit.getVersion());result.put("source_commit",System.getenv("GITHUB_SHA"));result.put("restarted",second);if(error!=null)result.put("failure",error.toString());Files.writeString(Path.of("banya-native.json"),Journal.JSON.toJson(result));}catch(Exception e){getLogger().log(java.util.logging.Level.SEVERE,"Report failed",e);}Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);}
}
