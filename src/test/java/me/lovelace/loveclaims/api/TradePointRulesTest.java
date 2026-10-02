package me.lovelace.loveclaims.api;

import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TradePointRulesTest {

    @Test
    void idsAreShortNamesWithoutSpacesOrSymbols() {
        assertTrue(TradePointRules.isValidId("a1"));
        assertTrue(TradePointRules.isValidId("rynok-5"));
        assertTrue(TradePointRules.isValidId("Точка_7"));
        assertFalse(TradePointRules.isValidId(""));
        assertFalse(TradePointRules.isValidId(null));
        assertFalse(TradePointRules.isValidId("two words"));
        assertFalse(TradePointRules.isValidId("bad;id"));
        assertFalse(TradePointRules.isValidId("x".repeat(25)));
        assertTrue(TradePointRules.isValidId("x".repeat(24)));
    }

    @Test
    void zoneIsBuiltFromTheLowerCornerUpwards() {
        BoundingBox box = TradePointRules.zoneFromCorners(10, 64, 20, 4, 70, 25);
        assertEquals(4, box.getMinX());
        assertEquals(11, box.getMaxX());   // the corner block itself is inside
        assertEquals(20, box.getMinZ());
        assertEquals(26, box.getMaxZ());
        assertEquals(63, box.getMinY());   // one block below the lower corner
        assertEquals(77, box.getMaxY());   // twelve blocks above it, inclusive
    }

    @Test
    void zoneDoesNotDependOnCornerOrder() {
        assertEquals(TradePointRules.zoneFromCorners(1, 5, 2, 8, 9, 6),
                TradePointRules.zoneFromCorners(8, 9, 6, 1, 5, 2));
    }

    @Test
    void rentCostIsFirstPeriodPlusRenewals() {
        assertEquals(100, TradePointRules.rentCost(100, 80, 1));
        assertEquals(100 + 80 * 3, TradePointRules.rentCost(100, 80, 4));
        assertEquals(0, TradePointRules.rentCost(100, 80, 0));
        assertEquals(Long.MAX_VALUE, TradePointRules.rentCost(1, Long.MAX_VALUE, 5));
    }

    @Test
    void extendLimitFollowsThePrepaidRule() {
        long week = 7L * 86_400_000L;
        assertEquals(4, TradePointRules.maxExtendPeriods(0, week, 4));
        assertEquals(3, TradePointRules.maxExtendPeriods(week, week, 4));
        assertEquals(1, TradePointRules.maxExtendPeriods(3 * week, week, 4));
        assertEquals(0, TradePointRules.maxExtendPeriods(4 * week, week, 4));
        assertEquals(4, TradePointRules.maxExtendPeriods(-5, week, 4));
        assertEquals(0, TradePointRules.maxExtendPeriods(0, week, 0));
    }
}
