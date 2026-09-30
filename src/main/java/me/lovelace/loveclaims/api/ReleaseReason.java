package me.lovelace.loveclaims.api;

/** Why a rented plot went back to the landlord. */
public enum ReleaseReason {
    /** Rental time ran out (including the trade-point grace period). */
    EXPIRED,
    /** The tenant gave the plot up (abandon GUI, {@code /rental sell}). */
    ABANDONED,
    /** The tenant could not pay the periodic tax/rent. */
    EVICTED,
    /** An administrator removed the tenant. */
    ADMIN
}
