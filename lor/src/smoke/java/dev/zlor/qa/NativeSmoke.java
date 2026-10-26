package dev.zlor.qa;

import dev.zlor.*;
import dev.zlor.rules.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/** Ephemeral CI server only; this JAR is never included in the user installer. */
public final class NativeSmoke extends JavaPlugin implements Listener {
    private ZLorPlugin p;private Player observer,target;private World normal,end;
    private int tick,stage,entered;private State.Device absorber;private Arrow inside,outside;private ItemDisplay floatingUi;
    private Dragons.Body dragon;private Location previous;private List<Location> towers;
    private long beamStart;private int pulses;private double totalDamage;private boolean recording,finished;
    private final Map<String,Object> checks=new LinkedHashMap<>();private final List<Long> pulseTicks=new ArrayList<>();
    @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);Bukkit.getScheduler().runTaskTimer(this,this::run,1,1);}
    private void check(String name,boolean ok){if(!ok)throw new IllegalStateException(name);checks.put(name,"PASS");}
    private void next(){stage++;entered=tick;}
    private int age(){return tick-entered;}
    private final boolean visual="1".equals(System.getenv("ZLOR_VISUAL"));
    private void face(Player player,Location at,Location center){at.setDirection(center.toVector().subtract(at.toVector()));player.teleport(at);if(visual){player.setAllowFlight(true);player.setFlying(true);}}
    private void run(){
        if(finished)return;tick++;
        if(visual)try{Files.writeString(Path.of("qa-stage.json"),StateStore.JSON.toJson(Map.of("stage",stage,"age",age(),"beam_age",beamStart==0?-1:p.tick-beamStart)));}catch(Exception ignored){}
        try{
            if(tick>6000)throw new IllegalStateException("Timeout at stage "+stage);
            if(stage==0){
                observer=Bukkit.getPlayerExact("LorObserver");target=Bukkit.getPlayerExact("LorTarget");if(observer==null||target==null)return;
                p=(ZLorPlugin)Bukkit.getPluginManager().getPlugin("Z_Lor");check("real_plugin_enabled",p!=null&&p.isEnabled());
                normal=Bukkit.getWorlds().stream().filter(w->w.getEnvironment()==World.Environment.NORMAL).findFirst().orElseThrow();end=Bukkit.getWorlds().stream().filter(w->w.getEnvironment()==World.Environment.THE_END).findFirst().orElseThrow();
                for(World world:Bukkit.getWorlds())check("native_boundary_"+world.getKey(),p.nativeBoundary.ready(world));
                observer.setOp(true);observer.setGameMode(GameMode.CREATIVE);target.setGameMode(GameMode.SURVIVAL);normal.setDifficulty(Difficulty.HARD);
                observer.teleport(new Location(normal,10.5,71,.5));target.teleport(new Location(normal,.5,71,.5));
                for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)normal.getBlockAt(x,70,z).setType(Material.OBSIDIAN);
                target.setHealth(20);target.setNoDamageTicks(0);
                p.store.change(s->{s.roles.put(observer.getUniqueId(),new State.Role(observer.getName(),p.now()));s.dimensions.put(p.worldKey(normal),DimensionVoid.vanilla(normal.getMinHeight()-64,p.now()).set(80,p.now()));});next();return;
            }
            check("plugin_stays_enabled",p.isEnabled());
            if(!p.store.idle())return;
            if(stage==1&&age()>=25){
                check("real_native_void_damage",target.getHealth()<20&&target.getLastDamageCause()!=null&&target.getLastDamageCause().getCause()==EntityDamageEvent.DamageCause.VOID);
                absorber=new State.Device("void_absorber",new State.Pos(p.worldKey(normal),1,70,1),0);absorber.fuelUntil=p.fuelNow()+900000;normal.getBlockAt(1,70,1).setType(Material.TARGET);
                p.store.change(s->s.devices.put(absorber.id,absorber));next();return;
            }
            if(stage==2&&age()>=5){
                target.setHealth(20);target.setNoDamageTicks(0);observer.teleport(new Location(normal,100.5,71,.5));
                normal.getChunkAt(6,0).load();normal.setChunkForceLoaded(6,0,true);
                inside=normal.spawn(new Location(normal,8.5,73,8.5),Arrow.class);outside=normal.spawn(new Location(normal,100.5,73,.5),Arrow.class);floatingUi=normal.spawn(new Location(normal,100.5,73,2.5),ItemDisplay.class,e->e.setPersistent(false));next();return;
            }
            if(stage==3&&age()>=25){
                checks.put("shield_diagnostic",Map.of("health",target.getHealth(),"position",target.getLocation().toString(),"shield",p.protectedAt(target),"cause",String.valueOf(target.getLastDamageCause()==null?null:target.getLastDamageCause().getCause())));check("absorber_blocks_native_player_void",target.getHealth()==20);check("shield_keeps_real_projectile",inside.isValid());check("unshielded_projectile_native_discard",!outside.isValid());check("render_only_display_survives_raised_void",floatingUi.isValid());floatingUi.remove();normal.setChunkForceLoaded(6,0,false);
                p.store.change(s->s.dimensions.put(p.worldKey(normal),DimensionVoid.vanilla(normal.getMinHeight()-64,p.now())));if(visual)face(observer,new Location(normal,85.5,85,.5),new Location(normal,1.5,70.5,1.5));else observer.teleport(new Location(normal,1.5,71,.5));if(visual){normal.getBlockAt(79,82,0).setType(Material.STONE);normal.getBlockAt(79,83,0).setType(Material.COPPER_BLOCK);normal.getBlockAt(78,83,0).setType(Material.STONE);normal.getBlockAt(78,84,0).setType(Material.REDSTONE_TORCH);}
                Bukkit.dispatchCommand(observer,"lore view all");next();return;
            }
            if(visual&&stage==4&&age()%10==0){var camera=observer.getLocation();camera.setYaw(90);camera.setPitch(12);observer.teleport(camera);}
            if(stage==4&&age()>=(visual?180:8)){
                var hud=normal.getEntities().stream().filter(e->e instanceof TextDisplay).map(e->(TextDisplay)e).findFirst().orElseThrow();check("hud_readable_scale",hud.getTransformation().getScale().x>=.2);check("hud_private",observer.canSee(hud)&&!target.canSee(hud));
                check("sphere_world_mesh",normal.getEntities().stream().filter(e->e instanceof ItemDisplay).map(e->(ItemDisplay)e).anyMatch(e->new NamespacedKey("z_lor","dome_purple").equals(e.getItemStack().getItemMeta().getItemModel())&&e.getItemStack().getItemMeta().getCustomModelDataComponent().getFlags().size()==642&&e.getDisplayWidth()>100));
                var sphere=normal.getEntities().stream().filter(e->e instanceof ItemDisplay).map(e->(ItemDisplay)e).filter(e->new NamespacedKey("z_lor","dome_purple").equals(e.getItemStack().getItemMeta().getItemModel())).findFirst().orElseThrow();check("sphere_frustum_culling_disabled",sphere.getDisplayHeight()==0);

                Bukkit.dispatchCommand(observer,"lore view off");observer.teleport(new Location(normal,1.5,71,.5));observer.getInventory().clear();var fuel=p.items.make("liquid_void");fuel.setAmount(4);observer.getInventory().setItem(9,fuel);
                Bukkit.getPluginManager().callEvent(new PlayerInteractEvent(observer,Action.RIGHT_CLICK_BLOCK,null,p.devices.block(absorber),org.bukkit.block.BlockFace.UP,EquipmentSlot.HAND));next();return;
            }
            if(stage==5){
                if(p.state().devices.get(absorber.id).reserves()!=4){if(age()>(visual?400:180))throw new IllegalStateException("Real protocol GUI transfer timed out");return;}
                check("protocol_pickup_then_fuel_place",observer.getInventory().getItem(9)==null||observer.getInventory().getItem(9).isEmpty());check("protocol_cursor_not_duplicated",observer.getItemOnCursor().isEmpty());observer.closeInventory();
                target.setGameMode(GameMode.CREATIVE);observer.teleport(new Location(end,.5,90,.5));target.teleport(new Location(end,10.5,90,.5));p.dragons.stop(observer);next();return;
            }
            if(stage==6&&age()>=15){
                towers=NativeSupport.towers(end);for(var at:towers){end.getChunkAt(at.getBlockX()>>4,at.getBlockZ()>>4).load();if(!NativeSupport.entityTicking(end,at.getBlockX()>>4,at.getBlockZ()>>4))return;}
                for(var at:towers)for(Entity entity:end.getNearbyEntities(at.clone().add(.5,0,.5),1,2,1,e->e instanceof EnderCrystal))entity.remove();
                p.dragons.spawn(observer,"enriched");next();return;
            }
            if(stage==7){
                dragon=p.dragons.bodies().stream().filter(b->b.kind.equals("enriched")&&b.entity.getWorld().equals(end)).findFirst().orElse(null);if(dragon==null){if(age()>360)throw new IllegalStateException("Dragon command spawn timed out");return;}
                check("all_tower_crystals_restored",towers.size()==10&&towers.stream().allMatch(at->end.getNearbyEntities(at.clone().add(.5,0,.5),1,2,1,e->e instanceof EnderCrystal).size()==1));
                check("vanilla_dragon_entity",dragon.entity.getType()==EntityType.ENDER_DRAGON);check("vanilla_bossbar_title",dragon.bar.getTitle().equals("Эндер-дракон")&&!dragon.bar.getTitle().contains("❤"));check("no_global_dragon_nametag",dragon.entity.customName()==null);
                previous=dragon.entity.getLocation();check("no_immediate_spawn_beam",p.dragons.beams().isEmpty());next();return;
            }
            if(stage==8&&age()>=40){
                check("dragon_flies_after_command_spawn",dragon.entity.getLocation().distanceSquared(previous)>.25);
                var origin=p.structures.origin(end);for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)end.getBlockAt(origin.getBlockX()+x,89,origin.getBlockZ()+z).setType(Material.OBSIDIAN);
                target.getInventory().clear();target.setGameMode(GameMode.SURVIVAL);target.teleport(new Location(end,origin.getX(),90,origin.getZ()));target.setHealth(20);target.setNoDamageTicks(0);end.setDifficulty(Difficulty.HARD);
                if(visual)face(observer,new Location(end,origin.getX()+80,115,origin.getZ()+10),new Location(end,origin.getX(),90,origin.getZ()));
                if(visual)Bukkit.dispatchCommand(observer,"lore view dragons");
                check("manual_beam_started",p.dragons.startBeam(dragon));beamStart=p.tick;recording=true;next();return;
            }
            if(visual&&(stage==9||stage==10)&&age()%10==0){var camera=observer.getLocation();camera.setYaw(90);camera.setPitch(28);observer.teleport(camera);}
            if(stage==9){
                long elapsed=p.tick-beamStart;
                if(elapsed==100){var rays=end.getEntities().stream().filter(e->e instanceof ItemDisplay&&e.getPersistentDataContainer().has(new NamespacedKey("z_lor","beam_column"))).map(e->(ItemDisplay)e).toList();check("all_151_column_models_present",rays.size()==BeamPattern.COLUMNS.size());check("column_frustum_culling_disabled",rays.stream().allMatch(e->e.getDisplayHeight()==0));}
                if(elapsed==29)check("warning_does_not_damage",pulses==0);
                if(elapsed>=240){check("ten_one_second_damage_pulses",pulses==10&&pulseTicks.size()==10&&pulseTicks.get(9)-pulseTicks.get(0)==180);check("ownerless_magic_not_difficulty_scaled",Math.abs(totalDamage-20)<.001);check("one_erosion_credit_per_attack",p.state().beamHits.getOrDefault(target.getUniqueId(),0)==1&&target.getAttribute(Attribute.MAX_HEALTH).getValue()==19);next();}return;
            }
            if(stage==10&&p.tick-beamStart>=295){
                check("fade_does_not_damage",pulses==10);check("beam_cleanup",p.dragons.beams().isEmpty()&&end.getEntities().stream().noneMatch(e->e.getPersistentDataContainer().has(new NamespacedKey("z_lor","beam_column"))));recording=false;p.dragons.stop(observer);next();return;
            }
            if(stage==11&&age()>=5)finish(null);
        }catch(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"NATIVE_SMOKE_FAILED stage="+stage,error);finish(error);}
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void damage(EntityDamageEvent event){
        if(!recording||!event.getEntity().equals(target)||event.getCause()!=EntityDamageEvent.DamageCause.MAGIC)return;
        pulses++;totalDamage+=event.getFinalDamage();pulseTicks.add(p.tick-beamStart);
        Bukkit.getScheduler().runTask(this,()->{if(target.isOnline()&&!target.isDead())target.setHealth(target.getAttribute(Attribute.MAX_HEALTH).getValue());});
    }
    private void finish(Throwable error){
        if(finished)return;finished=true;
        try{
            var result=new LinkedHashMap<String,Object>();result.put("status",error==null?"PASS":"FAIL");result.put("plugin_version",p==null?"unknown":p.getDescription().getVersion());result.put("server",Bukkit.getVersion());result.put("checks",checks);result.put("pulse_ticks",pulseTicks);result.put("damage",totalDamage);result.put("client_rendered",false);result.put("source_commit",System.getenv("GITHUB_SHA"));if(error!=null)result.put("failure",error.toString());
            Files.writeString(Path.of("native-smoke.json"),StateStore.JSON.toJson(result));
        }catch(Exception writing){getLogger().log(java.util.logging.Level.SEVERE,"Smoke report failed",writing);}
        Bukkit.getScheduler().runTaskLater(this,()->Bukkit.shutdown(),5);
    }
}
