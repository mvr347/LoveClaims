package me.lovelace.loveclaims.util;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.auth.AuthOracle;
import org.bukkit.OfflinePlayer;

/** Messages must not reach a player who is still in the login lobby (LoveAuth, through LoveCore's AuthOracle). */
public final class AuthGate {
    private AuthGate() {
    }

    /** True when the player is offline (invitation waits for them), or online and logged in, or no auth plugin exists. */
    public static boolean canReceive(OfflinePlayer target) {
        if (!target.isOnline()) return true;
        var oracle = LoveCore.service(AuthOracle.class);
        return oracle.isEmpty() || oracle.get().isAuthenticated(target.getUniqueId());
    }
}
