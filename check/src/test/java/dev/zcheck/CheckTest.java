package dev.zcheck;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import static org.junit.jupiter.api.Assertions.*;
class CheckTest {
 ServerMock server; ZCheckPlugin plugin; PlayerMock admin,guest; World world;
 @BeforeEach void setup(){server=MockBukkit.mock();world=server.addSimpleWorld("test");plugin=MockBukkit.load(ZCheckPlugin.class);plugin.getConfig().set("effects.enabled",false);plugin.getConfig().set("title.repeat-delays-ticks",List.of());admin=server.addPlayer("Admin");admin.setOp(true);guest=server.addPlayer("Guest");guest.teleport(new Location(world,0,64,0));}
 @AfterEach void cleanup(){MockBukkit.unmock();}
 @Test void commandPermissionSelfAndBypass(){
  server.dispatchCommand(guest,"zc Admin");assertFalse(plugin.isChecked(admin.getUniqueId()));
  server.dispatchCommand(admin,"zc Admin");assertFalse(plugin.isChecked(admin.getUniqueId()));
  guest.addAttachment(plugin,"zcheck.bypass",true);server.dispatchCommand(admin,"zc Guest");assertFalse(plugin.isChecked(guest.getUniqueId()));
 }
 @Test void freezeRetainsCameraAndBlocksMovementTeleportAndBreak(){
  server.dispatchCommand(admin,"zc Guest");assertTrue(plugin.isChecked(guest.getUniqueId()));
  Location from=guest.getLocation(),to=from.clone().add(2,0,0);to.setYaw(30);
  PlayerMoveEvent move=new PlayerMoveEvent(guest,from,to);server.getPluginManager().callEvent(move);
  assertEquals(from.getX(),move.getTo().getX());assertEquals(30,move.getTo().getYaw());
  PlayerTeleportEvent teleport=new PlayerTeleportEvent(guest,from,from.clone().add(5,0,0));server.getPluginManager().callEvent(teleport);assertTrue(teleport.isCancelled());
  var breakEvent=new BlockBreakEvent(world.getBlockAt(0,63,0),guest);server.getPluginManager().callEvent(breakEvent);assertTrue(breakEvent.isCancelled());
  server.dispatchCommand(admin,"zc Guest");assertFalse(plugin.isChecked(guest.getUniqueId()));
 }
 @Test void commandsAreDeniedExceptExactWhitelist(){
  server.dispatchCommand(admin,"zc Guest");
  var a=new PlayerCommandPreprocessEvent(guest,"/spawn");server.getPluginManager().callEvent(a);assertTrue(a.isCancelled());
  var b=new PlayerCommandPreprocessEvent(guest,"/msg Admin привет");server.getPluginManager().callEvent(b);assertFalse(b.isCancelled());
  var c=new PlayerCommandPreprocessEvent(guest,"/evil:msg Admin hello");server.getPluginManager().callEvent(c);assertTrue(c.isCancelled());
 }
 @Test void damageIsCancelledForCheckedPlayer(){
  server.dispatchCommand(admin,"zc Guest");var event=new EntityDamageEvent(guest,EntityDamageEvent.DamageCause.FALL,10);server.getPluginManager().callEvent(event);assertTrue(event.isCancelled());
 }
 @Test void journalSurvivesAndMalformedFileIsNotReplaced()throws Exception{
  Path dir=Files.createTempDirectory("zcheck");CheckStore store=new CheckStore(dir);var r=CheckRecord.create(guest,admin.getUniqueId());store.save(Map.of(r.player(),r));assertEquals(r,store.load().get(r.player()));
  Files.writeString(dir.resolve("checks.yml"),"schema: 1\nchecks: [bad\n");assertThrows(Exception.class,store::load);assertEquals("schema: 1\nchecks: [bad\n",Files.readString(dir.resolve("checks.yml")));
 }
 @Test void apiReanchorsWhileTeleporting(){
  server.dispatchCommand(admin,"zc Guest");Location next=guest.getLocation().clone().add(5,0,0);assertTrue(plugin.moveCheckedPlayer(guest,next));assertEquals(next.getX(),guest.getLocation().getX());
 }
}
