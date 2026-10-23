package dev.zverity;

import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.mockbukkit.mockbukkit.entity.InteractionMock;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.mockbukkit.mockbukkit.world.Coordinate;
import org.mockbukkit.mockbukkit.block.BlockMock;
import org.bukkit.util.BoundingBox;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class PluginTest {
    /** MockBukkit has no collision engine. These fixtures model air/full cubes
     * only; they do not claim to simulate every Minecraft block shape. */
    static class CollisionWorld extends WorldMock {
        private final Map<Coordinate, BlockMock> blocks = new HashMap<>();
        CollisionWorld() { super(new WorldCreator("verity_test")); }
        @Override public BlockMock getBlockAt(int x, int y, int z) {
            return blocks.computeIfAbsent(new Coordinate(x, y, z), c -> new BlockMock(Material.AIR, new Location(this, x, y, z)) {
                @Override public boolean isPassable() { return !getType().isSolid(); }
                @Override public BoundingBox getBoundingBox() {
                    double size = getType().isAir() ? 0 : 1;
                    return new BoundingBox(x, y, z, x + size, y + size, z + size);
                }
            });
        }
        @Override public BlockMock getBlockAt(Coordinate c) { return getBlockAt(c.x, c.y, c.z); }
        @Override public BlockMock getBlockAt(Location p) { return getBlockAt(p.getBlockX(), p.getBlockY(), p.getBlockZ()); }
    }
    ServerMock server;
    WorldMock world;
    ZVerityPlugin plugin;
    PlayerMock player;

    @BeforeEach void setup() {
        server = MockBukkit.mock();
        world = new CollisionWorld(); server.addWorld(world);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) world.loadChunk(x, z);
        plugin = MockBukkit.load(ZVerityPlugin.class);
        plugin.getConfig().set("resource-pack.enabled", false);
        for (int x = -10; x <= 15; x++) for (int z = -10; z <= 15; z++) world.getBlockAt(x, 64, z).setType(Material.STONE);
        player = server.addPlayer("VerityTester"); player.setOp(true);
        player.teleport(new Location(world, 0, 65, 0));
    }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    private void placeBox(int x, int z) {
        assertTrue(server.dispatchCommand(player, "zverity give VerityTester"));
        var egg = player.getInventory().getItem(0);
        assertNotNull(egg);
        server.getPluginManager().callEvent(new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
            egg, world.getBlockAt(x, 64, z), BlockFace.UP, EquipmentSlot.HAND));
    }
    private Interaction hitbox() {
        return world.getEntities().stream().filter(Interaction.class::isInstance).map(Interaction.class::cast).findFirst().orElseThrow();
    }
    private ItemDisplay display() {
        return world.getEntities().stream().filter(ItemDisplay.class::isInstance).map(ItemDisplay.class::cast).findFirst().orElseThrow();
    }
    private void click() { server.getPluginManager().callEvent(new PlayerInteractEntityEvent(player, hitbox(), EquipmentSlot.HAND)); }
    private void ticks(int count) { server.getScheduler().performTicks(count); }

    @Test void spawnHasExactlyOneVisualAndOneCenteredHitbox() {
        placeBox(2, 0);
        assertEquals(1, world.getEntities().stream().filter(ItemDisplay.class::isInstance).count());
        assertEquals(1, world.getEntities().stream().filter(Interaction.class::isInstance).count());
        assertEquals(display().getY(), hitbox().getY() + hitbox().getInteractionHeight() / 2, .001);
    }
    @Test void stationaryPlayerTurningDoesNotMoveCompanionAroundThem() {
        placeBox(2, 0); click(); ticks(21); click(); ticks(100);
        Location before = display().getLocation();
        for (int yaw = 0; yaw < 360; yaw += 15) {
            Location at = player.getLocation(); at.setYaw(yaw); at.setPitch(yaw % 90); player.teleport(at); ticks(8);
        }
        assertEquals(before.getX(), display().getX(), .025);
        assertEquals(before.getZ(), display().getZ(), .025);
    }
    @Test void openingAndDuplicateClickDoNotKill() {
        placeBox(2, 0); click(); click(); ticks(21); click(); click(); ticks(1);
        assertTrue(player.getHealth() > 0);
        assertEquals(2, world.getEntities().stream().filter(e -> e instanceof ItemDisplay || e instanceof Interaction).count());
    }
    @Test void clearRemovesBothEntities() {
        placeBox(2, 0);
        assertTrue(server.dispatchCommand(player, "zverity clear"));
        assertTrue(world.getEntities().stream().noneMatch(e -> e instanceof ItemDisplay || e instanceof Interaction));
    }
    @Test void eggOnlySpawnsInFreeSpaceAndDoesNotConsumeWhenBlocked() {
        world.getBlockAt(2, 65, 0).setType(Material.STONE);
        placeBox(2, 0);
        assertNotNull(player.getInventory().getItem(0));
        assertTrue(world.getEntities().stream().noneMatch(e -> e instanceof ItemDisplay));
    }
    @Test void normalGiveIsNeverIntercepted() {
        var e = new PlayerCommandPreprocessEvent(player, "/give VerityTester diamond 1");
        plugin.playerCommand(e); assertFalse(e.isCancelled());
        e = new PlayerCommandPreprocessEvent(player, "/minecraft:give VerityTester paper 1");
        plugin.playerCommand(e); assertFalse(e.isCancelled());
    }
    private void followReady() { placeBox(2, 0); click(); ticks(21); click(); ticks(7); }
    @Test void followsActualPlayerMovementAndStops() {
        followReady(); double start = display().getX();
        player.teleport(new Location(world, -6, 65, 0)); ticks(200);
        assertTrue(display().getX() < start - 2);
        assertEquals(1.7, Math.hypot(display().getX() - player.getX(), display().getZ() - player.getZ()), .03);
        Location before = display().getLocation(); ticks(100);
        assertEquals(before.getX(), display().getX(), .001);
    }
    @Test void soundIsEmittedOnceWhenBoxOpens() {
        placeBox(2, 0); click(); ticks(21); click(); ticks(80);
        assertEquals(1, player.getHeardSounds().size());
    }
    @Test void subsequentClickKillsOnceAndRemovesBothEntities() {
        followReady(); Interaction old = hitbox();
        AtomicInteger deaths = new AtomicInteger();
        server.getPluginManager().registerEvent(PlayerDeathEvent.class, new Listener(){}, EventPriority.MONITOR,
            (listener, event) -> deaths.incrementAndGet(), plugin);
        click();
        assertEquals(0, player.getHealth());
        assertEquals(1, deaths.get());
        assertTrue(world.getEntities().stream().noneMatch(e -> e instanceof ItemDisplay || e instanceof Interaction));
        server.getPluginManager().callEvent(new PlayerInteractEntityEvent(player, old, EquipmentSlot.HAND));
        assertEquals(1, deaths.get());
    }
    @Test void fatalClickDropsStorageArmorAndOffhandExactlyOnceEvenWithKeepInventory() {
        followReady(); world.setGameRule(GameRule.KEEP_INVENTORY, true);
        player.getInventory().clear();
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 32));
        player.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        player.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
        List<ItemStack> drops = new ArrayList<>();
        server.getPluginManager().registerEvent(PlayerDeathEvent.class, new Listener(){}, EventPriority.MONITOR,
            (listener, event) -> {
                var e = (PlayerDeathEvent) event;
                assertFalse(e.getKeepInventory());
                e.getDrops().forEach(item -> drops.add(item.clone()));
            }, plugin);
        click();
        assertEquals(3, drops.size());
        assertEquals(32, drops.stream().filter(i -> i.getType() == Material.DIAMOND).mapToInt(ItemStack::getAmount).sum());
        assertEquals(1, drops.stream().filter(i -> i.getType() == Material.DIAMOND_HELMET).mapToInt(ItemStack::getAmount).sum());
        assertEquals(1, drops.stream().filter(i -> i.getType() == Material.SHIELD).mapToInt(ItemStack::getAmount).sum());
    }
    @Test void attackCanBreakBoxAndReturnOneEgg() {
        placeBox(2, 0);
        server.getPluginManager().callEvent(new EntityDamageByEntityEvent(player, hitbox(), EntityDamageEvent.DamageCause.ENTITY_ATTACK, 1));
        assertTrue(world.getEntities().stream().noneMatch(e -> e instanceof ItemDisplay || e instanceof Interaction));
        assertEquals(1, world.getEntities().stream().filter(Item.class::isInstance).count());
    }
    @Test void recordedInteractionAttackIsProcessedOnlyOnce() {
        placeBox(2, 0); click(); ticks(21);
        var hitbox = (InteractionMock) hitbox();
        hitbox.setLastAttack(new Interaction.PreviousInteraction() {
            public OfflinePlayer getPlayer() { return player; }
            public long getTimestamp() { return 42; }
        });
        ticks(25);
        assertTrue(player.getHealth() > 0);
        assertNotNull(display());
    }
    @Test void quitRemovesCompanionPair() {
        followReady(); player.disconnect();
        assertTrue(world.getEntities().stream().noneMatch(e -> e instanceof ItemDisplay || e instanceof Interaction));
    }
    @Test void worldUnloadRemovesBoxPair() {
        placeBox(2, 0);
        server.getPluginManager().callEvent(new WorldUnloadEvent(world));
        assertTrue(world.getEntities().stream().noneMatch(e -> e instanceof ItemDisplay || e instanceof Interaction));
    }
    @Test void staleDisplaysAreCleanedWithoutRemovingActivePair() {
        placeBox(2, 0);
        var orphan = world.spawn(new Location(world, 1, 65, 1), ItemDisplay.class);
        orphan.getPersistentDataContainer().set(new NamespacedKey(plugin, "thing"), PersistentDataType.STRING, "BOX");
        server.getPluginManager().callEvent(new ChunkLoadEvent(world.getChunkAt(0, 0), false));
        assertFalse(orphan.isValid());
        assertTrue(display().isValid()); assertTrue(hitbox().isValid());
    }
    @Test void activeLimitDoesNotConsumeAnotherEgg() {
        plugin.getConfig().set("verity.max-active", 1);
        placeBox(2, 0); placeBox(3, 0);
        assertEquals(1, world.getEntities().stream().filter(ItemDisplay.class::isInstance).count());
        assertNotNull(player.getInventory().getItem(0));
    }
    @Test void missingVisualAlsoRemovesHitboxOnNextTick() {
        placeBox(2, 0); Interaction hitbox = hitbox(); display().remove(); ticks(1);
        assertFalse(hitbox.isValid());
    }
    @Test void disableRemovesEveryManagedEntity() {
        placeBox(2, 0);
        server.getPluginManager().disablePlugin(plugin);
        assertTrue(world.getEntities().stream().noneMatch(e -> e instanceof ItemDisplay || e instanceof Interaction));
    }
    @Test void wallCannotBeCrossedByFollowing() {
        for (int z = -10; z <= 10; z++) for (int y = 65; y <= 68; y++) world.getBlockAt(1, y, z).setType(Material.STONE);
        followReady(); ticks(150);
        assertTrue(display().getX() >= 2.47, "sphere must stay outside the solid wall");
        assertTrue(player.getHealth() > 0);
    }
    @Test void placingInUnloadedChunkDoesNotLoadItOrConsumeEgg() {
        assertFalse(world.isChunkLoaded(2, 0));
        placeBox(32, 0);
        assertFalse(world.isChunkLoaded(2, 0));
        assertNotNull(player.getInventory().getItem(0));
        assertTrue(world.getEntities().stream().noneMatch(e -> e instanceof ItemDisplay));
    }
    @Test void offhandClickDoesNotAdvanceSphereState() {
        placeBox(2, 0); click(); ticks(21);
        server.getPluginManager().callEvent(new PlayerInteractEntityEvent(player, hitbox(), EquipmentSlot.OFF_HAND));
        ticks(7); click();
        assertTrue(player.getHealth() > 0);
    }
    @Test void playerWithoutPermissionCannotGiveEgg() {
        player.setOp(false);
        var e = new PlayerCommandPreprocessEvent(player, "/give VerityTester verity");
        plugin.playerCommand(e);
        assertTrue(e.isCancelled());
        assertTrue(player.getInventory().isEmpty());
    }
}
