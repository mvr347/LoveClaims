package me.lovelace.loveclaims.api.event;

import me.lovelace.loveclaims.api.ReleaseReason;
import me.lovelace.loveclaims.model.Claim;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** A trade point lost its tenant (ownership already back with the landlord). Main thread. */
public class TradePointReleasedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID player;
    private final Claim point;
    private final ReleaseReason reason;

    public TradePointReleasedEvent(@NotNull UUID player, @NotNull Claim point, @NotNull ReleaseReason reason) {
        this.player = player;
        this.point = point;
        this.reason = reason;
    }

    /** The former tenant. */
    public @NotNull UUID getPlayer() { return player; }
    public @NotNull Claim getPoint() { return point; }
    public @NotNull ReleaseReason getReason() { return reason; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
