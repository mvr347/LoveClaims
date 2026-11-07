package me.lovelace.loveclaims.gui;

import me.lovelace.loveclaims.LoveClaims;
import static me.lovelace.loveclaims.textures.HeadTextures.*;
import me.lovelace.loveclaims.model.Claim;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class DeleteConfirmGUI extends AbstractGUI {
    // gui-gen-5 Исключение 1 (hopper-confirm, 9 слотов): [С][✓][С][П][П][П][С][✗][С]
    private static final int CONFIRM_SLOT = 1;
    private static final int CANCEL_SLOT = 7;

    private final LoveClaims plugin;
    private final Player viewer;
    private final Claim claim;

    public DeleteConfirmGUI(LoveClaims plugin, Player viewer, Claim claim) {
        // The server's 27-slot confirm menu when its background and invisible item exist, otherwise 9 slots.
        super(ConfirmMenuLayout.available(viewer) ? ConfirmMenuLayout.SIZE : 9,
                ConfirmMenuLayout.available(viewer) ? ConfirmMenuLayout.title(viewer)
                        : Component.text(plugin.getConfigManager().getGuiText("delete-confirm.title")));
        this.plugin = plugin;
        this.viewer = viewer;
        this.claim = claim;
        setMenuItems();
    }

    @Override
    protected void setMenuItems() {
        if (inventory.getSize() == ConfirmMenuLayout.SIZE) {
            var yes = ConfirmMenuLayout.button(Component.text(plugin.getConfigManager().getGuiText("delete-confirm.confirm")),
                    List.of(Component.text(plugin.getConfigManager().getGuiText("delete-confirm.title"))));
            var no = ConfirmMenuLayout.button(Component.text(plugin.getConfigManager().getGuiText("delete-confirm.cancel")), List.of());
            if (yes != null && no != null) {
                ConfirmMenuLayout.fill(inventory, yes, no);
                return;
            }
        }
        // gui-gen-5 Исключение 1: "Назад" в этом шаблоне нет — "Отменить" (✗) уже возвращает в SettingsGUI.
        inventory.setItem(CONFIRM_SLOT, createHead(HEAD_DELETE_YES, Component.text(plugin.getConfigManager().getGuiText("delete-confirm.confirm")), null));
        inventory.setItem(CANCEL_SLOT, createHead(HEAD_DELETE_NO, Component.text(plugin.getConfigManager().getGuiText("delete-confirm.cancel")), null));
        setGlass(0, 2, 6, 8);
        // Слоты 3,4,5 остаются пустыми (AIR) — визуальный разделитель по шаблону.
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        boolean large = inventory.getSize() == ConfirmMenuLayout.SIZE;
        boolean yes = large ? ConfirmMenuLayout.isConfirm(event.getSlot()) : event.getSlot() == CONFIRM_SLOT;
        boolean no = large ? ConfirmMenuLayout.isCancel(event.getSlot()) : event.getSlot() == CANCEL_SLOT;
        if (yes) {
            // A rental plot or trade point is never deleted by its tenant (it is the landlord's land).
            if (!claim.isOwner(viewer.getUniqueId()) || claim.isRentalPlot()) {
                viewer.closeInventory();
                viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                plugin.getConfigManager().playSound(viewer, "gui-error");
                return;
            }

            viewer.closeInventory();
            if (claim.getAnchorLocation().getBlock().getType() != Material.AIR) {
                claim.getAnchorLocation().getBlock().setType(Material.AIR);
            }
            plugin.getStorage().deleteClaimAsync(claim.getId());
            plugin.getClaimManager().removeClaimFromCache(claim.getId());

            int currentSize = (int) Math.round(claim.getBoundingBox().getMaxX() - claim.getBoundingBox().getMinX());
            me.lovelace.loveclaims.model.ClaimTier currentTier = plugin.getAnchorManager().getTierBySize(currentSize);
            String tierId = currentTier != null ? currentTier.id() : "tier-1";

            ItemStack anchor = plugin.getAnchorManager().createAnchorItem(tierId);
            if (anchor != null) viewer.getInventory().addItem(anchor);

            viewer.sendMessage(plugin.getConfigManager().getComponent("delete-confirm.deleted"));
        } else if (no) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.openInventory(new SettingsGUI(plugin, viewer, claim).getInventory());
        }
    }
}
