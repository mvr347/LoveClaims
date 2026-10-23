package me.lovelace.loveclaims.integration;

import dev.lovelace.lovecore.api.npc.NpcHandle;
import dev.lovelace.lovecore.api.npc.NpcProvider;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class NpcIntegrationTest {

    /** In-memory NpcHandle: only name and tags matter to the recognition rules. */
    private static NpcHandle npc(String name, Map<String, String> tags) {
        return (NpcHandle) Proxy.newProxyInstance(NpcHandle.class.getClassLoader(), new Class<?>[]{NpcHandle.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "name" -> name;
                    case "tag" -> {
                        if (args.length == 1) yield Optional.ofNullable(tags.get((String) args[0]));
                        tags.put((String) args[0], (String) args[1]);
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(m.getName());
                });
    }

    private static NpcProvider provider(String id) {
        return (NpcProvider) Proxy.newProxyInstance(NpcProvider.class.getClassLoader(), new Class<?>[]{NpcProvider.class},
                (proxy, m, args) -> {
                    if (m.getName().equals("providerId")) return id;
                    throw new UnsupportedOperationException(m.getName());
                });
    }

    // ----- provider choice -----

    @Test
    void choiceDefaultsToCitizens() {
        assertEquals("citizens", NpcProviderChoice.resolve(null));
        assertEquals("citizens", NpcProviderChoice.resolve("  "));
        assertEquals("citizens", NpcProviderChoice.resolve("global"), "without a core 'global' means citizens");
        assertEquals("citizens", NpcProviderChoice.resolve(" GLOBAL "));
        assertTrue(NpcProviderChoice.isKnown("global"));
        assertTrue(NpcProviderChoice.isKnown(null));
    }

    @Test
    void choiceIsCaseInsensitiveAndRejectsUnknown() {
        assertEquals("loveessential", NpcProviderChoice.resolve(" LoveEssential "));
        assertTrue(NpcProviderChoice.isKnown("Citizens"));
        assertEquals("citizens", NpcProviderChoice.resolve("znpcs"));
        assertFalse(NpcProviderChoice.isKnown("znpcs"));
    }

    // ----- bridge: selection fallback and warn-once -----

    @Test
    void bridgeReturnsConfiguredProvider() {
        List<String> warnings = new ArrayList<>();
        NpcProvider ess = provider("loveessential");
        NpcBridge bridge = new NpcBridge(() -> "loveessential",
                id -> id.equals("loveessential") ? Optional.of(ess) : Optional.empty(), warnings::add);
        assertSame(ess, bridge.provider().orElseThrow());
        assertTrue(bridge.isConfigured("loveessential"));
        assertFalse(bridge.isConfigured("citizens"));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void missingProviderWarnsOnceAndWarnsAgainAfterItAppeared() {
        List<String> warnings = new ArrayList<>();
        Map<String, NpcProvider> registry = new HashMap<>();
        NpcBridge bridge = new NpcBridge(() -> "citizens", id -> Optional.ofNullable(registry.get(id)), warnings::add);

        assertTrue(bridge.provider().isEmpty());
        assertTrue(bridge.provider().isEmpty());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("'citizens'"));

        registry.put("citizens", provider("citizens"));
        assertTrue(bridge.provider().isPresent());
        registry.clear();
        assertTrue(bridge.provider().isEmpty());
        assertEquals(2, warnings.size());
    }

    @Test
    void unknownConfigValueFallsBackToCitizensWithOneWarning() {
        List<String> warnings = new ArrayList<>();
        NpcProvider citizens = provider("citizens");
        NpcBridge bridge = new NpcBridge(() -> "znpcs",
                id -> id.equals("citizens") ? Optional.of(citizens) : Optional.empty(), warnings::add);
        assertSame(citizens, bridge.provider().orElseThrow());
        assertSame(citizens, bridge.provider().orElseThrow());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("znpcs"));
    }

    // ----- tag recognition and the legacy name fallback -----

    @Test
    void taggedTaxerIsRecognisedWithoutLookingAtTheName() {
        NpcHandle h = npc("Совсем другое имя", new HashMap<>(Map.of(NpcTags.ROLE, NpcTags.ROLE_TAXER)));
        assertTrue(NpcTags.isTaxer(h, "§eСборщик налогов"));
    }

    @Test
    void legacyNamedTaxerIsRecognisedAndGetsTagged() {
        Map<String, String> tags = new HashMap<>();
        NpcHandle h = npc("§eСборщик налогов", tags);
        assertTrue(NpcTags.isTaxer(h, "§eСборщик налогов"));
        assertEquals(NpcTags.ROLE_TAXER, tags.get(NpcTags.ROLE));
    }

    @Test
    void unrelatedNpcIsNotTaxerAndStaysUntagged() {
        Map<String, String> tags = new HashMap<>();
        assertFalse(NpcTags.isTaxer(npc("Кузнец", tags), "§eСборщик налогов"));
        assertTrue(tags.isEmpty());
    }

    @Test
    void landlordWhoseNameContainsTaxerTextIsNeverTurnedIntoTaxer() {
        Map<String, String> tags = new HashMap<>(Map.of(NpcTags.ROLE, NpcTags.ROLE_LANDLORD, NpcTags.CLAIM, "c1"));
        NpcHandle h = npc("[Аренда] Сборщик налогов", tags);
        assertFalse(NpcTags.isTaxer(h, "Сборщик налогов"));
        assertEquals(NpcTags.ROLE_LANDLORD, tags.get(NpcTags.ROLE));
    }

    @Test
    void landlordIsMatchedByRoleAndClaimId() {
        NpcHandle h = npc("x", new HashMap<>(Map.of(NpcTags.ROLE, NpcTags.ROLE_LANDLORD, NpcTags.CLAIM, "c1")));
        assertTrue(NpcTags.isLandlordOf(h, "c1"));
        assertFalse(NpcTags.isLandlordOf(h, "c2"));
    }

    @Test
    void blankConfiguredNameNeverMatches() {
        assertFalse(NpcTags.legacyNameMatches("Любой", ""));
        assertFalse(NpcTags.legacyNameMatches(null, "Сборщик"));
    }
}
