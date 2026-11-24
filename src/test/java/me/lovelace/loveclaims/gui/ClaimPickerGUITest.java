package me.lovelace.loveclaims.gui;

import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.model.Claim;
import org.bukkit.Material;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClaimPickerGUITest {
    ServerMock server;
    LoveClaims plugin;
    PlayerMock owner;
    org.bukkit.World world;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        MockBukkit.createMockPlugin("LoveCore");
        plugin = MockBukkit.load(LoveClaims.class);
        owner = server.addPlayer("Owner");
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    private List<Claim> claims(int n) {
        List<Claim> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            org.bukkit.Location anchor = new org.bukkit.Location(world, i * 100, 64, 0);
            Claim c = new Claim(UUID.randomUUID(), world,
                    new BoundingBox(i * 100 - 5, 0, -5, i * 100 + 5, 100, 5), owner.getUniqueId(), anchor);
            c.setName("Дом " + i);
            plugin.getClaimManager().addClaimToCache(c);
            list.add(c);
        }
        return list;
    }

    @Test
    void threeClaimsUseCentredRowInside27SlotMenuWithEmptyWallsAndFooter() {
        var inv = new ClaimPickerGUI(plugin, owner, claims(3)).getInventory();
        assertEquals(27, inv.getSize());
        for (int slot : new int[]{11, 13, 15}) assertEquals(Material.PLAYER_HEAD, inv.getItem(slot).getType());
        assertNull(inv.getItem(10));
        assertNull(inv.getItem(12));
        assertNull(inv.getItem(14));
        assertNull(inv.getItem(16));
        for (int wall : new int[]{9, 17}) assertNull(inv.getItem(wall), "side wall " + wall + " must be empty");
        // Footer: Close always, Back absent in root menu -> glass, no AIR in header/footer
        assertEquals(Material.PLAYER_HEAD, inv.getItem(26).getType());
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, inv.getItem(25).getType());
        for (int i = 0; i < 9; i++) assertNotNull(inv.getItem(i), "header slot " + i + " must not be empty");
        for (int i = 18; i < 27; i++) assertNotNull(inv.getItem(i), "footer slot " + i + " must not be empty");
    }

    @Test
    void sizeGrowsWithClaimCountAndPaginationIsOnly54() {
        assertEquals(27, ClaimPickerGUI.sizeFor(7));
        assertEquals(36, ClaimPickerGUI.sizeFor(8));
        assertEquals(36, ClaimPickerGUI.sizeFor(14));
        assertEquals(54, ClaimPickerGUI.sizeFor(15));
    }
}
