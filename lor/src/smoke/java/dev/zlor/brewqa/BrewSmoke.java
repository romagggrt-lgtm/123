package dev.zlor.brewqa;
import dev.zlor.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;

/** Isolated QA plugin; never shipped inside Z_Lor. */
public final class BrewSmoke extends JavaPlugin {
 ZLorPlugin lor; Player one,two; World world; Location spot; int phase=-1,age;long startTick;
 final LinkedHashMap<String,Boolean> checks=new LinkedHashMap<>();
 final NamespacedKey itemId=new NamespacedKey("z_lor","brew");
 @Override public void onEnable(){lor=(ZLorPlugin)Bukkit.getPluginManager().getPlugin("Z_Lor");if(lor==null||!lor.isEnabled()){fail("plugin unavailable");return;}world=Bukkit.getWorlds().getFirst();spot=new Location(world,0,65,0);world.getChunkAt(spot).load();if(Files.exists(getServer().getWorldContainer().toPath().resolve("brew-restart-marker")))phase=100;Bukkit.getScheduler().runTaskTimer(this,this::tick,1,1);}
 ItemStack awkward(){var i=new ItemStack(Material.POTION);var m=(PotionMeta)i.getItemMeta();m.setBasePotionType(PotionType.AWKWARD);i.setItemMeta(m);return i;}
 ItemStack water(){var i=new ItemStack(Material.POTION);var m=(PotionMeta)i.getItemMeta();m.setBasePotionType(PotionType.WATER);i.setItemMeta(m);return i;}
 String id(ItemStack i){return i!=null&&i.getItemMeta() instanceof PotionMeta m?m.getPersistentDataContainer().get(itemId,PersistentDataType.STRING):null;}
 void check(String name,boolean condition){checks.put(name,condition);if(!condition)throw new IllegalStateException(name);}
 void next(){phase++;age=0;}
 void prepare(ItemStack input,ItemStack ingredient,boolean mixed){
  Block block=spot.getBlock();block.setType(Material.BREWING_STAND);BrewingStand state=(BrewingStand)block.getState();state.setFuelLevel(0);state.setBrewingTime(0);state.update(true,false);
  BrewerInventory inv=((BrewingStand)block.getState()).getInventory();inv.clear();inv.setFuel(new ItemStack(Material.BLAZE_POWDER));inv.setIngredient(ingredient);
  inv.setItem(0,input.clone());inv.setItem(1,input.clone());inv.setItem(2,mixed?water():input.clone());
 }
 void runBrew(ItemStack input,ItemStack ingredient,boolean mixed,String result,Material form){
  if(age==1)prepare(input,ingredient,mixed);
  BrewingStand state=(BrewingStand)spot.getBlock().getState();
  if(age==8){check("started_"+phase,state.getBrewingTime()>=390&&state.getBrewingTime()<=400);check("fuel_"+phase,state.getFuelLevel()>0);state.setBrewingTime(1);state.update(true,false);}
  if(age==13){BrewerInventory inv=((BrewingStand)spot.getBlock().getState()).getInventory();
   for(int i=0;i<(mixed?2:3);i++)check("result_"+phase+"_"+i,inv.getItem(i)!=null&&inv.getItem(i).getType()==form&&(result.equals("VANILLA_AWKWARD")?((PotionMeta)inv.getItem(i).getItemMeta()).getBasePotionType()==PotionType.AWKWARD:Objects.equals(id(inv.getItem(i)),result)));
   if(mixed)check("unmatched_unchanged",inv.getItem(2)!=null&&((PotionMeta)inv.getItem(2).getItemMeta()).getBasePotionType()==PotionType.WATER);
   next();
  }
 }
 @SuppressWarnings("deprecation") void tick(){try{
  age++;
  if(phase==-1){one=Bukkit.getPlayerExact("BrewOne");two=Bukkit.getPlayerExact("BrewTwo");if(one==null||two==null)return;one.teleport(new Location(world,4.5,66,2.5));two.teleport(new Location(world,6.5,66,2.5));phase=20;age=0;return;}
  switch(phase){
   case 20 -> runBrew(water(),new ItemStack(Material.NETHER_WART),false,"VANILLA_AWKWARD",Material.POTION);
   case 21 -> {
    if(age==1){prepare(awkward(),null,false);Block hopper=spot.clone().add(0,1,0).getBlock();hopper.setType(Material.HOPPER);((org.bukkit.block.Hopper)hopper.getState()).getInventory().setItem(0,new ItemStack(Material.SHULKER_SHELL));}
    if(age==40){BrewingStand state=(BrewingStand)spot.getBlock().getState();check("hopper_inserts_custom_ingredient",state.getInventory().getIngredient()!=null&&state.getInventory().getIngredient().getType()==Material.SHULKER_SHELL);check("hopper_starts_native_brewing",state.getBrewingTime()>0);spot.clone().add(0,1,0).getBlock().setType(Material.AIR);phase=0;age=0;}
   }
   case 0 -> runBrew(awkward(),new ItemStack(Material.SHULKER_SHELL),false,"LEVITATION",Material.POTION);
   case 1 -> runBrew(awkward(),lor.items.make("void_breath"),true,"EROSION_1",Material.POTION);
   case 2 -> runBrew(lor.brewing.potion("EROSION_1",Material.POTION),new ItemStack(Material.GLOWSTONE_DUST),false,"EROSION_2",Material.POTION);
   case 3 -> runBrew(lor.brewing.potion("EROSION_1",Material.POTION),new ItemStack(Material.GHAST_TEAR),false,"RESTORATION",Material.POTION);
   case 4 -> runBrew(lor.brewing.potion("LEVITATION",Material.POTION),new ItemStack(Material.REDSTONE),false,"LEVITATION_LONG",Material.POTION);
   case 5 -> runBrew(lor.brewing.potion("EROSION_1",Material.POTION),new ItemStack(Material.GUNPOWDER),false,"EROSION_1",Material.SPLASH_POTION);
   case 6 -> runBrew(lor.brewing.potion("EROSION_1",Material.SPLASH_POTION),new ItemStack(Material.DRAGON_BREATH),false,"EROSION_1",Material.LINGERING_POTION);
   case 7 -> {
    if(age==1)prepare(lor.brewing.potion("EROSION_2",Material.POTION),new ItemStack(Material.GLOWSTONE_DUST),false);
    if(age==12){check("strong_cannot_be_further_upgraded",((BrewingStand)spot.getBlock().getState()).getBrewingTime()==0);next();}
   }
   case 8 -> {
    if(age==1){ItemStack fake=new ItemStack(Material.PAPER);fake.editMeta(m->m.displayName(net.kyori.adventure.text.Component.text("Дыхание Войда")));prepare(awkward(),fake,false);}
    if(age==12){check("renamed_paper_not_an_ingredient",((BrewingStand)spot.getBlock().getState()).getBrewingTime()==0);next();}
   }
   case 9 -> {
    if(age==1){one.setHealth(20);one.setNoDamageTicks(0);startTick=lor.tick;lor.brewing.consume(new PlayerItemConsumeEvent(one,lor.brewing.potion("EROSION_1",Material.POTION)));}
    long dt=lor.tick-startTick;if(dt==39)check("erosion_first_not_early",Math.abs(one.getHealth()-20)<.001);
    if(dt==41)check("erosion_one_hp_after_two_seconds",Math.abs(one.getHealth()-19)<.001);
    if(dt==81){check("erosion_repeats_after_two_seconds",Math.abs(one.getHealth()-18)<.001);one.setHealth(1);one.setNoDamageTicks(0);}
    if(dt==121){check("erosion_one_cannot_kill",!one.isDead()&&one.getHealth()>=1);next();}
   }
   case 10 -> {
    if(age==1){two.getInventory().clear();two.getInventory().addItem(new ItemStack(Material.DIAMOND,5));two.setHealth(2);two.setNoDamageTicks(0);startTick=lor.tick;lor.brewing.consume(new PlayerItemConsumeEvent(two,lor.brewing.potion("EROSION_2",Material.POTION)));}
    long dt=lor.tick-startTick;if(dt==39)check("erosion_two_not_early",!two.isDead());
    if(dt==43){check("erosion_two_can_kill",two.isDead());next();}
   }
   case 11 -> {if(age==5){boolean protectedDrop=world.getNearbyEntities(two.getLocation(),4,4,4).stream().filter(e->e instanceof Item).map(e->(Item)e).anyMatch(i->i.getItemStack().getType()==Material.DIAMOND&&i.getItemStack().getAmount()==5&&i.isInvulnerable());check("erosion_death_drops_protected",protectedDrop);next();}}
   case 12 -> {
    if(age==1)prepare(awkward(),new ItemStack(Material.SHULKER_SHELL),false);
    if(age==8){check("persist_brew_running_before_restart",((BrewingStand)spot.getBlock().getState()).getBrewingTime()>0);finish("RESTART",null);}
   }
   case 100 -> {
    if(age==1){check("stand_survived_restart",spot.getBlock().getType()==Material.BREWING_STAND);BrewingStand state=(BrewingStand)spot.getBlock().getState();check("brewing_survived_restart",state.getBrewingTime()>0);check("ingredient_survived_restart",state.getInventory().getIngredient()!=null&&state.getInventory().getIngredient().getType()==Material.SHULKER_SHELL);startTick=state.getBrewingTime();}
    if(age==22){check("brewing_progressed_after_restart",((BrewingStand)spot.getBlock().getState()).getBrewingTime()<startTick);finish("PASS",null);}
   }
  }
 }catch(Throwable e){fail(e.toString());}}
 void fail(String error){getLogger().severe("Brew QA: "+error);finish("FAIL",error);}
 void finish(String status,String error){try{
  com.google.gson.JsonObject result=new com.google.gson.JsonObject();result.addProperty("status",status);if(error!=null)result.addProperty("error",error);var c=new com.google.gson.JsonObject();checks.forEach(c::addProperty);result.add("checks",c);
  Files.writeString(getServer().getWorldContainer().toPath().resolve("brew-smoke.json"),result.toString());
 }catch(Exception ex){getLogger().severe(ex.toString());}Bukkit.getScheduler().cancelTasks(this);}
}
