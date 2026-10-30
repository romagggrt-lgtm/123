package dev.znick;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

record Nick(UUID id, String account, String raw, int wire, Component text, String plain) {
    static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
        .character('&').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    static final Pattern CODES = Pattern.compile("(?i)&#[0-9a-f]{6}|&[0-9a-fklmnor]");
    static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N}_-]+");

    static Nick parse(UUID id, String account, String raw, int wire, int max, NamedTextColor color) {
        if (raw == null || raw.length() > 2048 || wire < 0 || wire > 0x0fffffff)
            throw new IllegalArgumentException("Invalid nickname data");
        String converted = raw.replaceAll("(?i)&#([0-9a-f]{6})", "&#$1");
        Component text = LEGACY.deserialize(converted).applyFallbackStyle(color);
        String plain = PlainTextComponentSerializer.plainText().serialize(text);
        if (plain.codePointCount(0, plain.length()) < 1 || plain.codePointCount(0, plain.length()) > max
            || !NAME.matcher(plain).matches() || !CODES.matcher(raw).replaceAll("").equals(plain))
            throw new IllegalArgumentException("Invalid nickname");
        return new Nick(id, account, raw, wire, text, plain);
    }

    static String key(String plain) {
        return Normalizer.normalize(plain, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    /** Seven legacy colour pairs plus the vanilla font's zero-advance ZWNJ.
     * Exactly 15 UTF-16 code units, below the protocol's 16-unit profile limit.
     * No account name is placed in the client profile. The visible name is the
     * team's full Adventure prefix; formatting pairs render no glyphs. */
    String alias() {
        String digits = String.format(Locale.ROOT, "%07x", wire);
        StringBuilder out = new StringBuilder(15);
        for (char c : digits.toCharArray()) out.append('§').append(c);
        return out.append('\u200c').toString();
    }

    String team() { return "zn$" + Integer.toHexString(wire); }
}
