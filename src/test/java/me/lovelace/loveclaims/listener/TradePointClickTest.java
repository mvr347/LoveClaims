package me.lovelace.loveclaims.listener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradePointClickTest {
    @Test
    void onlyAPlayersClickOnTheSignIsRefused() {
        assertTrue(RentalInteractListener.refusesTradePointClick(false, true));
        assertFalse(RentalInteractListener.refusesTradePointClick(false, false), "placing a block elsewhere");
        assertFalse(RentalInteractListener.refusesTradePointClick(true, true), "administrators rebuild the point");
        assertFalse(RentalInteractListener.refusesTradePointClick(true, false));
    }
}
