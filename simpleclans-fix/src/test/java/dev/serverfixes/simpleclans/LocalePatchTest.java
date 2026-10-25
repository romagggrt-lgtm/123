package dev.serverfixes.simpleclans;

import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.*;
import java.util.zip.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.*;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.objectweb.asm.*;
import static org.junit.jupiter.api.Assertions.*;

class LocalePatchTest {
    static final String PREFIX = "net.sacredlabyrinth.phaed.simpleclans.acf.";
    static Path original, patched;
    ServerMock server;
    Plugin plugin;
    CraftEntity player;
    IsolatedLoader loader;
    Object manager;
    Class<?> managerClass;
    Method read;

    @BeforeAll static void buildPatch() throws Exception {
        original = Path.of(System.getProperty("simpleclans.original"));
        patched = Path.of("target/SimpleClans_" + PatchSimpleClans.VERSION + ".jar");
        PatchSimpleClans.patch(original, patched);
    }
    @BeforeEach void setup() throws Exception {
        server = MockBukkit.mock();
        server.addSimpleWorld("locale_test");
        plugin = MockBukkit.createMockPlugin("LocaleTest");
        player = new CraftEntity(server); server.addPlayer(player);
        loader = new IsolatedLoader(patched);
        managerClass = loader.loadClass(PREFIX + "BukkitCommandManager");
        manager = managerClass.getConstructor(Plugin.class).newInstance(plugin);
        read = managerClass.getDeclaredMethod("readPlayerLocale", Player.class); read.setAccessible(true);
    }
    @AfterEach void cleanup() throws Exception { if (loader != null) loader.close(); MockBukkit.unmock(); }

