package dev.zpantera;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.block.BlockMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.entity.WolfMock;
import java.util.function.Consumer;
import org.mockbukkit.mockbukkit.world.*;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import static org.junit.jupiter.api.Assertions.*;

class PluginTest {
    static class TestServer extends ServerMock {
        boolean stopping;
        @Override public boolean isStopping(){return stopping;}
    }
    /** Explicit full-cube fixture, not a claim of native collision/pathfinding coverage. */
    static class Ground extends WorldMock {
        final Map<Coordinate,BlockMock> blocks=new HashMap<>();
        Ground(String name) {super(new WorldCreator(name));}
        @Override public <T extends Entity> T spawn(Location p,Class<T> type,Consumer<? super T> consumer,CreatureSpawnEvent.SpawnReason reason,boolean randomize,boolean event) {
            if(type==TextDisplay.class){
                var label=new org.mockbukkit.mockbukkit.entity.TextDisplayMock(MockBukkit.getMock(),UUID.randomUUID()){
                    Display.Billboard billboard;
                    @Override public void setBillboard(Display.Billboard b){billboard=b;}
                    @Override public Display.Billboard getBillboard(){return billboard;}
                };
                label.setLocation(p);if(consumer!=null)consumer.accept(type.cast(label));MockBukkit.getMock().registerEntity(label);return type.cast(label);
            }
            if(type!=Wolf.class)return super.spawn(p,type,consumer,reason,randomize,event);
            var wolf=new WolfMock(MockBukkit.getMock(),UUID.randomUUID()) {
                final Map<org.bukkit.attribute.Attribute,org.bukkit.attribute.AttributeInstance> extra=new HashMap<>();
                @Override public org.bukkit.attribute.AttributeInstance getAttribute(org.bukkit.attribute.Attribute a){
                    var original=super.getAttribute(a);
                    return original!=null?original:extra.computeIfAbsent(a,k->new org.mockbukkit.mockbukkit.attribute.AttributeInstanceMock(k,1));
                }
                boolean removeFar;
                @Override public void setRemoveWhenFarAway(boolean b){removeFar=b;}
                @Override public boolean getRemoveWhenFarAway(){return removeFar;}
                boolean pickup;
                @Override public void setCanPickupItems(boolean b){pickup=b;}
                @Override public boolean getCanPickupItems(){return pickup;}
            };
            wolf.setLocation(p);if(consumer!=null)consumer.accept(type.cast(wolf));MockBukkit.getMock().registerEntity(wolf);
            if(event){var spawn=new CreatureSpawnEvent(wolf,reason);Bukkit.getPluginManager().callEvent(spawn);if(spawn.isCancelled())wolf.remove();}
            return type.cast(wolf);
        }
        @Override public BlockMock getBlockAt(int x,int y,int z) {
            return blocks.computeIfAbsent(new Coordinate(x,y,z),c->new BlockMock(Material.AIR,new Location(this,x,y,z)) {
                @Override public boolean isPassable(){return !getType().isSolid();}
                @Override public BoundingBox getBoundingBox(){double n=getType().isAir()?0:1;return new BoundingBox(x,y,z,x+n,y+n,z+n);}
            });
        }
        @Override public BlockMock getBlockAt(Coordinate c){return getBlockAt(c.x,c.y,c.z);}
        @Override public BlockMock getBlockAt(Location p){return getBlockAt(p.getBlockX(),p.getBlockY(),p.getBlockZ());}
    }
    static class TestPlayer extends PlayerMock {
        int particles,sounds,packs; Location soundOrigin;String soundKey;SoundCategory soundCategory;
        TestPlayer(ServerMock s,String name){super(s,name);}
        @Override public void playSound(Location p,String key,SoundCategory category,float volume,float pitch){sounds++;soundOrigin=p.clone();soundKey=key;soundCategory=category;}
        @Override public void spawnParticle(Particle p,Location at,int count,double x,double y,double z,double extra){assertEquals(Particle.HEART,p);particles+=count;}
        @Override public void addResourcePack(UUID id,String url,byte[] hash,String prompt,boolean required){packs++;}
    }
    static class TestNavigation implements Navigation {
        int moves,stops;Location destination;double speed;
        public void prepare(Wolf w){} // Native MobGoals/Pathfinder are not implemented by MockBukkit.
        public boolean move(Wolf w,Location p,double speed){moves++;destination=p.clone();this.speed=speed;return true;}
        public void stop(Wolf w){stops++;}
    }
    ServerMock server; Ground world; ZPanteraPlugin plugin; TestPlayer player;
    TestNavigation nav;AtomicLong now;
    @BeforeEach void setup(){
        server=MockBukkit.mock(new TestServer());world=new Ground("pantera_test");server.addWorld(world);floor(world);
        plugin=MockBukkit.load(ZPanteraPlugin.class);nav=new TestNavigation();plugin.navigation=nav;
        now=new AtomicLong(System.currentTimeMillis());plugin.clock=now::get;
        player=new TestPlayer(server,"PanteraTester");server.addPlayer(player);player.setOp(true);player.teleport(new Location(world,0,65,0));
    }
    static void floor(Ground world){
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)world.loadChunk(x,z);
        for(int x=-8;x<=14;x++)for(int z=-8;z<=14;z++)world.getBlockAt(x,64,z).setType(Material.STONE);
    }
    @AfterEach void cleanup(){MockBukkit.unmock();}
    void command(String args){assertTrue(server.dispatchCommand(server.getConsoleSender(),"zpantera "+args));}
    void ticks(int n){server.getScheduler().performTicks(n);}
    PanteraRuntime spawn(){assertTrue(server.dispatchCommand(player,"zpantera spawn"));return plugin.active.values().stream().findFirst().orElseThrow();}
    PanteraRuntime place(int x,int z){
        ticks(2); // One new physical egg click, distinct from both-hand events in the same tick.
        command("give PanteraTester 1");
        server.getPluginManager().callEvent(new PlayerInteractEvent(player,Action.RIGHT_CLICK_BLOCK,player.getInventory().getItemInMainHand(),
            world.getBlockAt(x,64,z),BlockFace.UP,EquipmentSlot.HAND));
        return plugin.active.values().stream().findFirst().orElse(null);
    }
    void click(PanteraRuntime r){server.getPluginManager().callEvent(new PlayerInteractEntityEvent(player,r.headHit,EquipmentSlot.HAND));}
    @Test void customEggCreatesRealRigAndConsumesOnlyInSurvival(){
        PanteraRuntime r=place(2,0);assertNotNull(r);assertTrue(player.getInventory().getItemInMainHand().isEmpty());
        assertEquals(9,r.parts.size());assertEquals(13,r.entities.size());assertTrue(r.base.isInvisible());assertTrue(r.base.isSilent());
        assertEquals(30,r.base.getHealth());assertTrue(r.entities.stream().noneMatch(Entity::isPersistent));
        command("removeall");player.setGameMode(GameMode.CREATIVE);assertNotNull(place(2,0));assertEquals(1,player.getInventory().getItemInMainHand().getAmount());
    }
    @Test void firstRightClickTamesAndUsesOriginalSoundKeyAtPanther(){
        PanteraRuntime r=place(2,0);click(r);
        assertEquals(player.getUniqueId(),r.state.owner);assertEquals(now.get()+300000,r.state.expiresAt);
        assertEquals(1,player.sounds);assertEquals("z_pantera:pantera",player.soundKey);assertEquals(r.base.getLocation(),player.soundOrigin);
        assertEquals(SoundCategory.MASTER,player.soundCategory);
        assertEquals(28,player.particles);
    }
    @Test void leftClickCannotDamageAndHasSameTamingMechanic(){
        PanteraRuntime r=place(2,0);
        var attack=new PrePlayerAttackEntityEvent(player,r.bodyHit,true);server.getPluginManager().callEvent(attack);
        assertTrue(attack.isCancelled());assertEquals(player.getUniqueId(),r.state.owner);assertEquals(1,player.sounds);
        var damage=new EntityDamageByEntityEvent(player,r.base,EntityDamageEvent.DamageCause.ENTITY_ATTACK,7);
        server.getPluginManager().callEvent(damage);assertTrue(damage.isCancelled());assertEquals(30,r.base.getHealth());assertEquals(1,player.sounds);
    }
    @Test void cooldownDeduplicatesBothHandsAtEntityAndDamageEvents(){
        PanteraRuntime r=place(2,0);click(r);
        server.getPluginManager().callEvent(new PlayerInteractAtEntityEvent(player,r.headHit,new org.bukkit.util.Vector(),EquipmentSlot.HAND));
        server.getPluginManager().callEvent(new PlayerInteractEntityEvent(player,r.headHit,EquipmentSlot.OFF_HAND));click(r);
        assertEquals(1,player.sounds);assertEquals(28,player.particles);
        now.addAndGet(1999);click(r);assertEquals(1,player.sounds);now.incrementAndGet();click(r);assertEquals(2,player.sounds);assertEquals(56,player.particles);
    }
    @Test void heartsContinueWithoutRepeatingAudio(){PanteraRuntime r=place(2,0);click(r);ticks(102);assertTrue(player.particles>=38);assertEquals(1,player.sounds);}
    @Test void ownerQuitRemovesEveryModelAndRecord(){
        PanteraRuntime r=place(2,0);click(r);server.getPluginManager().callEvent(new PlayerQuitEvent(player,Component.empty()));
        assertTrue(plugin.states.isEmpty());assertTrue(plugin.active.isEmpty());assertTrue(r.entities.isEmpty());
        assertTrue(world.getEntities().stream().noneMatch(e->e instanceof Wolf||e instanceof ItemDisplay||e instanceof Interaction||e instanceof TextDisplay));
    }
    @Test void expiresExactlyFiveMinutesAfterFirstTameWithoutClickExtension(){
        PanteraRuntime r=place(2,0);click(r);long expiration=r.state.expiresAt;now.addAndGet(120000);click(r);assertEquals(expiration,r.state.expiresAt);
        now.set(expiration-1);ticks(20);assertEquals(1,plugin.states.size());now.incrementAndGet();ticks(20);assertTrue(plugin.states.isEmpty());
    }
    @Test void nonDamageableInteractionStillRespondsToLeftClick(){
        PanteraRuntime r=place(2,0);var attack=new PrePlayerAttackEntityEvent(player,r.headHit,false);
        assertTrue(attack.isCancelled());server.getPluginManager().callEvent(attack);assertEquals(player.getUniqueId(),r.state.owner);assertEquals(1,player.sounds);
    }
    @Test void nativePathRequestsUseOwnerPositionAndRotationAloneNeverChangesIt(){
        PanteraRuntime r=place(2,0);click(r);player.teleport(new Location(world,9,65,0,0,0));ticks(10);
        assertTrue(nav.moves>0);Location first=nav.destination.clone();
        player.setRotation(170,0);ticks(10);assertEquals(first.toVector(),nav.destination.toVector());
        assertEquals(player.getLocation().toVector(),nav.destination.toVector());
        player.teleport(r.base.getLocation().clone().add(2,0,0));ticks(10);assertFalse(r.moving);assertTrue(nav.stops>0);
        int requests=nav.moves;player.setRotation(-70,0);ticks(30);assertEquals(requests,nav.moves);
    }
    @Test void sprintUsesFasterPathSpeed(){
        PanteraRuntime r=place(2,0);click(r);player.teleport(new Location(world,7,65,0));ticks(10);double walking=nav.speed;
        player.setSprinting(true);ticks(10);assertTrue(nav.speed>walking);
    }
    @Test void farOwnerCausesSafeTeleportAndEveryPartChangesWorld(){
        PanteraRuntime r=place(2,0);click(r);Ground second=new Ground("other_world");server.addWorld(second);floor(second);
        player.teleport(new Location(second,0,65,0));ticks(12);assertSame(second,r.base.getWorld());
        assertTrue(SafePosition.safe(r.base.getLocation()));for(Entity e:r.entities)assertSame(second,e.getWorld());
        double d=r.base.getLocation().distance(player.getLocation());assertTrue(d>=1.8&&d<=4.8);
    }
    @Test void unsafeTeleportRedirectIsCancelled(){
        PanteraRuntime r=place(2,0);Location unsafe=new Location(world,0,20,0);
        var e=new EntityTeleportEvent(r.base,r.base.getLocation(),unsafe);server.getPluginManager().callEvent(e);assertTrue(e.isCancelled());
    }
    @Test void otherPlayerCanRepeatHeartsButCannotStealOwner(){
        PanteraRuntime r=place(2,0);click(r);long deadline=r.state.expiresAt;
        TestPlayer stranger=new TestPlayer(server,"Friend");server.addPlayer(stranger);stranger.teleport(new Location(world,1,65,0));
        now.addAndGet(2000);server.getPluginManager().callEvent(new PlayerInteractEntityEvent(stranger,r.headHit,EquipmentSlot.HAND));
        assertEquals(player.getUniqueId(),r.state.owner);assertEquals(deadline,r.state.expiresAt);assertEquals(1,stranger.sounds);
    }
    @Test void nearbyPlayersHearSoundAndSeeHeartsButDistantPlayersDoNot(){
        PanteraRuntime r=place(2,0);
        TestPlayer near=new TestPlayer(server,"Near"),far=new TestPlayer(server,"Far");server.addPlayer(near);server.addPlayer(far);
        near.teleport(new Location(world,8,65,0));far.teleport(new Location(world,40,65,0));click(r);
        assertEquals(1,near.sounds);assertEquals(28,near.particles);assertEquals(0,far.sounds);assertEquals(0,far.particles);
    }
    @Test void damageResistanceStillAllowsEnvironmentalDamageAndCanBeReconfigured(){
        PanteraRuntime r=place(2,0);var e=new EntityDamageEvent(r.base,EntityDamageEvent.DamageCause.FALL,8);server.getPluginManager().callEvent(e);
        assertFalse(e.isCancelled());assertEquals(2,e.getDamage());
        plugin.getConfig().set("pantera.invulnerable",true);plugin.saveConfig();command("reload");
        var invulnerable=new EntityDamageEvent(r.base,EntityDamageEvent.DamageCause.FIRE,8);server.getPluginManager().callEvent(invulnerable);
        assertTrue(invulnerable.isCancelled());assertTrue(r.base.isInvulnerable());
    }
    @Test void cannotAttackTargetOrDamageOwner(){
        PanteraRuntime r=place(2,0);click(r);
        var target=new EntityTargetLivingEntityEvent(r.base,player,EntityTargetEvent.TargetReason.CLOSEST_PLAYER);server.getPluginManager().callEvent(target);assertTrue(target.isCancelled());
        var damage=new EntityDamageByEntityEvent(r.base,player,EntityDamageEvent.DamageCause.ENTITY_ATTACK,10);server.getPluginManager().callEvent(damage);assertTrue(damage.isCancelled());
        var change=new EntityChangeBlockEvent(r.base,world.getBlockAt(3,64,0),Material.AIR.createBlockData());server.getPluginManager().callEvent(change);assertTrue(change.isCancelled());
        var item=world.dropItem(new Location(world,2,65,0),new ItemStack(Material.DIAMOND));
        var pickup=new EntityPickupItemEvent(r.base,item,0);server.getPluginManager().callEvent(pickup);assertTrue(pickup.isCancelled());
    }
    @Test void deathRemovesVisualsAndGivesNoWolfDrops(){
        PanteraRuntime r=place(2,0);var drops=new ArrayList<ItemStack>();drops.add(new ItemStack(Material.BONE));
        var e=new EntityDeathEvent(r.base,org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(),drops,4);server.getPluginManager().callEvent(e);
        assertTrue(e.getDrops().isEmpty());assertEquals(0,e.getDroppedExp());r.base.remove();ticks(2);assertTrue(plugin.states.isEmpty());assertTrue(plugin.active.isEmpty());
    }
    @Test void restartRestoresOwnerDeadlineHealthAndNoDuplicateVisuals(){
        PanteraRuntime r=place(2,0);click(r);r.base.setHealth(22);UUID id=r.state.id;long expiration=r.state.expiresAt;
        restart();assertEquals(1,plugin.active.size());PanteraRuntime restored=plugin.active.get(id);
        assertNotNull(restored);assertEquals(player.getUniqueId(),restored.state.owner);assertEquals(expiration,restored.state.expiresAt);assertEquals(22,restored.base.getHealth());
        assertEquals(9,world.getEntities().stream().filter(ItemDisplay.class::isInstance).count());assertEquals(1,world.getEntities().stream().filter(TextDisplay.class::isInstance).count());
    }
    void restart(){plugin.onDisable();HandlerList.unregisterAll((org.bukkit.plugin.Plugin)plugin);server.getScheduler().cancelTasks(plugin);plugin.onEnable();}
    @Test void chunkUnloadSuspendsAllPiecesAndRepeatedLoadDoesNotDuplicate(){
        PanteraRuntime r=place(2,0);UUID id=r.state.id;
        server.getPluginManager().callEvent(new EntitiesUnloadEvent(world.getChunkAt(0,0),List.copyOf(r.entities)));
        assertTrue(plugin.active.isEmpty());assertEquals(1,plugin.states.size());
        var load=new EntitiesLoadEvent(world.getChunkAt(0,0),List.of());server.getPluginManager().callEvent(load);server.getPluginManager().callEvent(load);ticks(4);
        assertNotNull(plugin.active.get(id));assertEquals(1,plugin.active.size());assertEquals(9,world.getEntities().stream().filter(ItemDisplay.class::isInstance).count());
    }
    @Test void wildPantherDoesNotExpireBeforeTaming(){place(2,0);now.addAndGet(3600000);ticks(40);assertEquals(1,plugin.active.size());}
    @Test void namesAreSingleAndCanBeDisabledAndReenabledWithoutDuplicates(){
        PanteraRuntime r=place(2,0);
        assertEquals("❤ Kazan Pantera ❤",net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(r.label.text()));
        plugin.getConfig().set("pantera.name-visible",false);plugin.saveConfig();command("reload");assertNull(r.label);
        plugin.getConfig().set("pantera.name-visible",true);plugin.saveConfig();command("reload");command("reload");
        assertEquals(1,world.getEntities().stream().filter(TextDisplay.class::isInstance).count());
    }
    @Test void twoPanthersHaveIndependentOwnersCooldownsAndDeadlines(){
        PanteraRuntime first=place(2,0);click(first);now.addAndGet(5000);place(2,3);
        PanteraRuntime second=plugin.active.values().stream().filter(r->r!=first).findFirst().orElseThrow();click(second);
        assertEquals(2,player.sounds);assertEquals(5000,second.state.expiresAt-first.state.expiresAt);assertEquals(2,plugin.active.size());
    }
    @Test void blockedOrHazardousSpawnPreservesEgg(){
        world.getBlockAt(2,66,0).setType(Material.STONE);assertNull(place(2,0));assertEquals(1,player.getInventory().getItemInMainHand().getAmount());
        player.getInventory().clear();world.getBlockAt(2,66,0).setType(Material.AIR);world.getBlockAt(2,64,0).setType(Material.MAGMA_BLOCK);
        assertNull(place(2,0));assertEquals(1,player.getInventory().getItemInMainHand().getAmount());
    }
    @Test void cancelledCreatureSpawnRollsBackAllPiecesAndKeepsEgg(){
        server.getPluginManager().registerEvent(CreatureSpawnEvent.class,new Listener(){},EventPriority.NORMAL,(l,e)->{if(e instanceof CreatureSpawnEvent spawn)spawn.setCancelled(true);},plugin);
        assertNull(place(2,0));assertTrue(plugin.states.isEmpty());assertEquals(1,player.getInventory().getItemInMainHand().getAmount());
        assertTrue(world.getEntities().stream().noneMatch(e->!(e instanceof Player)));
    }
    @Test void usePermissionAndAdministrativePermissionAreSeparate(){
        player.setOp(false);assertNotNull(place(2,0));PanteraRuntime r=plugin.active.values().iterator().next();
        assertTrue(server.dispatchCommand(player,"zpantera removeall"));assertEquals(1,plugin.active.size());
        player.addAttachment(plugin,"zpantera.use",false);click(r);assertNull(r.state.owner);
    }
    @Test void renamedVanillaEggDoesNotCreateCustomMob(){
        ItemStack fake=new ItemStack(Material.CAT_SPAWN_EGG);fake.editMeta(m->m.displayName(Component.text("Яйцо призыва Пантеры")));
        server.getPluginManager().callEvent(new PlayerInteractEvent(player,Action.RIGHT_CLICK_BLOCK,fake,world.getBlockAt(2,64,0),BlockFace.UP,EquipmentSlot.HAND));
        assertTrue(plugin.states.isEmpty());
    }
    @Test void spectatorAndRemoteInteractionCannotTame(){
        PanteraRuntime r=place(2,0);player.setGameMode(GameMode.SPECTATOR);click(r);assertNull(r.state.owner);
        player.setGameMode(GameMode.SURVIVAL);player.teleport(new Location(world,20,65,0));click(r);assertNull(r.state.owner);
    }
    @Test void pluginNeverSendsResourcePacksOnJoinSpawnOrReload(){place(2,0);ticks(160);command("reload");assertEquals(0,player.packs);}
    @Test void spawnRemoveCommandsAndTabCompletionWork(){
        PanteraRuntime r=spawn();assertEquals(1,plugin.active.size());
        assertTrue(plugin.onTabComplete(player,plugin.getCommand("zpantera"),"zpantera",new String[]{"re"}).containsAll(List.of("remove","removeall","reload")));
        assertEquals(List.of("PanteraTester"),plugin.onTabComplete(player,plugin.getCommand("zpantera"),"zpantera",new String[]{"give","PanteraT"}));
        assertTrue(server.dispatchCommand(player,"zpantera remove"));assertTrue(plugin.states.isEmpty());assertTrue(r.entities.isEmpty());
    }
    @Test void stoppingServerDoesNotTreatOwnerAsOrdinaryQuitter(){
        PanteraRuntime r=place(2,0);click(r);((TestServer)server).stopping=true;
        server.getPluginManager().callEvent(new PlayerQuitEvent(player,Component.empty()));assertEquals(1,plugin.states.size());restart();assertEquals(1,plugin.active.size());
    }
    @Test void cancelledDeathKeepsPetAndItsModel(){
        PanteraRuntime r=place(2,0);
        var death=new EntityDeathEvent(r.base,org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(),new ArrayList<>());
        death.setCancelled(true);server.getPluginManager().callEvent(death);ticks(4);assertEquals(1,plugin.active.size());assertTrue(r.base.isValid());
    }
    @Test void bothHandEggEventsInSameTickCannotCreateTwoPets(){
        place(2,0);player.getInventory().setItemInOffHand(plugin.egg(1));
        server.getPluginManager().callEvent(new PlayerInteractEvent(player,Action.RIGHT_CLICK_BLOCK,player.getInventory().getItemInOffHand(),world.getBlockAt(2,64,3),BlockFace.UP,EquipmentSlot.OFF_HAND));
        assertEquals(1,plugin.active.size());assertEquals(1,player.getInventory().getItemInOffHand().getAmount());
    }
    @Test void stuckNativePathEventuallyUsesSafeRecovery(){
        PanteraRuntime r=place(2,0);click(r);Location original=r.base.getLocation();player.teleport(new Location(world,9,65,0));ticks(180);
        assertTrue(nav.moves>=15);assertNotEquals(original.toVector(),r.base.getLocation().toVector());assertTrue(SafePosition.safe(r.base.getLocation()));
    }
    @Test void noDryLandMeansNoUnsafeTeleport(){
        PanteraRuntime r=place(2,0);click(r);Location original=r.base.getLocation();Ground danger=new Ground("danger");server.addWorld(danger);floor(danger);
        for(int x=-8;x<=14;x++)for(int z=-8;z<=14;z++)danger.getBlockAt(x,64,z).setType(Material.LAVA);
        player.teleport(new Location(danger,0,65,0));ticks(100);assertEquals(original,r.base.getLocation());
    }
    @Test void modelKeepsDetailsInDarknessAndRetainsBrighterEnvironmentLight(){
        world.getBlockAt(2,65,0).setLightFromSky((byte)0);
        PanteraRuntime r=place(2,0);
        for(ItemDisplay part:r.parts.values()){assertEquals(8,part.getBrightness().getBlockLight());assertEquals(0,part.getBrightness().getSkyLight());}
        var block=world.getBlockAt(r.base.getLocation());block.setLightFromBlocks((byte)13);block.setLightFromSky((byte)15);ticks(10);
        for(ItemDisplay part:r.parts.values()){assertEquals(13,part.getBrightness().getBlockLight());assertEquals(15,part.getBrightness().getSkyLight());}
    }
    @Test void lightingReloadCanRestoreNaturalLightWithoutRespawningPet(){
        PanteraRuntime r=place(2,0);UUID id=r.state.id;
        plugin.getConfig().set("model.minimum-block-light",0);plugin.saveConfig();command("reload");
        for(ItemDisplay part:r.parts.values())assertNull(part.getBrightness());
        plugin.getConfig().set("model.minimum-block-light",12);plugin.saveConfig();command("reload");
        for(ItemDisplay part:r.parts.values())assertEquals(12,part.getBrightness().getBlockLight());
        assertSame(r,plugin.active.get(id));assertEquals(9,r.parts.size());
    }
    @Test void soundCategoryCanBeChangedWithoutDeletingSettingsOrPets(){
        PanteraRuntime r=place(2,0);click(r);assertEquals(SoundCategory.MASTER,player.soundCategory);
        plugin.getConfig().set("effects.sound-category","neutral");plugin.saveConfig();command("reload");now.addAndGet(2000);click(r);
        assertEquals(SoundCategory.NEUTRAL,player.soundCategory);assertEquals(2,player.sounds);
    }
}
