package me.lovelace.loveclaims.gui;

import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.model.ClaimFlag;
import me.lovelace.loveclaims.util.GuiText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;

import static me.lovelace.loveclaims.textures.HeadTextures.*;

/**
 * Claim settings (27 slots): hide the anchor block and release the claim. The proximity-border hint is always on
 * for everyone, the default-access page and "move anchor" are gone (owner request 2026-11-24).
 */
public class SettingsGUI extends AbstractGUI {
    private final LoveClaims plugin;
    private final Player viewer;
    private final Claim claim;

    public SettingsGUI(LoveClaims plugin, Player viewer, Claim claim) {
        super(27, plugin.getConfigManager().getComponent("settings.title"));
        this.plugin = plugin;
        this.viewer = viewer;
        this.claim = claim;
        setMenuItems();
    }

    private boolean isOwner() {
        return viewer.getUniqueId().equals(claim.getOwnerUuid());
    }

    private boolean canEdit() {
        return isOwner() || claim.isManager(viewer.getUniqueId());
    }

    @Override
    protected void setMenuItems() {
        inventory.clear();
        String roleKey = isOwner() ? "settings.role-owner" : claim.isManager(viewer.getUniqueId()) ? "settings.role-manager" : "settings.role-member";
        inventory.setItem(0, createHead(HEAD_SETTINGS, GuiText.name(plugin, "main.settings-name"), List.of(
                GuiText.name(plugin, "settings.profile-lore"), GuiText.name(plugin, roleKey))));

        // Work zone: two buttons centred (12, 14); the side walls stay empty.
        int[] slots = centeredRowSlots(2);
        inventory.setItem(slots[0], hideButton());
        inventory.setItem(slots[1], releaseButton());

        setFooterButtons(null,
                createHead(HEAD_BACK, plugin.getConfigManager().getComponent("common.back"), null),
                createHead(HEAD_BARRIER, plugin.getConfigManager().getComponent("common.close"), null));
        fillFrameGlass();
    }

    private org.bukkit.inventory.ItemStack hideButton() {
        boolean hidden = claim.getFlag(ClaimFlag.HIDE_ANCHOR);
        if (!canEdit()) return blocked("settings.hide-name", "settings.hide-blocked-lore");
        Component state = GuiText.name(plugin, hidden ? "settings.on" : "settings.off");
        Component name = GuiText.name(plugin, "settings.hide-name")
                .append(Component.text(" | ", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY)).append(state)
                .decoration(TextDecoration.ITALIC, false);
        return createHead(HEAD_HIDE_ANCHOR, name, GuiText.lore(plugin, "settings.hide-lore",
                "state", hidden ? "<green>Включено</green>" : "<red>Выключено</red>"));
    }

    private org.bukkit.inventory.ItemStack releaseButton() {
        if (!isOwner() || claim.isRentalPlot()) return blocked("settings.release-name", "settings.release-blocked-lore");
        return createHead(HEAD_BARRIER, GuiText.name(plugin, "settings.release-name"), GuiText.lore(plugin, "settings.release-lore"));
    }

    /** RULE 10.4: an unavailable button keeps its place, says why and shows the blocked click. */
    private org.bukkit.inventory.ItemStack blocked(String nameKey, String loreKey) {
        List<Component> lore = new ArrayList<>(GuiText.lore(plugin, loreKey));
        Component name = GuiText.name(plugin, nameKey).color(net.kyori.adventure.text.format.NamedTextColor.GRAY)
                .append(Component.text(" | ", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY))
                .append(Component.text("Недоступно", net.kyori.adventure.text.format.NamedTextColor.RED))
                .decoration(TextDecoration.ITALIC, false);
        return createHead(HEAD_BARRIER, name, lore);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        int size = inventory.getSize();
        if (slot == size - 1) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.closeInventory();
            return;
        }
        if (slot == size - 2) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.openInventory(new MainClaimGUI(plugin, viewer, claim).getInventory());
            return;
        }
        int[] slots = centeredRowSlots(2);

        if (slot == slots[0]) {
            if (!canEdit()) {
                deny();
                return;
            }
            boolean newState = !claim.getFlag(ClaimFlag.HIDE_ANCHOR);
            claim.setFlag(ClaimFlag.HIDE_ANCHOR, newState);
            plugin.getStorage().saveFlagAsync(claim.getId(), ClaimFlag.HIDE_ANCHOR, newState);
            if (claim.getAnchorLocation() != null) {
                if (newState) {
                    claim.getAnchorLocation().getBlock().setType(Material.AIR);
                } else {
                    me.lovelace.loveclaims.model.ClaimTier tier = plugin.getAnchorManager().resolveClaimTier(claim);
                    claim.getAnchorLocation().getBlock().setType(tier != null ? tier.material() : Material.CAMPFIRE);
                }
            }
            plugin.getConfigManager().playSound(viewer, "gui-click");
            setMenuItems();
            return;
        }
        if (slot == slots[1]) {
            if (!isOwner() || claim.isRentalPlot()) {
                deny();
                return;
            }
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.openInventory(new DeleteConfirmGUI(plugin, viewer, claim).getInventory());
        }
    }

    private void deny() {
        plugin.getConfigManager().playSound(viewer, "gui-error");
        viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
    }
}
