package dev.inventorycache;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.inventory.PlayerInventory;

public final class InventoryCommand implements CommandExecutor, TabCompleter, Listener {
   private final InventoryCachePlugin plugin;
   private final InventoryStore store;
   private final Set<String> kitNames;
   private final Set<UUID> pendingReplacements = new HashSet<>();

   InventoryCommand(InventoryCachePlugin var1, InventoryStore var2, Set<String> var3) {
      this.plugin = var1;
      this.store = var2;
      this.kitNames = var3;
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      if (var1 instanceof Player var5) {
         if (!var5.isOp()) {
            var5.sendMessage("§cНедостаточно прав.");
            return true;
         } else if (var4.length >= 2 && var4.length <= 3 && (var4[0].equalsIgnoreCase("save") || var4[0].equalsIgnoreCase("load"))) {
            String var6 = InventoryStore.normalizeName(var4[1]);
            if (var6 == null) {
               var5.sendMessage("§cНазвание: 1–48 символов (a-z, 0-9, _ и -).");
               return true;
            } else {
               Player var7 = var4.length == 3 ? Bukkit.getPlayerExact(var4[2]) : var5;
               if (var7 != null && var7.isOnline()) {
                  if (var4[0].equalsIgnoreCase("save")) {
                     this.save(var5, var7, var6);
                  } else {
                     this.load(var5, var7, var6);
                  }

                  return true;
               } else {
                  var5.sendMessage("§cИгрок не найден или не в сети.");
                  return true;
               }
            }
         } else {
            var5.sendMessage("§cИспользование: /dk <save|load> <название> [ник]");
            return true;
         }
      } else {
         var1.sendMessage("§cКоманда доступна только игрокам.");
         return true;
      }
   }

   private void save(Player var1, Player var2, String var3) {
      InventoryData var4;
      try {
         var4 = InventoryData.capture(var2.getInventory());
      } catch (RuntimeException var6) {
         this.failure(var1, "capture kit", var6);
         return;
      }

      String var5 = var1.getName() + " (inventory of " + var2.getName() + ")";
      if (!this.plugin.onDisk(() -> {
         try {
            this.store.saveKit(var3, var4, var5);
            this.plugin.onMain(() -> {
               this.kitNames.add(var3);
               var1.sendMessage("§aИнвентарь сохранён: §f" + var3);
            });
         } catch (Exception var6x) {
            this.plugin.onMain(() -> this.failure(var1, "save kit '" + var3 + "'", var6x));
         }
      })) {
         var1.sendMessage("§cОчередь сохранения переполнена. Повторите позже.");
      }
   }

   private void load(Player var1, Player var2, String var3) {
      UUID var4 = var2.getUniqueId();
      if (!this.pendingReplacements.add(var4)) {
         var1.sendMessage("§cИнвентарь этого игрока уже обрабатывается.");
      } else {
         if (!this.plugin.onDisk(() -> {
            try {
               InventoryData var4x = this.store.loadKit(var3);
               this.plugin.onMain(() -> this.prepareLoad(var1, var4, var3, var4x));
            } catch (InventoryStore.NotFoundException var5) {
               this.plugin.onMain(() -> {
                  this.pendingReplacements.remove(var4);
                  var1.sendMessage("§cНабор не найден.");
               });
            } catch (Exception var6) {
               this.plugin.onMain(() -> {
                  this.pendingReplacements.remove(var4);
                  this.failure(var1, "read kit '" + var3 + "'", var6);
               });
            }
         })) {
            this.pendingReplacements.remove(var4);
            var1.sendMessage("§cОчередь сохранения переполнена. Повторите позже.");
         }
      }
   }

