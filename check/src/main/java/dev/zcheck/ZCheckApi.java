package dev.zcheck;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Obtain from ServicesManager; all calls must run on the main server thread. */
public interface ZCheckApi {
    boolean isChecked(UUID player);
    /** Moves the freeze anchor and player as one managed administrative operation.
     * Returns false if another plugin cancels teleport or the journal cannot be saved. */
    boolean moveCheckedPlayer(Player player, Location destination);
}
