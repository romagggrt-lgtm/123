package dev.inventorycache;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class InventoryData {
   public static final int MAX_SECTION_BYTES = 8388608;
   public static final int MAX_TOTAL_BYTES = 12582912;
   private final byte[] storage;
   private final byte[] armor;
   private final byte[] extra;
   private final int heldSlot;

   public InventoryData(byte[] var1, byte[] var2, byte[] var3, int var4) {
      this.storage = (byte[])var1.clone();
      this.armor = (byte[])var2.clone();
      this.extra = (byte[])var3.clone();
      this.heldSlot = var4;
      if (var4 >= 0 && var4 <= 8) {
         if (var1.length > 8388608 || var2.length > 8388608 || var3.length > 8388608 || (long)var1.length + var2.length + var3.length > 12582912L) {
            throw new IllegalArgumentException("Inventory data exceeds safety limit");
         }
      } else {
         throw new IllegalArgumentException("Invalid selected hotbar slot");
      }
   }

   public static InventoryData capture(PlayerInventory var0) {
      return new InventoryData(
         ItemStack.serializeItemsAsBytes(var0.getStorageContents()),
         ItemStack.serializeItemsAsBytes(var0.getArmorContents()),
         ItemStack.serializeItemsAsBytes(var0.getExtraContents()),
         var0.getHeldItemSlot()
      );
   }

   public InventoryData.Decoded decodeFor(PlayerInventory var1) throws IOException {
      ItemStack[] var2 = checkedDecode(this.storage, var1.getStorageContents().length);
      ItemStack[] var3 = checkedDecode(this.armor, var1.getArmorContents().length);
      ItemStack[] var4 = checkedDecode(this.extra, var1.getExtraContents().length);
      return new InventoryData.Decoded(var2, var3, var4, this.heldSlot);
   }

   private static ItemStack[] checkedDecode(byte[] var0, int var1) throws IOException {
      try (DataInputStream var2 = new DataInputStream(new ByteArrayInputStream(var0))) {
         if (var2.readUnsignedByte() != 1) {
            throw new IOException("Unsupported Paper inventory array format");
         }

         int var3 = var2.readInt();
         if (var3 != var1) {
            throw new IOException("Slot count mismatch: " + var3 + " vs " + var1);
         }

         for (int var4 = 0; var4 < var3; var4++) {
            int var5 = var2.readInt();
            if (var5 < 0 || var5 > var2.available()) {
               throw new IOException("Invalid item payload length at slot " + var4);
            }

            var2.skipNBytes(var5);
         }

         if (var2.available() != 0) {
            throw new IOException("Trailing bytes in inventory array");
         }
      }

      try {
         ItemStack[] var9 = ItemStack.deserializeItemsFromBytes(var0);
         if (var9.length != var1) {
            throw new IOException("Decoded item count mismatch");
         } else {
            return var9;
         }
      } catch (RuntimeException var7) {
         throw new IOException("Paper failed to decode item metadata", var7);
      }
   }

   public void apply(PlayerInventory var1) throws IOException {
      this.decodeFor(var1).apply(var1);
   }

   public boolean matches(PlayerInventory var1) {
      InventoryData var2 = capture(var1);
      return this.heldSlot == var2.heldSlot
         && Arrays.equals(this.storage, var2.storage)
         && Arrays.equals(this.armor, var2.armor)
         && Arrays.equals(this.extra, var2.extra);
   }

   public byte[] storage() {
      return (byte[])this.storage.clone();
   }

   public byte[] armor() {
      return (byte[])this.armor.clone();
   }

   public byte[] extra() {
      return (byte[])this.extra.clone();
   }

   public int heldSlot() {
      return this.heldSlot;
   }

   public String checksum() {
      try {
         MessageDigest var1 = MessageDigest.getInstance("SHA-256");
         add(var1, this.storage);
         add(var1, this.armor);
         add(var1, this.extra);
         var1.update((byte)this.heldSlot);
         return HexFormat.of().formatHex(var1.digest());
      } catch (NoSuchAlgorithmException var2) {
         throw new IllegalStateException(var2);
      }
   }

   private static void add(MessageDigest var0, byte[] var1) {
      var0.update((byte)(var1.length >>> 24));
      var0.update((byte)(var1.length >>> 16));
      var0.update((byte)(var1.length >>> 8));
      var0.update((byte)var1.length);
      var0.update(var1);
   }

   public record Decoded(ItemStack[] storage, ItemStack[] armor, ItemStack[] extra, int heldSlot) {
      public void apply(PlayerInventory var1) {
         var1.setStorageContents(this.storage);
         var1.setArmorContents(this.armor);
         var1.setExtraContents(this.extra);
         var1.setHeldItemSlot(this.heldSlot);
      }
   }
}
