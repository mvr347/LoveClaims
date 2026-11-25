package me.lovelace.loveclaims.gui;

import dev.lovelace.lovecore.api.economy.MoneyConfig;
import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.util.CoinFormat;
import me.lovelace.loveclaims.util.GuiText;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

import static me.lovelace.loveclaims.textures.HeadTextures.*;

/**
 * The craftsman turns an improved gem (made by the player on a workbench) into a modified one for coins.
 * The player clicks the gem in their own inventory; a confirmation follows. Opened from the anchor master's
 * menu, so Back returns there. 27 slots, one content button in the centre of the work row.
 */
public class GemModifyGUI extends AbstractGUI {
    private final LoveClaims plugin;
    private final Player viewer;

    public GemModifyGUI(LoveClaims plugin, Player viewer) {
        super(27, GuiText.name(plugin, "gem-modify.title"));
        this.plugin = plugin;
        this.viewer = viewer;
        setMenuItems();
    }

    static long cost(LoveClaims plugin) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("gems.modify");
        if (sec == null) return 0L;
        try {
            return MoneyConfig.get(sec, "cost", 0L);
        } catch (Throwable t) {
            return 0L;
        }
    }

    static String costText(LoveClaims plugin, long coins) {
        return coins > 0 ? CoinFormat.formatGlyphs(coins) : plugin.getConfigManager().getString("gem-modify.free");
    }

    @Override
    protected void setMenuItems() {
        inventory.clear();
        inventory.setItem(0, createHead(HEAD_NPC, GuiText.name(plugin, "gem-modify.head-name"),
                GuiText.lore(plugin, "gem-modify.head-lore")));
        inventory.setItem(centeredRowSlots(1)[0], createHead(HEAD_INFO, GuiText.name(plugin, "gem-modify.button-name"),
                GuiText.lore(plugin, "gem-modify.button-lore", "cost", costText(plugin, cost(plugin)))));
        setFooterButtons(null,
                createHead(HEAD_BACK, plugin.getConfigManager().getComponent("common.back"), null),
                createHead(HEAD_BARRIER, plugin.getConfigManager().getComponent("common.close"), null));
        fillFrameGlass();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        int size = inventory.getSize();
        if (slot == size - 1) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.closeInventory();
        } else if (slot == size - 2) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.openInventory(new AnchorUpgradeGUI(plugin, viewer).getInventory());
        }
    }

    @Override
    public void handleBottomClick(InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || plugin.getGemFactory() == null) return;
        var stats = plugin.getGemFactory().readStats(clicked);
        if (stats.isEmpty() || plugin.getGemRegistry().modifiedFor(stats.get().gemId()).isEmpty()) {
            plugin.getConfigManager().playSound(viewer, "gui-error");
            viewer.sendMessage(plugin.getConfigManager().getMessage("gem-modify.not-improved"));
            return;
        }
        plugin.getConfigManager().playSound(viewer, "gui-click");
        viewer.openInventory(new GemModifyConfirmGUI(plugin, viewer, stats.get(), event.getSlot(), clicked).getInventory());
    }

    /** Header button text used by the master's menu. */
    static List<net.kyori.adventure.text.Component> headerLore(LoveClaims plugin) {
        return GuiText.lore(plugin, "gem-modify.header-button-lore");
    }
}
