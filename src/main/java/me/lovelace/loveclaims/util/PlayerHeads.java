package me.lovelace.loveclaims.util;

import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.meta.SkullMeta;

/** Use already available textures; opening a members menu must never resolve offline UUIDs at Mojang. */
public final class PlayerHeads {
    private PlayerHeads() {}

    public static void apply(SkullMeta meta, OfflinePlayer player) {
        var profile = player.getPlayerProfile();
        if (profile.getTextures().getSkin() != null) meta.setOwnerProfile(profile);
    }
}
