package me.lovelace.loveclaims.api.event;

import me.lovelace.loveclaims.model.Claim;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired on the main thread BEFORE a player is charged for a trade point. Cancelling it stops the
 * rental; nothing has been paid or changed yet. LoveShops cancels it for players whose
 * reputation keeps them off the market.
 */
public class TradePointRentRequestEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Claim point;
    private boolean cancelled;
    private String denyMessage;

    public TradePointRentRequestEvent(@NotNull Player player, @NotNull Claim point) {
        this.player = player;
        this.point = point;
    }

    public @NotNull Player getPlayer() { return player; }
    public @NotNull Claim getPoint() { return point; }

    /** Message shown to the player when the event is cancelled; {@code null} = generic one. */
    public @Nullable String getDenyMessage() { return denyMessage; }
    public void setDenyMessage(@Nullable String denyMessage) { this.denyMessage = denyMessage; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
