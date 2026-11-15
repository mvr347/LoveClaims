package me.lovelace.loveclaims.gui;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.List;

/**
 * The server's two-button confirmation menu (owner decision 2026-11-05): 27 slots, a resource-pack background in
 * the title and two big invisible button zones. Every slot of a zone carries the same item, so hovering anywhere
 * in the zone shows the button's name and lore. Without ItemsAdder (no background, no invisible item) callers
 * keep their 9-slot window. ItemsAdder is reached by reflection: it is an optional soft dependency.
 */
public final class ConfirmMenuLayout {

    public static final int SIZE = 27;
    public static final int[] CONFIRM_ZONE = {1, 2, 3, 10, 11, 12, 19, 20, 21};
    public static final int[] CANCEL_ZONE = {5, 6, 7, 14, 15, 16, 23, 24, 25};
    public static final String TITLE = "&f%img_offset_-8%%img_confirm_menu%";
    public static final String INVISIBLE_ITEM = "itemsadder-voidcore:invisible";

    private ConfirmMenuLayout() {
    }

    public static boolean isConfirm(int slot) {
        return contains(CONFIRM_ZONE, slot);
    }

    public static boolean isCancel(int slot) {
        return contains(CANCEL_ZONE, slot);
    }

    private static boolean contains(int[] zone, int slot) {
        for (int s : zone) if (s == slot) return true;
        return false;
    }

    /** The invisible item, or null when ItemsAdder is absent or does not know it. */
    @Nullable
    private static ItemStack invisibleItem() {
        if (!Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) return null;
        for (String candidate : new String[]{"dev.beer.itemsadder.api.CustomStack", "beer.devs.itemsadder.api.CustomStack",
                "dev.lone.itemsadder.api.CustomStack"}) {
            try {
                Class<?> clazz = Class.forName(candidate, true, Bukkit.getPluginManager().getPlugin("ItemsAdder").getClass().getClassLoader());
                Method getInstance = clazz.getMethod("getInstance", String.class);
                Object stack = getInstance.invoke(null, INVISIBLE_ITEM);
                if (stack == null) return null;
                Object item = clazz.getMethod("getItemStack").invoke(stack);
                return item instanceof ItemStack is ? is.clone() : null;
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // try the next package name of the ItemsAdder API
            }
        }
        return null;
    }

    /**
     * True when the background glyph and the invisible item can both be produced for this viewer. The title is
     * resolved here too: if the font image is missing or its placeholder stays unresolved, the invisible buttons
     * would have no background and no visible labels, so the visible 9-slot window must be kept.
     */
    public static boolean available(Player viewer) {
        return invisibleItem() != null && backgroundResolved(resolveBackground(viewer));
    }

    private static String resolveBackground(Player viewer) {
        var ia = Bukkit.getPluginManager().getPlugin("ItemsAdder");
        if (ia == null || !ia.isEnabled()) return null;
        for (String title : new String[]{TITLE, "&f%img_offset_-8%%img_confirm%"}) {
            String resolved = title;
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                resolved = PlaceholderAPI.setPlaceholders(viewer, title);
                if (backgroundResolved(resolved)) return resolved;
            }
            // Resolve the font image through ItemsAdder itself too: its PAPI expansion is optional.
            String marker = title.replaceAll("%img_([^%]+)%", ":$1:");
            for (String root : new String[]{"dev.lone.itemsadder.api", "dev.beer.itemsadder.api", "beer.devs.itemsadder.api"}) {
                try {
                    var fonts = Class.forName(root + ".FontImages.FontImageWrapper", true, ia.getClass().getClassLoader());
                    resolved = (String) fonts.getMethod("replaceFontImages", String.class).invoke(null, marker);
                    if (backgroundResolved(resolved)) return resolved;
                } catch (ReflectiveOperationException | LinkageError ignored) { }
            }
        }
        return null;
    }

    static boolean backgroundResolved(String resolved) {
        return resolved != null && !resolved.contains("%img_") && !resolved.contains("%offset")
                && !resolved.contains(":confirm") && !resolved.contains(":offset_") && !resolved.isBlank();
    }

    public static Component title(Player viewer) {
        String resolved = resolveBackground(viewer);
        return backgroundResolved(resolved) ? LegacyComponentSerializer.legacyAmpersand().deserialize(resolved) : Component.empty();
    }

    /** The invisible item with a name and lore, or null when ItemsAdder does not know it. */
    @Nullable
    public static ItemStack button(Component name, List<Component> lore) {
        ItemStack item = invisibleItem();
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore.stream().map(l -> l.decoration(TextDecoration.ITALIC, false)).toList());
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static void fill(Inventory inventory, ItemStack confirm, ItemStack cancel) {
        for (int slot : CONFIRM_ZONE) inventory.setItem(slot, confirm);
        for (int slot : CANCEL_ZONE) inventory.setItem(slot, cancel);
    }
}
