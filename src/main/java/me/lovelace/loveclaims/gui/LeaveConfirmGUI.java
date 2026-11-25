package me.lovelace.loveclaims.gui;

import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.util.GuiText;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

/** Confirmation for a member leaving a claim (the server's confirm menu when ItemsAdder provides it). */
public class LeaveConfirmGUI extends AbstractGUI {
    private final LoveClaims plugin;
    private final Player viewer;
    private final Claim claim;

    public LeaveConfirmGUI(LoveClaims plugin, Player viewer, Claim claim) {
        super(confirmSize(viewer), confirmTitle(viewer, plugin.getConfigManager().getComponent("leave-confirm.title")));
        this.plugin = plugin;
        this.viewer = viewer;
        this.claim = claim;
        setMenuItems();
    }

    private String ownerName() {
        String name = Bukkit.getOfflinePlayer(claim.getOwnerUuid()).getName();
        return name != null ? GuiText.esc(name) : "???";
    }

    @Override
    protected void setMenuItems() {
        placeConfirm(GuiText.name(plugin, "leave-confirm.confirm"),
                GuiText.lore(plugin, "leave-confirm.confirm-lore", "owner", ownerName()),
                GuiText.name(plugin, "leave-confirm.cancel"), List.of());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        if (isConfirmSlot(slot)) {
            viewer.closeInventory();
            if (plugin.getClaimManager().getClaimById(claim.getId()).orElse(null) != claim
                    || claim.isOwner(viewer.getUniqueId()) || !claim.getMembers().containsKey(viewer.getUniqueId())) {
                plugin.getConfigManager().playSound(viewer, "gui-error");
                return;
            }
            claim.getMembers().remove(viewer.getUniqueId());
            plugin.getClaimManager().syncTrustRevoked(claim, viewer.getUniqueId());
            plugin.getStorage().removeMemberAsync(claim.getId(), viewer.getUniqueId());
            claim.setModified(true);
            viewer.sendMessage(plugin.getConfigManager().getMessage("leave-confirm.left", "owner", ownerName()));
            plugin.getConfigManager().playSound(viewer, "anchor-break");
            Player owner = Bukkit.getPlayer(claim.getOwnerUuid());
            if (owner != null) {
                owner.sendMessage(plugin.getConfigManager().getMessage("leave-confirm.owner-notify",
                        "player", GuiText.esc(viewer.getName())));
            }
        } else if (isCancelSlot(slot)) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.openInventory(new MembersGUI(plugin, viewer, claim).getInventory());
        }
    }
}
