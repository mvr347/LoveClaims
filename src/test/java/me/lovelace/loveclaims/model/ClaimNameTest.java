package me.lovelace.loveclaims.model;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ClaimNameTest {
    @Test void legacyNullNamesBecomeNull() {
        assertNull(Claim.sanitizeName("Приват null"));
        assertNull(Claim.sanitizeName("null"));
        assertNull(Claim.sanitizeName("  "));
        assertNull(Claim.sanitizeName(null));
    }
    @Test void realNamesAreKept() {
        assertEquals("Приват Steve", Claim.sanitizeName("Приват Steve"));
        assertEquals("Nullhaven", Claim.sanitizeName("Nullhaven"));
    }
}
