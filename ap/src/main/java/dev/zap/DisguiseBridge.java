package dev.zap;

import java.util.Locale;
import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import org.bukkit.entity.Player;

/** Optional LibsDisguises public API; loaded only while that plugin is enabled. */
final class DisguiseBridge {
 private DisguiseBridge() {}
 static String active(Player player){Disguise disguise=DisguiseAPI.getDisguise(player);return disguise==null||!disguise.isDisguiseInUse()?null:disguise.getType().name().toLowerCase(Locale.ROOT);}
}
