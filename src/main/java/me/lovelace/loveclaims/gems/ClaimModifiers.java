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

    public static ClaimModifiers fromGems(Collection<GemStats> gems) {
        if (gems == null || gems.isEmpty()) {
            return EMPTY;
        }
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
            slots += Math.max(1, gem.slotCost());
            radius += gem.radiusBonus();
            members += gem.membersBonus();
            crop += gem.cropGrowthMultiplier();
            furnace += gem.furnaceSpeedMultiplier();
            doubleDrop = Math.max(doubleDrop, gem.doubleDropChance());
            bypass = bypass || gem.allowLimitBypass();
            gem.potionEffects().forEach((type, amp) -> effects.merge(type, amp, Math::max));
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