    @Test void originalJarReproducesExactLocaleFieldFailure() throws Exception {
        try (var oldLoader = new IsolatedLoader(original)) {
            var type = oldLoader.loadClass(PREFIX + "BukkitCommandManager");
            var oldManager = type.getConstructor(Plugin.class).newInstance(plugin);
            // MockBukkit does not implement cancellation of Paper async tasks.
            // Use ACF's existing Bukkit scheduler only for this failure-path test.
            var scheduler = type.getDeclaredField("scheduler"); scheduler.setAccessible(true);
            scheduler.set(oldManager, oldLoader.loadClass(PREFIX + "ACFBukkitScheduler").getConstructor().newInstance());
            var oldRead = type.getDeclaredMethod("readPlayerLocale", Player.class); oldRead.setAccessible(true);
            List<LogRecord> records = new ArrayList<>();
            var log = Logger.getLogger(plugin.getName());
            Handler handler = new Handler() {
                public void publish(LogRecord record) { records.add(record); }
                public void flush() {} public void close() {}
            };
            log.addHandler(handler);
            try { oldRead.invoke(oldManager, player); } finally { log.removeHandler(handler); }
            assertTrue((boolean) field(oldManager, "cantReadLocale"));
            assertTrue(records.stream().anyMatch(r -> r.getMessage().contains("NoSuchFieldException: locale")), records.toString());
        }
    }
    @Test void patchedActualManagerDetectsRussianThroughPublicApi() throws Exception {
        player.clientLocale = "ru_RU";
        read.invoke(manager, player);
        assertEquals(Locale.forLanguageTag("ru-RU"), locales().get(player.getUniqueId()));
        assertEquals("ru_RU", strings().get(player.getUniqueId()));
        assertFalse((boolean) field(manager, "cantReadLocale"));
    }
    @Test void unchangedClientLanguageDoesNotOverwriteManualLanguage() throws Exception {
        read.invoke(manager, player);
        managerClass.getMethod("setPlayerLocale", Player.class, Locale.class).invoke(manager, player, Locale.FRENCH);
        read.invoke(manager, player); read.invoke(manager, player);
        assertEquals(Locale.FRENCH, locales().get(player.getUniqueId()));
    }
    @Test void changesNotifyOnceAndRepeatedReadsDoNotNotify() throws Exception {
        List<List<Object>> events = new ArrayList<>();
        var callback = loader.loadClass(PREFIX + "IssuerLocaleChangedCallback");
        var listener = java.lang.reflect.Proxy.newProxyInstance(loader, new Class<?>[]{callback}, (p, m, a) -> { events.add(Arrays.asList(a)); return null; });
        managerClass.getMethod("onLocaleChange", callback).invoke(manager, listener);
        read.invoke(manager, player); read.invoke(manager, player);
        player.clientLocale = "ru_RU"; read.invoke(manager, player); read.invoke(manager, player);
        assertEquals(2, events.size());
        assertNull(events.getFirst().get(1));
        assertEquals(Locale.US, events.get(1).get(1));
        assertEquals(Locale.forLanguageTag("ru-RU"), events.get(1).get(2));
    }
    @Test void languageTagsSupportScriptsAndRegionalVariants() throws Exception {
        player.clientLocale = "zh-Hant-TW"; read.invoke(manager, player);
        assertEquals(Locale.forLanguageTag("zh-Hant-TW"), locales().get(player.getUniqueId()));
        player.clientLocale = "es_mx"; read.invoke(manager, player);
        assertEquals(Locale.forLanguageTag("es-MX"), locales().get(player.getUniqueId()));
        player.clientLocale = "en"; read.invoke(manager, player);
        assertEquals(Locale.ENGLISH, locales().get(player.getUniqueId()));
    }
    @Test void missingOrInvalidClientLocaleKeepsLastValidLanguage() throws Exception {
        read.invoke(manager, player);
        for (String raw : new String[]{null, "", "   ", "_", "!invalid!"}) {
            player.clientLocale = raw; assertDoesNotThrow(() -> read.invoke(manager, player));
            assertEquals(Locale.US, locales().get(player.getUniqueId()));
        }
    }
    @Test void disconnectedPlayerIsIgnored() throws Exception {
        player.disconnect(); read.invoke(manager, player);
        assertTrue(locales().isEmpty()); assertTrue(strings().isEmpty());
    }
    @Test void originalJoinAndQuitListenerWorksWithPatchedMethod() throws Exception {
        var listenerType = loader.loadClass(PREFIX + "ACFBukkitListener");
        var constructor = listenerType.getConstructor(managerClass, Plugin.class); constructor.setAccessible(true);
        Object listener = constructor.newInstance(manager, plugin);
        var join = listenerType.getMethod("onPlayerJoin", PlayerJoinEvent.class); join.setAccessible(true);
        var quit = listenerType.getMethod("onPlayerQuit", PlayerQuitEvent.class); quit.setAccessible(true);
        player.clientLocale = "ru_RU";
        join.invoke(listener, new PlayerJoinEvent(player, (String)null));
        assertEquals(Locale.forLanguageTag("ru-RU"), locales().get(player.getUniqueId()));
        // ACF also checks language one second later, including changes made after login.
        player.clientLocale = "de_DE"; server.getScheduler().performTicks(20);
        assertEquals(Locale.GERMANY, locales().get(player.getUniqueId()));
        quit.invoke(listener, new PlayerQuitEvent(player, (String)null));
        assertTrue(locales().isEmpty()); assertTrue(strings().isEmpty());
    }
    @Test void allOtherExistingJarEntriesStayByteIdentical() throws Exception {
        try (var before = new ZipFile(original.toFile()); var after = new ZipFile(patched.toFile())) {
            assertNull(after.getEntry("dev/serverfixes/simpleclans/PatchSimpleClans.class"));
            assertNull(after.getEntry("org/objectweb/asm/ClassReader.class"));
            for (var it = before.entries(); it.hasMoreElements();) {
                var entry = it.nextElement(); if (entry.isDirectory()) continue;
                assertNotNull(after.getEntry(entry.getName()), entry.getName());
                if (Set.of("plugin.yml", PatchSimpleClans.MANAGER + ".class").contains(entry.getName())) continue;
                assertArrayEquals(before.getInputStream(entry).readAllBytes(), after.getInputStream(after.getEntry(entry.getName())).readAllBytes(), entry.getName());
            }
            assertTrue(new String(after.getInputStream(after.getEntry("plugin.yml")).readAllBytes()).contains(PatchSimpleClans.VERSION));
            assertNotNull(after.getEntry(PatchSimpleClans.HELPER + ".class"));
        }
    }
    @Test void patchedMethodContainsNoReflectionOrNmsCalls() throws Exception {
        List<String> calls = new ArrayList<>();
        try (var jar = new ZipFile(patched.toFile())) {
            new ClassReader(jar.getInputStream(jar.getEntry(PatchSimpleClans.MANAGER + ".class"))).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                    if (!n.equals("readPlayerLocale")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) { calls.add(owner + "." + name); }
                    };
                }
            }, 0);
        }
        assertEquals(List.of(PatchSimpleClans.HELPER + ".read"), calls);
    }
    @Test void unexpectedInputIsRejectedBeforeProducingOutput() throws Exception {
        var input = Files.createTempFile("not-simpleclans", ".jar"); Files.write(input, new byte[]{1,2,3});
        var output = input.resolveSibling(input.getFileName() + ".fixed.jar");
        assertThrows(IllegalArgumentException.class, () -> PatchSimpleClans.patch(input, output));
        assertFalse(Files.exists(output));
        assertThrows(IllegalArgumentException.class, () -> PatchSimpleClans.patch(original, original));
    }
    @SuppressWarnings("unchecked") Map<UUID, Locale> locales() throws Exception { return (Map<UUID, Locale>)field(manager, "issuersLocale"); }
    @SuppressWarnings("unchecked") Map<UUID, String> strings() throws Exception { return (Map<UUID, String>)field(manager, "issuersLocaleString"); }
    static Object field(Object object, String name) throws Exception {
        for (var type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object); } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    /** Simulates modern CraftEntity: entity exists, internal locale field does not. */
    public static final class CraftEntity extends PlayerMock {
        public final Object entity = new Object();
        String clientLocale = "en_US";
        CraftEntity(ServerMock server) { super(server, "LocaleTester"); }
        @Override public String getLocale() { return clientLocale; }
    }
    static final class IsolatedLoader extends URLClassLoader {
        IsolatedLoader(Path jar) throws Exception { super(new URL[]{jar.toUri().toURL()}, LocalePatchTest.class.getClassLoader()); }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith("net.sacredlabyrinth.phaed.simpleclans.")) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                var type = findLoadedClass(name); if (type == null) type = findClass(name);
                if (resolve) resolveClass(type); return type;
            }
        }
    }
}
