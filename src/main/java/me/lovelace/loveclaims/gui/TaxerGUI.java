package me.lovelace.loveclaims.gui;

import me.lovelace.loveclaims.LoveClaims;
import static me.lovelace.loveclaims.textures.HeadTextures.*;
import me.lovelace.loveclaims.model.Claim;
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
    // Рабочая зона 27-слотового меню: 7 интерьерных слотов (10-16).
    private static final int[] CONTENT_SLOTS = {10, 11, 12, 13, 14, 15, 16};

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

        if (ownedPlots.isEmpty()) {
            inventory.setItem(CONTENT_SLOTS[0], createHead(HEAD_BARRIER, plugin.getConfigManager().getComponent("rental-no-rented"), List.of(
                    Component.text("§7Арендуйте участок, чтобы"),
                    Component.text("§7оплачивать здесь налоги.")
            )));
        } else {
            NamespacedKey key = new NamespacedKey(plugin, "plot_id");
            for (int i = 0; i < ownedPlots.size() && i < CONTENT_SLOTS.length; i++) {
                Claim plot = ownedPlots.get(i);

                long taxAmount = Math.round(plot.getRentalPrice() * (plugin.getRentalManager().getTaxPercentage() / 100.0));
                long timeLeft = (plot.getRentalEndTime() - System.currentTimeMillis()) / 1000;
                long days = Math.max(0, timeLeft / 86400);
                long hours = Math.max(0, (timeLeft % 86400) / 3600);

                ItemStack item = createHead(HEAD_INFO,
                        plugin.getConfigManager().getComponent("rental-list.plot-name", "name", plot.getName()),
                        List.of(
                                plugin.getConfigManager().getComponent("msg-rental-list-entry", "role", "", "name", "", "days", String.valueOf(days), "hours", String.valueOf(hours)),
                                plugin.getConfigManager().getComponent("rental-player.tax-amount", "amount", String.valueOf(taxAmount)),
                                Component.empty(),
                                plugin.getConfigManager().getComponent("rental-list.plot-lore-click")
                        ));

                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, plot.getId().toString());
                    item.setItemMeta(meta);
                }

                inventory.setItem(CONTENT_SLOTS[i], item);
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
                long taxAmount = Math.round(plot.getRentalPrice() * (plugin.getRentalManager().getTaxPercentage() / 100.0));

                if (plugin.getCurrencyManager().hasEnough(viewer, taxAmount)) {
                    if (plugin.getCurrencyManager().takeCurrency(viewer, taxAmount)) {
                        long extendTime = plugin.getRentalManager().getTaxDays() * 86400000L;
                        plot.setRentalEndTime(plot.getRentalEndTime() + extendTime);
                        plugin.getStorage().saveClaimAsync(plot);
                        plugin.getRentalManager().updateIndicator(plot);

                        viewer.sendMessage(plugin.getConfigManager().getMessage("rental-paytax-success", "days", String.valueOf(plugin.getRentalManager().getTaxDays())));
                        plugin.getConfigManager().playSound(viewer, "tax-paid");
                        setMenuItems(); // Обновляем GUI
                    }
                } else {
                    viewer.sendMessage(plugin.getConfigManager().getMessage("rental-paytax-needed", "needed", plugin.getCurrencyManager().getNeededCoinsString(taxAmount)));
                    plugin.getConfigManager().playSound(viewer, "anchor-error");
                    viewer.closeInventory();
                }
            });
        }
    }
}
