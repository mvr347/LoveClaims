package me.lovelace.loveclaims.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * A player entered or left a claim. Fired on the main thread before LoveClaims sends its own
 * enter/leave title and action bar. A listener that shows its own message must call
 * {@link #setHandled(boolean)} with {@code true}: LoveClaims then sends nothing.
 * LoveClans uses this to show clan-relation-aware text on clan territories.
 */
public class ClaimBorderCrossEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final UUID claimId;
    private final boolean enter;
    private final boolean clanTerritory;
    private boolean handled;

    public ClaimBorderCrossEvent(@NotNull Player player, @NotNull UUID claimId, boolean enter, boolean clanTerritory) {
        this.player = player;
        this.claimId = claimId;
        this.enter = enter;
        this.clanTerritory = clanTerritory;
    }

    public @NotNull Player getPlayer() { return player; }
    public @NotNull UUID getClaimId() { return claimId; }
    /** {@code true} when entering the claim, {@code false} when leaving it. */
    public boolean isEnter() { return enter; }
    public boolean isClanTerritory() { return clanTerritory; }
    public boolean isHandled() { return handled; }
    public void setHandled(boolean handled) { this.handled = handled; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
