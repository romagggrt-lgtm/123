package net.sacredlabyrinth.phaed.simpleclans.acf;

import java.util.Locale;
import java.util.Objects;
import org.bukkit.entity.Player;

/** Paper 1.21.11 compatibility for ACF's client-language detection. */
final class ClientLocale {
    private ClientLocale() {}

    static void read(BukkitCommandManager manager, Player player) {
        if (!player.isOnline()) return;
        String raw = player.getLocale();
        if (raw == null || raw.isBlank()) return;
        var id = player.getUniqueId();
        if (raw.equals(manager.issuersLocaleString.get(id))) return;
        Locale locale = Locale.forLanguageTag(raw.replace('_', '-'));
        if (locale.getLanguage().isEmpty()) return;
        Locale previous = manager.issuersLocale.put(id, locale);
        manager.issuersLocaleString.put(id, raw);
        if (!Objects.equals(locale, previous)) {
            manager.notifyLocaleChange(manager.getCommandIssuer(player), previous, locale);
        }
    }
}
