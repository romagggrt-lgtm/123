package dev.zlor;

import dev.zlor.rules.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import static org.junit.jupiter.api.Assertions.*;

class Fixes101Test {
    ServerMock server;PluginTest.TestPlugin p;World world;org.mockbukkit.mockbukkit.entity.PlayerMock admin,ordinary;
    @BeforeEach void setup()throws Exception{
        PluginTest.WALL.set(1_000_000);PluginTest.NANOS.set(1_000_000_000);server=MockBukkit.mock();world=server.addSimpleWorld("fixes_test");
        p=MockBukkit.loadWith(PluginTest.TestPlugin.class,ZLorPlugin.class.getResourceAsStream("/plugin.yml"));assertTrue(p.isEnabled());
        admin=server.addPlayer("Scenarist");admin.setOp(true);ordinary=server.addPlayer("Ordinary");admin.teleport(new Location(world,.5,65,.5));ordinary.teleport(new Location(world,1.5,65,.5));world.getChunkAt(0,0).load();settle();
    }
    @AfterEach void cleanup(){MockBukkit.unmock();}
    void settle()throws Exception{long end=System.nanoTime()+4_000_000_000L;do{server.getScheduler().performOneTick();Thread.sleep(1);}while(!p.store.idle()&&System.nanoTime()<end);assertTrue(p.store.idle());assertTrue(p.isEnabled());}
    void scen()throws Exception{server.dispatchCommand(server.getConsoleSender(),"scen add Scenarist");settle();admin.setOp(false);}
    void command(String text)throws Exception{server.dispatchCommand(admin,text);settle();}
    State.Device device(String kind,int x,int y,int z)throws Exception{world.getBlockAt(x,y,z).setType(Material.TARGET);var d=new State.Device(kind,new State.Pos(p.worldKey(world),x,y,z),0);p.store.change(s->s.devices.put(d.id,d));settle();return p.state().devices.get(d.id);}
    @Test void lowerInventoryPickupAndPlaceAreNativeNotCancelled()throws Exception{
        var d=device("void_absorber",0,64,2);open(d);
        for(var action:List.of(InventoryAction.PICKUP_ALL,InventoryAction.PICKUP_HALF,InventoryAction.PLACE_ALL)){
            var event=new InventoryClickEvent(ordinary.getOpenInventory(),InventoryType.SlotType.CONTAINER,9,ClickType.LEFT,action);server.getPluginManager().callEvent(event);assertFalse(event.isCancelled(),action.toString());
        }
        var collect=new InventoryClickEvent(ordinary.getOpenInventory(),InventoryType.SlotType.CONTAINER,9,ClickType.DOUBLE_CLICK,InventoryAction.COLLECT_TO_CURSOR);server.getPluginManager().callEvent(collect);assertTrue(collect.isCancelled());
    }
    void open(State.Device d){ordinary.getInventory().setItemInMainHand(new ItemStack(Material.AIR));server.getPluginManager().callEvent(new PlayerInteractEvent(ordinary,Action.RIGHT_CLICK_BLOCK,null,p.devices.block(d),org.bukkit.block.BlockFace.UP,EquipmentSlot.HAND));}
    @Test void topHotbarKeySafelySwapsFuelAndRejectsOtherItems()throws Exception{
        var d=device("void_absorber",0,64,2);open(d);var fuel=p.items.make("liquid_void");fuel.setAmount(5);ordinary.getInventory().setItem(2,fuel);
        var event=new InventoryClickEvent(ordinary.getOpenInventory(),InventoryType.SlotType.CONTAINER,0,ClickType.NUMBER_KEY,InventoryAction.HOTBAR_SWAP,2);server.getPluginManager().callEvent(event);assertTrue(event.isCancelled());settle();assertEquals(5,p.state().devices.get(d.id).fuel[0]);assertTrue(ordinary.getInventory().getItem(2)==null||ordinary.getInventory().getItem(2).isEmpty());
        ordinary.getInventory().setItem(2,new ItemStack(Material.DIAMOND));event=new InventoryClickEvent(ordinary.getOpenInventory(),InventoryType.SlotType.CONTAINER,1,ClickType.NUMBER_KEY,InventoryAction.HOTBAR_SWAP,2);server.getPluginManager().callEvent(event);settle();assertEquals(0,p.state().devices.get(d.id).fuel[1]);assertEquals(Material.DIAMOND,ordinary.getInventory().getItem(2).getType());
    }
    @Test void bottomOnlyDragIsNotCancelledAndFuelCanBeSpreadAcrossInputs()throws Exception{
        var d=device("void_absorber",0,64,2);open(d);var old=p.items.make("liquid_void");old.setAmount(4);ordinary.setItemOnCursor(old);
        var item=p.items.make("liquid_void");item.setAmount(2);
        var lower=new InventoryDragEvent(ordinary.getOpenInventory(),new ItemStack(Material.AIR),old,false,Map.of(9,item));server.getPluginManager().callEvent(lower);assertFalse(lower.isCancelled());
        var top=new InventoryDragEvent(ordinary.getOpenInventory(),new ItemStack(Material.AIR),old,false,Map.of(0,item,1,item));server.getPluginManager().callEvent(top);assertTrue(top.isCancelled());server.getScheduler().performOneTick();settle();assertEquals(2,p.state().devices.get(d.id).fuel[0]);assertEquals(2,p.state().devices.get(d.id).fuel[1]);assertTrue(ordinary.getItemOnCursor().isEmpty());
    }
    @Test void oldConfigGetsTenSecondBeamWithoutOverwritingCustomSettings(){
        var config=new YamlConfiguration();config.set("dragons.beam-active-ticks",60);assertTrue(ZLorPlugin.migrate(config));assertEquals(200,config.getInt("dragons.beam-active-ticks"));assertEquals(60,config.getInt("dragons.beam-fade-ticks"));assertFalse(ZLorPlugin.migrate(config));
        var custom=new YamlConfiguration();custom.set("dragons.beam-active-ticks",300);ZLorPlugin.migrate(custom);assertEquals(300,custom.getInt("dragons.beam-active-ticks"));
    }
    @Test void beamHasTenSeparateDamagePulsesAndHarmlessWarningAndFade(){
        var timeline=new BeamTimeline(30,200,60);var pulses=new ArrayList<Long>();for(long tick=0;tick<300;tick++)if(timeline.pulse(tick))pulses.add(tick);
        assertEquals(List.of(30L,50L,70L,90L,110L,130L,150L,170L,190L,210L),pulses);assertEquals(0,timeline.opacity(29));assertEquals(1,timeline.opacity(30));assertTrue(timeline.opacity(260)>0&&timeline.opacity(260)<1);assertFalse(timeline.firing(230));assertTrue(timeline.finished(290));
    }
    @Test void viewCommandsToggleTogetherAndOffClearsBothWithoutChangingAudit()throws Exception{
        scen();var audit=p.state().actions.get(admin.getUniqueId());p.visuals.command(admin,new String[]{"all"});assertTrue(p.visuals.viewingVoid(admin.getUniqueId()));assertTrue(p.visuals.viewingDragons(admin.getUniqueId()));
        p.visuals.command(admin,new String[]{"off"});assertFalse(p.visuals.viewingVoid(admin.getUniqueId()));assertFalse(p.visuals.viewingDragons(admin.getUniqueId()));assertEquals(audit,p.state().actions.get(admin.getUniqueId()));
    }
    @Test void unionHidesOnlyCoveredCellsAndNeverOtherDimensions(){
        var a=new Visuals.Dome(UUID.randomUUID(),new Location(world,0,64,0),50,Color.PURPLE,4000,10000,true);var b=new Visuals.Dome(UUID.randomUUID(),new Location(world,30,64,0),50,Color.PURPLE,4000,10000,true);
        assertTrue(DomeDisplays.mask(a,List.of(a)).stream().allMatch(Boolean::booleanValue));var mask=DomeDisplays.mask(a,List.of(a,b));assertTrue(mask.contains(false));assertTrue(mask.contains(true));assertEquals(642,mask.size());
    }
    @Test void towerRepairAddsOnlyMissingEntitiesAndPreservesOccupiedPositions(){
        var positions=new ArrayList<Location>();for(int x:List.of(0,8,16)){world.getChunkAt(x>>4,0).load();world.getBlockAt(x,80,0).setType(Material.BEDROCK);positions.add(new Location(world,x,81,0));}
        world.getBlockAt(16,81,0).setType(Material.DIAMOND_BLOCK);assertEquals(2,CrystalRestorer.restore(world,positions,Integer.MAX_VALUE));assertEquals(0,CrystalRestorer.restore(world,positions,Integer.MAX_VALUE));assertEquals(Material.DIAMOND_BLOCK,world.getBlockAt(16,81,0).getType());
    }
    @Test void ticketOwnersCannotReleaseEachOthersChunk(){
        var calls=new ArrayList<Boolean>();try(var leases=new ChunkLeases(p,(world,x,z,present)->calls.add(present))){Object a=new Object(),b=new Object();leases.acquire(a,world,0,0);leases.acquire(b,world,0,0);assertEquals(1,leases.size());leases.release(a);assertEquals(1,leases.size());assertEquals(List.of(true),calls);leases.release(b);assertEquals(0,leases.size());assertEquals(List.of(true,false),calls);}
    }
    @Test void packCellIndicesMatchTheServersOverlapMask()throws Exception{
        var data=StateStore.JSON.fromJson(java.nio.file.Files.readString(java.nio.file.Path.of("reference/dome-geometry.json")),com.google.gson.JsonObject.class).getAsJsonArray("centres");
        assertEquals(DomeDisplays.CELLS.size(),data.size());for(int i=0;i<data.size();i++)for(int axis=0;axis<3;axis++)assertEquals(data.get(i).getAsJsonArray().get(axis).getAsDouble(),DomeDisplays.CELLS.get(i)[axis],1e-12,"cell "+i+" axis "+axis);
    }
}
