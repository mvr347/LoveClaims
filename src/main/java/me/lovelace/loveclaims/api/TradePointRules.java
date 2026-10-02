package me.lovelace.loveclaims.api;

import org.bukkit.util.BoundingBox;

import java.util.regex.Pattern;

/**
 * Pure rules of trade points (no server needed, covered by tests): which ids are allowed, the zone
 * a point gets from two corners, and what renting several periods costs.
 */
public final class TradePointRules {

    private static final Pattern ID = Pattern.compile("[\\p{L}\\p{N}_-]{1,24}");

    private TradePointRules() {}

    /** A point id is what the admin types (and what the sign shows): letters, digits, {@code _} and {@code -}, up to 24 chars. */
    public static boolean isValidId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    /**
     * The zone of a trade point from two corner blocks, built like {@code /rental admin create}: the
     * corners give the footprint, the zone starts one block below the lower corner and reaches
     * twelve blocks above it, so the whole stall (and its roof) is covered.
     */
    public static BoundingBox zoneFromCorners(int x1, int y1, int z1, int x2, int y2, int z2) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);
        int baseY = Math.min(y1, y2);
        return new BoundingBox(minX, baseY - 1, minZ, maxX + 1, baseY + 12 + 1, maxZ + 1);
    }

    /**
     * Price of renting a free point for {@code periods} periods: the first at the full price, every
     * further one at the renewal price. Saturates instead of overflowing.
     */
    public static long rentCost(long firstPeriod, long renewal, int periods) {
        if (periods <= 0) return 0L;
        long extra = Math.max(0L, renewal);
        long rest = periods - 1L;
        if (extra != 0 && rest > Long.MAX_VALUE / extra) return Long.MAX_VALUE;
        long total = Math.max(0L, firstPeriod) + extra * rest;
        return total < 0 ? Long.MAX_VALUE : total;
    }

    /**
     * How many more periods a tenant may pay for at once, by the rule "one more is allowed while the
     * time already prepaid is below {@code maxPrepaid} periods".
     */
    public static int maxExtendPeriods(long aheadMillis, long periodMillis, int maxPrepaid) {
        if (periodMillis <= 0 || maxPrepaid <= 0) return 0;
        long limit = periodMillis * maxPrepaid;
        long ahead = Math.max(0L, aheadMillis);
        if (ahead >= limit) return 0;
        return (int) ((limit - ahead - 1) / periodMillis) + 1;
    }
}
