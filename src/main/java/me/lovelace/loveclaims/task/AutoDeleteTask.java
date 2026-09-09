package me.lovelace.loveclaims.task;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.model.Claim;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AutoDeleteTask {
    private final LoveClaims plugin;
    private ScheduledTask task;

    public AutoDeleteTask(LoveClaims plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin, scheduler -> {
            if (!plugin.getConfigManager().getConfig().getBoolean("auto-delete.enabled", true)) return;

            int days = plugin.getConfigManager().getConfig().getInt("auto-delete.days-inactive", 30);
            long maxInactiveMillis = days * 24L * 60L * 60L * 1000L;
            boolean dropAnchor = plugin.getConfigManager().getConfig().getBoolean("auto-delete.drop-anchor", false);

            long currentTime = System.currentTimeMillis();
            List<Claim> toDelete = new ArrayList<>();

            for (Claim claim : plugin.getClaimManager().getAllClaims()) {
                // Пропускаем арендные участки (у них собственный жизненный цикл в RentalExpirationTask)
                if (claim.isRentalPlot()) continue;

                // Пропускаем клановые территории
                if (claim.isClanTerritory() || claim.getClaimType() == Claim.ClaimType.CLAN) continue;

                // Пропускаем некорректные временные метки
                if (claim.getLastActive() <= 0) continue;

                if (currentTime - claim.getLastActive() > maxInactiveMillis) {
                    toDelete.add(claim);
                }
            }

            for (Claim claim : toDelete) {
                plugin.getClaimManager().removeClaimFromCache(claim.getId());
                plugin.getStorage().deleteClaimAsync(claim.getId());

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (claim.getAnchorLocation() != null && claim.getAnchorLocation().getWorld() != null) {
                        claim.getAnchorLocation().getBlock().setType(Material.AIR);

                        if (dropAnchor && claim.getWorld() != null) {
                            int currentSize = (int) Math.round(claim.getBoundingBox().getMaxX() - claim.getBoundingBox().getMinX());
                            me.lovelace.loveclaims.model.ClaimTier currentTier = plugin.getAnchorManager().getTierBySize(currentSize);
                            String tierId = currentTier != null ? currentTier.id() : "tier-1";

                            ItemStack anchor = plugin.getAnchorManager().createAnchorItem(tierId);
                            if (anchor != null) {
                                claim.getWorld().dropItemNaturally(claim.getAnchorLocation(), anchor);
                            }
                        }
                    }

                    plugin.getLogger().info("Deleted inactive claim (" + days + " days inactive) of " + claim.getOwnerUuid() + " (" + claim.getName() + ")");
                });
            }
        }, 1L, 30L, TimeUnit.MINUTES);
    }

    public void cancel() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    public boolean isCancelled() {
        return task == null || task.isCancelled();
    }
}