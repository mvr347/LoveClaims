package me.lovelace.loveclaims.listener;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProtectionIntrusionTest {
    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    @Test void sameClaimOrWildernessIsFine() {
        assertFalse(ProtectionListener.intrudes(a, a));
        assertFalse(ProtectionListener.intrudes(null, null));
        assertFalse(ProtectionListener.intrudes(a, null));
    }
    @Test void crossingIntoAnotherClaimIsBlocked() {
        assertTrue(ProtectionListener.intrudes(a, b));
        assertTrue(ProtectionListener.intrudes(null, b));
    }
}
