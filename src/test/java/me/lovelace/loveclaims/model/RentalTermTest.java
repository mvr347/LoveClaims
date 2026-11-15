package me.lovelace.loveclaims.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RentalTermTest {
  private final UUID tenant = UUID.randomUUID();

  private Claim rented(long end) {
    Claim claim = new Claim(UUID.randomUUID(), null, null, tenant, null);
    claim.setParentClaimId(UUID.randomUUID());
    claim.setRentalEndTime(end);
    return claim;
  }

  @Test
  void expiredClassicRentalIsReleasedEvenThoughIsRentedIsFalse() {
    Claim claim = rented(100);
    assertFalse(claim.isRented());
    assertFalse(RentalTerm.expired(claim, 99, 0));
    assertTrue(RentalTerm.expired(claim, 100, 0));
    assertTrue(RentalTerm.expired(claim, 101, 0));
  }

  @Test
  void tradePointKeepsTenantUntilGraceEndsButThenExpires() {
    Claim claim = rented(100);
    claim.setPlotType(PlotType.TRADE_POINT);
    assertTrue(RentalTerm.hasAssignedTenant(claim));
    assertFalse(RentalTerm.expired(claim, 119, 20));
    assertTrue(RentalTerm.expired(claim, 120, 20));
    assertTrue(RentalTerm.expired(claim, 500, 20));
  }

  @Test
  void freePlotsAndLandlordsAreNeverEvicted() {
    Claim claim = rented(100);
    claim.setOwnerUuid(claim.getParentClaimId());
    assertFalse(RentalTerm.expired(claim, 1000, 0));
    claim.setOwnerUuid(tenant);
    claim.setRentalEndTime(0);
    assertFalse(RentalTerm.expired(claim, 1000, 0));
    claim.setParentClaimId(null);
    assertFalse(RentalTerm.hasAssignedTenant(claim));
  }

  @Test
  void staleConfirmationCannotReleaseAnotherTenantOrRenewedTerm() {
    Claim claim = rented(100);
    RentalTerm confirmation = RentalTerm.capture(claim);
    assertTrue(confirmation.canAbandon(claim, tenant));
    assertFalse(confirmation.canAbandon(claim, UUID.randomUUID()));
    claim.setOwnerUuid(UUID.randomUUID());
    assertFalse(confirmation.canAbandon(claim, tenant));
    claim.setOwnerUuid(tenant);
    claim.setRentalEndTime(200);
    assertFalse(confirmation.canAbandon(claim, tenant));
  }

  @Test
  void removedOrReloadedPlotInvalidatesConfirmation() {
    Claim claim = rented(100);
    RentalTerm confirmation = RentalTerm.capture(claim);
    assertFalse(confirmation.canAbandon(null, tenant));
    assertFalse(confirmation.canAbandon(rented(100), tenant));
    claim.setRentalEndTime(0);
    assertFalse(confirmation.canAbandon(claim, tenant));
  }

  @Test
  void graceDeadlineCannotOverflowIntoThePast() {
    Claim claim = rented(Long.MAX_VALUE - 10);
    assertFalse(RentalTerm.expired(claim, Long.MAX_VALUE, 20));
  }
}
