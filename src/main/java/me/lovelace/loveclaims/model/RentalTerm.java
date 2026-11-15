package me.lovelace.loveclaims.model;

import java.util.UUID;

/** A confirmation is valid only for the live plot and rental term it was opened for. */
public record RentalTerm(Claim plot, UUID tenant, long end) {
  public static RentalTerm capture(Claim plot) {
    return new RentalTerm(plot, plot.getOwnerUuid(), plot.getRentalEndTime());
  }

  public boolean canAbandon(Claim current, UUID actor) {
    return current == plot
        && hasAssignedTenant(current)
        && tenant.equals(actor)
        && tenant.equals(current.getOwnerUuid())
        && end == current.getRentalEndTime();
  }

  /** Ownership survives expiration until release has actually been persisted. */
  public static boolean hasAssignedTenant(Claim plot) {
    return plot != null
        && plot.isRentalPlot()
        && plot.getOwnerUuid() != null
        && !plot.getOwnerUuid().equals(plot.getParentClaimId())
        && plot.getRentalEndTime() > 0;
  }

  public static boolean expired(Claim plot, long now, long graceMillis) {
    long end = plot.getRentalEndTime();
    // Subtract instead of adding the grace period to avoid timestamp overflow.
    return hasAssignedTenant(plot) && now >= end && now - end >= Math.max(0, graceMillis);
  }
}
