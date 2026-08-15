package me.lovelace.loveclaims.gui;

import me.lovelace.loveclaims.LoveClaims;
import static me.lovelace.loveclaims.textures.HeadTextures.*;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.model.TrustLevel;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;

public class RentalPaymentGUI extends AbstractGUI {
    // gui-gen-5 Исключение 1 (hopper-confirm, 9 слотов): [С][✓][С][П][П][П][С][✗][С]
    // По сути это подтверждение/отмена оплаты аренды — тот же класс диалогов, что и
    // DeleteConfirmGUI/RentalAbandonConfirmGUI, поэтому используется тот же шаблон
    // (доп. "info"-голова, ранее занимавшая слот 2, свёрнута в lore кнопки подтверждения).
    private static final int CONFIRM_SLOT = 1;
    private static final int CANCEL_SLOT = 7;

    private final LoveClaims plugin;
    private final Claim plot;
    private boolean processing = false;

    public RentalPaymentGUI(LoveClaims plugin, Claim plot) {
        super(9, plugin.getConfigManager().getComponent("rental-payment.title", "name", plot.getName()));
        this.plugin = plugin;
        this.plot = plot;
        setMenuItems();
    }

    @Override
    protected void setMenuItems() {
        List<Component> confirmLore = new ArrayList<>();
        confirmLore.addAll(plugin.getConfigManager().getHelpMessage("rental-payment.info-lore", "name", plot.getName(), "price", String.valueOf(plot.getRentalPrice())));
        confirmLore.add(Component.empty());
        confirmLore.addAll(plugin.getConfigManager().getHelpMessage("rental-payment.confirm-lore", "price", String.valueOf(plot.getRentalPrice())));

        inventory.setItem(CONFIRM_SLOT, createHead(HEAD_DELETE_YES,
                plugin.getConfigManager().getComponent("rental-payment.confirm-name"),
                confirmLore));

        inventory.setItem(CANCEL_SLOT, createHead(HEAD_DELETE_NO,
                plugin.getConfigManager().getComponent("common.cancel"),
                plugin.getConfigManager().getHelpMessage("common.cancel-lore")));

        setGlass(0, 2, 6, 8);
        // Слоты 3,4,5 остаются пустыми (AIR) — визуальный разделитель по шаблону.
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();

        if (event.getSlot() == CANCEL_SLOT) {
            player.closeInventory();
            plugin.getConfigManager().playSound(player, "gui-click");
        } else if (event.getSlot() == CONFIRM_SLOT) {
            if (processing) return;
            processing = true;

            if (plot.getRentalEndTime() > System.currentTimeMillis()
                    && plot.getOwnerUuid() != null
                    && !plot.getOwnerUuid().equals(player.getUniqueId())
                    && !plot.getOwnerUuid().equals(plot.getParentClaimId())) {
                player.sendMessage(plugin.getConfigManager().getMessage("rental-already-taken"));
                plugin.getConfigManager().playSound(player, "anchor-error");
                player.closeInventory();
                return;
            }

            long ownedCount = plugin.getClaimManager().getAllClaims().stream()
                    .filter(Claim::isRentalPlot)
                    .filter(c -> c.getOwnerUuid() != null && c.getOwnerUuid().equals(player.getUniqueId()))
                    .filter(c -> c.getRentalEndTime() > System.currentTimeMillis())
                    .count();

            if (ownedCount >= 1 && !player.hasPermission("loveclaims.rental.bypasslimit")) {
                player.sendMessage(plugin.getConfigManager().getMessage("rental-limit-reached"));
                plugin.getConfigManager().playSound(player, "anchor-error");
                player.closeInventory();
                return;
            }

            long price = plot.getRentalPrice();
            if (price < 0) {
                player.closeInventory();
                return;
            }

            if (plugin.getCurrencyManager().hasEnough(player, price)) {
                if (plugin.getCurrencyManager().takeCurrency(player, price)) {
                    plot.setOwnerUuid(player.getUniqueId());
                    plot.setRentalEndTime(System.currentTimeMillis() + plugin.getRentalManager().getTaxDays() * 86400000L);
                    plot.setTrust(player.getUniqueId(), TrustLevel.OWNER);
                    plugin.getClaimManager().syncTrustGranted(plot, player.getUniqueId());
                    plugin.getStorage().saveMemberAsync(plot.getId(), player.getUniqueId(), TrustLevel.OWNER);
                    plugin.getStorage().saveClaimAsync(plot);
                    plugin.getRentalManager().updateIndicator(plot);
                    player.closeInventory();
                    player.sendMessage(plugin.getConfigManager().getMessage("rental-buy-success", "name", plot.getName()));
                    plugin.getConfigManager().playSound(player, "tax-paid");
                } else {
                    processing = false;
                    player.sendMessage(plugin.getConfigManager().getMessage("rental-insufficient-funds", "price", String.valueOf(price)));
                    plugin.getConfigManager().playSound(player, "anchor-error");
                }
            } else {
                processing = false;
                player.sendMessage(plugin.getConfigManager().getMessage("rental-paytax-needed", "needed", plugin.getCurrencyManager().getNeededCoinsString(price)));
                plugin.getConfigManager().playSound(player, "anchor-error");
                player.closeInventory();
            }
        }
    }

    public Claim getClaim() {
        return this.plot;
    }
}
