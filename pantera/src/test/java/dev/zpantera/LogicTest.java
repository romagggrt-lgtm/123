package dev.zpantera;

import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import static org.junit.jupiter.api.Assertions.*;

class LogicTest {
    ServerMock server;PluginTest.Ground world;
    @BeforeEach void setup(){server=MockBukkit.mock();world=new PluginTest.Ground("logic");server.addWorld(world);PluginTest.floor(world);}
    @AfterEach void cleanup(){MockBukkit.unmock();}
    @Test void safeTeleportRejectsEveryListedDangerAndMissingFloor(){
        var p=new Location(world,2.5,65,.5);assertTrue(SafePosition.safe(p));
        for(Material material:List.of(Material.LAVA,Material.WATER,Material.FIRE,Material.SOUL_FIRE,Material.MAGMA_BLOCK,
            Material.CACTUS,Material.CAMPFIRE,Material.SOUL_CAMPFIRE,Material.POWDER_SNOW,Material.SWEET_BERRY_BUSH,Material.WITHER_ROSE,Material.AIR)){
            world.getBlockAt(2,64,0).setType(material);assertFalse(SafePosition.safe(p),material.toString());
        }
    }
    @Test void safeTeleportChecksHeadAndEdgesNotOnlyCenter(){
        var p=new Location(world,2.1,65,.5);assertTrue(SafePosition.safe(p));world.getBlockAt(1,66,0).setType(Material.STONE);assertFalse(SafePosition.safe(p));
    }
    @Test void worldBorderMustContainTheWholeBody(){
        world.getWorldBorder().setCenter(0,0);world.getWorldBorder().setSize(6);
        assertTrue(SafePosition.safe(new Location(world,2.5,65,.5)));
        assertFalse(SafePosition.safe(new Location(world,2.8,65,.5)));
    }
    @Test void unloadedChunksAreNotLoadedBySafeSearch(){
        assertFalse(world.isChunkLoaded(100,100));assertNull(SafePosition.nearby(new Location(world,1600,65,1600)));assertFalse(world.isChunkLoaded(100,100));
    }
    @Test void safeSearchIsIndependentOfPlayerYaw(){
        var p=new Location(world,0,65,0,0,0);Location a=SafePosition.nearby(p);p.setYaw(180);Location b=SafePosition.nearby(p);
        assertNotNull(a);assertNotNull(b);assertEquals(a.toVector(),b.toVector());
    }
    @Test void stateTamesOnceAndCannotExtendOrStealLifetime(){
        var p=new PanteraState(UUID.randomUUID(),new Location(world,0,65,0),30);UUID first=UUID.randomUUID();
        assertTrue(p.tame(first,10000,300000));assertFalse(p.tame(UUID.randomUUID(),20000,300000));
        assertEquals(first,p.owner);assertEquals(310000,p.expiresAt);assertFalse(p.expired(309999));assertTrue(p.expired(310000));
    }
    @Test void atomicStoreRoundTripsOwnershipLocationAndHealth() throws Exception {
        Path file=Files.createTempDirectory("pantera-store").resolve("state.yml");var store=new PanteraStore(file);
        var p=new PanteraState(UUID.randomUUID(),new Location(world,1.25,65,-2.5,178,0),23);p.tame(UUID.randomUUID(),10000,300000);
        store.save(Map.of(p.id,p));Map<UUID,PanteraState> loaded=new HashMap<>();store.load(loaded);var r=loaded.get(p.id);
        assertEquals(p.owner,r.owner);assertEquals(p.world,r.world);assertEquals(23,r.health);assertEquals(310000,r.expiresAt);assertEquals(p.x,r.x);assertEquals(p.z,r.z);assertEquals(p.yaw,r.yaw);
    }
    @Test void missingWorldIsPreservedWithoutCreatingOrLoadingIt() throws Exception {
        Path file=Files.createTempDirectory("pantera-absent-world").resolve("state.yml");var store=new PanteraStore(file);
        var p=new PanteraState(UUID.randomUUID(),new Location(world,0,65,0),30);p.world=UUID.randomUUID();store.save(Map.of(p.id,p));
        Map<UUID,PanteraState> loaded=new HashMap<>();store.load(loaded);assertEquals(p.world,loaded.get(p.id).world);assertNull(loaded.get(p.id).location());
    }
    @Test void malformedStoreNeverOverwritesOriginalOrPartiallyLoads() throws Exception {
        Path file=Files.createTempDirectory("pantera-corrupt").resolve("state.yml");var store=new PanteraStore(file);
        Files.writeString(file,"schema: 999\n");assertThrows(java.io.IOException.class,()->store.load(new HashMap<>()));assertEquals("schema: 999\n",Files.readString(file));
        var p=new PanteraState(UUID.randomUUID(),new Location(world,0,65,0),30);store.save(Map.of(p.id,p));
        var yaml=YamlConfiguration.loadConfiguration(file.toFile());yaml.set("panteras."+p.id+".health",-1);yaml.save(file.toFile());String original=Files.readString(file);
        Map<UUID,PanteraState> out=new HashMap<>();assertThrows(java.io.IOException.class,()->store.load(out));assertTrue(out.isEmpty());assertEquals(original,Files.readString(file));
    }
    @Test void settingsClampUnsafeSoundCooldownParticleCountAndPopulation(){
        var c=new YamlConfiguration();c.set("effects.sound-cooldown-seconds",.1);c.set("effects.burst-hearts",500000);c.set("pantera.max-count",99999);c.set("pantera.health",Double.NaN);
        Settings s=Settings.read(c);assertEquals(2000,s.cooldownMillis());assertEquals(64,s.burstHearts());assertEquals(128,s.maxCount());assertEquals(30,s.health());
    }
    @Test void oldConfigurationGetsAudibleCategoryAndMinimumLightingWithoutReset(){
        var old=new YamlConfiguration();old.set("effects.sound-volume",.8);old.set("pantera.health",37);
        var s=Settings.read(old);assertEquals(SoundCategory.MASTER,s.soundCategory());assertEquals(8,s.minimumBlockLight());
        assertEquals(.8,s.volume(),.0001);assertEquals(37,s.health());
        old.set("effects.sound-category","unknown");old.set("model.minimum-block-light",99);s=Settings.read(old);
        assertEquals(SoundCategory.MASTER,s.soundCategory());assertEquals(15,s.minimumBlockLight());
    }
    Rig rig()throws Exception{return new Rig(getClass().getResourceAsStream("/rig.json"));}
    @Test void allAnimationTransformsRemainFiniteAndContinuous() throws Exception {
        Rig rig=rig();Map<String,Matrix4f> previous=null;
        for(int frame=0;frame<=200;frame++){
            double t=frame*.01;var pose=rig.pose(0,0,0,t*2*Math.PI/.8,t,1,0);assertEquals(9,pose.size());
            for(var e:pose.entrySet()){
                float[] matrix=e.getValue().get(new float[16]);for(float n:matrix)assertTrue(Float.isFinite(n));
                if(previous!=null){float[] before=previous.get(e.getKey()).get(new float[16]);for(int i=0;i<16;i++)assertTrue(Math.abs(matrix[i]-before[i])<.1);}
            }
            previous=pose;
        }
    }
    @Test void legsAlternateAndTailSegmentsRemainConnected() throws Exception {
        Rig rig=rig();var pose=rig.pose(180,0,0,Math.PI/2,0,1,0);
        assertNotEquals(pose.get("front_left").m12(),pose.get("front_right").m12());
        assertEquals(pose.get("front_left").m12(),pose.get("rear_right").m12(),.001);
        // Tail_2's hinge equals the first bone's transformed endpoint.
        Vector3f expected=pose.get("tail_1").transformPosition(new Vector3f(rig.pivots.get("tail_2")).sub(rig.pivots.get("tail_1")));
        Vector3f actual=pose.get("tail_2").getTranslation(new Vector3f());assertTrue(expected.distance(actual)<.0001);
    }
    @Test void modelFrontMatchesMinecraftYawAndShortTurnDoesNotSpin()throws Exception{
        Rig rig=rig();Vector3f front=rig.pose(0,0,0,0,0,0,0).get("body").transformDirection(new Vector3f(0,0,-1));
        assertEquals(1,front.z,.0001);assertEquals(181,Rig.approachAngle(179,-179,5),.0001);
    }
}
