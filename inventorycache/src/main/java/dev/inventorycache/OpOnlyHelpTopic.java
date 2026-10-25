package dev.inventorycache;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.help.HelpTopic;

public final class OpOnlyHelpTopic extends HelpTopic {
   public OpOnlyHelpTopic(String var1) {
      this.name = var1;
      this.shortText = "Inventory snapshot management";
      this.fullText = "/dk save <name> [player]\n/dk load <name> [player]";
   }

   public boolean canSee(CommandSender var1) {
      return var1 instanceof Player var2 && var2.isOp();
   }
}
