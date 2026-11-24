package me.lovelace.loveclaims.gui;

import me.lovelace.loveclaims.LoveClaims;
import static me.lovelace.loveclaims.textures.HeadTextures.*;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.model.ClaimFlag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class SettingsGUI extends AbstractGUI {
    private static final MiniMessage MM = MiniMessage.miniMessage();

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

    @Override
    protected void setMenuItems() {
        inventory.clear();

        boolean isOwner = viewer.getUniqueId().equals(claim.getOwnerUuid());
        boolean isManager = claim.isManager(viewer.getUniqueId());
        boolean canEditSettings = isOwner || isManager;

        // gui-gen-5 RULE 3: слот 0 — тематическая иконка меню настроек.
        inventory.setItem(0, createHead(HEAD_SETTINGS, plugin.getConfigManager().getComponent("main.settings-name"),
                List.of(noItalic(MM.deserialize("<dark_gray>Настройки привата")),
                        noItalic(MM.deserialize("<dark_gray><st>─────────────────────</st></dark_gray>")),
                        noItalic(MM.deserialize(canEditSettings ? "<gray>Ваша роль: <white>" + (isOwner ? "владелец" : "менеджер")
                                : "<gray>Ваша роль: <white>участник")))));

        // Рабочая зона (ряд 1, слоты 9-17). Стенки 9 и 17 — AIR.
        // Центрированы 4 кнопки: 10 (границы при приближении), 12 (скрыть/показать якорь), 14 (базовый доступ), 16 (перенести якорь).
        inventory.setItem(10, createProximityBorderButton());

        if (canEditSettings) {
            inventory.setItem(12, createAnchorToggle());
            inventory.setItem(14, createDefaultAccessButton());
        } else {
            inventory.setItem(12, inactiveButton(plugin.getConfigManager().getComponent("settings.flags.hide"),
                    "нужны права менеджера", "скрыть якорь"));
            inventory.setItem(14, inactiveButton(plugin.getConfigManager().getComponent("settings.default-access.name"),
                    "нужны права менеджера", "изменить доступ"));
        }

        if (isOwner) {
            List<Component> moveLore = new ArrayList<>();
            moveLore.add(plugin.getConfigManager().getComponent("settings.move-desc"));
            moveLore.add(Component.text(" "));
            moveLore.addAll(plugin.getConfigManager().getHelpMessage("settings.states.click-toggle"));
            inventory.setItem(16, createHead(HEAD_MOVE_ANCHOR,
                    plugin.getConfigManager().getComponent("settings.move"),
                    moveLore));
        } else {
            inventory.setItem(16, inactiveButton(plugin.getConfigManager().getComponent("settings.move"),
                    "только владелец привата", "перенести якорь"));
        }

        // Footer (слоты 18-26, RULE 7):
        // Слот 24 (Позиция 6) — доп. кнопка "Удалить приват" (только у владельца)
        // Слот 25 (Позиция 7) — Back (возврат в MainClaimGUI)
        // Слот 26 (Позиция 8) — Close
        ItemStack deleteBtn = isOwner
                ? createHead(HEAD_BARRIER, plugin.getConfigManager().getComponent("settings.delete"),
                List.of(noItalic(MM.deserialize("<dark_gray>Необратимое действие")),
                        Component.empty(),
                        noItalic(MM.deserialize("<gray>Приват будет удалён, якорь и гемы")),
                        noItalic(MM.deserialize("<gray>вернутся вам предметами.")),
                        noItalic(MM.deserialize("<dark_gray><st>─────────────────────</st></dark_gray>")),
                        noItalic(MM.deserialize("<green>ЛКМ <dark_gray>— <gray>перейти к подтверждению"))))
                : null;

        setFooterButtons(
                deleteBtn,
                createHead(HEAD_BACK, plugin.getConfigManager().getComponent("common.back"), null),
                createHead(HEAD_BARRIER, plugin.getConfigManager().getComponent("common.close"), null)
        );

        fillFrameGlass();
    }

    private ItemStack createProximityBorderButton() {
        me.lovelace.loveclaims.model.UserData data = plugin.getUserManager().getUserData(viewer.getUniqueId());
        boolean isEnabled = data.isShowProximityBorder();

        Component compName = withState(plugin.getConfigManager().getComponent("settings.proximity-border.name"), isEnabled);

        List<Component> lore = new ArrayList<>();
        lore.addAll(plugin.getConfigManager().getHelpMessage("settings.proximity-border.desc"));


        lore.add(plugin.getConfigManager().getComponent("settings.states.status").append(
                plugin.getConfigManager().getComponent(isEnabled ? "settings.states.enabled" : "settings.states.disabled")));
        lore.add(Component.text(" "));
        lore.addAll(plugin.getConfigManager().getHelpMessage("settings.states.click-toggle"));

        return createHead(HEAD_BORDER, compName, lore);
    }

    private ItemStack createDefaultAccessButton() {
        List<Component> lore = new ArrayList<>();
        lore.add(plugin.getConfigManager().getComponent("settings.default-access.desc"));
        lore.add(Component.text(" "));
        lore.addAll(plugin.getConfigManager().getHelpMessage("settings.states.click-toggle"));
        return createHead(HEAD_MEMBERS, plugin.getConfigManager().getComponent("settings.default-access.name"), lore);
    }

    private ItemStack createAnchorToggle() {
        boolean isHidden = claim.getFlag(ClaimFlag.HIDE_ANCHOR);
        Component compName = withState(plugin.getConfigManager().getComponent("settings.flags.hide"), isHidden);

        List<Component> lore = new ArrayList<>();
        lore.add(plugin.getConfigManager().getComponent("settings.flags.hide-desc"));

        lore.add(plugin.getConfigManager().getComponent("settings.states.status").append(
                plugin.getConfigManager().getComponent(isHidden ? "settings.states.enabled" : "settings.states.disabled")));
        lore.add(Component.text(" "));
        lore.addAll(plugin.getConfigManager().getHelpMessage("settings.states.click-toggle"));

        return createHead(HEAD_HIDE_ANCHOR, compName, lore);
    }

    /** RULE 10.2: "Name | State" for a toggle. */
    private static Component withState(Component base, boolean on) {
        return base.append(MM.deserialize(on ? " <dark_gray>|</dark_gray> <green>Включено</green>"
                : " <dark_gray>|</dark_gray> <red>Выключено</red>")).decoration(TextDecoration.ITALIC, false);
    }

    /** RULE 10.4: an unavailable button keeps its place, says why, and shows the blocked click. */
    private ItemStack inactiveButton(Component baseName, String reason, String action) {
        Component name = noItalic(baseName.color(net.kyori.adventure.text.format.NamedTextColor.GRAY))
                .append(MM.deserialize(" <dark_gray>|</dark_gray> <red>Недоступно</red>"));
        return createHead(HEAD_BARRIER, name, List.of(
                noItalic(MM.deserialize("<dark_gray><st>─────────────────────</st></dark_gray>")),
                noItalic(MM.deserialize("<red>✕ Действие недоступно")),
                noItalic(MM.deserialize("<red>Причина: <gray>" + reason)),
                noItalic(MM.deserialize("<dark_gray>ЛКМ — " + action + " (заблокировано)"))));
    }

    private static Component noItalic(Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
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

        // Blocked buttons answer with the error sound and a short hint (RULE 10.4)
        if ((slot == 12 || slot == 14) && !canEditSettings || slot == 16 && !isOwner) {
            plugin.getConfigManager().playSound(viewer, "gui-error");
            viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
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
                    me.lovelace.loveclaims.model.ClaimTier currentTier = plugin.getAnchorManager().resolveClaimTier(claim);
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
            if (plugin.getClaimManager().getClaimById(claim.getId()).orElse(null) != claim
                    || claim.isRentalPlot()) {
                viewer.closeInventory();
                viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                plugin.getConfigManager().playSound(viewer, "gui-error");
                return;
            }
            viewer.closeInventory();

            plugin.getAnchorManager().giveClaimAnchorAndGems(claim, viewer);

            plugin.getStorage().deleteClaimAsync(claim.getId());
            plugin.getClaimManager().removeClaimFromCache(claim.getId());

            if (claim.getAnchorLocation() != null) {
                claim.getAnchorLocation().getBlock().setType(Material.AIR);
            }

            viewer.sendMessage(plugin.getConfigManager().getMessage("rental-move-success"));
            plugin.getConfigManager().playSound(viewer, "anchor-break");
        }
    }
}
