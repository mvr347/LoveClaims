package me.lovelace.loveclaims.gems;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static me.lovelace.loveclaims.gems.GemFusion.Check.*;
import static org.junit.jupiter.api.Assertions.*;

class GemFusionTest {
    private static GemStats gem(GemRarity r, int radius) {
        return new GemStats("t", r, r.slotCost(), radius, 0, 0.0, 0.0, 0.0, false, Map.of(), null);
    }

    @Test
    void legendaryNeedsTierTwoAndOnlyOneFusionEver() {
        var legendary = gem(GemRarity.LEGENDARY, 10);
        assertEquals(LEGENDARY_TOO_STRONG, GemFusion.check(1, legendary, false, false, 0, 3));
        assertEquals(OK, GemFusion.check(2, legendary, false, false, 0, 6));
        assertEquals(OK, GemFusion.check(3, legendary, false, false, 0, 8));
        assertEquals(OK, GemFusion.check(1, gem(GemRarity.BASIC, 6), false, false, 0, 3));
        assertEquals(ALREADY_FUSED, GemFusion.check(3, gem(GemRarity.BASIC, 6), true, false, 0, 8));
    }

    @Test
    void needsFreeSlotAndWorkingCore() {
        var basic = gem(GemRarity.BASIC, 6);
        assertEquals(NO_FREE_SLOT, GemFusion.check(1, basic, false, false, 3, 3));
        assertEquals(ANCHOR_BROKEN, GemFusion.check(1, basic, false, true, 0, 3));
        assertTrue(GemFusion.needsExtraCost(3));
        assertFalse(GemFusion.needsExtraCost(2));
    }

    @Test
    void fusedGemTakesOneSlotAndIsNeverCutOrDropped() {
        var legendary = new GemStats("l", GemRarity.LEGENDARY, 3, 20, 0, 1.0, 0.0, 0.0, false, Map.of(), null);
        // tier 2 anchor, cracked core: the fused gem still counts one slot and full strength
        var mods = ClaimModifiers.fromGems(java.util.List.of(), 2, true, legendary);
        assertEquals(1, mods.usedSlots());
        assertEquals(20, mods.radiusBonus());
        assertEquals(1.0, mods.cropGrowthMultiplier(), 1e-9);
        // an ordinary legendary gem in the same tier 2 anchor would be cut by the gap
        var cut = ClaimModifiers.fromGems(java.util.List.of(legendary), 2, false, null);
        assertEquals(10, cut.radiusBonus());
        assertEquals(3, cut.usedSlots());
    }
}
