package me.lovelace.loveclaims.gems;

/**
 * Rules for fusing a gem into an anchor for good. A claim can be fused only once in its lifetime.
 * The fused gem takes one slot whatever its slot cost, is never cut by overload and cannot be removed.
 * A legendary gem needs an anchor of tier 2 or higher; tier 3 also costs extra ingredients.
 */
public final class GemFusion {
    public enum Check { OK, ALREADY_FUSED, LEGENDARY_TOO_STRONG, NO_FREE_SLOT, ANCHOR_BROKEN }

    private GemFusion() {
    }

    public static Check check(int anchorLevel, GemStats gem, boolean alreadyFused, boolean broken,
                              int usedSlots, int maxSlots) {
        if (alreadyFused) return Check.ALREADY_FUSED;
        if (broken) return Check.ANCHOR_BROKEN;
        if (gem.rarity() == GemRarity.LEGENDARY && anchorLevel < 2) return Check.LEGENDARY_TOO_STRONG;
        if (usedSlots + 1 > maxSlots) return Check.NO_FREE_SLOT;
        return Check.OK;
    }

    /** Tier 3 anchors need the extra ingredients and coins from config. */
    public static boolean needsExtraCost(int anchorLevel) {
        return anchorLevel >= 3;
    }
}
