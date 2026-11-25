package me.lovelace.loveclaims.gui;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.gems.GemStats;
import me.lovelace.loveclaims.util.GuiText;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Confirmation for turning an improved gem into a modified one (the server's confirm menu when available). */
public class GemModifyConfirmGUI extends AbstractGUI {
    private final LoveClaims plugin;
    private final Player viewer;
    private final GemStats gem;
    private final int inventorySlot;
    private final ItemStack snapshot;

    public GemModifyConfirmGUI(LoveClaims plugin, Player viewer, GemStats gem, int inventorySlot, ItemStack snapshot) {
        super(confirmSize(viewer), confirmTitle(viewer, GuiText.name(plugin, "gem-modify.confirm-title")));
        this.plugin = plugin;
        this.viewer = viewer;
        this.gem = gem;
        this.inventorySlot = inventorySlot;
        this.snapshot = snapshot.clone();
        setMenuItems();
    }

    private boolean hasCoins(long coins) {
        if (coins <= 0) return true;
        var eco = LoveCore.service(LoveEconomy.class);
        return eco.isPresent() && eco.get().has(viewer, coins);
    }

    @Override
    protected void setMenuItems() {
        long coins = GemModifyGUI.cost(plugin);
        String to = plugin.getGemRegistry().modifiedFor(gem.gemId()).orElse("?");
        String have = coins <= 0 ? "" : plugin.getConfigManager().getString(hasCoins(coins) ? "gem-modify.have-ok" : "gem-modify.have-no");
        placeConfirm(GuiText.name(plugin, "gem-modify.confirm-name"),
                GuiText.lore(plugin, "gem-modify.confirm-lore", "from", GuiText.esc(gem.gemId()), "to", GuiText.esc(to),
                        "cost", GemModifyGUI.costText(plugin, coins), "have", have),
                GuiText.name(plugin, "gem-modify.cancel-name"), GuiText.lore(plugin, "gem-modify.cancel-lore"));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        if (isCancelSlot(slot)) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.openInventory(new GemModifyGUI(plugin, viewer).getInventory());
            return;
        }
        if (!isConfirmSlot(slot)) return;

        GemModifyGUI back = new GemModifyGUI(plugin, viewer);
        ItemStack current = viewer.getInventory().getItem(inventorySlot);
        var target = plugin.getGemRegistry().modifiedFor(gem.gemId());
        if (current == null || !current.isSimilar(snapshot) || target.isEmpty()) {
            fail(back, "gem-modify.changed");
            return;
        }
        long coins = GemModifyGUI.cost(plugin);
        if (coins > 0) {
            var eco = LoveCore.service(LoveEconomy.class);
            if (eco.isEmpty() || !eco.get().has(viewer, coins) || !eco.get().charge(viewer, coins)) {
                fail(back, "gem-modify.no-coins");
                return;
            }
        }
        if (current.getAmount() > 1) {
            current.setAmount(current.getAmount() - 1);
        } else {
            viewer.getInventory().setItem(inventorySlot, null);
        }
        ItemStack result = plugin.getGemRegistry().createGemItem(target.get(), 1);
        if (result != null) {
            viewer.getInventory().addItem(result).values()
                    .forEach(left -> viewer.getWorld().dropItemNaturally(viewer.getLocation(), left));
        }
        plugin.getEffects().playFor(viewer, "anchor-upgrade");
        viewer.sendMessage(plugin.getConfigManager().getMessage("gem-modify.done", "gem", GuiText.esc(target.get())));
        viewer.openInventory(back.getInventory());
    }

    private void fail(GemModifyGUI back, String key) {
        plugin.getConfigManager().playSound(viewer, "gui-error");
        viewer.sendMessage(plugin.getConfigManager().getMessage(key));
        viewer.openInventory(back.getInventory());
    }
}
