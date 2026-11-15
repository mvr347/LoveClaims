package me.lovelace.loveclaims.task;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.api.ReleaseReason;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.model.RentalTerm;
import me.lovelace.loveclaims.util.CoinFormat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public class RentalExpirationTask {
  private final LoveClaims plugin;
  private BukkitTask task;

  /** Minimum gap between two automatic renewal attempts for one trade point. */
  private static final long RENEW_RETRY_MILLIS = 10 * 60_000L;

  /**
   * Last automatic renewal attempt per trade point; entries are dropped once the point has no
   * tenant.
   */
  private final Map<UUID, Long> lastRenewAttempt = new HashMap<>();

  public RentalExpirationTask(LoveClaims plugin) {
    this.plugin = plugin;
  }

  public void start() {
    if (!isCancelled()) return;
    // Decisions and live state changes are serialized with payments, transfers and GUI clicks.
    // Storage writes remain asynchronous.
    task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1200L, 1200L);
  }

  private void tick() {
    long now = System.currentTimeMillis();
    long taxInterval = plugin.getRentalManager().getTaxDays() * 86400000L;
    var claims = new java.util.ArrayList<>(plugin.getClaimManager().getAllClaims());
    var activePoints = new HashSet<UUID>();
    for (Claim claim : claims) {
      if (!claim.isRentalPlot()) continue;
      // A callback processing an earlier plot may have removed another one.
      if (plugin.getClaimManager().getClaimById(claim.getId()).orElse(null) != claim) continue;
      try {
        if (claim.isTradePoint()) {
          if (RentalTerm.hasAssignedTenant(claim)) activePoints.add(claim.getId());
          processTradePoint(claim, now);
          continue;
        }
        if (RentalTerm.expired(claim, now, 0)) {
          terminateRent(claim, ReleaseReason.EXPIRED);
          continue;
        }
        if (!RentalTerm.hasAssignedTenant(claim) || !claim.isRented()) continue;
        if (claim.getLastTaxTime() == 0) {
          claim.setLastTaxTime(now);
          plugin.getStorage().saveClaimAsync(claim);
          continue;
        }
        if (now - claim.getLastTaxTime() < taxInterval) continue;
        long taxAmount =
            Math.round(
                claim.getRentalPrice() * (plugin.getRentalManager().getTaxPercentage() / 100.0));
        Player renter = Bukkit.getPlayer(claim.getOwnerUuid());
        if (renter == null || !plugin.getRentalManager().paymentsAvailable()) continue;
        if (plugin.getRentalManager().hasFunds(renter, claim, taxAmount)) {
          if (plugin.getRentalManager().charge(renter, claim, taxAmount)) {
            claim.setLastTaxTime(now);
            plugin.getStorage().saveClaimAsync(claim);
            renter.sendMessage(
                plugin
                    .getConfigManager()
                    .getMessage("rental-tax-paid", "amount", CoinFormat.formatGlyphs(taxAmount)));
          }
        } else {
          terminateRent(claim, ReleaseReason.EVICTED);
          renter.sendMessage(
              plugin
                  .getConfigManager()
                  .getMessage("rental-tax-failed", "amount", CoinFormat.formatGlyphs(taxAmount)));
        }
      } catch (Exception ex) {
        plugin
            .getLogger()
            .log(
                java.util.logging.Level.SEVERE,
                "RentalExpirationTask: failed to process plot " + claim.getId(),
                ex);
      }
    }
    lastRenewAttempt.keySet().retainAll(activePoints);
  }

  public void cancel() {
    if (task != null && !task.isCancelled()) {
      task.cancel();
    }
    lastRenewAttempt.clear();
  }

  public boolean isCancelled() {
    return task == null || task.isCancelled();
  }

  /**
   * A trade point pays rent per period. Shortly before the term ends the rent is renewed
   * automatically (from the till kept in LoveShops, else from the online tenant's pocket); if that
   * fails the point stays with the tenant for the grace period - the shop is closed by LoveShops
   * meanwhile - and is only then released. Runs on the main thread.
   */
  private void processTradePoint(Claim claim, long now) {
    var rentals = plugin.getRentalManager();
    if (!RentalTerm.hasAssignedTenant(claim)) {
      lastRenewAttempt.remove(claim.getId());
      return;
    }
    long end = claim.getRentalEndTime();

    if (RentalTerm.expired(claim, now, rentals.getTradePointGraceMillis())) {
      lastRenewAttempt.remove(claim.getId());
      terminateRent(claim, ReleaseReason.EXPIRED);
      return;
    }

    if (end > now && end - now <= rentals.getTradePointWarnMillis() && !claim.isExpiryWarned()) {
      rentals.warnExpiry(claim);
    }

    if (rentals.isTradePointAutoRenew() && end - now <= rentals.getTradePointRenewLeadMillis()) {
      Long last = lastRenewAttempt.get(claim.getId());
      if (last != null && now - last < RENEW_RETRY_MILLIS) return;
      lastRenewAttempt.put(claim.getId(), now);
      if (rentals.renewTradePoint(claim)) {
        lastRenewAttempt.remove(claim.getId());
      }
    }
  }

  private void terminateRent(Claim claim, ReleaseReason reason) {
    plugin.getRentalManager().release(claim, reason);
  }
}
