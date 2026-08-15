package me.lovelace.loveclaims.gui;

import me.lovelace.loveclaims.LoveClaims;
import static me.lovelace.loveclaims.textures.HeadTextures.*;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.model.ClaimFlag;
import me.lovelace.loveclaims.model.TrustLevel;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class SettingsGUI extends AbstractGUI {
    private final LoveClaims plugin;
    private final Player viewer;
    private final Claim claim;

    public SettingsGUI(LoveClaims plugin, Player viewer, Claim claim) {
        super(45, Component.text(plugin.getConfigManager().getGuiText("settings.title")));
        this.plugin = plugin;
        this.viewer = viewer;
        this.claim = claim;
        setMenuItems();
    }

    @Override
    protected void setMenuItems() {
        boolean isOwner = viewer.getUniqueId().equals(claim.getOwnerUuid());
        boolean isManager = claim.getTrust(viewer.getUniqueId()) == TrustLevel.MANAGER;
        boolean canEditSettings = isOwner || isManager;

        // gui-gen-5 RULE 3: слот 0 — тематическая иконка меню настроек.
        inventory.setItem(0, createHead(HEAD_SETTINGS, plugin.getConfigManager().getComponent("main.settings-name"), null));

        // Рабочая зона, ряд 1 (интерьер 19-25): базовые флаги привата + msg + расширение.
        if (canEditSettings) {
            inventory.setItem(19, createToggle(HEAD_HIDE_ANCHOR, "hide", ClaimFlag.HIDE_ANCHOR));
            inventory.setItem(20, createToggle(HEAD_PVP, "pvp", ClaimFlag.PVP));
            inventory.setItem(21, createToggle(HEAD_DENY_ENTRY, "deny", ClaimFlag.DENY_ENTRY));
        } else {
            inventory.setItem(19, createHead(HEAD_BARRIER, Component.text(plugin.getConfigManager().getString("settings.barrier-name")), List.of(Component.text(plugin.getConfigManager().getString("settings.barrier-lore")))));
            inventory.setItem(20, createHead(HEAD_BARRIER, Component.text(plugin.getConfigManager().getString("settings.barrier-name")), List.of(Component.text(plugin.getConfigManager().getString("settings.barrier-lore")))));
            inventory.setItem(21, createHead(HEAD_BARRIER, Component.text(plugin.getConfigManager().getString("settings.barrier-name")), List.of(Component.text(plugin.getConfigManager().getString("settings.barrier-lore")))));
        }

        String msgState = plugin.getConfigManager().getGuiText("settings.states.disabled");
        boolean screen = claim.getFlag(ClaimFlag.MSG_SCREEN);
        boolean actionbar = claim.getFlag(ClaimFlag.MSG_ACTIONBAR);
        if (screen && actionbar) msgState = plugin.getConfigManager().getGuiText("settings.states.msg-all");
        else if (screen) msgState = plugin.getConfigManager().getGuiText("settings.states.msg-screen");
        else if (actionbar) msgState = plugin.getConfigManager().getGuiText("settings.states.msg-actionbar");

        java.util.List<Component> mLore = new ArrayList<>();
        mLore.add(Component.text(plugin.getConfigManager().getGuiText("settings.states.status") + msgState));
        mLore.add(Component.text(" "));
        mLore.add(Component.text(plugin.getConfigManager().getGuiText("settings.flags.msg-desc")));

        String msgName = plugin.getConfigManager().getGuiText("settings.flags.msg");
        inventory.setItem(22, createHead(HEAD_MSG, Component.text("§e" + msgName), mLore));

        // Рабочая зона, ряд 2 (интерьер 28-34): бафы + описание.
        if (canEditSettings) {
            inventory.setItem(28, createToggle(HEAD_HASTE, "haste", ClaimFlag.PERK_HASTE));
            inventory.setItem(29, createToggle(HEAD_REGEN, "regen", ClaimFlag.PERK_REGEN));
            inventory.setItem(30, createToggle(HEAD_SATURATION, "saturation", ClaimFlag.PERK_SATURATION));
            inventory.setItem(31, createToggle(HEAD_CROP_GROWTH, "crop_growth", ClaimFlag.PERK_CROP_GROWTH));
        } else {
            inventory.setItem(28, createHead(HEAD_BARRIER, Component.text(plugin.getConfigManager().getString("settings.barrier-name")), List.of(Component.text(plugin.getConfigManager().getString("settings.barrier-lore")))));
            inventory.setItem(29, createHead(HEAD_BARRIER, Component.text(plugin.getConfigManager().getString("settings.barrier-name")), List.of(Component.text(plugin.getConfigManager().getString("settings.barrier-lore")))));
            inventory.setItem(30, createHead(HEAD_BARRIER, Component.text(plugin.getConfigManager().getString("settings.barrier-name")), List.of(Component.text(plugin.getConfigManager().getString("settings.barrier-lore")))));
            inventory.setItem(31, createHead(HEAD_BARRIER, Component.text(plugin.getConfigManager().getString("settings.barrier-name")), List.of(Component.text(plugin.getConfigManager().getString("settings.barrier-lore")))));
        }

        if (canEditSettings) {
            java.util.List<Component> dLore = new ArrayList<>();
            for (String s : plugin.getConfigManager().getGuiLore("main.desc-lore")) dLore.add(Component.text(s));
            inventory.setItem(32, createHead(HEAD_DESC, Component.text(plugin.getConfigManager().getGuiText("main.desc-name")), dLore));
        } else {
            inventory.setItem(32, createHead(HEAD_BARRIER, Component.text(plugin.getConfigManager().getString("settings.barrier-name")), List.of(Component.text(plugin.getConfigManager().getString("settings.barrier-lore")))));
        }

        if (viewer.getUniqueId().equals(claim.getOwnerUuid())) {
            int currentSize = (int) Math.round(claim.getBoundingBox().getMaxX() - claim.getBoundingBox().getMinX());
            me.lovelace.loveclaims.model.ClaimTier currentTier = plugin.getAnchorManager().getTierBySize(currentSize);

            boolean hasBypassExpand = viewer.hasPermission("loveclaims.bypass.expand");
            int maxAllowedSize = currentTier != null ? currentTier.maxRadius() * 2 + 1 : currentSize;
            boolean canExpand = currentSize < maxAllowedSize || hasBypassExpand;
            String tierName = currentTier != null ? plugin.getConfigManager().getGuiText("settings.expand.tier-name", "name", currentTier.id()) : "Unknown";

            me.lovelace.loveclaims.model.UserData userData = plugin.getQuestManager().getUserData(viewer.getUniqueId());
            int points = userData.getExpansionBlocks();
            boolean hasPoints = points > 0 || hasBypassExpand;

            java.util.List<Component> lore = new ArrayList<>();
            lore.add(Component.text(plugin.getConfigManager().getGuiText("settings.expand.lore-current", "size", String.valueOf(currentSize))));
            lore.add(Component.text(plugin.getConfigManager().getGuiText("settings.expand.lore-max", "tier", tierName, "max", String.valueOf(maxAllowedSize))));
            lore.add(Component.text(plugin.getConfigManager().getGuiText("settings.expand.lore-points", "points", String.valueOf(points))));
            lore.add(Component.text(" "));

            if (!canExpand) {
                lore.add(Component.text(plugin.getConfigManager().getGuiText("settings.expand.lore-max-reach")));
                lore.add(Component.text(plugin.getConfigManager().getGuiText("settings.expand.lore-req-tier", "next_tier", "?")));
            } else if (!hasPoints) {
                lore.add(Component.text(plugin.getConfigManager().getGuiText("settings.expand.lore-req-points")));
                lore.add(Component.text(plugin.getConfigManager().getGuiText("settings.expand.lore-do-quests")));
            } else {
                lore.add(Component.text(plugin.getConfigManager().getGuiText("settings.expand.lore-click")));
            }

            inventory.setItem(23, createHead((canExpand && hasPoints) ? HEAD_EXPAND : HEAD_BARRIER, Component.text(plugin.getConfigManager().getGuiText((canExpand && hasPoints) ? "settings.expand.can" : "settings.expand.cannot")), lore));

            // Footer Д (доп.кнопка, Исключение 3): опасное действие "удалить приват" — только у владельца.
            setFooterButtons(
                    createHead(HEAD_BARRIER, Component.text(plugin.getConfigManager().getGuiText("settings.delete")), null),
                    createHead(HEAD_BACK, Component.text(plugin.getConfigManager().getGuiText("common.back")), null),
                    createHead(HEAD_BARRIER, Component.text(plugin.getConfigManager().getGuiText("common.close")), null)
            );
        } else {
            setFooterButtons(
                    null,
                    createHead(HEAD_BACK, Component.text(plugin.getConfigManager().getGuiText("common.back")), null),
                    createHead(HEAD_BARRIER, Component.text(plugin.getConfigManager().getGuiText("common.close")), null)
            );
        }

        fillFrameGlass();
    }

    // ИСПРАВЛЕНИЕ: Убрали аргумент fallbackMat (он всегда был null)
    private ItemStack createToggle(String headBase64, String flagKey, ClaimFlag flag) {
        boolean isLockedByQuest = false;
        if (flag.name().startsWith("PERK_")) {
            me.lovelace.loveclaims.model.UserData data = plugin.getQuestManager().getUserData(viewer.getUniqueId());
            isLockedByQuest = !data.hasBuffUnlocked(flag.name()) && !viewer.hasPermission("loveclaims.bypass.flags");
        }

        boolean isLocked = isLockedByQuest;
        String name = plugin.getConfigManager().getGuiText("settings.flags." + flagKey);
        String lockAdd = isLockedByQuest ? plugin.getConfigManager().getGuiText("settings.states.locked") : " ";
        Component compName = Component.text((isLocked ? "§c" : "§e") + name + lockAdd);

        java.util.List<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.text("§7" + plugin.getConfigManager().getGuiText("settings.flags." + flagKey + "-desc")));

        boolean state = claim.getFlag(flag);
        String stateStr = state ? plugin.getConfigManager().getGuiText("settings.states.enabled") : plugin.getConfigManager().getGuiText("settings.states.disabled");

        lore.add(Component.text(plugin.getConfigManager().getGuiText("settings.states.status") + stateStr));
        lore.add(Component.text(" "));

        if (isLockedByQuest) lore.add(Component.text(plugin.getConfigManager().getGuiText("settings.states.locked-desc")));
        else lore.add(Component.text(plugin.getConfigManager().getGuiText("settings.states.click-toggle")));

        if (isLocked) return createHead(HEAD_BARRIER, compName, lore);
        if (headBase64 != null) return createHead(headBase64, compName, lore);

        ItemStack item = new ItemStack(Material.STONE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(compName);
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        boolean isOwner = viewer.getUniqueId().equals(claim.getOwnerUuid());
        boolean isManager = claim.getTrust(viewer.getUniqueId()) == TrustLevel.MANAGER;
        boolean canEditSettings = isOwner || isManager;

        if (slot == 44) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.closeInventory();
            return;
        }
        if (slot == 43) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.openInventory(new MainClaimGUI(plugin, viewer, claim).getInventory());
            return;
        }
        if (slot == 42 && isOwner) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.openInventory(new DeleteConfirmGUI(plugin, viewer, claim).getInventory());
            return;
        }
        if (slot == 32 && canEditSettings) {
            viewer.closeInventory();
            plugin.getConfigManager().playSound(viewer, "gui-click");

            // ИСПРАВЛЕНИЕ: Используем новый метод ChatListener'а
            plugin.getChatListener().setPendingDesc(viewer.getUniqueId(), claim.getId());
            viewer.sendMessage(Component.text("§eВведите новое описание в чат (или 'отмена' для выхода):"));

            return;
        }

        if (slot == 23 && isOwner) {
            boolean hasBypassExpand = viewer.hasPermission("loveclaims.bypass.expand");
            me.lovelace.loveclaims.model.UserData userData = plugin.getQuestManager().getUserData(viewer.getUniqueId());
            if (!hasBypassExpand && userData.getExpansionBlocks() < 1) {
                plugin.getConfigManager().playSound(viewer, "gui-error");
                return;
            }

            int currentSize = (int) Math.round(claim.getBoundingBox().getMaxX() - claim.getBoundingBox().getMinX());
            me.lovelace.loveclaims.model.ClaimTier currentTier = plugin.getAnchorManager().getTierBySize(currentSize);
            int maxAllowedSize = currentTier != null ? currentTier.maxRadius() * 2 + 1 : currentSize;

            if (!hasBypassExpand && currentSize >= maxAllowedSize) {
                plugin.getConfigManager().playSound(viewer, "gui-error");
                return;
            }

            org.bukkit.util.BoundingBox newBox = claim.getBoundingBox().clone().expand(0.5, 0.5, 0.5);
            if (plugin.getClaimManager().checkOverlap(claim.getWorld(), newBox, claim.getId())) {
                viewer.sendMessage(plugin.getConfigManager().getMessage("claim-overlap"));
                plugin.getConfigManager().playSound(viewer, "gui-error");
                return;
            }

            if (!hasBypassExpand) {
                userData.removeExpansionBlocks(1);
                plugin.getStorage().saveUserDataAsync(userData);
            }
            org.bukkit.util.BoundingBox oldBox = claim.getBoundingBox().clone();
            claim.setBoundingBox(newBox);
            plugin.getClaimManager().resizeClaimInCache(claim, oldBox);
            plugin.getStorage().saveClaimAsync(claim);
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> plugin.getQuestManager().saveUsers());

            plugin.getConfigManager().playSound(viewer, "anchor-place");
            me.lovelace.loveclaims.task.BorderDisplayTask.showBorder(plugin, viewer, claim.getBoundingBox(), 100);
            setMenuItems();
            return;
        }

        if (slot == 22 && canEditSettings) {
            boolean screen = claim.getFlag(ClaimFlag.MSG_SCREEN);
            boolean actionbar = claim.getFlag(ClaimFlag.MSG_ACTIONBAR);

            // ИСПРАВЛЕНИЕ: Убрали лишние redundant-условия (IDE больше не будет ругаться)
            if (screen && actionbar) {
                claim.setFlag(ClaimFlag.MSG_SCREEN, true);
                claim.setFlag(ClaimFlag.MSG_ACTIONBAR, false);
            } else if (screen) {
                claim.setFlag(ClaimFlag.MSG_SCREEN, false);
                claim.setFlag(ClaimFlag.MSG_ACTIONBAR, true);
            } else if (actionbar) {
                claim.setFlag(ClaimFlag.MSG_SCREEN, false);
                claim.setFlag(ClaimFlag.MSG_ACTIONBAR, false);
            } else {
                claim.setFlag(ClaimFlag.MSG_SCREEN, true);
                claim.setFlag(ClaimFlag.MSG_ACTIONBAR, true);
            }

            plugin.getStorage().saveFlagAsync(claim.getId(), ClaimFlag.MSG_SCREEN, claim.getFlag(ClaimFlag.MSG_SCREEN));
            plugin.getStorage().saveFlagAsync(claim.getId(), ClaimFlag.MSG_ACTIONBAR, claim.getFlag(ClaimFlag.MSG_ACTIONBAR));
            plugin.getConfigManager().playSound(viewer, "gui-click");
            setMenuItems();
            return;
        }

        ClaimFlag targetFlag = switch (slot) {
            case 19 -> ClaimFlag.HIDE_ANCHOR;
            case 20 -> ClaimFlag.PVP;
            case 21 -> ClaimFlag.DENY_ENTRY;
            case 28 -> ClaimFlag.PERK_HASTE;
            case 29 -> ClaimFlag.PERK_REGEN;
            case 30 -> ClaimFlag.PERK_SATURATION;
            case 31 -> ClaimFlag.PERK_CROP_GROWTH;
            default -> null;
        };

        if (targetFlag != null && canEditSettings) {
            if (targetFlag.name().startsWith("PERK_")) {
                me.lovelace.loveclaims.model.UserData data = plugin.getQuestManager().getUserData(viewer.getUniqueId());
                if (!data.hasBuffUnlocked(targetFlag.name()) && !viewer.hasPermission("loveclaims.bypass.flags")) {
                    plugin.getConfigManager().playSound(viewer, "gui-error");
                    return;
                }
            }

            boolean newState = !claim.getFlag(targetFlag);
            claim.setFlag(targetFlag, newState);
            plugin.getStorage().saveFlagAsync(claim.getId(), targetFlag, newState);
            if (targetFlag == ClaimFlag.HIDE_ANCHOR) {
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
        }
    }
}
