package me.lovelace.loveclaims.util;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Optional ItemsAdder items (soft dependency, reached by reflection). A config may name an ItemsAdder item
 * ("namespace:id") as the visual base of an anchor, core, gem or seal: its material, model and texture are taken,
 * while name, lore and the plugin's own tags are still applied by LoveClaims. A blank or unknown id, or a missing
 * ItemsAdder, silently keeps the vanilla item.
 */
public final class IaItems {
    private static final String[] ROOTS = {"dev.lone.itemsadder.api", "dev.beer.itemsadder.api", "beer.devs.itemsadder.api"};

    private IaItems() {
    }

    /** A fresh copy of the ItemsAdder item, or null when ItemsAdder or the id is not available. */
    @Nullable
    public static ItemStack get(@Nullable String id) {
        if (id == null || id.isBlank()) return null;
        var ia = Bukkit.getPluginManager().getPlugin("ItemsAdder");
        if (ia == null || !ia.isEnabled()) return null;
        for (String root : ROOTS) {
            try {
                Class<?> clazz = Class.forName(root + ".CustomStack", true, ia.getClass().getClassLoader());
                Method getInstance = clazz.getMethod("getInstance", String.class);
                Object stack = getInstance.invoke(null, id.trim());
                if (stack == null) return null;
                Object item = clazz.getMethod("getItemStack").invoke(stack);
                return item instanceof ItemStack is ? is.clone() : null;
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // try the next package name of the ItemsAdder API
            }
        }
        return null;
    }

    /** The ItemsAdder item for {@code id}, or {@code fallback} when there is none. */
    public static ItemStack orElse(@Nullable String id, ItemStack fallback) {
        ItemStack item = get(id);
        return item != null ? item : fallback;
    }
}
