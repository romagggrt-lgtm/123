package dev.zbanya;

import java.util.*;
import net.kyori.adventure.text.Component;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.potion.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import static org.junit.jupiter.api.Assertions.*;

class BanyaTest {
    /** MockBukkit lacks a few valid 1.21.11 decorative state keys. These
     * opaque states preserve strings for journal tests, not native rendering. */
    static class OpaqueState extends org.mockbukkit.mockbukkit.block.data.BlockDataMock {
        final String raw;
        OpaqueState(String raw){super(Material.matchMaterial(raw.split("\\[")[0]));this.raw=raw;}
        @Override public String getAsString(){return raw;}
        @Override public String getAsString(boolean ignored){return raw;}
        @Override public org.mockbukkit.mockbukkit.block.data.BlockDataMock clone(){return new OpaqueState(raw);}
        @Override public boolean equals(Object other){return other instanceof org.bukkit.block.data.BlockData d&&raw.equals(d.getAsString());}
        @Override public int hashCode(){return raw.hashCode();}
    }
    static class TestServer extends ServerMock {
        @Override public org.bukkit.block.data.BlockData createBlockData(String raw){try{return super.createBlockData(raw);}catch(IllegalArgumentException unsupported){if(raw.startsWith("minecraft:lantern[")||raw.startsWith("minecraft:campfire[")||raw.contains("_wall["))return new OpaqueState(raw);throw unsupported;}}
    }

