package me.lovelace.loveclaims.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;

class IaItemsTest {
    @BeforeEach
    void setup() {
        MockBukkit.mock();
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    @Test
    void withoutItemsAdderOrWithABlankIdTheVanillaItemStays() {
        ItemStack fallback = new ItemStack(Material.EMERALD);
        assertNull(IaItems.get(null));
        assertNull(IaItems.get(" "));
        assertNull(IaItems.get("claims:anchor_1"));
        assertSame(fallback, IaItems.orElse("", fallback));
        assertSame(fallback, IaItems.orElse("not a key!", fallback));
        // off by default: a missing ItemsAdder item keeps the vanilla look
        assertSame(fallback, IaItems.orElse("claims:anchor_1", fallback));
        // opt-in: the id becomes the vanilla item model
        IaItems.setModelFallback(true);
        try {
            ItemStack modeled = IaItems.orElse("claims:anchor_1", fallback);
            assertEquals(Material.EMERALD, modeled.getType());
            assertEquals("claims:anchor_1", modeled.getItemMeta().getItemModel().asString());
            assertFalse(fallback.getItemMeta().hasItemModel(), "the fallback stays untouched");
        } finally {
            IaItems.setModelFallback(false);
        }
    }
}
