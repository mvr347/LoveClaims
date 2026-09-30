package me.lovelace.loveclaims.api;

import me.lovelace.loveclaims.model.Claim;

/**
 * Lets another plugin (LoveShops) pay a trade point's rent from money it holds for the tenant.
 * Currency is physical items in an online player's inventory, so an offline tenant can only pay
 * from a balance the shop keeps for them (the stall's till).
 */
public interface TradePointRentPayer {

    /**
     * Takes {@code amount} from the tenant's stored funds without touching any inventory.
     *
     * @return {@code true} if the whole amount was taken, {@code false} if nothing was taken
     */
    boolean payFromTill(Claim point, long amount);
}
