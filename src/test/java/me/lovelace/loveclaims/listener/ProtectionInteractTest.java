package me.lovelace.loveclaims.listener;

import static org.junit.jupiter.api.Assertions.*;

import me.lovelace.loveclaims.model.ClaimPermission;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class ProtectionInteractTest {

    @Test
    void containersNeedTheContainersPermissionNotBuild() {
        for (Material m : new Material[] {Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL, Material.HOPPER,
                Material.FURNACE, Material.WHITE_SHULKER_BOX, Material.DISPENSER}) {
            assertEquals(ClaimPermission.CONTAINERS, ProtectionListener.requiredPermission(m), m.name());
        }
    }

    @Test
    void doorsButtonsAndLeversNeedInteract() {
        for (Material m : new Material[] {Material.OAK_DOOR, Material.IRON_TRAPDOOR, Material.STONE_BUTTON, Material.LEVER}) {
            assertEquals(ClaimPermission.INTERACT, ProtectionListener.requiredPermission(m), m.name());
        }
    }

    @Test
    void everythingElseCountsAsBuilding() {
        for (Material m : new Material[] {Material.STONE, Material.CRAFTING_TABLE, Material.CAKE, Material.ANVIL}) {
            assertEquals(ClaimPermission.BUILD, ProtectionListener.requiredPermission(m), m.name());
        }
    }

    @Test
    void potionsMilkAndHoneyAreItemUseWithoutAskingTheRegistry() {
        assertTrue(ProtectionListener.isItemUse(Material.POTION));
        assertTrue(ProtectionListener.isItemUse(Material.MILK_BUCKET));
        assertTrue(ProtectionListener.isItemUse(Material.HONEY_BOTTLE));
        assertFalse(ProtectionListener.isItemUse(null));
    }

    @Test
    void protectedBlocksDoNotDisableVanillaWeaponsInHand() {
        for (Material material : new Material[]{Material.TRIDENT, Material.BOW, Material.CROSSBOW, Material.SHIELD})
            assertTrue(ProtectionListener.isItemUse(material));
        for (Material material : Material.values())
            if (material.name().endsWith("_SPEAR")) assertTrue(ProtectionListener.isItemUse(material));
        assertFalse(ProtectionListener.isWeaponUse(Material.LAVA_BUCKET));
        assertFalse(ProtectionListener.isWeaponUse(Material.FLINT_AND_STEEL));
        assertFalse(ProtectionListener.isWeaponUse(Material.STONE));
    }
}
