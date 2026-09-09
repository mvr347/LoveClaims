package me.lovelace.loveclaims.gui;

import me.lovelace.loveclaims.LoveClaims;
import static me.lovelace.loveclaims.textures.HeadTextures.*;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.model.ClaimFlag;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class SettingsGUI extends AbstractGUI {
    private final LoveClaims plugin;
    private final Player viewer;
    private final Claim claim;

    public SettingsGUI(LoveClaims plugin, Player viewer, Claim claim) {
        super(27, Component.text(plugin.getConfigManager().getGuiText("settings.title")));
        this.plugin = plugin;
        this.viewer = viewer;
        this.claim = claim;
        setMenuItems();
    }

    @Override
    protected void setMenuItems() {
        inventory.clear();

        boolean isOwner = viewer.getUniqueId().equals(claim.getOwnerUuid());
        boolean isManager = claim.isManager(viewer.getUniqueId());
        boolean canEditSettings = isOwner || isManager;

        // gui-gen-5 RULE 3: слот 0 — тематическая иконка меню настроек.
        inventory.setItem(0, createHead(HEAD_SETTINGS, plugin.getConfigManager().getComponent("main.settings-name"), null));

        // Рабочая зона (ряд 1, слоты 9-17). Стенки 9 и 17 — AIR.
        // Центрированы 4 кнопки: 10 (границы при приближении), 12 (скрыть/показать якорь), 14 (базовый доступ), 16 (перенести якорь).
        inventory.setItem(10, createProximityBorderButton());

        if (canEditSettings) {
            inventory.setItem(12, createAnchorToggle());
            inventory.setItem(14, createDefaultAccessButton());
        } else {
            ItemStack barrier = createHead(HEAD_BARRIER,
                    Component.text(plugin.getConfigManager().getString("settings.barrier-name")),
                    List.of(Component.text(plugin.getConfigManager().getString("settings.barrier-lore"))));
            inventory.setItem(12, barrier);
            inventory.setItem(14, barrier);
        }

        if (isOwner) {
            List<Component> moveLore = new ArrayList<>();
            moveLore.add(Component.text("§7" + plugin.getConfigManager().getGuiText("settings.move-desc")));
            moveLore.add(Component.text(" "));
            moveLore.add(Component.text(plugin.getConfigManager().getGuiText("settings.states.click-toggle")));
            inventory.setItem(16, createHead(HEAD_MOVE_ANCHOR,
                    Component.text("§e" + plugin.getConfigManager().getGuiText("settings.move")),
                    moveLore));
        } else {
            inventory.setItem(16, createHead(HEAD_BARRIER,
                    Component.text(plugin.getConfigManager().getString("settings.barrier-name")),
                    List.of(Component.text(plugin.getConfigManager().getString("settings.barrier-lore")))));
        }

        // Footer (слоты 18-26, RULE 7):
        // Слот 24 (Позиция 6) — доп. кнопка "Удалить приват" (только у владельца)
        // Слот 25 (Позиция 7) — Back (возврат в MainClaimGUI)
        // Слот 26 (Позиция 8) — Close
        ItemStack deleteBtn = isOwner
                ? createHead(HEAD_BARRIER, Component.text(plugin.getConfigManager().getGuiText("settings.delete")), null)
                : null;

        setFooterButtons(
                deleteBtn,
                createHead(HEAD_BACK, Component.text(plugin.getConfigManager().getGuiText("common.back")), null),
                createHead(HEAD_BARRIER, Component.text(plugin.getConfigManager().getGuiText("common.close")), null)
        );

        fillFrameGlass();
    }

    private ItemStack createProximityBorderButton() {
        me.lovelace.loveclaims.model.UserData data = plugin.getUserManager().getUserData(viewer.getUniqueId());
        boolean isEnabled = data.isShowProximityBorder();

        String name = plugin.getConfigManager().getGuiText("settings.proximity-border.name");
        Component compName = Component.text("§e" + (name != null ? name : "Границы при приближении"));

        List<Component> lore = new ArrayList<>();
        String desc = plugin.getConfigManager().getGuiText("settings.proximity-border.desc");
        lore.add(Component.text("§7" + (desc != null ? desc : "Показ границ при приближении к привату")));

        String stateStr = isEnabled
                ? plugin.getConfigManager().getGuiText("settings.states.enabled")
                : plugin.getConfigManager().getGuiText("settings.states.disabled");

        lore.add(Component.text(plugin.getConfigManager().getGuiText("settings.states.status") + stateStr));
        lore.add(Component.text(" "));
        lore.add(Component.text(plugin.getConfigManager().getGuiText("settings.states.click-toggle")));

        return createHead(HEAD_BORDER, compName, lore);
    }

    private ItemStack createDefaultAccessButton() {
        String name = plugin.getConfigManager().getGuiText("settings.default-access.name");
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("§7" + plugin.getConfigManager().getGuiText("settings.default-access.desc")));
        lore.add(Component.text(" "));
        lore.add(Component.text(plugin.getConfigManager().getGuiText("settings.states.click-toggle")));
        return createHead(HEAD_MEMBERS, Component.text(name), lore);
    }

    private ItemStack createAnchorToggle() {
        String name = plugin.getConfigManager().getGuiText("settings.flags.hide");
        Component compName = Component.text("§e" + name);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("§7" + plugin.getConfigManager().getGuiText("settings.flags.hide-desc")));

        boolean isHidden = claim.getFlag(ClaimFlag.HIDE_ANCHOR);
        String stateStr = isHidden
                ? plugin.getConfigManager().getGuiText("settings.states.enabled")
                : plugin.getConfigManager().getGuiText("settings.states.disabled");

        lore.add(Component.text(plugin.getConfigManager().getGuiText("settings.states.status") + stateStr));
        lore.add(Component.text(" "));
        lore.add(Component.text(plugin.getConfigManager().getGuiText("settings.states.click-toggle")));

        return createHead(HEAD_HIDE_ANCHOR, compName, lore);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        boolean isOwner = viewer.getUniqueId().equals(claim.getOwnerUuid());
        boolean isManager = claim.isManager(viewer.getUniqueId());
        boolean canEditSettings = isOwner || isManager;

        // Footer действия
        if (slot == 26) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.closeInventory();
            return;
        }
        if (slot == 25) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.openInventory(new MainClaimGUI(plugin, viewer, claim).getInventory());
            return;
        }
        if (slot == 24 && isOwner) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.openInventory(new DeleteConfirmGUI(plugin, viewer, claim).getInventory());
            return;
        }

        // Переключение границ при приближении (персональная настройка игрока)
        if (slot == 10) {
            me.lovelace.loveclaims.model.UserData data = plugin.getUserManager().getUserData(viewer.getUniqueId());
            boolean newState = !data.isShowProximityBorder();
            data.setShowProximityBorder(newState);
            plugin.getStorage().saveUserDataAsync(data);
            plugin.getConfigManager().playSound(viewer, "gui-click");
            setMenuItems();
            return;
        }

        // Скрыть / показать якорь
        if (slot == 12 && canEditSettings) {
            boolean newState = !claim.getFlag(ClaimFlag.HIDE_ANCHOR);
            claim.setFlag(ClaimFlag.HIDE_ANCHOR, newState);
            plugin.getStorage().saveFlagAsync(claim.getId(), ClaimFlag.HIDE_ANCHOR, newState);

            if (claim.getAnchorLocation() != null) {
                if (newState) {
                    claim.getAnchorLocation().getBlock().setType(Material.AIR);
                } else {
                    int currentSize = (int) Math.round(claim.getBoundingBox().getMaxX() - claim.getBoundingBox().getMinX());
                    me.lovelace.loveclaims.model.ClaimTier currentTier = plugin.getAnchorManager().getTierBySize(currentSize);
                    claim.getAnchorLocation().getBlock().setType(currentTier != null ? currentTier.material() : Material.CAMPFIRE);
                }
            }

            plugin.getConfigManager().playSound(viewer, "gui-click");
            setMenuItems();
            return;
        }

        // Базовый доступ игроков
        if (slot == 14 && canEditSettings) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.openInventory(new DefaultPermissionsGUI(plugin, viewer, claim).getInventory());
            return;
        }

        // Перенести якорь (свернуть приват)
        if (slot == 16 && isOwner) {
            viewer.closeInventory();

            int currentSize = (int) Math.round(claim.getBoundingBox().getMaxX() - claim.getBoundingBox().getMinX());
            me.lovelace.loveclaims.model.ClaimTier currentTier = plugin.getAnchorManager().getTierBySize(currentSize);

            ItemStack anchor = plugin.getAnchorManager().createAnchorItem(currentTier != null ? currentTier.id() : "tier-1");
            if (anchor != null) {
                viewer.getInventory().addItem(anchor).values().forEach(item -> viewer.getWorld().dropItemNaturally(viewer.getLocation(), item));
            }

            plugin.getClaimManager().removeClaimFromCache(claim.getId());
            plugin.getStorage().deleteClaimAsync(claim.getId());

            if (claim.getAnchorLocation() != null) {
                claim.getAnchorLocation().getBlock().setType(Material.AIR);
            }

            viewer.sendMessage(plugin.getConfigManager().getMessage("rental-move-success"));
            plugin.getConfigManager().playSound(viewer, "anchor-break");
        }
    }
}
