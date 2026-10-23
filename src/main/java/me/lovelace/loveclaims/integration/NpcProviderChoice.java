package me.lovelace.loveclaims.integration;

import java.util.Locale;
import java.util.Set;

/** Pure parsing of {@code integrations.npc-provider}: which NPC backend LoveClaims talks to. */
public final class NpcProviderChoice {
    public static final String DEFAULT = "citizens";
    /** Config value that follows {@code integrations.provider} of LoveCore. */
    public static final String GLOBAL = "global";
    private static final Set<String> KNOWN = Set.of("citizens", "loveessential");

    private NpcProviderChoice() {
    }

    /** True for a blank, missing or {@code global} value: the provider is then the global choice of LoveCore. */
    public static boolean followsGlobal(String raw) {
        return raw == null || raw.isBlank() || GLOBAL.equals(normalize(raw));
    }

    /** True when {@code raw} names a provider this plugin knows (blank and {@code global} follow the core). */
    public static boolean isKnown(String raw) {
        return followsGlobal(raw) || KNOWN.contains(normalize(raw));
    }

    /**
     * Provider id to use for {@code raw}. {@code global}, blank or missing follows {@code integrations.provider}
     * of LoveCore (without LoveCore: {@link #DEFAULT}); a known id wins; an unknown value falls back to
     * {@link #DEFAULT}.
     */
    public static String resolve(String raw) {
        if (followsGlobal(raw)) {
            try {
                return dev.lovelace.lovecore.api.LoveCore.providerId(
                        dev.lovelace.lovecore.api.provider.ProviderKind.NPC, GLOBAL);
            } catch (LinkageError coreMissingOrOld) {
                return DEFAULT;
            }
        }
        String id = normalize(raw);
        return KNOWN.contains(id) ? id : DEFAULT;
    }

    private static String normalize(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
