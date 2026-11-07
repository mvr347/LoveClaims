package me.lovelace.loveclaims.listener;

import me.lovelace.loveclaims.LoveClaims;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * Loads the claims of a world that came up after LoveClaims started (a world plugin that creates worlds late).
 * Their rows are skipped by the start-up load; without this the trade points, rental plots and claims of such a
 * world are missing until the next restart, and LoveShops shows its points without names and prices (2026-11-07).
 */
public final class WorldClaimsListener implements Listener {
    private final LoveClaims plugin;

    public WorldClaimsListener(LoveClaims plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        var world = event.getWorld();
        plugin.getStorage().loadAllClaims().thenAccept(claims -> Bukkit.getScheduler().runTask(plugin, () -> {
            int added = 0;
            for (var claim : claims) {
                if (!world.equals(claim.getWorld()) || plugin.getClaimManager().getClaimById(claim.getId()).isPresent()) continue;
                plugin.getClaimManager().addClaimToCache(claim);
                if (claim.isRentalPlot() && claim.getName() != null) {
                    plugin.getRentalManager().registerPlotName(claim.getName(), claim.getId());
                }
                added++;
            }
            if (added > 0) plugin.getLogger().info("Loaded " + added + " claims of the world " + world.getName() + ".");
        })).exceptionally(error -> {
            plugin.getLogger().warning("Could not load the claims of " + world.getName() + ": " + error.getMessage());
            return null;
        });
    }
}
