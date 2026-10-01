package me.lovelace.loveclaims.task;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.api.ReleaseReason;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.util.CoinFormat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class RentalExpirationTask {
    private final LoveClaims plugin;
    private ScheduledTask task;

    /** Minimum gap between two automatic renewal attempts for one trade point. */
    private static final long RENEW_RETRY_MILLIS = 10 * 60_000L;
    /** Last automatic renewal attempt per trade point; entries are dropped once the point has no tenant. */
    private final Map<UUID, Long> lastRenewAttempt = new ConcurrentHashMap<>();

    public RentalExpirationTask(LoveClaims plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin, scheduler -> {
            long now = System.currentTimeMillis();
            long taxInterval = plugin.getRentalManager().getTaxDays() * 86400000L;

            for (Claim claim : plugin.getClaimManager().getAllClaims()) {
                if (!claim.isRentalPlot()) continue;

                if (claim.isTradePoint()) {
                    processTradePoint(claim, now);
                    continue;
                }

                // Каждый плот обрабатывается независимо: необработанное исключение на ОДНОМ
                // плоте (например, из-за повреждённых данных) раньше прерывало бы весь foreach и
                // все последующие плоты пропускали бы сбор аренды/налога в этом тике - жилец мог
                // бы систематически избегать оплаты, если сортировка коллекции стабильно ставит
                // "проблемный" плот перед его собственным.
                try {
                    // 1. Check expiration
                    if (claim.isRented() && claim.getRentalEndTime() < now) {
                        Bukkit.getScheduler().runTask(plugin, () -> terminateRent(claim, ReleaseReason.EXPIRED));
                        continue;
                    }

                    // 2. Check taxes
                    if (claim.isRented()) {
                        if (claim.getLastTaxTime() == 0) {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                claim.setLastTaxTime(now);
                                plugin.getStorage().saveClaimAsync(claim);
                            });
                        }

                        if (now - claim.getLastTaxTime() >= taxInterval) {
                            long taxAmount = Math.round(claim.getRentalPrice() * (plugin.getRentalManager().getTaxPercentage() / 100.0));
                            UUID renterId = claim.getOwnerUuid();

                            // Bukkit.getPlayer() and everything after it touch live server state, so the
                            // lookup happens on the main thread, not on this async scheduler thread.
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                Player renter = Bukkit.getPlayer(renterId);
                                if (renter == null) return;
                                if (plugin.getCurrencyManager().hasEnough(renter, taxAmount)) {
                                    if (plugin.getCurrencyManager().takeCurrency(renter, taxAmount)) {
                                        claim.setLastTaxTime(now);
                                        plugin.getStorage().saveClaimAsync(claim);
                                        renter.sendMessage(plugin.getConfigManager().getMessage("rental-tax-paid", "amount", CoinFormat.formatGlyphs(taxAmount)));
                                    }
                                } else {
                                    terminateRent(claim, ReleaseReason.EVICTED);
                                    renter.sendMessage(plugin.getConfigManager().getMessage("rental-tax-failed", "amount", CoinFormat.formatGlyphs(taxAmount)));
                                }
                            });
                        }
                    }
                } catch (Exception ex) {
                    plugin.getLogger().severe("RentalExpirationTask: failed to process plot " + claim.getId() + ": " + ex.getMessage());
                }
            }
        }, 1L, 1L, TimeUnit.MINUTES);
    }

    public void cancel() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    public boolean isCancelled() {
        return task == null || task.isCancelled();
    }

    /**
     * A trade point pays rent per period. Shortly before the term ends the rent is renewed
     * automatically (from the till kept in LoveShops, else from the online tenant's pocket); if
     * that fails the point stays with the tenant for the grace period - the shop is closed by
     * LoveShops meanwhile - and is only then released. Runs on the async scheduler: everything
     * that touches the world or players is handed to the main thread.
     */
    private void processTradePoint(Claim claim, long now) {
        var rentals = plugin.getRentalManager();
        if (!rentals.hasTenant(claim)) {
            lastRenewAttempt.remove(claim.getId());
            return;
        }
        long end = claim.getRentalEndTime();

        if (now > end + rentals.getTradePointGraceMillis()) {
            lastRenewAttempt.remove(claim.getId());
            Bukkit.getScheduler().runTask(plugin, () -> terminateRent(claim, ReleaseReason.EXPIRED));
            return;
        }

        if (end > now && end - now <= rentals.getTradePointWarnMillis() && !claim.isExpiryWarned()) {
            Bukkit.getScheduler().runTask(plugin, () -> rentals.warnExpiry(claim));
        }

        if (rentals.isTradePointAutoRenew() && end - now <= rentals.getTradePointRenewLeadMillis()) {
            Long last = lastRenewAttempt.get(claim.getId());
            if (last != null && now - last < RENEW_RETRY_MILLIS) return;
            lastRenewAttempt.put(claim.getId(), now);
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Re-check on the main thread: a manual payment may have extended the term meanwhile.
                if (!rentals.hasTenant(claim)) return;
                if (claim.getRentalEndTime() - System.currentTimeMillis() > rentals.getTradePointRenewLeadMillis()) return;
                if (rentals.renewTradePoint(claim)) {
                    lastRenewAttempt.remove(claim.getId());
                }
            });
        }
    }

    private void terminateRent(Claim claim, ReleaseReason reason) {
        plugin.getRentalManager().release(claim, reason);
    }
}