    static final AtomicLong CLOCK=new AtomicLong(1000);static boolean FAIL;
    public static class TestPlugin extends ZBanyaPlugin {
        @Override public void onEnable(){nanoClock=CLOCK::get;super.onEnable();}
        /** MockBukkit has no chunk-ticket implementation. Native CI covers it. */
        @Override protected void load(Bath b,Runnable action){action.run();}
        /** Persistent hanging entities are verified separately on real Paper. */
        @Override protected void decorate(Bath b){}
        /** Mock setBlockData does not update its BlockState when the material changes. */
        @Override protected void setBlock(Block block,org.bukkit.block.data.BlockData data){if(block.getType()!=data.getMaterial())block.setType(data.getMaterial());super.setBlock(block,data);}
        @Override protected Journal openJournal(Path path)throws Exception{return new Journal(path){@Override public CompletableFuture<Void> save(Bath b){return FAIL?CompletableFuture.failedFuture(new java.io.IOException("Synthetic disk full")):super.save(b);}};}
    }
    ServerMock server;World world;TestPlugin p;PlayerMock admin,guest;
    @BeforeEach void setup(){FAIL=false;CLOCK.set(1000);server=MockBukkit.mock(new TestServer());world=server.addSimpleWorld("banya_test");
        for(int cx=-3;cx<=3;cx++)for(int cz=-3;cz<=3;cz++)world.getChunkAt(cx,cz).load();
        for(int x=-38;x<=38;x++)for(int z=-38;z<=38;z++){world.getBlockAt(x,63,z).setType(Material.DIRT);world.getBlockAt(x,64,z).setType(Material.GRASS_BLOCK);}
        admin=server.addPlayer("Builder");admin.setOp(true);admin.teleport(new Location(world,.5,65,.5));guest=server.addPlayer("Guest");guest.teleport(new Location(world,35.5,65,35.5));
        p=MockBukkit.loadWith(TestPlugin.class,ZBanyaPlugin.class.getResourceAsStream("/plugin.yml"));assertTrue(p.isEnabled());p.getConfig().set("sound.enabled",false);
    }
    @AfterEach void teardown(){MockBukkit.unmock();}
    void settle()throws Exception{long until=System.nanoTime()+6_000_000_000L;do{server.getScheduler().performOneTick();Thread.sleep(2);}while(!p.idle()&&System.nanoTime()<until);assertTrue(p.idle());assertTrue(p.isEnabled());}
    Bath build()throws Exception{p.getConfig().set("vkusnyashka.spawn-chance",1);p.create(admin);settle();assertEquals(1,p.all().size());return p.all().iterator().next();}
    @Test void permissionsAndCompletionDoNotGrantBuildingToOrdinaryPlayers(){assertTrue(server.dispatchCommand(guest,"zbanya set"));assertTrue(p.all().isEmpty());assertTrue(p.onTabComplete(guest,p.getCommand("zbanya"),"zbanya",new String[]{""}).isEmpty());assertEquals(List.of("set"),p.onTabComplete(admin,p.getCommand("zbanya"),"zbanya",new String[]{"s"}));}
    @Test void candidatesAreBoundedAndIncludeOddCoordinates(){var c=Sites.candidates(5,20);assertTrue(c.size()<1300);assertTrue(c.stream().anyMatch(q->q[0]==15&&q[1]==0));for(var q:c)assertTrue(Math.hypot(q[0],q[1])>=5&&Math.hypot(q[0],q[1])<=20);}
    @Test void doorAlwaysFacesThePlayerForAllFourRotations(){for(var pair:new double[][]{{0,-15},{15,0},{0,15},{-15,0}}){int turn=Blueprint.rotation(pair[0],pair[1]);var d=(org.bukkit.block.data.Directional)Bukkit.createBlockData(Blueprint.orient("minecraft:spruce_door[facing=north,half=lower]",turn));assertEquals(Blueprint.facing(BlockFace.NORTH,turn),d.getFacing());var v=Blueprint.rotate(0,-1,turn);assertTrue(v[0]*pair[0]+v[1]*pair[1]>0);}}
    @Test void wholeVolumeAndBuriedContainersAreRejected(){var at=admin.getLocation();int[] candidate={16,0};var b=Sites.probe(at,admin.getUniqueId(),List.of(),candidate,5,20,false);assertNotNull(b);int[] q=b.point(5,4,5);world.getBlockAt(q[0],q[1],q[2]).setType(Material.CHEST);assertNull(Sites.probe(at,admin.getUniqueId(),List.of(),candidate,5,20,false));world.getBlockAt(q[0],q[1],q[2]).setType(Material.AIR);q=b.point(4,-2,4);world.getBlockAt(q[0],q[1],q[2]).setType(Material.HOPPER);assertNull(Sites.probe(at,admin.getUniqueId(),List.of(),candidate,5,20,false));}
    @Test void underwaterAndCliffCellsRejectTheEntireFootprint(){int[] c={16,0};var b=Sites.probe(admin.getLocation(),admin.getUniqueId(),List.of(),c,5,20,false);assertNotNull(b);var at=b.point(5,0,5);world.getBlockAt(at[0],65,at[2]).setType(Material.WATER);assertNull(Sites.probe(admin.getLocation(),admin.getUniqueId(),List.of(),c,5,20,false));world.getBlockAt(at[0],65,at[2]).setType(Material.AIR);world.getBlockAt(at[0],64,at[2]).setType(Material.AIR);world.getBlockAt(at[0],63,at[2]).setType(Material.AIR);assertNull(Sites.probe(admin.getLocation(),admin.getUniqueId(),List.of(),c,5,20,false));}
    @Test void buildCreatesInteriorAndExactlyOnePersistentFoodRoll()throws Exception{var b=build();assertEquals("ACTIVE",b.phase);assertTrue(b.cells.size()>700);assertEquals(1,p.foodContainer(b).getInventory().getItem(0).getAmount());assertTrue(p.isFood(p.foodContainer(b).getInventory().getItem(0)));try(var reopened=new Journal(p.getDataFolder().toPath().resolve("baths"))){var saved=reopened.load();assertEquals(1,saved.size());assertTrue(saved.getFirst().hasFood);assertEquals("ACTIVE",saved.getFirst().phase);}p.create(admin);settle();assertEquals(1,p.all().size());}
    @Test void zeroChanceNeverCreatesFood()throws Exception{p.getConfig().set("vkusnyashka.spawn-chance",0);p.create(admin);settle();var b=p.all().iterator().next();assertFalse(b.hasFood);assertTrue(p.foodContainer(b).getInventory().isEmpty());}
    @Test void removalRestoresEveryChangedBlockAndPreservesNearbyChest()throws Exception{var b=build();var bounds=b.bounds();Block adjacent=world.getBlockAt(bounds[3]+1,b.y,bounds[5]);adjacent.setType(Material.CHEST);((Container)adjacent.getState()).getInventory().setItem(0,new ItemStack(Material.DIAMOND,3));p.remove(b,admin,false);settle();assertTrue(p.all().isEmpty());for(var c:b.cells)assertEquals(c.before(),world.getBlockAt(c.x(),c.y(),c.z()).getBlockData().getAsString());assertEquals(3,((Container)adjacent.getState()).getInventory().getItem(0).getAmount());}
    @Test void playerItemsInBarrelBlockRemovalInsteadOfBeingDeleted()throws Exception{var b=build();p.foodContainer(b).getInventory().setItem(4,new ItemStack(Material.DIAMOND,7));p.remove(b,admin,false);settle();assertEquals("ACTIVE",b.phase);assertEquals(7,p.foodContainer(b).getInventory().getItem(4).getAmount());assertEquals(1,p.all().size());}
    @Test void takenFoodReturnedToBarrelIsPreserved()throws Exception{var b=build();guest.openInventory(p.foodContainer(b).getInventory());var view=guest.getOpenInventory();var event=new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,0,ClickType.LEFT,InventoryAction.PICKUP_ALL);server.getPluginManager().callEvent(event);p.remove(b,admin,false);settle();assertEquals(1,p.all().size());assertNotNull(p.foodContainer(b).getInventory().getItem(0));}
    @Test void foreignBlockCausesSafeRefusalWithoutTerrainLoss()throws Exception{var b=build();var cell=b.cells.stream().filter(c->c.y()==b.y+2).findFirst().orElseThrow();world.getBlockAt(cell.x(),cell.y(),cell.z()).setType(Material.DIAMOND_BLOCK);p.remove(b,admin,false);settle();assertEquals(Material.DIAMOND_BLOCK,world.getBlockAt(cell.x(),cell.y(),cell.z()).getType());assertEquals("ACTIVE",b.phase);}
    @Test void journalFailureBeforeBuildLeavesWorldUnchanged()throws Exception{FAIL=true;p.create(admin);settle();assertTrue(p.all().isEmpty());for(int x=-20;x<=20;x++)for(int z=-20;z<=20;z++)assertEquals(Material.GRASS_BLOCK,world.getBlockAt(x,64,z).getType());}
    @Test void incompleteJournalRollsBackPartialBlocks()throws Exception{var b=build();p.foodContainer(b).getInventory().clear();b.phase="BUILDING";for(int i=0;i<b.cells.size()/2;i++){var c=b.cells.get(i);world.getBlockAt(c.x(),c.y(),c.z()).setBlockData(Bukkit.createBlockData(c.before()),false);}p.remove(b,admin,true);settle();assertTrue(p.all().isEmpty());for(var c:b.cells)assertEquals(c.before(),world.getBlockAt(c.x(),c.y(),c.z()).getBlockData().getAsString());}
    @Test void malformedJournalIsNeverSilentlyReset()throws Exception{Path dir=p.getDataFolder().toPath().resolve("bad");Files.createDirectories(dir);Path file=dir.resolve(UUID.randomUUID()+".json");Files.writeString(file,"{oops");try(var store=new Journal(dir)){assertThrows(Exception.class,store::load);assertEquals("{oops",Files.readString(file));}}
    @Test void consumptionHasExactlyRequestedAmplifiersAndDurations(){var item=p.food(UUID.randomUUID(),1);assertEquals("Вкусняшка Тома",net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName()));assertEquals(Material.BREAD,item.getType());/* FoodComponent cloning is incomplete in MockBukkit; native consumption at full hunger is tested on Paper. */server.getPluginManager().callEvent(new PlayerItemConsumeEvent(guest,item));assertEquals(1,guest.getPotionEffect(PotionEffectType.REGENERATION).getAmplifier());assertEquals(200,guest.getPotionEffect(PotionEffectType.REGENERATION).getDuration());assertEquals(2,guest.getPotionEffect(PotionEffectType.ABSORPTION).getAmplifier());assertEquals(2400,guest.getPotionEffect(PotionEffectType.ABSORPTION).getDuration());assertEquals(1200,guest.getPotionEffect(PotionEffectType.RESISTANCE).getDuration());assertEquals(2400,guest.getPotionEffect(PotionEffectType.FIRE_RESISTANCE).getDuration());assertEquals(160,guest.getPotionEffect(PotionEffectType.NAUSEA).getDuration());}
    @Test void renamedBreadAndCancelledEatingGiveNoEffects(){var plain=new ItemStack(Material.BREAD);plain.editMeta(m->m.displayName(Component.text("Вкусняшка Тома")));server.getPluginManager().callEvent(new PlayerItemConsumeEvent(guest,plain));assertTrue(guest.getActivePotionEffects().isEmpty());var e=new PlayerItemConsumeEvent(guest,p.food(UUID.randomUUID(),1));e.setCancelled(true);server.getPluginManager().callEvent(e);assertTrue(guest.getActivePotionEffects().isEmpty());}
    @Test void protectionDoesNotAffectAdjacentOrdinaryBlocks()throws Exception{var b=build();int[] q=b.point(0,0,0);var in=new BlockBreakEvent(world.getBlockAt(q[0],q[1],q[2]),guest);server.getPluginManager().callEvent(in);assertTrue(in.isCancelled());var out=new BlockBreakEvent(world.getBlockAt(37,64,37),guest);server.getPluginManager().callEvent(out);assertFalse(out.isCancelled());}
}
