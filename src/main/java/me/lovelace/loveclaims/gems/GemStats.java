package me.lovelace.loveclaims.gems;

import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;

public record GemStats(
        String gemId,
        GemRarity rarity,
        int slotCost,
        int radiusBonus,
        int membersBonus,
        double cropGrowthMultiplier,
        double furnaceSpeedMultiplier,
        double doubleDropChance,
        boolean allowLimitBypass,
        Map<PotionEffectType, Integer> potionEffects,
        HybridRollOutcome hybridOutcome
) {
    public GemStats {
        potionEffects = potionEffects == null ? Map.of() : Map.copyOf(potionEffects);
    }

    public GemStats(
            String gemId,
            GemRarity rarity,
            int slotCost,
            int radiusBonus,
            int membersBonus,
            double cropGrowthMultiplier,
            double furnaceSpeedMultiplier,
            double doubleDropChance,
            boolean allowLimitBypass,
            Map<PotionEffectType, Integer> potionEffects
    ) {
        this(gemId, rarity, slotCost, radiusBonus, membersBonus, cropGrowthMultiplier,
                furnaceSpeedMultiplier, doubleDropChance, allowLimitBypass, potionEffects, null);
    }

    public static GemStats empty(String gemId, GemRarity rarity) {
        return new GemStats(gemId, rarity, rarity.slotCost(), 0, 0, 0.0, 0.0, 0.0, false, Map.of(), null);
    }

    public GemStats combineWith(GemStats other) {
        var mergedEffects = new HashMap<>(this.potionEffects);
        other.potionEffects.forEach((type, amp) -> mergedEffects.merge(type, amp, Math::max));

        return new GemStats(
                this.gemId,
                this.rarity,
                this.slotCost,
                this.radiusBonus + other.radiusBonus,
                this.membersBonus + other.membersBonus,
                this.cropGrowthMultiplier + other.cropGrowthMultiplier,
                this.furnaceSpeedMultiplier + other.furnaceSpeedMultiplier,
                Math.max(this.doubleDropChance, other.doubleDropChance),
                this.allowLimitBypass || other.allowLimitBypass,
                mergedEffects,
                this.hybridOutcome
        );
    }
}
