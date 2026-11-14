package me.lovelace.loveclaims.util;

import org.bukkit.permissions.Permissible;

/** Managing claims does not grant permission to destroy every player's property. */
public final class ClaimBypass {
    private ClaimBypass() {}

    public static boolean allowed(Permissible player) {
        return player.hasPermission("loveclaims.bypass");
    }
}
