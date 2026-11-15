package me.lovelace.loveclaims.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimSaveVersionTest {
  private Claim claim() {
    return new Claim(UUID.randomUUID(), null, null, UUID.randomUUID(), null);
  }

  @Test
  void commitCannotDiscardChangesMadeAfterPreparingTheRow() {
    Claim claim = claim();
    long rowVersion = claim.getModificationVersion();
    claim.setRentalEndTime(200);
    claim.markSaved(rowVersion);
    assertTrue(claim.isModified());
    claim.markSaved(claim.getModificationVersion());
    assertFalse(claim.isModified());
  }

  @Test
  void unchangedCommitAndCompatibilitySetterKeepWorking() {
    Claim claim = claim();
    assertTrue(claim.isModified());
    claim.markSaved(claim.getModificationVersion());
    assertFalse(claim.isModified());
    claim.setName("Новое имя");
    assertTrue(claim.isModified());
    claim.setModified(false);
    assertFalse(claim.isModified());
    claim.setModified(true);
    assertTrue(claim.isModified());
  }
}
