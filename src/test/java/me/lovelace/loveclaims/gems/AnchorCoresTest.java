package me.lovelace.loveclaims.gems;

import me.lovelace.loveclaims.LoveClaims;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.*;

class AnchorCoresTest {
    ServerMock server;
    LoveClaims plugin;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        MockBukkit.createMockPlugin("LoveCore");
        plugin = MockBukkit.load(LoveClaims.class);
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    @Test
    void coreRoundTripsTierAndIsNotConfusedWithPlainMaterial() {
        for (int tier = 1; tier <= 3; tier++) {
            var core = AnchorCores.create(plugin, tier);
            assertEquals(tier, AnchorCores.readTier(plugin, core).orElseThrow());
            assertTrue(AnchorCores.isCore(plugin, core));
        }
        // a plain amethyst shard and copper ingot are not cores
        assertFalse(AnchorCores.isCore(plugin, new org.bukkit.inventory.ItemStack(Material.AMETHYST_SHARD)));
        assertFalse(AnchorCores.isCore(plugin, new org.bukkit.inventory.ItemStack(Material.COPPER_INGOT)));
    }

    @Test
    void crackedCoreAndFusedGemTravelWithTheAnchorItem() {
        var gem = new GemStats("domain", GemRarity.BASIC, 1, 6, 0, 0.0, 0.0, 0.0, false, java.util.Map.of(), null);
        var item = plugin.getAnchorManager().createAnchorItem("tier-2", null, gem, true);
        assertTrue(plugin.getAnchorManager().readBroken(item));
        assertEquals("domain", plugin.getAnchorManager().readFusedGem(item).orElseThrow().gemId());
        // a plain anchor carries neither
        var plain = plugin.getAnchorManager().createAnchorItem("tier-2");
        assertFalse(plugin.getAnchorManager().readBroken(plain));
        assertTrue(plugin.getAnchorManager().readFusedGem(plain).isEmpty());
        // the item is still recognised as an anchor of the right tier
        assertEquals("tier-2", plugin.getAnchorManager().getTierFromItem(item).orElseThrow().id());
    }

    @Test
    void onlyOwnUpgradeRecipesCountAsCoreRecipes() {
        assertTrue(AnchorCores.isCoreRecipe(plugin, new NamespacedKey(plugin, "core_craft_2")));
        assertFalse(AnchorCores.isCoreRecipe(plugin, new NamespacedKey(plugin, "gem_craft_domain")));
        assertFalse(AnchorCores.isCoreRecipe(plugin, NamespacedKey.minecraft("spyglass")));
        assertFalse(AnchorCores.isCoreRecipe(plugin, null));
    }
}
