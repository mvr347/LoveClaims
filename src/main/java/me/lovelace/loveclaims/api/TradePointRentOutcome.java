package me.lovelace.loveclaims.api;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Result of renting or prolonging a trade point through the API.
 *
 * @param status  what happened
 * @param message the refusal text to show the player (set for {@link Status#DENIED}), otherwise {@code null}
 * @param cost    what was charged (OK) or what was missing the player (NO_FUNDS)
 */
public record TradePointRentOutcome(Status status, @Nullable Component message, long cost) {

    public enum Status {
        OK,
        NOT_TRADE_POINT,
        /** The number of periods is outside what the server allows. */
        BAD_PERIODS,
        /** Taken, limit reached, disabled or vetoed by another plugin; see {@link #message()}. */
        DENIED,
        NO_ECONOMY,
        NO_FUNDS,
        /** Not the tenant of this point (prolonging). */
        NOT_TENANT
    }

    public boolean ok() {
        return status == Status.OK;
    }

    public static TradePointRentOutcome of(Status status) {
        return new TradePointRentOutcome(status, null, 0L);
    }
}
