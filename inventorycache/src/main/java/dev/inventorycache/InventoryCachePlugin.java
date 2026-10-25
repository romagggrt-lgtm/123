package dev.inventorycache;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class InventoryCachePlugin extends JavaPlugin {
   private InventoryStore store;
   private ExecutorService diskWorker;
   private final Set<String> availableKits = new ConcurrentSkipListSet<>();

   public void onEnable() {
      this.store = new InventoryStore(this.getDataFolder().toPath());

      try {
         this.store.initialize();
         this.availableKits.addAll(this.store.existingNames());
      } catch (IOException var3) {
         this.getLogger().severe("Could not initialize inventory storage: " + var3.getMessage());
         Bukkit.getPluginManager().disablePlugin(this);
         return;
      }

      this.diskWorker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(128), var0 -> {
         Thread var1x = new Thread(var0, "InventoryCache-Storage");
         var1x.setDaemon(false);
         return var1x;
      }, new AbortPolicy());
      PluginCommand var1 = this.getCommand("dk");
      if (var1 == null) {
         this.getLogger().severe("Command /dk is missing from plugin.yml");
         Bukkit.getPluginManager().disablePlugin(this);
      } else {
         InventoryCommand var2 = new InventoryCommand(this, this.store, this.availableKits);
         var1.setExecutor(var2);
         var1.setTabCompleter(var2);
         Bukkit.getPluginManager().registerEvents(var2, this);
         Bukkit.getScheduler().runTask(this, () -> {
            Bukkit.getHelpMap().addTopic(new OpOnlyHelpTopic("/dk"));
            Bukkit.getHelpMap().addTopic(new OpOnlyHelpTopic("/inventorycache:dk"));
         });
      }
   }

   public boolean onDisk(Runnable var1) {
      try {
         this.diskWorker.execute(var1);
         return true;
      } catch (RejectedExecutionException var3) {
         this.getLogger().warning("Storage task rejected: queue full or plugin stopping");
         return false;
      }
   }

   public void onMain(Runnable var1) {
      if (this.isEnabled()) {
         Bukkit.getScheduler().runTask(this, () -> {
            if (this.isEnabled()) {
               var1.run();
            }
         });
      }
   }

   public void onDisable() {
      if (this.diskWorker != null) {
         this.diskWorker.shutdown();

         try {
            if (!this.diskWorker.awaitTermination(5L, TimeUnit.SECONDS)) {
               this.getLogger().warning("Inventory writes did not finish within shutdown deadline");
               this.diskWorker.shutdownNow();
            }
         } catch (InterruptedException var2) {
            this.diskWorker.shutdownNow();
            Thread.currentThread().interrupt();
         }
      }
   }
}
