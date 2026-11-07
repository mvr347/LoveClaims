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

    @Test
    void rentalPlotsAndTradePointsHaveNoAnchor() {
        assertTrue(AnchorListener.usesAnchor(false, false), "a player's claim");
        assertFalse(AnchorListener.usesAnchor(false, true), "the floor under a trader is not an anchor");
        assertFalse(AnchorListener.usesAnchor(true, false), "clan territory is LoveClans' business");
    }
}