   private void prepareLoad(Player var1, UUID var2, String var3, InventoryData var4) {
      Player var5 = Bukkit.getPlayer(var2);
      if (var5 != null && var5.isOnline()) {
         InventoryData var6;
         InventoryData.Decoded var7;
         InventoryData.Decoded var8;
         try {
            PlayerInventory var9 = var5.getInventory();
            var7 = var4.decodeFor(var9);
            var6 = InventoryData.capture(var9);
            var8 = var6.decodeFor(var9);
         } catch (Exception var10) {
            this.pendingReplacements.remove(var2);
            this.failure(var1, "validate kit '" + var3 + "'", var10);
            return;
         }

         String var11 = var5.getName();
         if (!this.plugin.onDisk(() -> {
            try {
               this.store.saveBackup(var2, var11, var6);
               this.plugin.onMain(() -> this.finishLoad(var1, var2, var11, var3, var6, var7, var8));
            } catch (Exception var9x) {
               this.plugin.onMain(() -> {
                  this.pendingReplacements.remove(var2);
                  this.failure(var1, "backup inventory for " + var11, var9x);
               });
            }
         })) {
            this.pendingReplacements.remove(var2);
            var1.sendMessage("§cОчередь сохранения переполнена. Повторите позже.");
         }
      } else {
         this.pendingReplacements.remove(var2);
         var1.sendMessage("§cИгрок не найден или не в сети.");
      }
   }

   private void finishLoad(Player var1, UUID var2, String var3, String var4, InventoryData var5, InventoryData.Decoded var6, InventoryData.Decoded var7) {
      try {
         Player var8 = Bukkit.getPlayer(var2);
         if (var8 == null || !var8.isOnline()) {
            var1.sendMessage("§cИгрок не найден или не в сети.");
            return;
         }

         PlayerInventory var9 = var8.getInventory();
         if (!var5.matches(var9)) {
            var1.sendMessage("§cИнвентарь игрока изменился. Загрузка отменена, попробуйте ещё раз.");
            return;
         }

         try {
            var6.apply(var9);
            var8.updateInventory();
         } catch (Exception var18) {
            this.plugin.getLogger().log(Level.SEVERE, "Inventory restore failed; attempting rollback for " + var2, (Throwable)var18);

            try {
               var7.apply(var9);
               var8.updateInventory();
            } catch (Exception var17) {
               this.plugin
                  .getLogger()
                  .log(
                     Level.SEVERE,
                     "CRITICAL: Inventory rollback failed for " + var2 + "; locate the most recent backup in plugins/InventoryCache/backups/",
                     (Throwable)var17
                  );
            }

            var1.sendMessage("§cОшибка восстановления. Проверьте консоль и резервную копию.");
            return;
         }

         if (var2.equals(var1.getUniqueId())) {
            var1.sendMessage("§aИнвентарь загружен: §f" + var4);
         } else {
            var1.sendMessage("§aИнвентарь выдан игроку §f" + var3);
         }
      } catch (Exception var19) {
         this.failure(var1, "apply kit '" + var4 + "'", var19);
         return;
      } finally {
         this.pendingReplacements.remove(var2);
      }
   }

   private void failure(Player var1, String var2, Exception var3) {
      this.plugin.getLogger().log(Level.SEVERE, "Failed to " + var2, (Throwable)var3);
      var1.sendMessage("§cОшибка операции с инвентарём. Подробности в консоли.");
   }

   public List<String> onTabComplete(CommandSender var1, Command var2, String var3, String[] var4) {
      if (!(var1 instanceof Player var5 && var5.isOp())) {
         return List.of();
      } else if (var4.length == 1) {
         return matches(List.of("save", "load"), var4[0]);
      } else if (var4.length == 2 && var4[0].equalsIgnoreCase("load")) {
         return matches(this.kitNames, var4[1]);
      } else if (var4.length == 3 && (var4[0].equalsIgnoreCase("save") || var4[0].equalsIgnoreCase("load"))) {
         ArrayList<String> var6 = new ArrayList<>();

         for (Player var8 : Bukkit.getOnlinePlayers()) {
            var6.add(var8.getName());
         }

         return matches(var6, var4[2]);
      } else {
         return List.of();
      }
   }

   private static List<String> matches(Iterable<String> var0, String var1) {
      String var2 = var1.toLowerCase(Locale.ROOT);
      ArrayList<String> var3 = new ArrayList<>();

      for (String var5 : var0) {
         if (var5.toLowerCase(Locale.ROOT).startsWith(var2)) {
            var3.add(var5);
         }
      }

      var3.sort(Comparator.naturalOrder());
      return var3;
   }

   @EventHandler
   public void onCommandList(PlayerCommandSendEvent var1) {
      if (!var1.getPlayer().isOp()) {
         var1.getCommands().removeIf(var0 -> var0.equalsIgnoreCase("dk") || var0.equalsIgnoreCase("inventorycache:dk"));
      }
   }
}
