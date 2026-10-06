package me.lovelace.loveclaims.gui;

import me.lovelace.loveclaims.LoveClaims;
import static me.lovelace.loveclaims.textures.HeadTextures.*;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.util.CoinFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

public class TaxerGUI extends AbstractGUI {
    // Work row of the 27-slot menu: 7 interior slots (10-16); content is centered within it.
    private static final int WORK_ROW_FIRST = 10;
    private static final int WORK_ROW_LAST = 16;

    private final LoveClaims plugin;
    private final Player viewer;

    public TaxerGUI(LoveClaims plugin, Player viewer) {
        super(27, plugin.getConfigManager().getComponent("taxer-npc-name"));
        this.plugin = plugin;
        this.viewer = viewer;
        setMenuItems();
    }

    @Override
    protected void setMenuItems() {
        List<Claim> ownedPlots = plugin.getClaimManager().getAllClaims().stream()
                .filter(Claim::isRentalPlot)
                .filter(Claim::isRented)
                .filter(c -> c.getOwnerUuid() != null && c.getOwnerUuid().equals(viewer.getUniqueId()))
                .toList();

        // gui-gen-5 RULE 3: слот 0 — профиль напрямую (список СВОИХ арендованных плотов игрока).
        ItemStack self = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
        SkullMeta selfMeta = (SkullMeta) self.getItemMeta();
        if (selfMeta != null) {
            selfMeta.setOwningPlayer(viewer);
            selfMeta.displayName(Component.text("§e" + viewer.getName()));
            self.setItemMeta(selfMeta);
        }
        inventory.setItem(0, self);

        // Clear the work row first so a re-render (e.g. after paying tax) leaves no stale items.
        for (int s = WORK_ROW_FIRST; s <= WORK_ROW_LAST; s++) {
            inventory.setItem(s, null);
        }
        int[] contentSlots = centeredRowSlots(Math.max(1, ownedPlots.size()));

        if (ownedPlots.isEmpty()) {
            inventory.setItem(contentSlots[0], createHead(HEAD_BARRIER, plugin.getConfigManager().getComponent("rental-no-rented"), List.of(
                    Component.text("§7Арендуйте участок, чтобы"),
                    Component.text("§7оплачивать здесь налоги.")
            )));
        } else {
            NamespacedKey key = new NamespacedKey(plugin, "plot_id");
            for (int i = 0; i < ownedPlots.size() && i < contentSlots.length; i++) {
                Claim plot = ownedPlots.get(i);

                long taxAmount = plugin.getRentalManager().getRenewCost(plot);
                long timeLeft = (plot.getRentalEndTime() - System.currentTimeMillis()) / 1000;
                long days = Math.max(0, timeLeft / 86400);
                long hours = Math.max(0, (timeLeft % 86400) / 3600);

                ItemStack item = createHead(HEAD_INFO,
                        plugin.getConfigManager().getComponent("rental-list.plot-name", "name", plot.getName()),
                        List.of(
                                plugin.getConfigManager().getComponent("msg-rental-list-entry", "role", "", "name", "", "days", String.valueOf(days), "hours", String.valueOf(hours)),
                                plugin.getConfigManager().getComponent("rental-player.tax-amount", "amount", CoinFormat.formatGlyphs(taxAmount)),
                                Component.empty(),
                                plugin.getConfigManager().getComponent("rental-list.plot-lore-click")
                        ));

                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, plot.getId().toString());
                    item.setItemMeta(meta);
                }

                inventory.setItem(contentSlots[i], item);
            }
        }

        // Standalone-меню (открывается напрямую NPC-листенером) — Back неактивен, только Close.
        setFooterButtons(null, null, createHead(HEAD_DELETE_NO, Component.text("§cЗакрыть"), List.of()));
        fillFrameGlass();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        if (event.getSlot() == inventory.getSize() - 1) {
            viewer.closeInventory();
            return;
        }

        if (event.getCurrentItem().getItemMeta() == null) return;

        NamespacedKey key = new NamespacedKey(plugin, "plot_id");
        String idStr = event.getCurrentItem().getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);

        if (idStr != null) {
            UUID plotId = UUID.fromString(idStr);
            plugin.getClaimManager().getClaimById(plotId).ifPresent(plot -> {
                var rentals = plugin.getRentalManager();
                if (!rentals.canExtend(plot)) {
                    viewer.sendMessage(plugin.getConfigManager().getMessage("trade-point-prepaid-max"));
                    plugin.getConfigManager().playSound(viewer, "anchor-error");
                    return;
                }
                long taxAmount = rentals.getRenewCost(plot);

                if (!rentals.paymentsAvailable()) {
                    viewer.sendMessage(plugin.getConfigManager().getMessage("rental-no-economy"));
                    plugin.getConfigManager().playSound(viewer, "anchor-error");
                    return;
                }

                if (rentals.hasFunds(viewer, plot, taxAmount)) {
                    if (rentals.charge(viewer, plot, taxAmount)) {
                        rentals.extend(plot, rentals.getPeriodMillis(plot));

                        viewer.sendMessage(plugin.getConfigManager().getMessage("rental-paytax-success", "days", String.valueOf(rentals.getPeriodMillis(plot) / 86400000L)));
                        plugin.getConfigManager().playSound(viewer, "tax-paid");
                        setMenuItems(); // Обновляем GUI
                    }
                } else {
                    viewer.sendMessage(plugin.getConfigManager().getMessage("rental-paytax-needed", "needed", rentals.describeAmount(plot, taxAmount)));
                    plugin.getConfigManager().playSound(viewer, "anchor-error");
                    viewer.closeInventory();
                }
            });
        }
    }
}
