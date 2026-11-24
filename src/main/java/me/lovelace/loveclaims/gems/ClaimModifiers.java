package me.lovelace.loveclaims.gems;

import org.bukkit.potion.PotionEffectType;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Aggregated modifiers computed from all gems installed in a claim's anchor.
 */
public record ClaimModifiers(
        int usedSlots,
        int radiusBonus,
        int membersBonus,
        double cropGrowthMultiplier,
        double furnaceSpeedMultiplier,
        double doubleDropChance,
        boolean allowLimitBypass,
        Map<PotionEffectType, Integer> potionEffects
) {
    public static final ClaimModifiers EMPTY = new ClaimModifiers(0, 0, 0, 0.0, 0.0, 0.0, false, Map.of());

    public ClaimModifiers {
        potionEffects = potionEffects == null ? Map.of() : Map.copyOf(potionEffects);
    }

    @SuppressWarnings("deprecation")
    private static boolean isHarmful(PotionEffectType type) {
        try {
            return type.getEffectCategory() == PotionEffectType.Category.HARMFUL;
        } catch (RuntimeException e) {
            return false; // unknown category: treat as a buff
        }
    }

    public static ClaimModifiers fromGems(Collection<GemStats> gems) {
        return fromGems(gems, 0, false);
    }

    /**
     * @param anchorLevel anchor tier of the claim; gems above it lose part of their positive buffs
     *                    (see {@link GemOverload}); 0 disables the penalty
     * @param broken      a broken anchor core keeps the territory (radius, member slots) but turns every
     *                    other buff off, so a break never shrinks the claim around existing builds
     */
    public static ClaimModifiers fromGems(Collection<GemStats> gems, int anchorLevel, boolean broken) {
        return fromGems(gems, anchorLevel, broken, null);
    }

    /**
     * @param permanent the gem fused into the anchor for good, or null: counts as exactly one slot, always at
     *                  full strength (never cut by overload, kept while the core is cracked)
     */
    public static ClaimModifiers fromGems(Collection<GemStats> gems, int anchorLevel, boolean broken, GemStats permanent) {
        if ((gems == null || gems.isEmpty()) && permanent == null) {
            return EMPTY;
        }
        if (gems == null) gems = java.util.List.of();
        int slots = 0;
        int radius = 0;
        int members = 0;
        double crop = 0.0;
        double furnace = 0.0;
        double doubleDrop = 0.0;
        boolean bypass = false;
        Map<PotionEffectType, Integer> effects = new HashMap<>();

        for (GemStats gem : gems) {
            if (gem == null) continue;
            int gap = GemOverload.gap(gem, anchorLevel);
            double f = GemOverload.factor(gap);
            slots += Math.max(1, gem.slotCost());
            // Only positive bonuses are cut: a debuff gem must keep its full penalty.
            radius += gem.radiusBonus() > 0 ? (int) Math.round(gem.radiusBonus() * f) : gem.radiusBonus();
            members += gem.membersBonus() > 0 ? (int) Math.round(gem.membersBonus() * f) : gem.membersBonus();
            // The limit bypass keeps the unlocked extra perimeter, so it is territory, not a buff: a cracked
            // core must not shrink the protected area. It is still refused for a gem that does not fit.
            bypass = bypass || (gem.allowLimitBypass() && gap == 0);
            if (broken) continue;
            crop += gem.cropGrowthMultiplier() > 0 ? gem.cropGrowthMultiplier() * f : gem.cropGrowthMultiplier();
            furnace += gem.furnaceSpeedMultiplier() > 0 ? gem.furnaceSpeedMultiplier() * f : gem.furnaceSpeedMultiplier();
            doubleDrop = Math.max(doubleDrop, gem.doubleDropChance() * f);
            gem.potionEffects().forEach((type, amp) -> {
                // Harmful effects (debuff gems) keep their full level: only buffs are cut by the gap.
                int level = isHarmful(type) ? amp : amp - gap; // amplifier is 0-based: one gap = one level lower, below 0 the effect is gone
                if (level >= 0) effects.merge(type, level, Math::max);
            });
        }

        if (permanent != null) {
            slots += 1;
            radius += permanent.radiusBonus();
            members += permanent.membersBonus();
            crop += permanent.cropGrowthMultiplier();
            furnace += permanent.furnaceSpeedMultiplier();
            doubleDrop = Math.max(doubleDrop, permanent.doubleDropChance());
            bypass = bypass || permanent.allowLimitBypass();
            permanent.potionEffects().forEach((type, amp) -> effects.merge(type, amp, Math::max));
        }

        return new ClaimModifiers(
                slots,
                radius,
                members,
                crop,
                furnace,
                doubleDrop,
                bypass,
                effects
        );
    }
}
