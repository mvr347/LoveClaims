package me.lovelace.loveclaims.gems;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GemOverloadTest {

    private static GemStats gem(GemRarity rarity, int radius, double crop) {
        return new GemStats("t", rarity, rarity.slotCost(), radius, 0, crop, 0.0, 0.0, false, Map.of(), null);
    }

    @Test
    void gapIsZeroWhenGemFitsOrLevelUnknown() {
        assertEquals(0, GemOverload.gap(gem(GemRarity.MODIFIED, 10, 0), 2));
        assertEquals(0, GemOverload.gap(gem(GemRarity.BASIC, 10, 0), 3));
        assertEquals(0, GemOverload.gap(gem(GemRarity.LEGENDARY, 10, 0), 0));
        assertEquals(2, GemOverload.gap(gem(GemRarity.LEGENDARY, 10, 0), 1));
    }

    @Test
    void positiveBuffsAreCutPerGapButDebuffsAreNot() {
        var legendary = gem(GemRarity.LEGENDARY, 20, 1.0);
        var debuff = gem(GemRarity.BASIC, -8, 0);
        var mods = ClaimModifiers.fromGems(List.of(legendary, debuff), 1, false);
        // gap 2 -> x0.25: 20 -> 5, debuff stays -8
        assertEquals(5 - 8, mods.radiusBonus());
        assertEquals(0.25, mods.cropGrowthMultiplier(), 1e-9);
    }

    @Test
    void noPenaltyWithoutAnchorLevel() {
        var mods = ClaimModifiers.fromGems(List.of(gem(GemRarity.LEGENDARY, 20, 1.0)));
        assertEquals(20, mods.radiusBonus());
        assertEquals(1.0, mods.cropGrowthMultiplier(), 1e-9);
    }

    @Test
    void brokenAnchorKeepsTerritoryButDropsOtherBuffs() {
        var mods = ClaimModifiers.fromGems(List.of(gem(GemRarity.BASIC, 6, 0.5)), 1, true);
        assertEquals(6, mods.radiusBonus());
        assertEquals(0.0, mods.cropGrowthMultiplier(), 1e-9);
    }

    @Test
    void brokenAnchorKeepsTheLimitBypassPerimeter() {
        var imperium = new GemStats("imperium_domain", GemRarity.LEGENDARY, 3, 0, 0, 0.0, 0.0, 0.0, true, Map.of(), null);
        assertTrue(ClaimModifiers.fromGems(List.of(imperium), 3, true).allowLimitBypass());
        // a gem that does not fit the anchor still cannot lift the limits
        assertFalse(ClaimModifiers.fromGems(List.of(imperium), 1, true).allowLimitBypass());
    }

    @Test
    void harmfulPotionEffectsAreNotWeakenedByTheGap() {
        var hybrid = new GemStats("h", GemRarity.MODIFIED, 2, 0, 0, 0.0, 0.0, 0.0, false,
                Map.of(org.bukkit.potion.PotionEffectType.HUNGER, 0), null);
        // tier 1 anchor, gap 1: a buff of level I would vanish, a debuff of level I must stay
        var mods = ClaimModifiers.fromGems(List.of(hybrid), 1, false);
        assertTrue(mods.potionEffects().containsKey(org.bukkit.potion.PotionEffectType.HUNGER));
    }

    @Test
    void breakChanceGrowsWithOverloadAboveThreshold() {
        int t = GemOverload.explodeThreshold();
        assertEquals(0.0, GemOverload.breakChance(t - 1));
        assertTrue(GemOverload.breakChance(t) > 0);
        assertTrue(GemOverload.breakChance(t + 1) > GemOverload.breakChance(t));
        assertTrue(GemOverload.breakChance(t + 2) >= GemOverload.breakChance(t + 1));
    }
}
