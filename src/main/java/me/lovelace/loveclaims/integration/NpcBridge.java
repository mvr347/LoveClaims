package me.lovelace.loveclaims.integration;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.npc.NpcProvider;
import me.lovelace.loveclaims.LoveClaims;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Resolves the configured {@link NpcProvider} on every use (providers register late, and the
 * config can be reloaded). A missing provider is logged once and the NPC feature just stays off.
 */
public final class NpcBridge {
    private final Supplier<String> configured;
    private final Function<String, Optional<NpcProvider>> lookup;
    private final Consumer<String> warn;
    private String warnedAbout;
    private String warnedBadValue;

    public NpcBridge(Supplier<String> configured, Function<String, Optional<NpcProvider>> lookup, Consumer<String> warn) {
        this.configured = configured;
        this.lookup = lookup;
        this.warn = warn;
    }

    public static NpcBridge forPlugin(LoveClaims plugin) {
        return new NpcBridge(
                () -> plugin.getConfig().getString("integrations.npc-provider", NpcProviderChoice.DEFAULT),
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

    /** Id of the provider LoveClaims is configured to use (unknown values fall back to the default). */
    public synchronized String providerId() {
        String raw = configured.get();
        String id = NpcProviderChoice.resolve(raw);
        if (!NpcProviderChoice.isKnown(raw)) {
            if (!raw.equals(warnedBadValue)) {
                warnedBadValue = raw;
                warn.accept("integrations.npc-provider: unknown value '" + raw + "', using '" + id
                        + "' (allowed: citizens, loveessential).");
            }
        } else {
            warnedBadValue = null;
        }
        return id;
    }

    /** The configured provider, or empty (with one warning per provider) when it is not available. */
    public synchronized Optional<NpcProvider> provider() {
        String id = providerId();
        Optional<NpcProvider> found = lookup.apply(id);
        if (found.isPresent()) {
            warnedAbout = null;
        } else if (!id.equals(warnedAbout)) {
            warnedAbout = id;
            warn.accept("NPC provider '" + id + "' is not available (LoveCore missing or the provider plugin is not "
                    + "installed/enabled yet): taxer and landlord NPCs are disabled until it appears.");
        }
        return found;
    }

    /** True when an NPC reporting {@code npcProviderId} belongs to the provider LoveClaims is configured for. */
    public boolean isConfigured(String npcProviderId) {
        return npcProviderId != null && npcProviderId.equalsIgnoreCase(providerId());
    }
}
