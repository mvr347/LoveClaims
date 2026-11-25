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
    private static final ClaimPermission[] PERMISSIONS = {ClaimPermission.BUILD, ClaimPermission.CONTAINERS,
            ClaimPermission.INTERACT, ClaimPermission.CORE, ClaimPermission.MANAGE};

    private static int[] permissionSlots() {
        return centeredRowSlots(PERMISSIONS.length);
    }

    public MemberActionGUI(LoveClaims plugin, Player viewer, Claim claim, UUID targetId) {
        super(27, plugin.getConfigManager().getComponent("member-action.title"));
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
        String targetName = target.getName() != null ? target.getName() : plugin.getConfigManager().getString("members.unknown");

        // gui-gen-5: Слот 0 — голова настраиваемого участника
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta headMeta = (SkullMeta) head.getItemMeta();
        if (headMeta != null) {
            me.lovelace.loveclaims.util.PlayerHeads.apply(headMeta, target);
            headMeta.displayName(plugin.getConfigManager().getComponent("members.member-name", "player", me.lovelace.loveclaims.util.GuiText.esc(targetName))
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            List<Component> headLore = new ArrayList<>();
            headLore.add(me.lovelace.loveclaims.util.GuiText.name(plugin, "member-action.profile-lore"));
            headMeta.lore(headLore);
            head.setItemMeta(headMeta);
        }
        inventory.setItem(0, head);

        // Work zone: five permission toggles, centred (11-15); the side walls stay empty.
        int[] slots = permissionSlots();
        for (int i = 0; i < PERMISSIONS.length; i++) {
            inventory.setItem(slots[i], createPermissionToggle(PERMISSIONS[i], Material.STONE));
        }

        // Footer (слоты 18-26):
        // Слот 24 — кнопка выгнать
        // Слот 25 — Назад
        // Слот 26 — Закрыть
        ItemStack kickBtn = createHead(HEAD_BARRIER,
                me.lovelace.loveclaims.util.GuiText.name(plugin, "member-action.kick-button"),
                me.lovelace.loveclaims.util.GuiText.lore(plugin, "member-action.kick-button-lore"));

        setFooterButtons(
                kickBtn,
                createHead(HEAD_BACK, plugin.getConfigManager().getComponent("common.back"), null),
                createHead(HEAD_BARRIER, plugin.getConfigManager().getComponent("common.close"), null)
        );

        fillFrameGlass();
    }

    private ItemStack createPermissionToggle(ClaimPermission permission, Material material) {
        ItemStack item = createHead(permissionTexture(permission), null, null);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String nameKey = "permissions." + permission.name().toLowerCase() + ".name";
            String descKey = "permissions." + permission.name().toLowerCase() + ".desc";

            meta.displayName(plugin.getConfigManager().getComponent(nameKey).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(plugin.getConfigManager().getComponent(descKey));
            lore.add(Component.text(" "));

            boolean enabled = claim.hasPermission(targetId, permission);

            lore.add(plugin.getConfigManager().getComponent("settings.states.status")
                    .append(plugin.getConfigManager().getComponent(enabled ? "permissions.status-enabled" : "permissions.status-disabled")));
            lore.addAll(plugin.getConfigManager().getHelpMessage("permissions.click-toggle"));

            meta.lore(lore.stream().map(c -> c.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)).toList());
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
        int[] toggleSlots = permissionSlots();
        for (int i = 0; i < PERMISSIONS.length; i++) {
            if (toggleSlots[i] == slot) permToToggle = PERMISSIONS[i];
        }

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
                String permName = plugin.getConfigManager().getString("permissions." + permToToggle.name().toLowerCase() + ".name");
                onlineTarget.sendMessage(plugin.getConfigManager().getMessage("member-action.perm-changed", "name", permName,
                        "state", plugin.getConfigManager().getString(!current ? "member-action.allowed" : "member-action.denied")));
            }
        }
    }
}
