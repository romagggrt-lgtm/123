package dev.inventorycache;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class InventoryStore {
   private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9_-]{1,48}");
   private static final int MAX_FILE_BYTES = 17825792;
   private final Path kitsDir;
   private final Path backupDir;

   public InventoryStore(Path var1) {
      this.kitsDir = var1.resolve("kits");
      this.backupDir = var1.resolve("backups");
   }

   public void initialize() throws IOException {
      Files.createDirectories(this.kitsDir);
      Files.createDirectories(this.backupDir);
      if (Files.isSymbolicLink(this.kitsDir) || Files.isSymbolicLink(this.backupDir)) {
         throw new IOException("Inventory storage directories must not be symlinks");
      }
   }

   public static String normalizeName(String var0) {
      String var1 = var0.toLowerCase(Locale.ROOT);
      return VALID_NAME.matcher(var1).matches() ? var1 : null;
   }

   public Set<String> existingNames() throws IOException {
      HashSet<String> var1 = new HashSet<>();

      try (Stream<Path> var2 = Files.list(this.kitsDir)) {
         var2.filter(var0 -> Files.isRegularFile(var0, LinkOption.NOFOLLOW_LINKS))
            .map(var0 -> var0.getFileName().toString())
            .filter(var0 -> var0.endsWith(".yml"))
            .map(var0 -> var0.substring(0, var0.length() - 4))
            .filter(var0 -> VALID_NAME.matcher(var0).matches())
            .forEach(var1::add);
      }

      return var1;
   }

   public void saveKit(String var1, InventoryData var2, String var3) throws IOException {
      checkName(var1);
      Path var4 = this.kitsDir.resolve(var1 + ".yml");
      safeWrite(var4, this.serialize(var2, var3));
   }

   public InventoryData loadKit(String var1) throws IOException {
      checkName(var1);
      Path var2 = this.kitsDir.resolve(var1 + ".yml");
      if (!Files.exists(var2, LinkOption.NOFOLLOW_LINKS)) {
         throw new InventoryStore.NotFoundException();
      } else if (!Files.isRegularFile(var2, LinkOption.NOFOLLOW_LINKS)) {
         throw new IOException("Kit is not a regular file: " + var1);
      } else {
         long var3 = Files.size(var2);
         if (var3 != 0L && var3 <= 17825792L) {
            String var5 = Files.readString(var2, StandardCharsets.UTF_8);
            YamlConfiguration var6 = new YamlConfiguration();

            try {
               var6.loadFromString(var5);
            } catch (InvalidConfigurationException var13) {
               throw new IOException("Damaged YAML: " + var1, var13);
            }

            if (var6.getInt("schema", -1) != 1) {
               throw new IOException("Unsupported inventory schema for " + var1);
            } else {
               try {
                  String var7 = required(var6, "storage");
                  String var8 = required(var6, "armor");
                  String var9 = required(var6, "extra");
                  String var10 = required(var6, "sha256");
                  if (!var10.matches("[0-9a-f]{64}")) {
                     throw new IOException("Invalid inventory checksum in " + var1);
                  } else if (!var6.isInt("held-slot")) {
                     throw new IOException("Missing hotbar slot in " + var1);
                  } else {
                     InventoryData var11 = new InventoryData(
                        Base64.getDecoder().decode(var7), Base64.getDecoder().decode(var8), Base64.getDecoder().decode(var9), var6.getInt("held-slot")
                     );
                     if (!var11.checksum().equals(var10)) {
                        throw new IOException("Checksum mismatch in " + var1);
                     } else {
                        return var11;
                     }
                  }
               } catch (IllegalArgumentException var12) {
                  throw new IOException("Invalid inventory record: " + var1, var12);
               }
            }
         } else {
            throw new IOException("Bad file size: " + var1);
         }
      }
   }

   public Path saveBackup(UUID var1, String var2, InventoryData var3) throws IOException {
      Path var4 = this.backupDir.resolve(var1.toString());
      Files.createDirectories(var4);
      if (Files.isSymbolicLink(var4)) {
         throw new IOException("Backup directory must not be a symlink");
      } else {
         Path var5 = var4.resolve(Instant.now().toEpochMilli() + "-" + UUID.randomUUID() + ".yml");
         safeWrite(var5, this.serialize(var3, "before load for " + var2));
         return var5;
      }
   }

   private static String required(YamlConfiguration var0, String var1) throws IOException {
      String var2 = var0.getString(var1);
      if (var2 != null && !var2.isEmpty()) {
         return var2;
      } else {
         throw new IOException("Missing inventory field: " + var1);
      }
   }

   private static void checkName(String var0) {
      if (!VALID_NAME.matcher(var0).matches()) {
         throw new IllegalArgumentException("Invalid kit name");
      }
   }

   private String serialize(InventoryData var1, String var2) throws IOException {
      YamlConfiguration var3 = new YamlConfiguration();
      var3.set("schema", 1);
      var3.set("saved-at", Instant.now().toString());
      var3.set("saved-by", var2);
      var3.set("held-slot", var1.heldSlot());
      var3.set("storage", Base64.getEncoder().encodeToString(var1.storage()));
      var3.set("armor", Base64.getEncoder().encodeToString(var1.armor()));
      var3.set("extra", Base64.getEncoder().encodeToString(var1.extra()));
      var3.set("sha256", var1.checksum());
      String var4 = var3.saveToString();
      if (var4.getBytes(StandardCharsets.UTF_8).length > 17825792) {
         throw new IOException("Serialized inventory is too large");
      } else {
         return var4;
      }
   }

   private static void safeWrite(Path var0, String var1) throws IOException {
      if (Files.isSymbolicLink(var0)) {
         throw new IOException("Refusing to write through a symlink");
      } else {
         Path var2 = var0.resolveSibling(var0.getFileName() + "." + UUID.randomUUID() + ".tmp");

         try {
            byte[] var3 = var1.getBytes(StandardCharsets.UTF_8);

            try (FileChannel var4 = FileChannel.open(var2, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
               ByteBuffer var5 = ByteBuffer.wrap(var3);

               while (var5.hasRemaining()) {
                  var4.write(var5);
               }

               var4.force(true);
            }

            try {
               Files.move(var2, var0, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException var13) {
               throw new IOException("Filesystem does not support atomic inventory saves", var13);
            }
         } finally {
            Files.deleteIfExists(var2);
         }
      }
   }

   public static final class NotFoundException extends IOException {
      public NotFoundException() {
         super("Kit not found");
      }
   }
}
