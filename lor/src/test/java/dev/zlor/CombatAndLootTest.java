package dev.zlor;

import java.util.*;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.damage.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.*;
import static org.junit.jupiter.api.Assertions.*;

class CombatAndLootTest {
    /** MockBukkit has no SCALE default; this fixture supplies that API value,
     * not a native renderer or a claim that vanilla dragon scaling was tested. */
    static final class TestDragon extends EnderDragonMock {
        private final AttributeInstance scale=new org.mockbukkit.mockbukkit.attribute.AttributeInstanceMock(Attribute.SCALE,1);
        TestDragon(ServerMock server,UUID id){super(server,id);}
        @Override public AttributeInstance getAttribute(Attribute type){return type==Attribute.SCALE?scale:super.getAttribute(type);}
    }
    /** Records damage delivery because MockBukkit does not implement DamageSource.
     * Native MAGIC armour/invulnerability handling remains a live-server check. */
    static final class BeamPlayer extends PlayerMock {
        DamageSource lastSource;int damageCalls;
        boolean ignoreDamage;
        BeamPlayer(ServerMock server){super(server,"BeamTester");}
        @Override public void damage(double amount,DamageSource source){lastSource=source;damageCalls++;if(!ignoreDamage)setHealth(Math.max(0,getHealth()-amount));}
    }
    ServerMock server;PluginTest.TestPlugin p;World world;PlayerMock player;
    @BeforeEach void setup()throws Exception{
        server=MockBukkit.mock();world=server.addSimpleWorld("combat_test");world.getChunkAt(0,0).load();
        p=MockBukkit.loadWith(PluginTest.TestPlugin.class,ZLorPlugin.class.getResourceAsStream("/plugin.yml"));assertTrue(p.isEnabled());
        player=server.addPlayer("CombatTester");player.teleport(new Location(world,.5,65,.5));settle();
    }
    @AfterEach void cleanup(){MockBukkit.unmock();}
    void settle()throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);do{server.getScheduler().performOneTick();Thread.sleep(1);}while(!p.store.idle()&&System.nanoTime()<end);assertTrue(p.store.idle());assertTrue(p.isEnabled());}
    Dragons.Body dragon(String kind,double health)throws Exception{
        var d=new State.Dragon();d.kind=kind;d.health=health;d.status="ACTIVE";d.world=p.worldKey(world);d.x=.5;d.y=90;d.z=.5;
        var entity=new TestDragon(server,d.id);entity.setLocation(new Location(world,d.x,d.y,d.z));server.registerEntity(entity);
        p.store.change(s->s.dragons.put(d.id,d));settle();p.dragons.restore();
        return p.dragons.bodies().stream().filter(b->b.id.equals(d.id)).findFirst().orElseThrow();
    }
    @Test void kingTakesRealLogicalDamageFromAll1500HpAndKeepsNativePoolInSync()throws Exception{
        var king=dragon("void_king",1500);assertEquals(1500,p.dragons.health(king));assertEquals(1024,king.entity.getHealth(),1e-6);
        var damage=new EntityDamageEvent(king.entity,EntityDamageEvent.DamageCause.ENTITY_ATTACK,500);p.dragons.damage(damage);assertTrue(damage.isCancelled());
        assertEquals(1000,p.dragons.health(king));assertEquals(1024*1000/1500d,king.entity.getHealth(),1e-6);
        var state=p.store.copy();p.dragons.capture(state);assertEquals(1000,state.dragons.get(king.id).health);
    }
    @Test void crystalPulsePrioritisesInjuredKingAndDoesNotHealEveryDragon()throws Exception{
        var king=dragon("void_king",1000);var normal=dragon("default",100);
        var event=new EntityRegainHealthEvent(normal.entity,1,EntityRegainHealthEvent.RegainReason.ENDER_CRYSTAL);p.dragons.heal(event);assertTrue(event.isCancelled());assertEquals(1001,p.dragons.health(king));
        var duplicate=new EntityRegainHealthEvent(king.entity,1,EntityRegainHealthEvent.RegainReason.ENDER_CRYSTAL);p.dragons.heal(duplicate);assertTrue(duplicate.isCancelled());assertEquals(1001,p.dragons.health(king));assertEquals(100,normal.entity.getHealth());
    }
    @Test void enrichedIgnoresArrowsButOrdinaryDoesNot()throws Exception{
        var enriched=dragon("enriched",500);var normal=dragon("default",200);var arrow=world.spawn(new Location(world,0,70,0),Arrow.class);
        var a=new EntityDamageByEntityEvent(arrow,enriched.entity,EntityDamageEvent.DamageCause.PROJECTILE,12);p.dragons.damage(a);assertTrue(a.isCancelled());
        var b=new EntityDamageByEntityEvent(arrow,normal.entity,EntityDamageEvent.DamageCause.PROJECTILE,12);p.dragons.damage(b);assertFalse(b.isCancelled());
    }
    @Test void dragonsNeverApplyFriendlyFireToOtherTrackedDragons()throws Exception{
        var one=dragon("default",200);var two=dragon("enriched",500);var event=new EntityDamageByEntityEvent(one.entity,two.entity,EntityDamageEvent.DamageCause.ENTITY_ATTACK,30);p.dragons.damage(event);assertTrue(event.isCancelled());
    }
    @Test void starChoosesEnrichedAndReducesNonLeadersIncomingDamageToThird()throws Exception{
        var normal=dragon("default",200);var enriched=dragon("enriched",500);p.store.change(s->{var arena=new State.Arena();arena.star=true;s.arenas.put(p.worldKey(world),arena);});settle();
        assertEquals(enriched.id,p.dragons.leader(world));var event=new EntityDamageByEntityEvent(player,normal.entity,EntityDamageEvent.DamageCause.ENTITY_ATTACK,30);p.dragons.damage(event);assertEquals(10,event.getDamage());
    }
    @Test void oneBeamLaunchCountsOnlyOnceEvenWithMultipleDamagePulses()throws Exception{
        var receiver=new BeamPlayer(server);server.addPlayer(receiver);receiver.teleport(new Location(world,.5,65,.5));player=receiver;settle();
        var source=dragon("enriched",500);var beam=new Dragons.Beam(source,new Location(world,.5,64,.5),p.tick);
        p.tick+=p.dragons.timeline().warning();
        var method=Dragons.class.getDeclaredMethod("hit",Dragons.Beam.class,Dragons.Body.class,Player.class);method.setAccessible(true);
        method.invoke(p.dragons,beam,source,player);settle();method.invoke(p.dragons,beam,source,player);settle();
        assertEquals(1,p.state().beamHits.get(player.getUniqueId()));assertEquals(19,player.getAttribute(Attribute.MAX_HEALTH).getValue());
        assertEquals(DamageType.MAGIC,receiver.lastSource.getDamageType());
        var second=new Dragons.Beam(source,new Location(world,.5,64,.5),p.tick);p.tick+=p.dragons.timeline().warning();method.invoke(p.dragons,second,source,player);settle();assertEquals(2,p.state().beamHits.get(player.getUniqueId()));assertEquals(18,player.getAttribute(Attribute.MAX_HEALTH).getValue());
    }
    private BeamPlayer receiver()throws Exception{
        var receiver=new BeamPlayer(server);server.addPlayer(receiver);receiver.teleport(new Location(world,.5,65,.5));player=receiver;settle();return receiver;
    }
    private void pulse(Dragons.Beam beam,Dragons.Body source,Player receiver)throws Exception{
        var method=Dragons.class.getDeclaredMethod("hit",Dragons.Beam.class,Dragons.Body.class,Player.class);method.setAccessible(true);method.invoke(p.dragons,beam,source,receiver);
    }
    @Test void warningFadeAndCreativePlayersNeverTakeBeamDamage()throws Exception{
        var receiver=receiver();var source=dragon("enriched",500);var beam=new Dragons.Beam(source,new Location(world,.5,64,.5),p.tick);long start=p.tick;
        pulse(beam,source,receiver);assertEquals(0,receiver.damageCalls);assertTrue(beam.counted.isEmpty());
        p.tick=start+30;receiver.setGameMode(GameMode.CREATIVE);pulse(beam,source,receiver);assertEquals(0,receiver.damageCalls);
        receiver.setGameMode(GameMode.SURVIVAL);p.tick=start+230;pulse(beam,source,receiver);assertEquals(0,receiver.damageCalls);assertFalse(p.state().beamHits.containsKey(receiver.getUniqueId()));
    }
    @Test void rejectedDamageDoesNotConsumeErosionCredit()throws Exception{
        var receiver=receiver();receiver.ignoreDamage=true;var source=dragon("enriched",500);var beam=new Dragons.Beam(source,new Location(world,.5,64,.5),p.tick);p.tick+=30;
        pulse(beam,source,receiver);settle();assertTrue(beam.counted.isEmpty());assertFalse(p.state().beamHits.containsKey(receiver.getUniqueId()));
        receiver.ignoreDamage=false;pulse(beam,source,receiver);settle();assertEquals(1,p.state().beamHits.get(receiver.getUniqueId()));assertEquals(19,receiver.getAttribute(Attribute.MAX_HEALTH).getValue());
    }
    @Test void diskAcknowledgementCannotSwallowTheNextDamagePulse()throws Exception{
        var receiver=receiver();var source=dragon("enriched",500);var beam=new Dragons.Beam(source,new Location(world,.5,64,.5),p.tick);p.tick+=30;
        pulse(beam,source,receiver);assertTrue(beam.pending.contains(receiver.getUniqueId()));p.tick+=20;pulse(beam,source,receiver);
        assertEquals(2,receiver.damageCalls);assertEquals(16,receiver.getHealth());assertNull(receiver.lastSource.getCausingEntity());assertNull(receiver.lastSource.getDirectEntity());settle();assertEquals(1,p.state().beamHits.get(receiver.getUniqueId()));
    }
    @Test void restoredDragonStartsInFlightWithFreshAttackCooldownAndVanillaPublicBar()throws Exception{
        p.tick=10_000;var body=dragon("enriched",500);
        assertEquals(EnderDragon.Phase.CIRCLING,body.entity.getPhase());assertTrue(p.tick-body.lastBeam<10);assertEquals("Эндер-дракон",body.bar.getTitle());assertNull(body.entity.customName());assertFalse(body.entity.isCustomNameVisible());
    }
    private PlayerDeathEvent nativeDeath(List<ItemStack> drops){
        player.setGameMode(GameMode.SURVIVAL);player.setLastDamageCause(new EntityDamageEvent(player,EntityDamageEvent.DamageCause.VOID,40));
        return new PlayerDeathEvent(player,DamageSource.builder(DamageType.OUT_OF_WORLD).build(),drops,0,(Component)null,false);
    }
    @Test void voidTotemConsumesExactlyOneAndMarksOnlyRealNativeDrops()throws Exception{
        var drops=new ArrayList<>(List.of(new ItemStack(Material.TOTEM_OF_UNDYING),new ItemStack(Material.DIAMOND,5)));var event=nativeDeath(drops);
        long before=world.getEntities().stream().filter(e->e instanceof Item).count();p.loot.death(event);assertEquals(before,world.getEntities().stream().filter(e->e instanceof Item).count());
        assertEquals(1,event.getDrops().size());assertEquals(Material.DIAMOND,event.getDrops().getFirst().getType());assertEquals(5,event.getDrops().getFirst().getAmount());
        Item actual=world.dropItem(player.getLocation(),event.getDrops().getFirst().clone());assertTrue(p.loot.protectsItem(actual));assertFalse(actual.hasGravity());assertTrue(actual.isInvulnerable());assertEquals(5,actual.getItemStack().getAmount());
    }
    @Test void yellowShieldDividesOnlyVoidDamageByEightAndExpiresByWallClock()throws Exception{
        var event=nativeDeath(new ArrayList<>(List.of(new ItemStack(Material.TOTEM_OF_UNDYING),new ItemStack(Material.DIAMOND))));p.loot.death(event);Item actual=world.dropItem(player.getLocation(),event.getDrops().getFirst().clone());
        var damage=new EntityDamageEvent(player,EntityDamageEvent.DamageCause.VOID,4);p.voids.damage(damage);assertFalse(damage.isCancelled());assertEquals(.5,damage.getDamage());
        var combat=new EntityDamageEvent(player,EntityDamageEvent.DamageCause.ENTITY_ATTACK,4);p.voids.damage(combat);assertEquals(4,combat.getDamage());
        PluginTest.WALL.addAndGet(300_001);p.loot.tick();assertFalse(p.loot.protectsItem(actual));assertTrue(actual.hasGravity());assertFalse(actual.isInvulnerable());
    }
    @Test void keepInventoryOrMissingTotemCreatesNoFalseResourceShield(){
        var keep=nativeDeath(new ArrayList<>(List.of(new ItemStack(Material.TOTEM_OF_UNDYING),new ItemStack(Material.DIAMOND))));keep.setKeepInventory(true);p.loot.death(keep);assertTrue(p.loot.zones().isEmpty());
        var absent=nativeDeath(new ArrayList<>(List.of(new ItemStack(Material.DIAMOND))));p.loot.death(absent);assertTrue(p.loot.zones().isEmpty());
    }
    @Test void protectedAndOrdinaryDropsDoNotMergeAndExtendProtection(){
        var event=nativeDeath(new ArrayList<>(List.of(new ItemStack(Material.TOTEM_OF_UNDYING),new ItemStack(Material.DIAMOND))));p.loot.death(event);Item protectedItem=world.dropItem(player.getLocation(),event.getDrops().getFirst().clone());Item ordinary=world.dropItem(player.getLocation(),new ItemStack(Material.DIAMOND));
        var merge=new ItemMergeEvent(protectedItem,ordinary);p.loot.merge(merge);assertTrue(merge.isCancelled());assertFalse(p.loot.protectsItem(ordinary));
    }
    @Test void stopReopensAnAlreadyInactiveFountainWithoutOverwritingForeignBlocks()throws Exception{
        var structure=new State.Structures();structure.world=p.worldKey(world);structure.initialized=true;structure.portalX=0;structure.portalY=80;structure.portalZ=0;
        for(int[] xz:new int[][]{{0,0},{3,0},{-3,0},{0,3},{0,-3}})world.getBlockAt(xz[0],80,xz[1]).setType(Material.BEDROCK);
        world.getBlockAt(1,80,0).setType(Material.DIAMOND_BLOCK);
        p.store.change(s->s.structures.put(p.worldKey(world),structure));settle();p.structures.hidePortal(world,true);settle();
        assertTrue(p.state().structures.get(p.worldKey(world)).portalHidden);assertEquals(19,p.state().structures.get(p.worldKey(world)).portalCells.size());
        p.structures.hidePortal(world,false);settle();assertEquals(Material.END_PORTAL,world.getBlockAt(-1,80,0).getType());assertEquals(Material.DIAMOND_BLOCK,world.getBlockAt(1,80,0).getType());assertEquals(Material.BEDROCK,world.getBlockAt(0,80,0).getType());
    }
    @Test void greenEntryShieldRequiresActualPlatformAndProtectsPlayersOnly()throws Exception{
        var structure=new State.Structures();structure.world=p.worldKey(world);structure.initialized=true;structure.entryX=0;structure.entryY=64;structure.entryZ=0;
        p.store.change(s->s.structures.put(p.worldKey(world),structure));settle();((org.mockbukkit.mockbukkit.world.WorldMock)world).setEnvironment(World.Environment.THE_END);
        try{
            assertFalse(p.protectedAt(player));world.getBlockAt(0,64,0).setType(Material.OBSIDIAN);assertTrue(p.protectedAt(player));
            var arrow=world.spawn(player.getLocation(),Arrow.class);assertFalse(p.protectedAt(arrow));
            world.getBlockAt(0,64,0).setType(Material.AIR);assertFalse(p.protectedAt(player));
        }finally{((org.mockbukkit.mockbukkit.world.WorldMock)world).setEnvironment(World.Environment.NORMAL);}
    }
    @Test void stoppedDragonInSavedChunkIsRemovedInsteadOfBeingResurrectedOnRestore()throws Exception{
        var data=new State.Dragon();data.world=p.worldKey(world);data.status="STOPPED";
        var stale=new TestDragon(server,data.id);stale.setLocation(new Location(world,0,80,0));server.registerEntity(stale);
        p.store.change(s->s.dragons.put(data.id,data));settle();assertTrue(stale.isValid());p.dragons.restore();assertFalse(stale.isValid());assertTrue(p.dragons.bodies().isEmpty());
    }
}
