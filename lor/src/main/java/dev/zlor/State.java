package dev.zlor;

import dev.zlor.rules.*;
import java.util.*;

/** Versioned data only; no Bukkit objects or client-provided authority. */
public final class State {
    public int schema = 1;
    public long revision, fuelClock;
    public Map<String, DimensionVoid> dimensions = new LinkedHashMap<>();
    public Map<String, Long> dimensionRevisions = new HashMap<>();
    public Map<UUID, Role> roles = new LinkedHashMap<>();
    public Map<UUID, String> names = new HashMap<>();
    public Set<UUID> immune = new HashSet<>();
    public Map<UUID, Audit> actions = new HashMap<>();
    public RestrictionSettings restrictions = RestrictionSettings.defaults();
    public long restrictionsRevision;
    public Map<UUID, Device> devices = new LinkedHashMap<>();
    public Map<UUID, Dragon> dragons = new LinkedHashMap<>();
    public Map<String, Arena> arenas = new LinkedHashMap<>();
    public Map<String, Structures> structures = new LinkedHashMap<>();
    public Map<UUID, Integer> beamHits = new HashMap<>();
    public Map<String, Audio> audio = new LinkedHashMap<>();

    public record Role(String lastName, long since) {}
    public record Audit(String source, String detail, String dimension, long at) {}
    public record Pos(String world, int x, int y, int z) {
        public String key() { return world + ":" + x + ":" + y + ":" + z; }
    }
    public static final class Device {
        public UUID id = UUID.randomUUID(), peer;
        public boolean linkPending;
        public String kind;
        public Pos pos;
        public float yaw;
        public int[] fuel = new int[8];
        public int bottles;
        public long fuelUntil;
        public int warning = 64, danger, mode = AlarmSettings.ANALOG, volume = 50, range = 32, sound = 6;
        public Device() {}
        public Device(String kind, Pos pos, float yaw) { this.kind=kind; this.pos=pos; this.yaw=yaw; }
        public int reserves() { return Arrays.stream(fuel).sum(); }
        public long remaining(long fuelNow) { return Math.max(0, fuelUntil-fuelNow); }
        public boolean active(long fuelNow) { return kind.equals("void_absorber") && remaining(fuelNow)>0; }
    }
    public static final class Dragon {
        public UUID id = UUID.randomUUID(), run;
        public String world, kind = "default", status = "SUMMONING";
        public double x, y, z, health = 200;
        public long summonRemaining, kingQuietTicks, kingNextRepair = 1200;
        public Set<UUID> ritualCrystals = new HashSet<>();
        public Set<UUID> towerCrystals = new HashSet<>();
        public double maxHealth() { return switch(kind) {case "enriched" -> 500; case "void_king" -> 1500; default -> 200;}; }
    }
    public static final class Arena {
        public boolean star, previousStar, waveStarApplied;
        public UUID leader, run;
        public int wave;
        public String phase = "IDLE";
        public Set<UUID> members = new LinkedHashSet<>(), dead = new HashSet<>();
        public boolean waveActive() { return phase.equals("SPAWNING") || phase.equals("ACTIVE"); }
    }
    public record BlockImage(Pos pos, String data) {}
    public static final class Structures {
        public String world;
        public int portalX, portalY = 63, portalZ, entryX = 100, entryY = 73, entryZ;
        public boolean initialized, rule, portalHidden;
        public double threshold;
        public int target = 100;
        public Move move;
        public List<BlockImage> portalCells = new ArrayList<>();
    }
    public static final class Move {
        public UUID token = UUID.randomUUID();
        public int oldPortalY, newPortalY, oldEntryY, newEntryY;
        public List<BlockImage> before = new ArrayList<>(), after = new ArrayList<>();
    }
    public record Audio(String hash, String fileName, double seconds) {}

    public void touch(String world) { dimensionRevisions.merge(world,1L,Long::sum); }
    public void validate() {
        if (schema != 1 || revision < 0 || fuelClock < 0 || dimensions == null || devices == null ||
                roles == null || immune == null || names == null || actions == null ||
                dragons == null || arenas == null || structures == null || beamHits == null ||
                restrictions == null || audio == null || dimensionRevisions == null) {
            throw new IllegalArgumentException("Неполные или неподдерживаемые данные Z_Lor");
        }
        if (devices.size()>4096 || dragons.size()>4096) throw new IllegalArgumentException("Превышен лимит записей");
        var positions = new HashSet<String>();
        devices.forEach((id,d)->{
            if (!id.equals(d.id) || d.pos==null || d.pos.world()==null || !Items.BLOCKS.contains(d.kind) ||
                    d.fuel==null || d.fuel.length!=8 || d.bottles<0 || d.bottles>64 ||
                    d.volume<0 || d.volume>100 || d.range<8 || d.range>128 || d.sound<0 || d.sound>7 ||
                    d.fuelUntil<0 || !positions.add(d.pos.key())) throw new IllegalArgumentException("Повреждена запись устройства " + id);
            for (int count:d.fuel) if(count<0 || count>16) throw new IllegalArgumentException("Повреждён запас топлива");
            new AlarmSettings(d.warning,d.danger);
        });
        dimensions.forEach((key,d)->new DimensionVoid(d.active(),d.baseY(),d.perFourHours(),d.anchor(),d.minuteMode()));
        beamHits.forEach((id,hits)->{if(hits<0 || hits>7)throw new IllegalArgumentException("Повреждён счётчик луча");});
    }
}
