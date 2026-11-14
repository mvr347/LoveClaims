package me.lovelace.loveclaims.model;

import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClaimIsolationTest {
    private final UUID owner = UUID.randomUUID(), guest = UUID.randomUUID();
    private Claim claim(UUID player) {
        return new Claim(UUID.randomUUID(), null, null, player, null);
    }

    @Test void pendingInvitationHasNoRightsAndAccessAcceptanceDoesNotGrantBuild() {
        Claim a = claim(owner), b = claim(UUID.randomUUID());
        var invite = new ClaimInvitation(a.getId(), owner, owner, 100);
        assertTrue(invite.valid(a, 99));
        assertFalse(a.hasPermission(guest, ClaimPermission.BUILD));
        assertTrue(a.getMembers().isEmpty());
        a.setTrust(guest, TrustLevel.ACCESS);
        assertTrue(a.hasPermission(guest, ClaimPermission.INTERACT));
        assertFalse(a.hasPermission(guest, ClaimPermission.BUILD));
        assertFalse(b.hasPermission(guest, ClaimPermission.INTERACT));
        assertFalse(b.hasPermission(owner, ClaimPermission.BUILD));
        a.setTrust(guest, TrustLevel.BUILD);
        assertTrue(a.hasPermission(guest, ClaimPermission.BUILD));
        assertFalse(b.hasPermission(guest, ClaimPermission.BUILD));
    }

    @Test void readingPermissionsNeverAddsMembersAndReturnedSetsDoNotGrantRights() {
        Claim claim = claim(owner);
        claim.getPermissions(guest).add(ClaimPermission.MANAGE);
        assertTrue(claim.getMembers().isEmpty());
        claim.setTrust(guest, TrustLevel.ACCESS);
        claim.getPermissions(guest).add(ClaimPermission.BUILD);
        assertFalse(claim.hasPermission(guest, ClaimPermission.BUILD));
    }

    @Test void publicPermissionsDoNotMakeAVisitorAMemberOrManager() {
        Claim claim = claim(owner);
        claim.setDefaultPermissions(EnumSet.allOf(ClaimPermission.class));
        assertTrue(claim.hasPermission(guest, ClaimPermission.BUILD));
        assertEquals(TrustLevel.NONE, claim.getTrust(guest));
        assertFalse(claim.isManager(guest));
        assertFalse(claim.hasPermission(guest, ClaimPermission.MANAGE));
        assertTrue(claim.isManager(owner));
    }

    @Test void invitationsExpireAndCannotSurviveOwnershipOrAuthorityChanges() {
        Claim claim = claim(owner);
        claim.setTrust(guest, TrustLevel.MANAGER);
        var invite = new ClaimInvitation(claim.getId(), owner, guest, 100);
        assertTrue(invite.valid(claim, 99));
        assertFalse(invite.valid(claim, 100));
        assertFalse(invite.valid(null, 99));
        assertFalse(invite.valid(claim(owner), 99));
        claim.removePlayer(guest);
        assertFalse(invite.valid(claim, 99));
        claim.setTrust(guest, TrustLevel.MANAGER);
        claim.setOwnerUuid(UUID.randomUUID());
        assertFalse(invite.valid(claim, 99));
    }
}
