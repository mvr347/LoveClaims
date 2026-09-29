package me.lovelace.loveclaims.listener;

import me.lovelace.loveclaims.LoveClaims;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Silently forbids players to spawn entities inside the spawn claim: boats, minecarts, armor
 * stands, end crystals, item frames, paintings, spawn eggs and entity buckets. Controlled by
 * {@code spawn-claim.flags.spawn-entities} (same flag family as the other spawn protections).
 * No message is sent on purpose.
 */
public class SpawnEntityListener implements Listener {
    private final LoveClaims plugin;

    public SpawnEntityListener(LoveClaims plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        if (isForbidden(event.getPlayer(), event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (isForbidden(event.getPlayer(), event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    /** Spawn eggs and fish/axolotl/tadpole buckets spawn a mob without any player-aware spawn event. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUseSpawningItem(PlayerInteractEvent event) {
        if (event.useItemInHand() == Event.Result.DENY) return;
        switch (event.getAction()) {
            case RIGHT_CLICK_BLOCK, RIGHT_CLICK_AIR -> { }
            default -> { return; }
        }
        ItemStack item = event.getItem();
        if (item == null || !spawnsEntity(item.getType())) return;

        Location where = event.getClickedBlock() != null
                ? event.getClickedBlock().getLocation()
                : event.getPlayer().getLocation();
        if (isForbidden(event.getPlayer(), where)) {
            event.setUseItemInHand(Event.Result.DENY);
            event.setCancelled(true);
        }
    }

    private boolean isForbidden(Player player, Location location) {
        if (player == null || hasBypass(player)) return false;
        if (!plugin.getConfigManager().isInsideSpawnClaim(location)) return false;
        return !plugin.getConfigManager().getSpawnFlag("spawn-entities");
    }

    private static boolean spawnsEntity(Material type) {
        String name = type.name();
        return name.endsWith("_SPAWN_EGG")
                || type == Material.AXOLOTL_BUCKET
                || type == Material.TADPOLE_BUCKET
                || type == Material.COD_BUCKET
                || type == Material.SALMON_BUCKET
                || type == Material.PUFFERFISH_BUCKET
                || type == Material.TROPICAL_FISH_BUCKET;
    }

    private boolean hasBypass(Player player) {
        return player.hasPermission("loveclaims.bypass")
                || player.hasPermission("loveclaims.admin")
                || player.hasPermission("loveclaims.moderator");
    }
}
