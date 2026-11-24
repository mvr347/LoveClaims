package me.lovelace.loveclaims.gems;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Rules for a gem that is stronger than the anchor it sits in.
 * <p>
 * Gap of one gem = its minimum anchor tier minus the anchor tier (never negative). Positive buffs of a
 * gem with gap g are multiplied by {@code multiplierPerGap^g}. The sum of all gaps is the claim's overload;
 * above the threshold, inserting a gem can break the anchor.
 */
public final class GemOverload {
    private static volatile double multiplierPerGap = 0.5;
    private static volatile int explodeThreshold = 3;
    private static volatile double chanceLow = 0.15;
    private static volatile double chanceMid = 0.40;
    private static volatile double chanceHigh = 0.75;

    private GemOverload() {
    }

    /** Reads gems.overload.* with the single-argument getters, so jar defaults apply to old config files. */
    public static void load(FileConfiguration cfg) {
        // Single-argument getters: a missing key gives the jar default, an explicit 0 stays 0
        // (0 chance = this overload level never breaks the anchor).
        double m = cfg.getDouble("gems.overload.buff-multiplier-per-gap");
        multiplierPerGap = m >= 0 && m <= 1 ? m : 0.5;
        int t = cfg.getInt("gems.overload.explode-threshold");
        explodeThreshold = t > 0 ? t : 3;
        chanceLow = probability(cfg.getDouble("gems.overload.break-chance.at-threshold"), 0.15);
        chanceMid = probability(cfg.getDouble("gems.overload.break-chance.at-threshold-plus-1"), 0.40);
        chanceHigh = probability(cfg.getDouble("gems.overload.break-chance.at-threshold-plus-2"), 0.75);
    }

    private static double probability(double v, double fallback) {
        return v >= 0 && v <= 1 ? v : fallback;
    }

    /** 0 when the anchor level is unknown (&lt;= 0) or the gem fits. */
    public static int gap(GemStats gem, int anchorLevel) {
        if (gem == null || anchorLevel <= 0) return 0;
        return Math.max(0, gem.rarity().minAnchorLevel() - anchorLevel);
    }

    public static double factor(int gap) {
        return gap <= 0 ? 1.0 : Math.pow(multiplierPerGap, gap);
    }

    public static int totalOverload(Iterable<GemStats> gems, int anchorLevel) {
        int sum = 0;
        for (GemStats g : gems) sum += gap(g, anchorLevel);
        return sum;
    }

    public static int explodeThreshold() {
        return explodeThreshold;
    }

    /** Chance that inserting a gem breaks the anchor, for the overload after the insertion. */
    public static double breakChance(int overload) {
        int over = overload - explodeThreshold;
        if (over < 0) return 0.0;
        if (over == 0) return chanceLow;
        if (over == 1) return chanceMid;
        return chanceHigh;
    }

    public static double multiplierPerGap() {
        return multiplierPerGap;
    }
}
