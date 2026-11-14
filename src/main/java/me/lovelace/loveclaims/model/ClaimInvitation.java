package me.lovelace.loveclaims.model;

import java.util.Objects;
import java.util.UUID;

/** An invitation is an offer only; accepting it must recheck the current claim and inviter. */
public record ClaimInvitation(UUID claimId, UUID owner, UUID inviter, long expiresAt) {
    public boolean valid(Claim claim, long now) {
        return claim != null && now < expiresAt && claimId.equals(claim.getId())
                && Objects.equals(owner, claim.getOwnerUuid()) && claim.isManager(inviter)
                && !claim.isTradePoint() && !claim.isClanTerritory();
    }
}
