package dev.zgold;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.block.BlockMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.*;
import static org.junit.jupiter.api.Assertions.*;

class PluginTest {
    /** MockBukkit has no collision engine. Only full cubes and air are simulated here. */
    static class CollisionWorld extends WorldMock {
        private final Map<Coordinate, BlockMock> blocks = new HashMap<>();
        CollisionWorld() { super(new WorldCreator("gold_test")); }
        @Override public BlockMock getBlockAt(int x, int y, int z) {
            return blocks.computeIfAbsent(new Coordinate(x, y, z), c -> new BlockMock(Material.AIR, new Location(this, x, y, z)) {
                @Override public boolean isPassable() { return !getType().isSolid(); }
                @Override public BoundingBox getBoundingBox() { double n = getType().isAir() ? 0 : 1; return new BoundingBox(x, y, z, x + n, y + n, z + n); }
            });
        }
        @Override public BlockMock getBlockAt(Coordinate c) { return getBlockAt(c.x, c.y, c.z); }
        @Override public BlockMock getBlockAt(Location p) { return getBlockAt(p.getBlockX(), p.getBlockY(), p.getBlockZ()); }
    }
    ServerMock server;
    WorldMock world;
    ZGoldPlugin plugin;
    PlayerMock player;
    static class PackServer extends ServerMock {
        org.bukkit.packs.ResourcePack pack;
        @Override public org.bukkit.packs.ResourcePack getServerResourcePack() { return pack; }
    }
    record ServerPack(UUID id, String url) implements org.bukkit.packs.ResourcePack {
        public UUID getId() { return id; }
        public String getUrl() { return url; }
        public String getHash() { return "ddfef4b8a51436ef085704b2e45407a06f1d4b7f"; }
        public String getPrompt() { return "Server pack"; }
        public boolean isRequired() { return true; }
    }
    static class SavedPlayer extends PlayerMock {
        boolean saved;
        int packRequests;
        SavedPlayer(ServerMock server, String name) { super(server, name); }
        @Override public void saveData() { saved = true; }
        // Capture the API boundary, without pretending to download or render a pack.
        @Override public void addResourcePack(UUID id, String url, byte[] hash, String prompt, boolean required) { packRequests++; }
    }
    @BeforeEach void setup() {
        server = MockBukkit.mock(new PackServer()); world = new CollisionWorld(); server.addWorld(world);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) world.loadChunk(x, z);
        for (int x = -5; x <= 12; x++) for (int z = -5; z <= 12; z++) world.getBlockAt(x, 64, z).setType(Material.STONE);
        plugin = MockBukkit.load(ZGoldPlugin.class);
        plugin.getConfig().set("resource-pack.enabled", false); plugin.saveConfig(); command("reload");
        player = new SavedPlayer(server, "GoldTester"); server.addPlayer(player);
        player.setOp(true); player.teleport(new Location(world, 0, 65, 0));
    }
    @AfterEach void cleanup() { MockBukkit.unmock(); }
    private void settle() {
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        do {
            plugin.pollStorage();if(plugin.writer.idle())return;
            try {Thread.sleep(1);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}
        }while(System.nanoTime()<deadline);
        fail("Storage worker did not settle");
    }
    private boolean dispatch(org.bukkit.command.CommandSender sender,String command) {
        boolean handled=server.dispatchCommand(sender,command);settle();return handled;
    }
    private void command(String args) { assertTrue(dispatch(server.getConsoleSender(), "zgold " + args));settle(); }
    private ItemStack give(int count) { command("give GoldTester " + count); return player.getInventory().getItemInMainHand(); }
    private void place(int x, int z) {
        var egg = give(1);
        server.getPluginManager().callEvent(new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, egg,
            world.getBlockAt(x, 64, z), BlockFace.UP, EquipmentSlot.HAND));
        settle();
    }
    private Interaction hitbox() { return world.getEntities().stream().filter(Interaction.class::isInstance).map(Interaction.class::cast).findFirst().orElseThrow(); }
    private ItemDisplay display() { return world.getEntities().stream().filter(ItemDisplay.class::isInstance).map(ItemDisplay.class::cast).findFirst().orElseThrow(); }
    private void clickRaw(){server.getPluginManager().callEvent(new PlayerInteractEntityEvent(player,hitbox(),EquipmentSlot.HAND));}
    private void click() {clickRaw();settle();}
    private void ticks(int n) { server.getScheduler().performTicks(n);settle(); }
    private int gold() {settle();return world.getEntities().stream().filter(Item.class::isInstance).map(Item.class::cast)
        .filter(i -> i.getItemStack().getType() == Material.GOLD_INGOT).mapToInt(i -> i.getItemStack().getAmount()).sum(); }
    private String answerCommand() {
        settle();
        Component c;
        while ((c = player.nextComponentMessage()) != null) {
            String command = findCommand(c); if (command != null) return command;
        }
        throw new AssertionError("No clickable Golda answer");
    }
    private String findCommand(Component c) {
        ClickEvent event = c.clickEvent(); if (event != null && event.value().startsWith("/zgold answer")) return event.value().substring(1);
        for (Component child : c.children()) { String command = findCommand(child); if (command != null) return command; }
        return null;
    }
    private void finishRewards() { place(2, 0); ticks(52); click(); ticks(7); click(); ticks(7); click(); }

    @Test void exactlyOneModelOneHitboxAndNoVanillaMob() {
        place(2, 0);
        assertEquals(1, world.getEntities().stream().filter(ItemDisplay.class::isInstance).count());
        assertEquals(1, world.getEntities().stream().filter(Interaction.class::isInstance).count());
        assertTrue(world.getEntities().stream().noneMatch(e -> e instanceof LivingEntity && !(e instanceof Player)));
        assertEquals(display().getY(), hitbox().getY() + hitbox().getInteractionHeight() / 2, .001);
    }
    @Test void survivalConsumesAndCreativeKeepsEgg() {
        place(2, 0); assertTrue(player.getInventory().getItemInMainHand().isEmpty()); command("removeall");
        player.setGameMode(GameMode.CREATIVE); place(2, 0); assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
    }
    @Test void blockedPlaceKeepsEggAndSpawnsNothing() {
        world.getBlockAt(2, 67, 0).setType(Material.STONE); place(2, 0);
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
        assertTrue(world.getEntities().stream().noneMatch(ItemDisplay.class::isInstance));
    }
    @Test void riseReachesConfiguredHeightAndNeverFalls() {
        place(2, 0); double start = display().getY(); click(); assertEquals(0, gold()); ticks(52);
        assertTrue(display().getY() > start + 1.9); assertEquals(67.3, display().getY(), .01);
        double x = display().getX(), z = display().getZ(); ticks(180);
        assertEquals(x, display().getX()); assertEquals(z, display().getZ()); assertTrue(display().getY() >= 67.3);
    }
    @Test void duplicateAtInteractAndOffhandOnlyAwardOneBatch() {
        place(2, 0); ticks(52); Interaction hit = hitbox(); click();
        server.getPluginManager().callEvent(new PlayerInteractAtEntityEvent(player, hit, new org.bukkit.util.Vector(), EquipmentSlot.HAND));
        server.getPluginManager().callEvent(new PlayerInteractEntityEvent(player, hit, EquipmentSlot.OFF_HAND)); click();
        assertEquals(5, gold());
    }
    @Test void threeClicksDropExactlyFifteenAndThenStop() {
        finishRewards(); assertEquals(15, gold()); assertTrue(answerCommand().endsWith("yes"));
        ticks(7); click(); ticks(7); click(); assertEquals(15, gold()); assertTrue(player.getHealth() > 0);
    }
    @Test void copiedOrForgedAnswerCannotKillAnotherPlayer() {
        finishRewards(); String answer = answerCommand(); PlayerMock stranger = server.addPlayer("Stranger");
        assertTrue(dispatch(stranger, answer)); assertEquals(20, stranger.getHealth());
        assertTrue(dispatch(player, "zgold answer " + UUID.randomUUID() + " " + UUID.randomUUID() + " yes"));
        assertEquals(20, player.getHealth());
    }
    @Test void bothAnswersKillWithNoDropsAndKeepEverySlotAndXp() {
        finishRewards(); String answer = answerCommand().replace(" yes", " no");
        player.getInventory().clear(); player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 19));
        player.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        player.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
        player.setItemOnCursor(new ItemStack(Material.EMERALD, 7));
        player.setTotalExperience(1278); player.setLevel(29); player.setExp(.37f);
        List<ItemStack> drops = new ArrayList<>(); AtomicInteger deaths = new AtomicInteger();
        server.getPluginManager().registerEvent(PlayerDeathEvent.class, new Listener(){}, EventPriority.MONITOR, (listener, event) -> {
            PlayerDeathEvent death = (PlayerDeathEvent) event; drops.addAll(death.getDrops()); deaths.incrementAndGet();
            assertTrue(death.getKeepInventory()); assertTrue(death.getKeepLevel()); assertEquals(0, death.getDroppedExp()); assertNull(death.deathMessage());
        }, plugin);
        assertTrue(dispatch(player, answer)); assertEquals(0, player.getHealth()); assertEquals(1, deaths.get()); assertTrue(drops.isEmpty());
        assertTrue(dispatch(player, answer)); assertEquals(1, deaths.get());
        player.respawn(); ticks(2);
        assertEquals(19, player.getInventory().getItem(0).getAmount()); assertEquals(Material.DIAMOND_HELMET, player.getInventory().getHelmet().getType());
        assertEquals(Material.SHIELD, player.getInventory().getItemInOffHand().getType()); assertEquals(7, player.getItemOnCursor().getAmount());
        assertEquals(29, player.getLevel()); assertEquals(1278, player.getTotalExperience()); assertEquals(.37f, player.getExp(), .0001);
        ticks(20); assertTrue(world.getEntities().stream().noneMatch(e -> e instanceof ItemDisplay || e instanceof Interaction));
    }
    @Test void yesAnswerAlsoKillsAndFades() {
        finishRewards(); String answer = answerCommand(); assertTrue(dispatch(player, answer));
        assertEquals(0, player.getHealth()); ticks(20);
        assertTrue(world.getEntities().stream().noneMatch(e -> e instanceof ItemDisplay || e instanceof Interaction));
    }
    @Test void unrelatedDeathDoesNotReceiveKeepInventory() {
        place(2, 0); boolean rule = Boolean.TRUE.equals(world.getGameRuleValue(GameRule.KEEP_INVENTORY));
        AtomicInteger seen = new AtomicInteger();
        server.getPluginManager().registerEvent(PlayerDeathEvent.class, new Listener(){}, EventPriority.MONITOR, (l, e) -> {
            assertFalse(((PlayerDeathEvent)e).getKeepInventory()); seen.incrementAndGet();
        }, plugin);
        player.setHealth(0); assertEquals(1, seen.get()); assertEquals(rule, Boolean.TRUE.equals(world.getGameRuleValue(GameRule.KEEP_INVENTORY)));
    }
    @Test void cancelledDeathDoesNotConsumeDialogueOrBroadcastFalseDeath() {
        finishRewards(); String answer = answerCommand();
        server.getPluginManager().registerEvent(PlayerDeathEvent.class, new Listener(){}, EventPriority.HIGHEST,
            (l, e) -> ((PlayerDeathEvent)e).setCancelled(true), plugin);
        assertTrue(dispatch(player, answer));
        assertTrue(player.getHealth() > 0); ticks(20); assertEquals(1, world.getEntities().stream().filter(ItemDisplay.class::isInstance).count());
        click(); assertNotEquals(answer, answerCommand());
    }
    @Test void permissionsAndRenamingCannotBypassCustomEgg() {
        player.setOp(false); command("give GoldTester"); place(2, 0); // zgold.use is public by default.
        assertEquals(1, world.getEntities().stream().filter(ItemDisplay.class::isInstance).count());
        assertTrue(dispatch(player, "zgold removeall")); assertEquals(1, world.getEntities().stream().filter(ItemDisplay.class::isInstance).count());
        command("removeall"); ItemStack fake = new ItemStack(Material.EGG); fake.editMeta(m -> m.displayName(Component.text("Яйцо Golda")));
        server.getPluginManager().callEvent(new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, fake, world.getBlockAt(2, 64, 0), BlockFace.UP, EquipmentSlot.HAND));
        assertTrue(world.getEntities().stream().noneMatch(ItemDisplay.class::isInstance));
    }
    @Test void restartKeepsCounterAndCreatesNoDuplicateVisuals() throws Exception {
        place(2, 0); ticks(52); click(); assertEquals(5, gold());
        plugin.onDisable(); org.bukkit.event.HandlerList.unregisterAll((org.bukkit.plugin.Plugin) plugin); server.getScheduler().cancelTasks(plugin);
        plugin.onEnable(); assertEquals(1, world.getEntities().stream().filter(ItemDisplay.class::isInstance).count());
        click(); ticks(7); click(); assertEquals(15, gold()); ticks(7); click(); assertEquals(15, gold());
    }
    @Test void independentGoldasHaveIndependentCounters() {
        place(2, 0); ticks(52); click(); assertEquals(5, gold()); place(2, 3); ticks(52);
        Interaction second = world.getEntities().stream().filter(Interaction.class::isInstance).map(Interaction.class::cast)
            .max(Comparator.comparingDouble(Entity::getZ)).orElseThrow();
        player.teleport(new Location(world, 0, 65, 3));
        server.getPluginManager().callEvent(new PlayerInteractEntityEvent(player, second, EquipmentSlot.HAND));
        assertEquals(10, gold()); assertEquals(2, world.getEntities().stream().filter(ItemDisplay.class::isInstance).count());
    }
    @Test void removeInvalidatesOldAnswerAndDeletesBothEntities() {
        finishRewards(); String answer = answerCommand(); command("removeall"); assertTrue(dispatch(player, answer));
        assertEquals(20, player.getHealth()); assertTrue(world.getEntities().stream().noneMatch(e -> e instanceof ItemDisplay || e instanceof Interaction));
    }
    @Test void deathJournalSurvivesDisableAndRecoversAfterRespawn() throws Exception {
        finishRewards(); String answer = answerCommand(); player.getInventory().setItem(0, new ItemStack(Material.NETHERITE_SWORD));
        assertTrue(dispatch(player, answer));
        Map<UUID, Golda> goldas = new HashMap<>(); Map<UUID, InventorySnapshot> pending = new HashMap<>();
        new GoldStore(plugin.getDataFolder().toPath().resolve("golda-state.yml")).load(goldas, pending);
        assertTrue(pending.get(player.getUniqueId()).confirmed); assertTrue(goldas.isEmpty());
        player.respawn(); ticks(2); assertEquals(Material.NETHERITE_SWORD, player.getInventory().getItem(0).getType());
    }
    @Test void malformedSaveIsNotOverwritten() throws Exception {
        Path file = Files.createTempFile("bad-golda", ".yml"); Files.writeString(file, "schema: 999\n");
        GoldStore store = new GoldStore(file);
        assertThrows(java.io.IOException.class, () -> store.load(new HashMap<>(), new HashMap<>()));
        assertEquals("schema: 999\n", Files.readString(file)); Files.delete(file);
    }
    @Test void binaryJournalRoundTripsEverySlotMetadataCursorAndXp() throws Exception {
        ItemStack[] original = new ItemStack[player.getInventory().getContents().length];
        for (int i = 0; i < original.length; i++) original[i] = new ItemStack(Material.DIAMOND, i + 1);
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        sword.editMeta(m -> {
            m.displayName(Component.text("Именной меч"));
            m.getPersistentDataContainer().set(new NamespacedKey("other_plugin", "unique"), PersistentDataType.STRING, "do-not-lose");
            m.setItemModel(new NamespacedKey("other_plugin", "sword"));
        });
        original[0] = sword; player.getInventory().setContents(original);
        player.setItemOnCursor(new ItemStack(Material.EMERALD, 13));
        player.setTotalExperience(5000); player.setLevel(45); player.setExp(.91f);
        InventorySnapshot snapshot = new InventorySnapshot(player);
        Path file = Files.createTempFile("gold-journal", ".yml"); GoldStore store = new GoldStore(file);
        store.save(Map.of(), Map.of(player.getUniqueId(), snapshot));
        Map<UUID, InventorySnapshot> pending = new HashMap<>(); store.load(new HashMap<>(), pending);
        player.getInventory().clear(); player.setItemOnCursor(null); player.setLevel(0); player.setExp(0);
        pending.get(player.getUniqueId()).restore(player);
        assertArrayEquals(original, player.getInventory().getContents());
        assertEquals("do-not-lose", player.getInventory().getItem(0).getItemMeta().getPersistentDataContainer()
            .get(new NamespacedKey("other_plugin", "unique"), PersistentDataType.STRING));
        assertEquals(13, player.getItemOnCursor().getAmount()); assertEquals(45, player.getLevel());
        assertEquals(5000, player.getTotalExperience()); assertEquals(.91f, player.getExp(), .0001);
        Files.delete(file);
    }
    @Test void unloadedChunkReturnsOnePairAndKeepsRewards() {
        place(2, 0); ticks(52); click(); assertEquals(5, gold());
        server.getPluginManager().callEvent(new ChunkUnloadEvent(world.getChunkAt(0, 0), true));
        assertTrue(world.getEntities().stream().noneMatch(e -> e instanceof ItemDisplay || e instanceof Interaction));
        server.getPluginManager().callEvent(new ChunkLoadEvent(world.getChunkAt(0, 0), false)); ticks(7);
        assertEquals(1, world.getEntities().stream().filter(ItemDisplay.class::isInstance).count());
        click(); ticks(7); click(); assertEquals(15, gold());
        command("removeall"); server.getPluginManager().callEvent(new ChunkLoadEvent(world.getChunkAt(0, 0), false)); ticks(2);
        assertTrue(world.getEntities().stream().noneMatch(ItemDisplay.class::isInstance));
    }
    @Test void pendingDialogueSurvivesRestartAndHasSamePersonalToken() {
        finishRewards(); String answer = answerCommand();
        plugin.onDisable(); HandlerList.unregisterAll((org.bukkit.plugin.Plugin)plugin); server.getScheduler().cancelTasks(plugin); plugin.onEnable();
        assertEquals(1, world.getEntities().stream().filter(ItemDisplay.class::isInstance).count());
        assertTrue(dispatch(player, answer)); assertEquals(0, player.getHealth());
        assertEquals(15, gold());
    }
    @Test void deniedUseDoesNotConsumeEggOrSpawnVisual() {
        player.setOp(false); player.addAttachment(plugin, "zgold.use", false); place(2, 0);
        assertTrue(world.getEntities().stream().noneMatch(ItemDisplay.class::isInstance));
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
    }
    @Test void twoPlayersShareFifteenTotalAndOnlyThirdClickerGetsQuestion() {
        place(2, 0); ticks(52); click(); ticks(7);
        PlayerMock other = server.addPlayer("OtherGoldUser"); other.teleport(player.getLocation());
        server.getPluginManager().callEvent(new PlayerInteractEntityEvent(other, hitbox(), EquipmentSlot.HAND)); ticks(7);
        server.getPluginManager().callEvent(new PlayerInteractEntityEvent(other, hitbox(), EquipmentSlot.HAND));
        assertEquals(15, gold());
        Component c; String answer = null;
        while ((c = other.nextComponentMessage()) != null) { String found = findCommand(c); if (found != null) { answer = found; break; } }
        assertNotNull(answer); assertTrue(dispatch(player, answer)); assertEquals(20, player.getHealth());
        assertTrue(dispatch(other, answer)); assertEquals(0, other.getHealth());
    }
    @Test void consumedAnswerIsRemovedEvenIfChunkUnloadsDuringFade() {
        finishRewards(); assertTrue(dispatch(player, answerCommand()));
        server.getPluginManager().callEvent(new ChunkUnloadEvent(world.getChunkAt(0, 0), true));
        server.getPluginManager().callEvent(new ChunkLoadEvent(world.getChunkAt(0, 0), false)); ticks(2);
        assertTrue(world.getEntities().stream().noneMatch(e -> e instanceof ItemDisplay || e instanceof Interaction));
    }
    private void enablePluginPack() {
        plugin.getConfig().set("resource-pack.enabled", true); plugin.saveConfig(); command("reload");
    }
    private void joinAgain() { plugin.join(new PlayerJoinEvent(player, Component.empty())); }
    private List<String> messages(PlayerMock p) {
        List<String> text = new ArrayList<>(); Component c;
        while ((c = p.nextComponentMessage()) != null) text.add(PlainTextComponentSerializer.plainText().serialize(c));
        return text;
    }
    @Test void rewardsAreSilentEvenWithLegacyCounterMessageConfigured() {
        plugin.getConfig().set("messages.reward", "Golda выдала <amount> слитков. Всего: <total>/<maximum>.");
        plugin.saveConfig(); command("reload"); place(2, 0); ticks(52); messages(player);
        click(); assertEquals(5, gold()); assertTrue(messages(player).isEmpty());
        ticks(7); click(); assertEquals(10, gold()); assertTrue(messages(player).isEmpty());
        ticks(7); click(); assertEquals(15, gold());
        List<String> text = messages(player); assertEquals(List.of("Golda: Про голду шутишь?", "[Да] [Нет]"), text);
    }
    @Test void serverPropertiesPackPreventsAnyAdditionalGoldRequest() {
        enablePluginPack(); ((PackServer)server).pack = new ServerPack(UUID.randomUUID(), "https://example.org/common.zip");
        joinAgain(); joinAgain(); ticks(200); assertEquals(0, ((SavedPlayer)player).packRequests);
    }
    @Test void duplicateJoinCallbacksSendOnlyOnePackWithoutServerProperties() {
        enablePluginPack(); joinAgain(); joinAgain(); ticks(200); assertEquals(1, ((SavedPlayer)player).packRequests);
        joinAgain(); ticks(200); assertEquals(1, ((SavedPlayer)player).packRequests);
    }
    @Test void packOwnerIsRecheckedBeforeDelayedRequestExecutes() {
        enablePluginPack(); joinAgain(); ticks(10);
        ((PackServer)server).pack = new ServerPack(UUID.randomUUID(), "https://example.org/common.zip");
        ticks(100); assertEquals(0, ((SavedPlayer)player).packRequests);
    }
    @Test void failedPackStatusNeverCreatesAnAutomaticReloadLoop() {
        enablePluginPack(); joinAgain(); ticks(45); assertEquals(1, ((SavedPlayer)player).packRequests);
        UUID pack = UUID.nameUUIDFromBytes("z_gold:pack:1.05_Gold".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        plugin.packStatus(new PlayerResourcePackStatusEvent(player, pack, PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD));
        joinAgain(); ticks(300); assertEquals(1, ((SavedPlayer)player).packRequests);
    }
    @Test void reconnectInvalidatesThePreviousDelayedPackTask() {
        enablePluginPack(); joinAgain(); ticks(10); plugin.quit(new PlayerQuitEvent(player, Component.empty())); joinAgain();
        ticks(30); assertEquals(0, ((SavedPlayer)player).packRequests);
        ticks(10); assertEquals(1, ((SavedPlayer)player).packRequests);
    }
    @Test void successfulServerPackIsRecognizedWithoutGoldWarning() {
        enablePluginPack(); ServerPack pack = new ServerPack(UUID.randomUUID(), "https://example.org/common.zip");
        ((PackServer)server).pack = pack;
        plugin.packStatus(new PlayerResourcePackStatusEvent(player, pack.id(), PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        messages(player); place(2, 0);
        assertTrue(messages(player).stream().noneMatch(m -> m.contains("Без Z_Server_Pack")));
        ticks(100); assertEquals(0, ((SavedPlayer)player).packRequests);
    }
    @Test void disablingPackBeforeDelayedRequestStopsIt() {
        enablePluginPack(); joinAgain(); ticks(10);
        plugin.getConfig().set("resource-pack.enabled", false); plugin.saveConfig(); command("reload");
        ticks(100); assertEquals(0, ((SavedPlayer)player).packRequests);
    }
    @Test void emptyChunksAndIdleTicksDoNotScheduleAnyDiskWrites(){
        long before=plugin.writer.submitted();
        for(int n=0;n<100;n++)server.getPluginManager().callEvent(new ChunkUnloadEvent(world.getChunkAt(10+n,10),true));
        ticks(1000);assertEquals(before,plugin.writer.submitted());
    }
    static final class DiskGate implements AutoCloseable {
        final java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
        @Override public void close(){release.countDown();}
    }
    private DiskGate blockDisk()throws Exception{
        settle();Path file=plugin.getDataFolder().toPath().resolve("golda-state.yml");
        assertTrue(plugin.writer.finish(Files.readAllBytes(file),java.time.Duration.ofSeconds(1)));
        DiskGate gate=new DiskGate();GoldStore actual=new GoldStore(file);
        plugin.writer=new GoldWriter(data->{gate.entered.countDown();try{gate.release.await();}catch(InterruptedException e){throw new java.io.IOException(e);}actual.write(data);});
        return gate;
    }
    private int goldNow(){return world.getEntities().stream().filter(Item.class::isInstance).map(Item.class::cast)
        .filter(i->i.getItemStack().getType()==Material.GOLD_INGOT).mapToInt(i->i.getItemStack().getAmount()).sum();}
    @Test void slowRewardSaveDoesNotFreezeTicksOrDuplicateGold()throws Exception{
        place(2,0);ticks(52);
        try(DiskGate gate=blockDisk()){
            clickRaw();assertTrue(gate.entered.await(1,java.util.concurrent.TimeUnit.SECONDS));assertEquals(0,goldNow());
            AtomicInteger otherTask=new AtomicInteger();server.getScheduler().runTaskTimer(plugin,otherTask::incrementAndGet,1,1);
            server.getScheduler().performTicks(100);assertEquals(100,otherTask.get());clickRaw();assertEquals(0,goldNow());
            gate.close();settle();assertEquals(5,goldNow());
        }
    }
    @Test void staleInventoryCancelsDeferredDeathWithoutOverwritingNewItems()throws Exception{
        finishRewards();String answer=answerCommand();player.getInventory().setItem(0,new ItemStack(Material.DIAMOND,4));
        try(DiskGate gate=blockDisk()){
            assertTrue(server.dispatchCommand(player,answer));assertTrue(gate.entered.await(1,java.util.concurrent.TimeUnit.SECONDS));assertEquals(20,player.getHealth());
            var drop=new PlayerDropItemEvent(player,world.dropItem(player.getLocation(),new ItemStack(Material.DIAMOND)));
            server.getPluginManager().callEvent(drop);assertTrue(drop.isCancelled());
            // External plugins may bypass inventory events. Revalidation must retain their newer data.
            player.getInventory().setItem(0,new ItemStack(Material.DIAMOND,9));
            gate.close();settle();assertEquals(20,player.getHealth());assertEquals(9,player.getInventory().getItem(0).getAmount());
            Map<UUID,InventorySnapshot> journal=new HashMap<>();new GoldStore(plugin.getDataFolder().toPath().resolve("golda-state.yml")).load(new HashMap<>(),journal);
            assertTrue(journal.isEmpty());
        }
    }
    @Test void failedRewardWriteDoesNotIssueGoldAndBlocksFurtherEconomyActions()throws Exception{
        place(2,0);ticks(52);settle();Path file=plugin.getDataFolder().toPath().resolve("golda-state.yml");
        assertTrue(plugin.writer.finish(Files.readAllBytes(file),java.time.Duration.ofSeconds(1)));byte[] previous=Files.readAllBytes(file);
        plugin.writer=new GoldWriter(data->{throw new java.io.IOException("simulated disk error");});clickRaw();settle();
        assertEquals(0,goldNow());clickRaw();settle();assertEquals(0,goldNow());assertArrayEquals(previous,Files.readAllBytes(file));
    }
    @Test void quittingDuringDeferredDeathCancelsItAndClearsJournal()throws Exception{
        finishRewards();String answer=answerCommand();
        try(DiskGate gate=blockDisk()){
            assertTrue(server.dispatchCommand(player,answer));assertTrue(gate.entered.await(1,java.util.concurrent.TimeUnit.SECONDS));
            plugin.quit(new PlayerQuitEvent(player,Component.empty()));gate.close();settle();assertEquals(20,player.getHealth());
            Map<UUID,InventorySnapshot> journal=new HashMap<>();new GoldStore(plugin.getDataFolder().toPath().resolve("golda-state.yml")).load(new HashMap<>(),journal);assertTrue(journal.isEmpty());
        }
    }
    @Test void savedRecoveryReceiptPreventsReplayingAnOlderInventory()throws Exception{
        player.getInventory().setItem(0,new ItemStack(Material.DIAMOND,4));InventorySnapshot snapshot=new InventorySnapshot(player);snapshot.confirmed=true;
        Path file=plugin.getDataFolder().toPath().resolve("golda-state.yml");
        plugin.onDisable();HandlerList.unregisterAll((org.bukkit.plugin.Plugin)plugin);server.getScheduler().cancelTasks(plugin);
        new GoldStore(file).save(Map.of(),Map.of(player.getUniqueId(),snapshot));plugin.onEnable();settle();assertTrue(((SavedPlayer)player).saved);
        player.getInventory().setItem(0,new ItemStack(Material.DIAMOND,9));
        plugin.onDisable();HandlerList.unregisterAll((org.bukkit.plugin.Plugin)plugin);server.getScheduler().cancelTasks(plugin);
        // Simulate a committed recovery whose subsequent journal deletion was lost.
        new GoldStore(file).save(Map.of(),Map.of(player.getUniqueId(),snapshot));plugin.onEnable();settle();
        assertEquals(9,player.getInventory().getItem(0).getAmount());
    }
    @Test void legacyJournalWithoutRecoveryIdRemainsReadable()throws Exception{
        player.getInventory().setItem(0,new ItemStack(Material.EMERALD,11));InventorySnapshot snapshot=new InventorySnapshot(player);
        Path file=Files.createTempFile("legacy-gold-journal",".yml");GoldStore store=new GoldStore(file);store.save(Map.of(),Map.of(player.getUniqueId(),snapshot));
        var yaml=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file.toFile());yaml.set("deaths."+player.getUniqueId()+".recovery-id",null);yaml.save(file.toFile());
        Map<UUID,InventorySnapshot> first=new HashMap<>(),second=new HashMap<>();store.load(new HashMap<>(),first);store.load(new HashMap<>(),second);
        assertEquals(first.get(player.getUniqueId()).recoveryId,second.get(player.getUniqueId()).recoveryId);
        player.getInventory().clear();first.get(player.getUniqueId()).restore(player);assertEquals(11,player.getInventory().getItem(0).getAmount());Files.delete(file);
    }
    @Test void stoppingDuringDeferredDeathCancelsEffectAndSavesRollback()throws Exception{
        finishRewards();String answer=answerCommand();
        try(DiskGate gate=blockDisk()){
            assertTrue(server.dispatchCommand(player,answer));assertTrue(gate.entered.await(1,java.util.concurrent.TimeUnit.SECONDS));
            Thread release=new Thread(()->{try{Thread.sleep(20);}catch(InterruptedException e){Thread.currentThread().interrupt();}gate.close();});release.start();
            plugin.onDisable();release.join();HandlerList.unregisterAll((org.bukkit.plugin.Plugin)plugin);server.getScheduler().cancelTasks(plugin);
            plugin.onEnable();settle();assertEquals(20,player.getHealth());
            Map<UUID,InventorySnapshot> journal=new HashMap<>();new GoldStore(plugin.getDataFolder().toPath().resolve("golda-state.yml")).load(new HashMap<>(),journal);assertTrue(journal.isEmpty());
            assertEquals(1,world.getEntities().stream().filter(ItemDisplay.class::isInstance).count());
        }
    }
}
