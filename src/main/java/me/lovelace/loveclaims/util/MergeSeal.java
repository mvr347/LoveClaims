package me.lovelace.loveclaims.util;

import me.lovelace.loveclaims.LoveClaims;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** Merge seal: the special item needed to merge claims of two different owners (spent once). */
public final class MergeSeal {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private MergeSeal() {
    }

    private static NamespacedKey key(LoveClaims plugin) {
        return new NamespacedKey(plugin, "merge_seal");
    }

    public static ItemStack create(LoveClaims plugin) {
        ItemStack item = new ItemStack(Material.NAUTILUS_SHELL);
        item.editMeta(meta -> {
            meta.displayName(noItalic(MM.deserialize("<gold>Печать слияния <dark_gray>|</dark_gray> <yellow>Приваты</yellow>")));
            meta.lore(List.of(
                    noItalic(MM.deserialize("<dark_gray>Особый предмет")),
                    Component.empty(),
                    noItalic(MM.deserialize("<gray>Нужна, чтобы объединить свой приват")),
                    noItalic(MM.deserialize("<gray>с приватом другого владельца.")),
                    noItalic(MM.deserialize("<dark_gray><st>─────────────────────</st></dark_gray>")),
                    noItalic(MM.deserialize("<green>ЛКМ <dark_gray>— <gray>по привату в меню слияния"))));
            meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    public static boolean is(LoveClaims plugin, ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(key(plugin), PersistentDataType.BYTE);
    }

    public static boolean has(LoveClaims plugin, Player player) {
        for (ItemStack it : player.getInventory().getContents()) {
            if (is(plugin, it)) return true;
        }
        return false;
    }

    /** Removes one seal from the player's inventory; false when there is none. */
    public static boolean consume(LoveClaims plugin, Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (!is(plugin, it)) continue;
            if (it.getAmount() > 1) it.setAmount(it.getAmount() - 1);
            else player.getInventory().setItem(i, null);
            return true;
        }
        return false;
    }

    private static Component noItalic(Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }
}
