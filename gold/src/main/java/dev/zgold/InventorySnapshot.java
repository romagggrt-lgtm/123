package dev.zgold;

import java.util.ArrayList;
import java.util.List;
import java.util.Base64;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Durable pre-death snapshot, including armor, offhand, cursor and exact XP. */
final class InventorySnapshot {
    final ItemStack[] contents;
    final ItemStack cursor;
    final int level, total;
    final float progress;
    boolean confirmed;
    final UUID recoveryId;

    InventorySnapshot(Player player) {
        recoveryId=UUID.randomUUID();
        contents = cloneItems(player.getInventory().getContents());
        cursor = copy(player.getItemOnCursor());
        level = player.getLevel(); total = player.getTotalExperience(); progress = player.getExp();
    }
    private InventorySnapshot(ItemStack[] contents, ItemStack cursor, int level, int total, float progress, boolean confirmed,UUID recoveryId) {
        if (contents.length < 41 || contents.length > 64 || level < 0 || total < 0 || !Float.isFinite(progress) || progress < 0 || progress > 1)
            throw new IllegalArgumentException("Invalid death snapshot");
        this.contents = contents; this.cursor = cursor; this.level = level; this.total = total;
        this.progress = progress; this.confirmed = confirmed;
        this.recoveryId=recoveryId;
    }
    void restore(Player player) {
        player.getInventory().setContents(cloneItems(contents));
        player.setTotalExperience(total); player.setLevel(level); player.setExp(progress);
        player.setItemOnCursor(copy(cursor));
    }
    boolean matches(Player player) {
        return java.util.Arrays.equals(contents,player.getInventory().getContents())
            &&java.util.Objects.equals(cursor,copy(player.getItemOnCursor()))
            &&level==player.getLevel()&&total==player.getTotalExperience()
            &&Float.compare(progress,player.getExp())==0;
    }
    void write(ConfigurationSection out) {
        List<String> items = new ArrayList<>();
        for (ItemStack item : contents) items.add(encode(item));
        // Paper's binary format keeps all 1.21.11 components, including custom PDC.
        out.set("items", items); out.set("cursor", encode(cursor)); out.set("level", level);
        out.set("total", total); out.set("progress", progress); out.set("confirmed", confirmed);out.set("recovery-id",recoveryId.toString());
    }
    static InventorySnapshot read(ConfigurationSection in) {
        List<?> values = in.getList("items");
        if (values == null || values.size() < 41 || values.size() > 64)
            throw new IllegalArgumentException("Invalid inventory length: " + (values == null ? "missing" : values.size()));
        ItemStack[] items = new ItemStack[values.size()];
        for (int i = 0; i < items.length; i++) {
            Object item = values.get(i);
            if (!(item instanceof String)) throw new IllegalArgumentException("Invalid item encoding");
            items[i] = decode((String) item);
        }
        String id=in.getString("recovery-id");
        UUID recovery=id==null?UUID.nameUUIDFromBytes((values.toString()+in.getString("cursor","")+in.getInt("level")+":"+in.getInt("total")+":"+in.getDouble("progress")).getBytes(java.nio.charset.StandardCharsets.UTF_8)):UUID.fromString(id);
        return new InventorySnapshot(items, decode(in.getString("cursor", "")), in.getInt("level"),
            in.getInt("total"), (float) in.getDouble("progress"), in.getBoolean("confirmed"),recovery);
    }
    static ItemStack copy(ItemStack item) { return item == null || item.isEmpty() ? null : item.clone(); }
    private static String encode(ItemStack item) {
        return item == null || item.isEmpty() ? "" : Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }
    private static ItemStack decode(String text) {
        if (text.length() > 2_000_000) throw new IllegalArgumentException("Item snapshot is too large");
        return text.isEmpty() ? null : ItemStack.deserializeBytes(Base64.getDecoder().decode(text));
    }
    private static ItemStack[] cloneItems(ItemStack[] items) {
        ItemStack[] copy = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) copy[i] = copy(items[i]);
        return copy;
    }
}
