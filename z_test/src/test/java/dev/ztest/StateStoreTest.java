package dev.ztest;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StateStoreTest {
    @TempDir Path dir;
    @Test void savesReloadsAndRemovesWithoutTouchingOtherEntries()throws Exception{
        var store=new StateStore(dir.toFile());var a=new Rift(UUID.randomUUID(),new Point("world",-41,65,17),new Point("z_test_purgatory",-39,65,17));
        var b=new Rift(UUID.randomUUID(),new Point("world_nether",1,70,2),new Point("z_test_purgatory",7,65,2));var detector=new Point("world",-40,65,19);
        store.addRift(a);store.addRift(b);store.addDetector(detector);
        var reloaded=new StateStore(dir.toFile());assertEquals(2,reloaded.rifts().size());assertTrue(reloaded.rifts().contains(a));assertTrue(reloaded.hasDetector(detector));
        reloaded.removeRift(a.id());reloaded.removeDetector(detector);var again=new StateStore(dir.toFile());assertEquals(java.util.List.of(b),java.util.List.copyOf(again.rifts()));assertFalse(again.hasDetector(detector));assertTrue(Files.exists(dir.resolve("state.yml.bak")));
    }
    @Test void refusesCorruptOrUnknownDataWithoutOverwritingIt()throws Exception{
        Path file=dir.resolve("state.yml");Files.writeString(file,"schema: 999\nrifts: []\n");String before=Files.readString(file);
        assertThrows(Exception.class,()->new StateStore(dir.toFile()));assertEquals(before,Files.readString(file));
        Files.writeString(file,"schema: 1\nrifts:\n  - id: not-a-uuid\n");String bad=Files.readString(file);assertThrows(Exception.class,()->new StateStore(dir.toFile()));assertEquals(bad,Files.readString(file));
    }
}
