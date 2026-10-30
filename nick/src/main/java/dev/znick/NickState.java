package dev.znick;

import java.util.*;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;

/** Published atomically; Netty and asynchronous chat never read Bukkit state. */
final class NickState {
    final Map<UUID, Nick> players;
    final Map<String, Nick> accounts;
    final Set<String> entries;
    final Pattern names;

    NickState(Map<UUID, Nick> values) {
        players = Map.copyOf(values);
        Map<String, Nick> a = new HashMap<>();
        Set<String> e = new HashSet<>();
        values.values().forEach(n -> { a.put(n.account().toLowerCase(Locale.ROOT), n); e.add(n.account()); e.add(n.alias()); });
        accounts = Map.copyOf(a);
        entries = Set.copyOf(e);
        names = values.isEmpty() ? null : Pattern.compile("(?i)(?<![\\p{L}\\p{N}_])(?:"
            + String.join("|", values.values().stream().map(Nick::account).map(Pattern::quote).toList())
            + ")(?![\\p{L}\\p{N}_])");
    }

    boolean managed(String entry) { return entries.contains(entry) || accounts.containsKey(entry.toLowerCase(Locale.ROOT)); }

    Component rewrite(Component input) {
        if (input == null || names == null) return input;
        return input.replaceText(TextReplacementConfig.builder().match(names).replacement((match, builder) -> {
            Nick n = accounts.get(match.group().toLowerCase(Locale.ROOT));
            return n == null ? builder.build() : n.text();
        }).build());
    }

    String entry(String account) {
        Nick n = accounts.get(account.toLowerCase(Locale.ROOT));
        return n == null ? account : n.alias();
    }
}
