package me.lovelace.loveclaims.gui;

import me.lovelace.loveclaims.LoveClaims;
import static me.lovelace.loveclaims.textures.HeadTextures.*;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.model.ClaimPermission;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MemberActionGUI extends AbstractGUI {
    private final LoveClaims plugin;
    private final Player viewer;
    private final Claim claim;
    private final UUID targetId;

    public MemberActionGUI(LoveClaims plugin, Player viewer, Claim claim, UUID targetId) {
        super(27, Component.text(plugin.getConfigManager().getGuiText("member-action.title")));
        this.plugin = plugin;
        this.viewer = viewer;
        this.claim = claim;
        this.targetId = targetId;
        setMenuItems();
    }

    @Override
    protected void setMenuItems() {
        inventory.clear();

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
        String targetName = target.getName() != null ? target.getName() : "Неизвестный";

        // gui-gen-5: Слот 0 — голова настраиваемого участника
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta headMeta = (SkullMeta) head.getItemMeta();
        if (headMeta != null) {
            headMeta.setOwningPlayer(target);
            headMeta.displayName(Component.text("§e" + targetName));
            List<Component> headLore = new ArrayList<>();
            headLore.add(Component.text("§7Настройка прав доступа"));
            headMeta.lore(headLore);
            head.setItemMeta(headMeta);
        }
        inventory.setItem(0, head);

        // Рабочая зона: ряд 1 (слоты 9-17). Слоты 9 и 17 пустые.
        // 4 кнопки переключения прав: 10, 12, 14, 16
        inventory.setItem(10, createPermissionToggle(ClaimPermission.BUILD, Material.IRON_PICKAXE));
        inventory.setItem(12, createPermissionToggle(ClaimPermission.CONTAINERS, Material.CHEST));
        inventory.setItem(14, createPermissionToggle(ClaimPermission.INTERACT, Material.OAK_DOOR));
        inventory.setItem(16, createPermissionToggle(ClaimPermission.MANAGE, Material.NAME_TAG));

        // Footer (слоты 18-26):
        // Слот 24 — кнопка выгнать
        // Слот 25 — Назад
        // Слот 26 — Закрыть
        ItemStack kickBtn = createHead(HEAD_BARRIER,
                Component.text(plugin.getConfigManager().getGuiText("member-action.kick-button")),
                List.of(Component.text(plugin.getConfigManager().getGuiText("member-action.kick-button-lore"))));

        setFooterButtons(
                kickBtn,
                createHead(HEAD_BACK, Component.text(plugin.getConfigManager().getGuiText("common.back")), null),
                createHead(HEAD_BARRIER, Component.text(plugin.getConfigManager().getGuiText("common.close")), null)
        );

        fillFrameGlass();
    }

    private ItemStack createPermissionToggle(ClaimPermission permission, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String nameKey = "permissions." + permission.name().toLowerCase() + ".name";
            String descKey = "permissions." + permission.name().toLowerCase() + ".desc";

            meta.displayName(Component.text(plugin.getConfigManager().getGuiText(nameKey)));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(plugin.getConfigManager().getGuiText(descKey)));
            lore.add(Component.text(" "));

            boolean enabled = claim.hasPermission(targetId, permission);
            String status = enabled
                    ? plugin.getConfigManager().getGuiText("permissions.status-enabled")
                    : plugin.getConfigManager().getGuiText("permissions.status-disabled");

            lore.add(Component.text("§7Статус: " + status));
            lore.add(Component.text(" "));
            lore.add(Component.text(plugin.getConfigManager().getGuiText("permissions.click-toggle")));

            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void openCorrectMembersGUI() {
        if (claim.isRentalPlot()) {
            viewer.openInventory(new RentalMembersGUI(plugin, viewer, claim).getInventory());
        } else {
            viewer.openInventory(new MembersGUI(plugin, viewer, claim).getInventory());
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        if (slot == 26) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.closeInventory();
            return;
        }

        if (slot == 25) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            openCorrectMembersGUI();
            return;
        }

        boolean isOwner = viewer.getUniqueId().equals(claim.getOwnerUuid());
        boolean isManager = claim.isManager(viewer.getUniqueId());

        if (!isOwner && !isManager) {
            plugin.getConfigManager().playSound(viewer, "gui-error");
            viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        if (targetId.equals(claim.getOwnerUuid())) {
            plugin.getConfigManager().playSound(viewer, "gui-error");
            return;
        }

        // Кнопка "Выгнать" на слоте 24
        if (slot == 24) {
            if (targetId.equals(viewer.getUniqueId())) {
                plugin.getConfigManager().playSound(viewer, "gui-error");
                viewer.sendMessage(plugin.getConfigManager().getMessage("manager-role-error"));
                return;
            }

            claim.getMembers().remove(targetId);
            plugin.getClaimManager().syncTrustRevoked(claim, targetId);
            plugin.getStorage().removeMemberAsync(claim.getId(), targetId);
            claim.setModified(true);

            viewer.sendMessage(plugin.getConfigManager().getMessage("member-removed"));
            plugin.getConfigManager().playSound(viewer, "anchor-break");

            Player onlineTarget = Bukkit.getPlayer(targetId);
            if (onlineTarget != null) {
                onlineTarget.sendMessage(plugin.getConfigManager().getComponent("chat-you-kicked", "player", viewer.getName()));
            }
            openCorrectMembersGUI();
            return;
        }

        ClaimPermission permToToggle = null;
        if (slot == 10) permToToggle = ClaimPermission.BUILD;
        else if (slot == 12) permToToggle = ClaimPermission.CONTAINERS;
        else if (slot == 14) permToToggle = ClaimPermission.INTERACT;
        else if (slot == 16) permToToggle = ClaimPermission.MANAGE;

        if (permToToggle != null) {
            if (permToToggle == ClaimPermission.MANAGE && !isOwner) {
                plugin.getConfigManager().playSound(viewer, "gui-error");
                viewer.sendMessage(plugin.getConfigManager().getComponent("permissions.owner-only-manage"));
                return;
            }

            if (targetId.equals(viewer.getUniqueId())) {
                plugin.getConfigManager().playSound(viewer, "gui-error");
                viewer.sendMessage(plugin.getConfigManager().getMessage("manager-role-error"));
                return;
            }

            boolean current = claim.hasPermission(targetId, permToToggle);
            claim.setPermission(targetId, permToToggle, !current);
            plugin.getStorage().saveMemberPermissionsAsync(claim.getId(), targetId, claim.getPermissions(targetId));
            plugin.getConfigManager().playSound(viewer, "gui-click");
            setMenuItems();

            Player onlineTarget = Bukkit.getPlayer(targetId);
            if (onlineTarget != null) {
                String permName = plugin.getConfigManager().getGuiText("permissions." + permToToggle.name().toLowerCase() + ".name");
                String stateText = !current ? "разрешено" : "запрещено";
                onlineTarget.sendMessage(Component.text("§7[§bLoveClaims§7] §fПраво " + permName + " §fдля вас было изменено: " + (!current ? "§a" : "§c") + stateText));
            }
        }
    }
}
