package me.lovelace.loveclaims.api.event;

import me.lovelace.loveclaims.model.Claim;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Fired once per rental period shortly before a trade point's rent runs out. Main thread. */
public class TradePointExpiryWarningEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID player;
    private final Claim point;
    private final long millisLeft;

    public TradePointExpiryWarningEvent(@NotNull UUID player, @NotNull Claim point, long millisLeft) {
        this.player = player;
        this.point = point;
        this.millisLeft = millisLeft;
    }

    public @NotNull UUID getPlayer() { return player; }
    public @NotNull Claim getPoint() { return point; }
    public long getMillisLeft() { return millisLeft; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
