package me.lovelace.loveclaims.model;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ClaimNameTest {
    @Test void legacyNullNamesBecomeNull() {
        assertNull(Claim.sanitizeName("Клейм null"));
        assertNull(Claim.sanitizeName("null"));
        assertNull(Claim.sanitizeName("  "));
        assertNull(Claim.sanitizeName(null));
    }
    @Test void realNamesAreKept() {
        assertEquals("Клейм Steve", Claim.sanitizeName("Клейм Steve"));
        assertEquals("Nullhaven", Claim.sanitizeName("Nullhaven"));
    }
}
