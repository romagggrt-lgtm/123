package dev.zlor;

import dev.zlor.rules.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import static org.junit.jupiter.api.Assertions.*;

class PluginTest {
    static final AtomicLong WALL=new AtomicLong(1_000_000),NANOS=new AtomicLong(1_000_000_000);
    public static class TestPlugin extends ZLorPlugin{
        @Override public void onEnable(){wall=WALL::get;nanos=NANOS::get;super.onEnable();}
        /** MockBukkit cannot render private displays. Command/view state stays
         * real; the native CI tests the actual render tick and packet visibility. */
        @Override protected Visuals createVisuals(){return new Visuals(this){@Override public void tick(){}};}
        @Override protected NativeBoundary createNativeBoundary(){return new NativeBoundary(this){
            @Override public void attach(World world){}
            @Override public boolean ready(World world){return true;}
            @Override public void update(World world){}
            @Override public String status(World world){return "UNIT TEST ONLY: native world tick is not simulated";}
        };}
    }
    ServerMock server;TestPlugin p;World world;PlayerMock admin,ordinary;
    @BeforeEach void setup()throws Exception{
        WALL.set(1_000_000);NANOS.set(1_000_000_000);server=MockBukkit.mock();world=server.addSimpleWorld("lor_test");
        p=MockBukkit.loadWith(TestPlugin.class,ZLorPlugin.class.getResourceAsStream("/plugin.yml"));assertTrue(p.isEnabled());
        admin=server.addPlayer("Scenarist");admin.setOp(true);ordinary=server.addPlayer("Ordinary");ordinary.setOp(false);
        admin.teleport(new Location(world,.5,65,.5));ordinary.teleport(new Location(world,1.5,65,.5));world.getChunkAt(0,0).load();settle();
    }
    @AfterEach void cleanup(){MockBukkit.unmock();}
    void settle()throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);do{server.getScheduler().performOneTick();Thread.sleep(1);}while(!p.store.idle()&&System.nanoTime()<end);assertTrue(p.store.idle());assertTrue(p.isEnabled());}
    void scen()throws Exception{server.dispatchCommand(server.getConsoleSender(),"scen add Scenarist");settle();admin.setOp(false);assertTrue(p.roles.scen(admin));}
    void command(String value)throws Exception{server.dispatchCommand(admin,value);settle();}
    State.Device device(String kind,int x,int y,int z)throws Exception{
        world.getBlockAt(x,y,z).setType(Material.TARGET);var device=new State.Device(kind,new State.Pos(p.worldKey(world),x,y,z),0);
        p.store.change(s->s.devices.put(device.id,device));settle();return p.state().devices.get(device.id);
    }
    @Test void opIsNotAnAutomaticScenaristBypassAndRoleSurvivesDeop()throws Exception{
        long revision=p.state().revision;server.dispatchCommand(admin,"lore void height set 10");settle();assertEquals(revision,p.state().revision);assertTrue(p.state().dimensions.isEmpty());
        scen();double target=p.voids.nativeY(world)+8;command("lore void height set "+target);assertEquals(target,p.voids.height(world));assertFalse(admin.isOp());assertTrue(p.state().roles.containsKey(admin.getUniqueId()));
    }
    @Test void unknownFirstRoleNeedsOnlineIdentityAndOfflineKnownRoleCanBeRemoved()throws Exception{
        server.dispatchCommand(server.getConsoleSender(),"scen add NeverSeen");settle();assertEquals(0,p.state().roles.size());
        scen();admin.disconnect();server.dispatchCommand(server.getConsoleSender(),"scen delete Scenarist");settle();assertFalse(p.state().roles.containsKey(admin.getUniqueId()));
    }
    @Test void helpStatusAndViewsDoNotReplaceLastMutationAudit()throws Exception{
        scen();command("lore void speed set minute 0,5");var action=p.state().actions.get(admin.getUniqueId());assertNotNull(action);
        command("lore help");command("lore status");command("lore void");command("lore void view");command("scen");assertEquals(action,p.state().actions.get(admin.getUniqueId()));
    }
    @Test void forgedItemNamesDoNotGainCustomIdentity(){var forged=new ItemStack(Material.PAPER);forged.editMeta(m->m.displayName(net.kyori.adventure.text.Component.text("Жидкий Войд")));assertNull(p.items.id(forged));assertTrue(p.items.is(p.items.make("liquid_void"),"liquid_void"));assertEquals(1,p.items.make("voidometer").getMaxStackSize());assertEquals(16,p.items.make("liquid_void").getMaxStackSize());}
    @Test void allFiveRecipesAreRegisteredAndNotGloballyDiscovered(){for(String id:List.of("voidometer","magnetite_core","void_absorber","void_alarm","void_siren")){var recipe=Bukkit.getRecipe(p.items.recipe(id));assertNotNull(recipe);assertEquals(1,recipe.getResult().getAmount());assertFalse(ordinary.hasDiscoveredRecipe(p.items.recipe(id)));}}
    @Test void customRecipeIngredientCannotBeReplacedByVanillaTarget(){var recipe=(ShapedRecipe)Bukkit.getRecipe(p.items.recipe("void_absorber"));assertTrue(recipe.getChoiceMap().values().stream().anyMatch(choice->choice instanceof RecipeChoice.ExactChoice));assertTrue(recipe.getChoiceMap().values().stream().filter(choice->choice instanceof RecipeChoice.ExactChoice).noneMatch(choice->choice.test(new ItemStack(Material.TARGET))));}
    @Test void administrativeGiveUsesRealCustomStacksAndSupportsNamespacedAlias()throws Exception{
        server.dispatchCommand(server.getConsoleSender(),"lore give Ordinary smplore:void_breath 1");settle();assertTrue(Arrays.stream(ordinary.getInventory().getContents()).anyMatch(i->p.items.is(i,"void_breath")));
        scen();assertTrue(server.dispatchCommand(admin,"smplore:lore help"));settle();assertTrue(p.roles.scen(admin));
    }
    @Test void ordinaryPlayerCannotUseLoreOrHiddenCommandCompletions()throws Exception{long before=p.state().revision;server.dispatchCommand(ordinary,"lore immune @s");settle();assertEquals(before,p.state().revision);assertTrue(p.state().immune.isEmpty());assertTrue(p.commands.complete(ordinary,"lore",new String[]{""}).isEmpty());}
    @Test void confirmationRejectsAnotherPlayerAndAnExpiredToken()throws Exception{
        scen();server.dispatchCommand(server.getConsoleSender(),"scen add Ordinary");settle();
        command("lore void height set 50");var pending=p.confirmations.get(admin.getUniqueId());assertNotNull(pending);assertEquals(p.voids.nativeY(world),p.voids.height(world));
        assertThrows(IllegalArgumentException.class,()->p.confirmations.finish(ordinary,pending.token().toString(),true));
        WALL.addAndGet(31_000);assertThrows(IllegalArgumentException.class,()->p.confirmations.finish(admin,pending.token().toString(),true));assertEquals(p.voids.nativeY(world),p.voids.height(world));
    }
    @Test void staleWorldRevisionCannotOverwriteAnotherScenaristsChange()throws Exception{
        scen();command("lore void height set 50");var pending=p.confirmations.get(admin.getUniqueId());
        p.store.change(s->{s.dimensions.put(p.worldKey(world),DimensionVoid.vanilla(-128,p.now()).set(-120,p.now()));s.touch(p.worldKey(world));});settle();
        assertThrows(IllegalArgumentException.class,()->p.confirmations.finish(admin,pending.token().toString(),true));assertEquals(-120,p.voids.height(world));
    }
    @Test void largeJumpOnlyAppliesAfterRealIssuedTokenAndCannotReplay()throws Exception{
        scen();command("lore void height set 19");var token=p.confirmations.get(admin.getUniqueId()).token().toString();command("lore void confirm "+token);assertEquals(19,p.voids.height(world));
        command("lore void confirm "+token);assertEquals(19,p.voids.height(world));assertNull(p.confirmations.get(admin.getUniqueId()));
    }
    @Test void revokingRoleInvalidatesPendingConfirmation()throws Exception{
        scen();command("lore void height set 50");var token=p.confirmations.get(admin.getUniqueId()).token().toString();
        server.dispatchCommand(server.getConsoleSender(),"scen delete Scenarist");settle();assertNull(p.confirmations.get(admin.getUniqueId()));assertThrows(IllegalArgumentException.class,()->p.confirmations.finish(admin,token,true));assertEquals(p.voids.nativeY(world),p.voids.height(world));
    }
    @Test void dimensionsAndSpeedRemainIndependentWithOfflineWallTime()throws Exception{
        scen();command("lore void speed set minute 0.5");World other=server.createWorld(new WorldCreator("other_dimension",new NamespacedKey("test","other_dimension")).environment(World.Environment.NETHER));p.nativeBoundary.attach(other);
        WALL.addAndGet(120_000);assertEquals(p.voids.nativeY(world)+1,p.voids.height(world),1e-9);assertEquals(other.getMinHeight()-64,p.voids.height(other),1e-9);
        assertEquals(0,p.fuelNow());
    }
    @Test void portableAbsorberRetainsPaidTimeAndCoreNeverRetainsLink(){
        var d=new State.Device("void_absorber",new State.Pos("minecraft:test",0,0,0),0);d.fuelUntil=60_000;
        ItemStack portable=p.items.portable(d,10_000);var restored=new State.Device("void_absorber",d.pos,0);p.items.restore(portable,restored,200_000);assertEquals(50_000,restored.remaining(200_000));
        var core=new State.Device("magnetite_core",d.pos,0);core.peer=UUID.randomUUID();ItemStack item=p.items.portable(core,0);assertFalse(item.getItemMeta().getPersistentDataContainer().has(p.items.portable));
    }
    @Test void fuelStartsExactlyOneFifteenMinutePortionAndConvertsOneBottle()throws Exception{
        var d=device("void_absorber",0,64,2);UUID id=d.id;p.store.change(s->{s.dimensions.put(p.worldKey(world),DimensionVoid.vanilla(-128,p.now()).set(20,p.now()));s.devices.get(id).fuel[0]=3;});settle();
        server.getScheduler().performTicks(20);settle();d=p.state().devices.get(d.id);assertEquals(2,d.reserves());assertEquals(1,d.bottles);assertEquals(900_000,d.remaining(p.fuelNow()));
        server.getScheduler().performTicks(60);settle();assertEquals(2,p.state().devices.get(d.id).reserves());
    }
    @Test void fuelClockIgnoresOfflineWallButExpiresDuringRealUptime()throws Exception{
        var d=device("void_absorber",0,64,2);UUID id=d.id;p.store.change(s->{s.dimensions.put(p.worldKey(world),DimensionVoid.vanilla(-128,p.now()).set(20,p.now()));s.devices.get(id).fuel[0]=2;});settle();server.getScheduler().performTicks(20);settle();
        WALL.addAndGet(9_000_000);assertTrue(p.state().devices.get(id).active(p.fuelNow()));NANOS.addAndGet(900_001_000_000L);server.getScheduler().performTicks(20);settle();assertEquals(0,p.state().devices.get(id).reserves());assertEquals(2,p.state().devices.get(id).bottles);
    }
    @Test void stockIsNotConsumedWhileBoundaryIsVanilla()throws Exception{var d=device("void_absorber",0,64,2);UUID id=d.id;p.store.change(s->s.devices.get(id).fuel[0]=2);settle();server.getScheduler().performTicks(60);settle();assertEquals(2,p.state().devices.get(id).reserves());assertEquals(0,p.state().devices.get(id).bottles);}
    @Test void liquidVoidCollectionConsumesOneBottleAndBreathIsNotFuel()throws Exception{
        p.store.change(s->s.dimensions.put(p.worldKey(world),DimensionVoid.vanilla(-128,p.now()).set(70,p.now())));settle();ordinary.getInventory().setItemInMainHand(new ItemStack(Material.GLASS_BOTTLE));
        assertTrue(p.voids.collectVoid(ordinary,EquipmentSlot.HAND));assertTrue(p.items.is(ordinary.getInventory().getItemInMainHand(),"liquid_void"));assertFalse(p.items.is(p.items.make("void_breath"),"liquid_void"));
    }
    @Test void collectingVoidUnderAbsorberFailsWithoutConsumingGlass()throws Exception{
        var d=device("void_absorber",0,64,2);UUID id=d.id;p.store.change(s->{s.devices.get(id).fuelUntil=900_000;s.dimensions.put(p.worldKey(world),DimensionVoid.vanilla(-128,p.now()).set(70,p.now()));});settle();
        ordinary.getInventory().setItemInMainHand(new ItemStack(Material.GLASS_BOTTLE));assertFalse(p.voids.collectVoid(ordinary,EquipmentSlot.HAND));assertEquals(Material.GLASS_BOTTLE,ordinary.getInventory().getItemInMainHand().getType());
    }
    @Test void immunityDoesNotRequireOpAndResetDoesNotDeleteItOrFuel()throws Exception{
        scen();var d=device("void_absorber",0,64,2);UUID id=d.id;p.store.change(s->{s.devices.get(id).fuelUntil=900_000;s.immune.add(admin.getUniqueId());});settle();
        p.restrictions.reset(admin,false);var token=p.confirmations.get(admin.getUniqueId()).token().toString();command("lore default confirm "+token);
        assertTrue(p.state().immune.contains(admin.getUniqueId()));assertEquals(900_000,p.state().devices.get(id).fuelUntil);assertTrue(p.roles.scen(admin));
    }
    @Test void placedDeviceConsumesOnceAfterDurableRegistration()throws Exception{
        Block target=world.getBlockAt(0,65,2);var before=target.getState();target.setType(Material.TARGET);ItemStack egg=p.items.make("void_siren");egg.setAmount(2);ordinary.getInventory().setItemInMainHand(egg);
        var event=new BlockPlaceEvent(target,before,target.getRelative(org.bukkit.block.BlockFace.DOWN),egg,ordinary,true,EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);assertTrue(event.isCancelled());target.setType(Material.AIR);settle();
        assertEquals(Material.TARGET,target.getType());assertNotNull(p.devices.at(target));assertEquals(1,ordinary.getInventory().getItemInMainHand().getAmount());
    }
    @Test void breakingCoreClearsBothEndsAndDropsUnlinkedIdentity()throws Exception{
        var a=device("magnetite_core",0,64,2);var b=device("magnetite_core",3,64,2);p.store.change(s->{s.devices.get(a.id).peer=b.id;s.devices.get(b.id).peer=a.id;});settle();
        ordinary.getInventory().setItemInMainHand(new ItemStack(Material.IRON_PICKAXE));var event=new BlockBreakEvent(p.devices.block(a),ordinary);server.getPluginManager().callEvent(event);assertTrue(event.isCancelled());settle();assertFalse(p.state().devices.containsKey(a.id));assertNull(p.state().devices.get(b.id).peer);
    }
    @Test void maxHpModifierDoesNotStackOnRepeatedLoginOrApplication()throws Exception{
        p.store.change(s->s.beamHits.put(ordinary.getUniqueId(),6));settle();p.dragons.applyMaxLoss(ordinary);assertEquals(14,ordinary.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());p.dragons.applyMaxLoss(ordinary);assertEquals(14,ordinary.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
    }
    @Test void structuresJournalMovesBothObjectsAndKeepsUnrelatedBlocks()throws Exception{
        var layout=new State.Structures();layout.world=p.worldKey(world);layout.initialized=true;layout.portalY=64;layout.entryY=48;
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)world.getBlockAt(100+x,48,z).setType(Material.OBSIDIAN);
        world.getBlockAt(0,64,0).setType(Material.BEDROCK);world.getBlockAt(1,64,0).setType(Material.END_PORTAL);world.getBlockAt(4,64,0).setType(Material.DIAMOND_BLOCK);
        var move=p.structures.plan(world,layout,20);assertEquals(20,move.newPortalY);assertEquals(30,move.newEntryY);assertEquals(48,move.oldEntryY);assertFalse(move.after.stream().anyMatch(image->image.pos().x()==4&&image.pos().y()==64));assertEquals(Material.DIAMOND_BLOCK,world.getBlockAt(4,64,0).getType());
    }
    @Test void occupiedDestinationRejectsWholeMoveBeforeChangingAnything()throws Exception{
        var layout=new State.Structures();layout.world=p.worldKey(world);layout.initialized=true;layout.portalY=64;layout.entryY=48;for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)world.getBlockAt(100+x,48,z).setType(Material.OBSIDIAN);
        world.getBlockAt(0,64,0).setType(Material.BEDROCK);world.getBlockAt(100,30,0).setType(Material.CHEST);
        assertThrows(IllegalArgumentException.class,()->p.structures.plan(world,layout,20));assertEquals(Material.OBSIDIAN,world.getBlockAt(100,48,0).getType());assertEquals(Material.CHEST,world.getBlockAt(100,30,0).getType());assertEquals(Material.BEDROCK,world.getBlockAt(0,64,0).getType());
    }
    @Test void commandListUsesCollectionAndHidesOnlyUnauthorisedOwnCommands()throws Exception{
        var list=new ArrayList<>(List.of("help","lore","smplore:lore","z_lor:lore","scen","minecraft:give","other:lore"));
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerCommandSendEvent(ordinary,list));assertEquals(List.of("help","minecraft:give","other:lore"),list);
        scen();list=new ArrayList<>(List.of("help","lore","scen"));server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerCommandSendEvent(admin,list));assertEquals(List.of("help","lore","scen"),list);
    }
    @Test void sameCoordinatesInDifferentDimensionsNeverShareFuelProtection()throws Exception{
        var a=device("void_absorber",0,64,2);World other=server.createWorld(new WorldCreator("nether_test",new NamespacedKey("test","nether_test")).environment(World.Environment.NETHER));other.getChunkAt(0,0).load();other.getBlockAt(0,64,2).setType(Material.TARGET);
        var b=new State.Device("void_absorber",new State.Pos(p.worldKey(other),0,64,2),0);
        p.store.change(s->{s.devices.get(a.id).fuelUntil=900_000;s.devices.put(b.id,b);});settle();
        assertTrue(p.devices.protectedAt(new Location(world,.5,64.5,2.5)));assertFalse(p.devices.protectedAt(new Location(other,.5,64.5,2.5)));
    }
    @Test void protectionUsesSphereRatherThanCylinderAndEndsAtExactUptime()throws Exception{
        var d=device("void_absorber",0,64,2);p.store.change(s->s.devices.get(d.id).fuelUntil=1000);settle();
        assertTrue(p.devices.protectedAt(new Location(world,49,64.5,2.5)));assertFalse(p.devices.protectedAt(new Location(world,49,110,2.5)));
        NANOS.addAndGet(1_000_000_000L);assertFalse(p.devices.protectedAt(new Location(world,.5,64.5,2.5)));
    }
    private void openFuel(State.Device d){ordinary.getInventory().setItemInMainHand(new ItemStack(Material.AIR));var event=new org.bukkit.event.player.PlayerInteractEvent(ordinary,Action.RIGHT_CLICK_BLOCK,null,p.devices.block(d),org.bukkit.block.BlockFace.UP,EquipmentSlot.HAND);server.getPluginManager().callEvent(event);assertEquals(9,ordinary.getOpenInventory().getTopInventory().getSize());}
    private InventoryClickEvent fuelClick(int slot){var event=new InventoryClickEvent(ordinary.getOpenInventory(),InventoryType.SlotType.CONTAINER,slot,ClickType.LEFT,InventoryAction.PICKUP_ALL);server.getPluginManager().callEvent(event);assertTrue(event.isCancelled());return event;}
    @Test void fuelGuiRejectsBreathButAcceptsLiquidAndKeepsOutputReadOnly()throws Exception{
        var d=device("void_absorber",0,64,2);openFuel(d);ordinary.setItemOnCursor(p.items.make("void_breath"));fuelClick(0);settle();assertEquals(0,p.state().devices.get(d.id).reserves());assertTrue(p.items.is(ordinary.getItemOnCursor(),"void_breath"));
        var fuel=p.items.make("liquid_void");fuel.setAmount(4);ordinary.setItemOnCursor(fuel);fuelClick(8);settle();assertEquals(0,p.state().devices.get(d.id).bottles);
        fuelClick(0);settle();assertEquals(4,p.state().devices.get(d.id).reserves());assertTrue(ordinary.getItemOnCursor().isEmpty());
    }
    @Test void externalInventoryChangeDuringPendingFuelTransferRollsBackInsteadOfDuplicating()throws Exception{
        var d=device("void_absorber",0,64,2);openFuel(d);var fuel=p.items.make("liquid_void");fuel.setAmount(3);ordinary.setItemOnCursor(fuel);fuelClick(0);
        ordinary.setItemOnCursor(new ItemStack(Material.DIAMOND,9));settle();assertEquals(0,p.state().devices.get(d.id).reserves());assertEquals(Material.DIAMOND,ordinary.getItemOnCursor().getType());assertEquals(9,ordinary.getItemOnCursor().getAmount());
    }
    @Test void physicalHopperOnlyTransfersLiquidVoidIntoInputSlots()throws Exception{
        var d=device("void_absorber",0,64,2);Block above=p.devices.block(d).getRelative(org.bukkit.block.BlockFace.UP);above.setType(Material.HOPPER);var hopper=(org.bukkit.block.Hopper)above.getState();
        hopper.getInventory().setItem(0,p.items.make("void_breath"));server.getScheduler().performTicks(8);settle();assertEquals(0,p.state().devices.get(d.id).reserves());
        var fuel=p.items.make("liquid_void");fuel.setAmount(2);hopper.getInventory().setItem(0,fuel);server.getScheduler().performTicks(8);settle();int transferred=p.state().devices.get(d.id).reserves();var left=hopper.getInventory().getItem(0);assertTrue(transferred>=1);assertEquals(2,transferred+(left==null?0:left.getAmount()));
    }
    @Test void physicalHopperBelowOnlyTakesTheEmptyBottleOutput()throws Exception{
        var d=device("void_absorber",0,64,2);p.store.change(s->{s.devices.get(d.id).bottles=2;s.devices.get(d.id).fuel[0]=4;});settle();
        Block below=p.devices.block(d).getRelative(org.bukkit.block.BlockFace.DOWN);below.setType(Material.HOPPER);var hopper=(org.bukkit.block.Hopper)below.getState();server.getScheduler().performTicks(8);settle();
        int extracted=Arrays.stream(hopper.getInventory().getContents()).filter(Objects::nonNull).filter(item->item.getType()==Material.GLASS_BOTTLE).mapToInt(ItemStack::getAmount).sum();
        assertEquals(4,p.state().devices.get(d.id).reserves());assertTrue(extracted>=1);assertEquals(2,p.state().devices.get(d.id).bottles+extracted);
    }
    @Test void dynamicAudioPackPreservesEveryBaseEntryAndVersionsItsDownload()throws Exception{
        var path=p.getDataFolder().toPath().resolve("base-test.zip");try(var zip=new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(path))){for(String name:List.of("pack.mcmeta","assets/shahed/keep.bin","assets/z_pantera/keep.bin")){zip.putNextEntry(new java.util.zip.ZipEntry(name));zip.write((name.equals("pack.mcmeta")?"{\"pack\":{\"pack_format\":75,\"description\":\"test\"}}":name).getBytes());zip.closeEntry();}}
        p.getConfig().set("audio.base-pack",path.toString());p.audio.buildPack(Map.of());String hash=p.audio.packHash();assertEquals(40,hash.length());
        var published=p.getDataFolder().toPath().resolve("packs").resolve(hash+".zip");assertTrue(java.nio.file.Files.exists(published));
        try(var zip=new java.util.zip.ZipFile(published.toFile())){assertEquals("assets/shahed/keep.bin",new String(zip.getInputStream(zip.getEntry("assets/shahed/keep.bin")).readAllBytes()));assertEquals("assets/z_pantera/keep.bin",new String(zip.getInputStream(zip.getEntry("assets/z_pantera/keep.bin")).readAllBytes()));}
    }
}
