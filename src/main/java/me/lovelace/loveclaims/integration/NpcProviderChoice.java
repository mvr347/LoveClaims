package me.lovelace.loveclaims.integration;

import java.util.Locale;
import java.util.Set;

/** Pure parsing of {@code integrations.npc-provider}: which NPC backend LoveClaims talks to. */
public final class NpcProviderChoice {
    public static final String DEFAULT = "citizens";
    private static final Set<String> KNOWN = Set.of("citizens", "loveessential");

    private NpcProviderChoice() {
    }

    /** True when {@code raw} names a provider this plugin knows (blank counts as "use the default"). */
    public static boolean isKnown(String raw) {
        return raw == null || raw.isBlank() || KNOWN.contains(normalize(raw));
    }

    /** Provider id to use for {@code raw}: blank or unknown values fall back to {@link #DEFAULT}. */
    public static String resolve(String raw) {
        if (raw == null || raw.isBlank()) return DEFAULT;
        String id = normalize(raw);
        return KNOWN.contains(id) ? id : DEFAULT;
    }

    private static String normalize(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
