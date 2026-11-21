package me.lovelace.loveclaims.integration;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.npc.NpcProvider;
import dev.lovelace.lovecore.api.provider.Providers;
import me.lovelace.loveclaims.LoveClaims;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Resolves the LoveEssential {@link NpcProvider} on every use (it registers late). A missing provider is logged
 * once and the NPC feature just stays off. 2026-11-21: the provider switch was removed; a leftover
 * {@code integrations.npc-provider} value is ignored and reported once.
 */
public final class NpcBridge {
    /** The only supported NPC provider. */
    public static final String PROVIDER_ID = Providers.LOVE_ESSENTIAL;
    /** Leftover key of the removed provider switch. */
    public static final String LEGACY_PROVIDER_PATH = "integrations.npc-provider";

    private final Supplier<String> leftover;
    private final Function<String, Optional<NpcProvider>> lookup;
    private final Consumer<String> warn;
    private String warnedAbout;
    private String warnedBadValue;

    /** @param leftover supplies the raw value of the removed {@code integrations.npc-provider} key, or null */
    public NpcBridge(Supplier<String> leftover, Function<String, Optional<NpcProvider>> lookup, Consumer<String> warn) {
        this.leftover = leftover;
        this.lookup = lookup;
        this.warn = warn;
    }

    /** The leftover value that is ignored, or null when it is absent, blank, {@code global} or {@code loveessential}. */
    public static String ignoredProviderValue(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty() || value.equalsIgnoreCase("global") || value.equalsIgnoreCase(PROVIDER_ID)) return null;
        return value;
    }

    public static NpcBridge forPlugin(LoveClaims plugin) {
        return new NpcBridge(
                () -> plugin.getConfig().getString(LEGACY_PROVIDER_PATH, null),
                NpcBridge::loveCoreLookup,
                plugin.getLogger()::warning);
    }

    private static Optional<NpcProvider> loveCoreLookup(String id) {
        try {
            return LoveCore.isAvailable() ? LoveCore.provider(NpcProvider.class, id) : Optional.empty();
        } catch (LinkageError | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Id of the provider: always LoveEssential; a leftover config value is reported once and ignored. */
    public synchronized String providerId() {
        String ignored = ignoredProviderValue(leftover.get());
        if (ignored != null) {
            if (!ignored.equals(warnedBadValue)) {
                warnedBadValue = ignored;
                warn.accept(LEGACY_PROVIDER_PATH + ": value '" + ignored + "' is ignored, NPCs come from "
                        + "LoveEssential only; remove the key from config.yml.");
            }
        } else {
            warnedBadValue = null;
        }
        return PROVIDER_ID;
    }

    /** The LoveEssential provider, or empty (with one warning) when it is not available. */
    public synchronized Optional<NpcProvider> provider() {
        String id = providerId();
        Optional<NpcProvider> found = lookup.apply(id);
        if (found.isPresent()) {
            warnedAbout = null;
        } else if (!id.equals(warnedAbout)) {
            warnedAbout = id;
            warn.accept("NPC provider '" + id + "' is not available (LoveCore or LoveEssential missing or not "
                    + "enabled yet): taxer and landlord NPCs are disabled until it appears.");
        }
        return found;
    }

    /** True when an NPC reporting {@code npcProviderId} belongs to LoveEssential. */
    public boolean isConfigured(String npcProviderId) {
        return npcProviderId != null && npcProviderId.equalsIgnoreCase(providerId());
    }
}
