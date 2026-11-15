package me.lovelace.loveclaims.gui;

import me.lovelace.loveclaims.LoveClaims;
import static me.lovelace.loveclaims.textures.HeadTextures.*;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.model.ClaimPermission;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class DefaultPermissionsGUI extends AbstractGUI {
    private final LoveClaims plugin;
    private final Player viewer;
    private final Claim claim;

    public DefaultPermissionsGUI(LoveClaims plugin, Player viewer, Claim claim) {
        super(27, plugin.getConfigManager().getComponent("settings.default-access.title"));
        this.plugin = plugin;
        this.viewer = viewer;
        this.claim = claim;
        setMenuItems();
    }

    @Override
    protected void setMenuItems() {
        inventory.clear();

        // gui-gen-5: Слот 0 — тематическая голова меню
        inventory.setItem(0, createHead(HEAD_MEMBERS,
                plugin.getConfigManager().getComponent("settings.default-access.name"),
                List.of(plugin.getConfigManager().getComponent("settings.default-access.desc"))));

        // Рабочая зона: ряд 1 (слоты 9-17). Слоты 9 и 17 пустые.
        // 3 кнопки центрированы на слотах 11, 13, 15
        inventory.setItem(11, createPermissionButton(ClaimPermission.BUILD, Material.IRON_PICKAXE));
        inventory.setItem(13, createPermissionButton(ClaimPermission.CONTAINERS, Material.CHEST));
        inventory.setItem(15, createPermissionButton(ClaimPermission.INTERACT, Material.OAK_DOOR));

        // Footer: Back (25) и Close (26)
        setFooterButtons(
                null,
                createHead(HEAD_BACK, plugin.getConfigManager().getComponent("common.back"), null),
                createHead(HEAD_BARRIER, plugin.getConfigManager().getComponent("common.close"), null)
        );

        fillFrameGlass();
    }

    private ItemStack createPermissionButton(ClaimPermission permission, Material material) {
        ItemStack item = createHead(permissionTexture(permission), null, null);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String nameKey = "permissions." + permission.name().toLowerCase() + ".name";
            String descKey = "permissions." + permission.name().toLowerCase() + ".desc";

            meta.displayName(plugin.getConfigManager().getComponent(nameKey));

            List<Component> lore = new ArrayList<>();
            lore.add(plugin.getConfigManager().getComponent(descKey));
            lore.add(Component.text(" "));

            boolean enabled = claim.getDefaultPermissions().contains(permission);

            lore.add(plugin.getConfigManager().getComponent("settings.states.status")
                    .append(plugin.getConfigManager().getComponent(enabled ? "permissions.status-enabled" : "permissions.status-disabled")));
            lore.add(plugin.getConfigManager().getComponent("permissions.public-warning"));
            lore.add(Component.text(" "));
            lore.addAll(plugin.getConfigManager().getHelpMessage("permissions.click-toggle"));

            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
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
            viewer.openInventory(new SettingsGUI(plugin, viewer, claim).getInventory());
            return;
        }

        boolean canEdit = viewer.getUniqueId().equals(claim.getOwnerUuid()) || claim.isManager(viewer.getUniqueId());
        if (!canEdit) {
            plugin.getConfigManager().playSound(viewer, "gui-error");
            viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        ClaimPermission permToToggle = null;
        if (slot == 11) permToToggle = ClaimPermission.BUILD;
        else if (slot == 13) permToToggle = ClaimPermission.CONTAINERS;
        else if (slot == 15) permToToggle = ClaimPermission.INTERACT;

        if (permToToggle != null) {
            boolean current = claim.getDefaultPermissions().contains(permToToggle);
            claim.setDefaultPermission(permToToggle, !current);
            plugin.getStorage().saveClaimAsync(claim);
            plugin.getConfigManager().playSound(viewer, "gui-click");
            setMenuItems();
        }
    }
}
