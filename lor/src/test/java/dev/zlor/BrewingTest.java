package dev.zlor;
import java.util.*;
import java.util.concurrent.TimeUnit;
import io.papermc.paper.potion.PotionMix;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import static org.junit.jupiter.api.Assertions.*;
class BrewingTest {
 ServerMock server;PluginTest.TestPlugin p;PlayerMock player;
 @BeforeEach void setup(){PluginTest.WALL.set(1_000_000);PluginTest.NANOS.set(1_000_000_000);server=MockBukkit.mock();server.addSimpleWorld("brew_test");p=MockBukkit.loadWith(PluginTest.TestPlugin.class,ZLorPlugin.class.getResourceAsStream("/plugin.yml"));assertTrue(p.isEnabled());player=server.addPlayer("Brewer");}
 @AfterEach void cleanup(){MockBukkit.unmock();}
 PotionMix mix(String id){return Objects.requireNonNull(p.brewMixes.get(new NamespacedKey("z_lor","brew_"+id)));}
 ItemStack awkward(){ItemStack i=new ItemStack(Material.POTION);PotionMeta m=(PotionMeta)i.getItemMeta();m.setBasePotionType(PotionType.AWKWARD);i.setItemMeta(m);return i;}
 void settle()throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);do{server.getScheduler().performOneTick();Thread.sleep(1);}while(!p.store.idle()&&System.nanoTime()<end);assertTrue(p.store.idle());}
 @Test void distinctBreathIdentityAndVanillaRenames(){
  var m=mix("erosion");assertTrue(m.getInput().test(awkward()));assertTrue(m.getIngredient().test(p.items.make("void_breath")));
  assertFalse(m.getIngredient().test(p.items.make("liquid_void")));var spoof=new ItemStack(Material.PAPER);spoof.editMeta(v->v.displayName(net.kyori.adventure.text.Component.text("Дыхание Войда")));assertFalse(m.getIngredient().test(spoof));
 }
 @Test void recipesEnforceFormsAndErosionMaximum(){
  var weak=p.brewing.potion("EROSION_1",Material.POTION);var strong=p.brewing.potion("EROSION_2",Material.POTION);
  var upgrade=mix("strong_potion");assertTrue(upgrade.getInput().test(weak));assertFalse(upgrade.getInput().test(strong));assertTrue(upgrade.getIngredient().test(new ItemStack(Material.GLOWSTONE_DUST)));
  assertTrue(mix("restore_potion").getInput().test(weak));assertFalse(mix("restore_potion").getInput().test(strong));
  assertTrue(mix("splash_erosion_1").getInput().test(weak));assertFalse(mix("splash_erosion_1").getInput().test(p.brewing.potion("EROSION_1",Material.SPLASH_POTION)));
  assertTrue(mix("lingering_erosion_1").getIngredient().test(new ItemStack(Material.DRAGON_BREATH)));
 }
 @Test void potionDataAndLevitationDuration(){
  PotionMeta meta=(PotionMeta)p.brewing.potion("LEVITATION",Material.POTION).getItemMeta();assertEquals(200,meta.getCustomEffects().getFirst().getDuration());
  PotionMeta longer=(PotionMeta)p.brewing.potion("LEVITATION_LONG",Material.POTION).getItemMeta();assertEquals(600,longer.getCustomEffects().getFirst().getDuration());
 }
 @Test void recoveryPreservesBeamCounterAndCannotExceedLoss()throws Exception{
  UUID id=player.getUniqueId();p.store.change(s->s.beamHits.put(id,6));settle();p.dragons.applyMaxLoss(player);
  var item=p.brewing.potion("RESTORATION",Material.POTION);p.brewing.consume(new PlayerItemConsumeEvent(player,item));settle();assertEquals(6,p.state().beamHits.get(id));assertEquals(2,p.state().beamRestored.get(id));
  for(int i=0;i<5;i++){p.brewing.consume(new PlayerItemConsumeEvent(player,item));settle();}assertEquals(6,p.state().beamRestored.get(id));assertEquals(6,p.state().beamHits.get(id));
 }
 @Test void erosionFirstPulseOccursAfterFortyTicksAndOneCannotKill(){
  player.setHealth(20);long start=p.tick;p.brewing.consume(new PlayerItemConsumeEvent(player,p.brewing.potion("EROSION_1",Material.POTION)));
  for(int i=1;i<40;i++){p.tick=start+i;p.brewing.tick();}assertEquals(20,player.getHealth());
  p.tick=start+40;p.brewing.tick();assertEquals(19,player.getHealth());
  player.setHealth(1);p.tick=start+80;p.brewing.tick();assertEquals(1,player.getHealth());
 }
 @Test void repeatedErosionKeepsStrongerLevelAndRefreshesWithoutStacking(){
  player.setHealth(20);long start=p.tick;p.brewing.consume(new PlayerItemConsumeEvent(player,p.brewing.potion("EROSION_2",Material.POTION)));
  p.tick=start+10;p.brewing.consume(new PlayerItemConsumeEvent(player,p.brewing.potion("EROSION_1",Material.POTION)));
  p.tick=start+40;p.brewing.tick();assertEquals(18,player.getHealth());
 }
 @Test void legacyStateLoadsWithoutNewField(){String json=StateStore.JSON.toJson(new State());var doc=com.google.gson.JsonParser.parseString(json).getAsJsonObject();doc.remove("beamRestored");State old=StateStore.JSON.fromJson(doc,State.class);old.validate();assertTrue(old.beamRestored.isEmpty());}
}
