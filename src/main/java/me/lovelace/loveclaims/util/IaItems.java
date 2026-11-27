package me.lovelace.loveclaims.util;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
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

    /** Step by step report of a lookup, for /loveclaimsadmin iacheck (why a configured id gives no item). */
    public static java.util.List<String> diagnose(@Nullable String id) {
        java.util.List<String> out = new java.util.ArrayList<>();
        var ia = Bukkit.getPluginManager().getPlugin("ItemsAdder");
        if (ia == null) {
            out.add("ItemsAdder: плагин не найден");
            return out;
        }
        out.add("ItemsAdder: " + (ia.isEnabled() ? "включён" : "ВЫКЛЮЧЕН") + ", версия "
                + ia.getPluginMeta().getVersion());
        if (id == null || id.isBlank()) {
            out.add("id: не задан");
            return out;
        }
        for (String root : ROOTS) {
            try {
                Class<?> clazz = Class.forName(root + ".CustomStack", true, ia.getClass().getClassLoader());
                out.add("API " + root + ": класс найден");
                Object stack = clazz.getMethod("getInstance", String.class).invoke(null, id.trim());
                if (stack == null) {
                    out.add("предмет '" + id + "': НЕ найден в ItemsAdder (не зарегистрирован или ещё не загружен)");
                } else {
                    Object item = clazz.getMethod("getItemStack").invoke(stack);
                    out.add("предмет '" + id + "': найден, материал "
                            + (item instanceof ItemStack is ? is.getType() : "?"));
                }
                return out;
            } catch (ReflectiveOperationException | LinkageError e) {
                out.add("API " + root + ": " + e.getClass().getSimpleName());
            }
        }
        out.add("ни один пакет API ItemsAdder не подошёл");
        return out;
    }

    private static volatile boolean modelFallback = false;

    /** Set from config itemsadder.model-fallback when the configuration is loaded. */
    public static void setModelFallback(boolean enabled) {
        modelFallback = enabled;
    }

    /**
     * The ItemsAdder item for {@code id}, or {@code fallback} when there is none. With the opt-in
     * {@code itemsadder.model-fallback} a missing item gets the id as its vanilla item model instead: that shows the
     * texture only when the resource pack really has a model under exactly this key, otherwise the item would look
     * broken, so it is off by default.
     */
    public static ItemStack orElse(@Nullable String id, ItemStack fallback) {
        ItemStack item = get(id);
        if (item != null) return item;
        if (!modelFallback || id == null || id.isBlank()) return fallback;
        NamespacedKey model = NamespacedKey.fromString(id.trim().toLowerCase(java.util.Locale.ROOT));
        if (model == null) return fallback;
        ItemStack copy = fallback.clone();
        copy.editMeta(meta -> meta.setItemModel(model));
        return copy;
    }
}
