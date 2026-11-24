package me.lovelace.loveclaims.gui;

import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.model.Claim;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SettingsGUITest {
    ServerMock server;
    LoveClaims plugin;
    PlayerMock owner, guest;
    Claim claim;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        var world = server.addSimpleWorld("world");
        MockBukkit.createMockPlugin("LoveCore");
        plugin = MockBukkit.load(LoveClaims.class);
        owner = server.addPlayer("Owner");
        guest = server.addPlayer("Guest");
        claim = new Claim(UUID.randomUUID(), world, new BoundingBox(0, 0, 0, 10, 100, 10), owner.getUniqueId(), owner.getLocation());
        plugin.getClaimManager().addClaimToCache(claim);
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    private static String plain(net.kyori.adventure.text.Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    @Test
    void unavailableButtonsKeepTheirSlotWithStatusAndReason() {
        var inv = new SettingsGUI(plugin, guest, claim).getInventory();
        for (int slot : new int[]{12, 14, 16}) {
            var meta = inv.getItem(slot).getItemMeta();
            assertTrue(plain(meta.displayName()).contains("Недоступно"), "slot " + slot);
            var lore = meta.lore().stream().map(SettingsGUITest::plain).toList();
            assertTrue(lore.stream().anyMatch(l -> l.startsWith("✕")), "status line, slot " + slot);
            assertTrue(lore.stream().anyMatch(l -> l.startsWith("Причина:")), "reason line, slot " + slot);
            assertTrue(lore.stream().anyMatch(l -> l.contains("(заблокировано)")), "blocked click, slot " + slot);
        }
    }

    @Test
    void ownerSeesActiveButtonsWithStateInTheName() {
        var inv = new SettingsGUI(plugin, owner, claim).getInventory();
        assertEquals(Material.PLAYER_HEAD, inv.getItem(10).getType());
        assertTrue(plain(inv.getItem(10).getItemMeta().displayName()).matches("(?s).*(Включено|Выключено).*"));
        assertFalse(plain(inv.getItem(12).getItemMeta().displayName()).contains("Недоступно"));
        // side walls stay empty, footer has no empty slot
        assertNull(inv.getItem(9));
        assertNull(inv.getItem(17));
        for (int i = 18; i < 27; i++) assertNotNull(inv.getItem(i), "footer slot " + i);
    }
}
