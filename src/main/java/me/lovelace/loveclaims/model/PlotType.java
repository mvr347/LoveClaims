package me.lovelace.loveclaims.model;

/**
 * Kind of a rental plot. {@link #RENTAL_PLOT} is the classic renter-built plot and stays the
 * default so every row written before this column existed keeps its behaviour.
 * {@link #TRADE_POINT} is a market stall managed together with LoveShops: a small locked-down
 * area where the tenant may not build, may not use containers and may not add members.
 */
public enum PlotType {
    RENTAL_PLOT,
    TRADE_POINT;

    public static PlotType parse(String raw) {
        if (raw == null || raw.isBlank()) return RENTAL_PLOT;
        try {
            return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return RENTAL_PLOT;
        }
    }
}
