package dev.inventorycache;

import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import static org.junit.jupiter.api.Assertions.*;

class InventoryCacheTest {
    ServerMock server;InventoryCachePlugin plugin;PlayerMock player;
    @BeforeEach void setup(){server=MockBukkit.mock();server.addSimpleWorld("cache_test");plugin=MockBukkit.load(InventoryCachePlugin.class);player=server.addPlayer("CacheTester");player.setOp(true);}
    @AfterEach void cleanup(){MockBukkit.unmock();}
    @Test void nonOpCommandListAcceptsArrayListAndRemovesOnlyOwnCommands(){
        player.setOp(false);var commands=new ArrayList<>(List.of("help","dk","inventorycache:dk","DK","other:dk","minecraft:give"));
        server.getPluginManager().callEvent(new PlayerCommandSendEvent(player,commands));
        assertEquals(List.of("help","other:dk","minecraft:give"),commands);
    }
    @Test void opCommandListIsPreserved(){
        var commands=new ArrayList<>(List.of("help","dk","inventorycache:dk"));server.getPluginManager().callEvent(new PlayerCommandSendEvent(player,commands));
        assertEquals(List.of("help","dk","inventorycache:dk"),commands);
    }
    @Test void publishedOneZeroZeroClassReproducesTheExactLinkageError()throws Exception{
        byte[] bytes=getClass().getResourceAsStream("/legacy/InventoryCommand.class").readAllBytes();
        ClassLoader isolated=new ClassLoader(InventoryCommand.class.getClassLoader()){
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
                if(!name.equals("dev.inventorycache.InventoryCommand"))return super.loadClass(name,resolve);
                Class<?> type=findLoadedClass(name);if(type==null)type=defineClass(name,bytes,0,bytes.length);if(resolve)resolveClass(type);return type;
            }
        };
        var old=isolated.loadClass("dev.inventorycache.InventoryCommand");var constructor=old.getDeclaredConstructors()[0];constructor.setAccessible(true);
        var store=new InventoryStore(Files.createTempDirectory("legacy-cache"));Object instance=constructor.newInstance(plugin,store,new HashSet<String>());
        player.setOp(false);var event=new PlayerCommandSendEvent(player,new ArrayList<>(List.of("dk")));
        var error=assertThrows(java.lang.reflect.InvocationTargetException.class,()->old.getMethod("onCommandList",PlayerCommandSendEvent.class).invoke(instance,event));
        assertInstanceOf(NoSuchMethodError.class,error.getCause());assertTrue(error.getCause().getMessage().contains("java.util.Set"));
        assertDoesNotThrow(()->new InventoryCommand(plugin,store,new HashSet<>()).onCommandList(event));assertTrue(event.getCommands().isEmpty());
    }
    @Test void formatRoundTripsAllInventorySectionsMetadataAndSelectedSlot()throws Exception{
        var item=new ItemStack(Material.DIAMOND_SWORD);item.editMeta(m->{m.setItemModel(new NamespacedKey("test","sword"));m.getPersistentDataContainer().set(new NamespacedKey("test","identity"),PersistentDataType.STRING,"saved");});
        player.getInventory().setItem(0,item);player.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        player.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));player.getInventory().setHeldItemSlot(4);
        var captured=InventoryData.capture(player.getInventory());var store=new InventoryStore(Files.createTempDirectory("cache-roundtrip"));store.initialize();store.saveKit("test",captured,"test");
        player.getInventory().clear();store.loadKit("test").apply(player.getInventory());
        assertEquals(item,player.getInventory().getItem(0));assertEquals(Material.DIAMOND_HELMET,player.getInventory().getHelmet().getType());
        assertEquals(Material.SHIELD,player.getInventory().getItemInOffHand().getType());assertEquals(4,player.getInventory().getHeldItemSlot());assertTrue(captured.matches(player.getInventory()));
    }
    @Test void savedSchemaOneFilesStayCompatibleAndTamperingIsRejected()throws Exception{
        Path dir=Files.createTempDirectory("cache-schema");var store=new InventoryStore(dir);store.initialize();
        store.saveKit("old",InventoryData.capture(player.getInventory()),"test");Path file=dir.resolve("kits/old.yml");
        String data=Files.readString(file);assertTrue(data.contains("schema: 1"));assertNotNull(store.loadKit("old"));
        Files.writeString(file,data.replaceFirst("sha256: [^\\n]+","sha256: "+"0".repeat(64)));
        assertThrows(java.io.IOException.class,()->store.loadKit("old"));
    }
    @Test void pathTraversalAndInvalidNamesAreRejected()throws Exception{
        var store=new InventoryStore(Files.createTempDirectory("cache-names"));store.initialize();
        assertNull(InventoryStore.normalizeName("../world"));assertNull(InventoryStore.normalizeName("a/b"));assertEquals("kit_1",InventoryStore.normalizeName("KIT_1"));
        assertThrows(IllegalArgumentException.class,()->store.loadKit("../world"));
    }
    @Test void asynchronousSaveAndLoadPreserveItemsAndCreateBackup()throws Exception{
        player.getInventory().setItem(0,new ItemStack(Material.DIAMOND,12));assertTrue(server.dispatchCommand(player,"dk save test"));
        Path root=plugin.getDataFolder().toPath();await(()->Files.exists(root.resolve("kits/test.yml")));
        player.getInventory().clear();assertTrue(server.dispatchCommand(player,"dk load test"));
        await(()->player.getInventory().getItem(0)!=null&&player.getInventory().getItem(0).getAmount()==12);
        try(var backups=Files.walk(root.resolve("backups"))){assertTrue(backups.anyMatch(p->p.toString().endsWith(".yml")));}
    }
    @Test void nonOpCannotSaveOrLoadEvenWhenCommandIsTypedDirectly(){
        player.setOp(false);server.dispatchCommand(player,"dk save denied");assertFalse(Files.exists(plugin.getDataFolder().toPath().resolve("kits/denied.yml")));
    }
    @Test void helpTopicRemainsOpOnly(){
        var topic=new OpOnlyHelpTopic("/dk");assertTrue(topic.canSee(player));player.setOp(false);assertFalse(topic.canSee(player));assertFalse(topic.canSee(server.getConsoleSender()));
    }
    @Test void sourceDataArraysAreDefensivelyCopied(){
        byte[] a={1,2,3};var data=new InventoryData(a,a,a,0);String sum=data.checksum();a[0]=9;data.storage()[0]=7;assertEquals(sum,data.checksum());
    }
    private void await(BooleanSupplier done)throws Exception{
        long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<end){server.getScheduler().performOneTick();if(done.getAsBoolean())return;Thread.sleep(1);}
        fail("Async inventory operation timed out");
    }
}
