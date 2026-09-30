package me.lovelace.loveclaims.api.event;

import me.lovelace.loveclaims.model.Claim;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** A trade point got a new tenant (payment done, ownership already switched). Main thread. */
public class TradePointRentedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID player;
    private final Claim point;

    public TradePointRentedEvent(@NotNull UUID player, @NotNull Claim point) {
        this.player = player;
        this.point = point;
    }

    public @NotNull UUID getPlayer() { return player; }
    public @NotNull Claim getPoint() { return point; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
