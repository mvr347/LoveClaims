package me.lovelace.loveclaims.util;

import me.lovelace.loveclaims.LoveClaims;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.List;

/** Menu texts from lang.yml without the default italics of item names and lore. */
public final class GuiText {
    private GuiText() {
    }

    public static Component name(LoveClaims plugin, String key, String... placeholders) {
        return plugin.getConfigManager().getComponent(key, placeholders).decoration(TextDecoration.ITALIC, false);
    }

    public static List<Component> lore(LoveClaims plugin, String key, String... placeholders) {
        return plugin.getConfigManager().getHelpMessage(key, placeholders).stream()
                .map(c -> c.decoration(TextDecoration.ITALIC, false)).toList();
    }

    /** Player-written text (claim names) must not inject tags into a menu. */
    public static String esc(String text) {
        return MiniMessage.miniMessage().escapeTags(text == null ? "" : text);
    }
}
