package me.lovelace.loveclaims.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A trade-point claim was fully removed from the world (admin delete, auto-delete, etc.).
 * Fired after the claim is already out of ClaimManager cache. Main thread.
 * LoveShops listens and destroys Citizens NPCs + market row for this point.
 */
public class TradePointDeletedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID claimId;
    private final @Nullable UUID formerTenant;
    private final boolean wasTradePoint;

    public TradePointDeletedEvent(@NotNull UUID claimId, @Nullable UUID formerTenant, boolean wasTradePoint) {
        this.claimId = claimId;
        this.formerTenant = formerTenant;
        this.wasTradePoint = wasTradePoint;
    }

    public @NotNull UUID getClaimId() { return claimId; }
    public @Nullable UUID getFormerTenant() { return formerTenant; }
    public boolean wasTradePoint() { return wasTradePoint; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
