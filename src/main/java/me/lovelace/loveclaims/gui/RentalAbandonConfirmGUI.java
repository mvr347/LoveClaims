package me.lovelace.loveclaims.gui;

import me.lovelace.loveclaims.LoveClaims;
import static me.lovelace.loveclaims.textures.HeadTextures.*;
import me.lovelace.loveclaims.model.Claim;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

public class RentalAbandonConfirmGUI extends AbstractGUI {
    // gui-gen-5 Исключение 1 (hopper-confirm, 9 слотов): [С][✓][С][П][П][П][С][✗][С]
    private static final int CONFIRM_SLOT = 1;
    private static final int CANCEL_SLOT = 7;

    private final LoveClaims plugin;
    private final Claim plot;

    public RentalAbandonConfirmGUI(LoveClaims plugin, Claim plot) {
        super(9, plugin.getConfigManager().getComponent("delete-confirm.title"));
        this.plugin = plugin;
        this.plot = plot;
        setMenuItems();
    }

    @Override
    protected void setMenuItems() {
        // Слот 1 (✓): подтвердить отказ — предупреждение, которое раньше жило в отдельной
        // "info"-голове по центру, теперь свёрнуто в lore кнопки подтверждения (Lore-структура).
        inventory.setItem(CONFIRM_SLOT, createHead(HEAD_DELETE_YES,
                plugin.getConfigManager().getComponent("delete-confirm.confirm"),
                List.of(
                        Component.text("§7Вы собираетесь отказаться"),
                        Component.text("§7от участка §e" + plot.getName()),
                        Component.text("§7Деньги за аренду §cне вернутся§7!"),
                        Component.text("§7Все участники будут удалены."),
                        Component.empty(),
                        Component.text("§cКликните для подтверждения!")
                )));

        // Слот 7 (✗): отмена
        inventory.setItem(CANCEL_SLOT, createHead(HEAD_BACK,
                plugin.getConfigManager().getComponent("delete-confirm.cancel"),
                List.of(plugin.getConfigManager().getComponent("common.cancel-lore"))));

        setGlass(0, 2, 6, 8);
        // Слоты 3,4,5 остаются пустыми (AIR) — визуальный разделитель по шаблону.
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();

        if (event.getSlot() == CANCEL_SLOT) {
            plugin.getConfigManager().playSound(player, "gui-click");
            player.openInventory(new RentalPlayerGUI(plugin, player, plot).getInventory());
        } else if (event.getSlot() == CONFIRM_SLOT) {
            player.closeInventory();
            plot.setRentalEndTime(0);
            plot.setOwnerUuid(plot.getParentClaimId());
            for (java.util.UUID oldMember : new java.util.ArrayList<>(plot.getMembers().keySet())) {
                plugin.getClaimManager().syncTrustRevoked(plot, oldMember);
                plugin.getStorage().removeMemberAsync(plot.getId(), oldMember);
            }
            plot.getMembers().clear();
            plugin.getStorage().saveClaimAsync(plot);
            plugin.getRentalManager().updateIndicator(plot);
            player.sendMessage(plugin.getConfigManager().getMessage("rental-refuse-success"));
            plugin.getConfigManager().playSound(player, "gui-click");
        }
    }
}
