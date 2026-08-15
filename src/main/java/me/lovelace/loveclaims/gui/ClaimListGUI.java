package me.lovelace.loveclaims.gui;

import me.lovelace.loveclaims.LoveClaims;
import static me.lovelace.loveclaims.textures.HeadTextures.*;
import me.lovelace.loveclaims.model.Claim;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

public class ClaimListGUI extends AbstractGUI {
    private static final int PAGE_SIZE = CONTENT_SLOTS_54.length;

    private final LoveClaims plugin;
    private final Player viewer;
    private final List<Claim> accessibleClaims = new ArrayList<>();
    private int page = 0;

    public ClaimListGUI(LoveClaims plugin, Player viewer) {
        super(54, Component.text(plugin.getConfigManager().getGuiText("list.title")));
        this.plugin = plugin;
        this.viewer = viewer;

        for (Claim c : plugin.getClaimManager().getAllClaims()) {
            if (c.getOwnerUuid().equals(viewer.getUniqueId()) || c.getTrust(viewer.getUniqueId()) != me.lovelace.loveclaims.model.TrustLevel.NONE) {
                accessibleClaims.add(c);
            }
        }
        setMenuItems();
    }

    @Override
    protected void setMenuItems() {
        inventory.clear();

        // gui-gen-5 RULE 3: слот 0 — профиль напрямую (это список приватов самого игрока).
        ItemStack self = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta selfMeta = (SkullMeta) self.getItemMeta();
        if (selfMeta != null) {
            selfMeta.setOwningPlayer(viewer);
            selfMeta.displayName(Component.text("§e" + viewer.getName()));
            self.setItemMeta(selfMeta);
        }
        inventory.setItem(0, self);

        int totalPages = Math.max(1, (int) Math.ceil(accessibleClaims.size() / (double) PAGE_SIZE));
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, accessibleClaims.size());

        for (int i = start; i < end; i++) {
            Claim claim = accessibleClaims.get(i);
            int contentSlot = CONTENT_SLOTS_54[i - start];

            String ownerName = Bukkit.getOfflinePlayer(claim.getOwnerUuid()).getName();
            if (ownerName == null) ownerName = "Неизвестно";

            me.lovelace.loveclaims.model.TrustLevel role = claim.getTrust(viewer.getUniqueId());
            if (claim.getOwnerUuid().equals(viewer.getUniqueId())) role = me.lovelace.loveclaims.model.TrustLevel.OWNER;
            String roleName = plugin.getConfigManager().getConfig().getString("claim.roles." + role.name(), role.name());

            int x = claim.getAnchorLocation().getBlockX();
            int y = claim.getAnchorLocation().getBlockY();
            int z = claim.getAnchorLocation().getBlockZ();

            List<Component> lore = new ArrayList<>();
            for (String s : plugin.getConfigManager().getGuiLore("list.claim-lore", "owner", ownerName, "role", roleName)) {
                lore.add(Component.text(s));
            }

            inventory.setItem(contentSlot, createHead(HEAD_INFO, Component.text(plugin.getConfigManager().getGuiText("list.claim-name", "x", String.valueOf(x), "y", String.valueOf(y), "z", String.valueOf(z))), lore));
        }

        // Пагинация (Исключение 2, только 54-слотовое): активна только если есть больше одной страницы.
        if (page > 0) {
            inventory.setItem(PAGINATION_PREV_SLOT_54, createHead(HEAD_ARROW_LEFT, Component.text("§6← Назад"), null));
        }
        if (end < accessibleClaims.size()) {
            inventory.setItem(PAGINATION_NEXT_SLOT_54, createHead(HEAD_ARROW_RIGHT, Component.text("§6Вперёд →"), null));
        }

        // Standalone-меню (открывается напрямую командой) — Back неактивен, только Close.
        setFooterButtons(null, null, createHead(HEAD_BARRIER, Component.text(plugin.getConfigManager().getGuiText("common.close")), null));
        fillFrameGlass();
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

        if (slot == PAGINATION_PREV_SLOT_54 && page > 0) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            page--;
            setMenuItems();
            return;
        }
        if (slot == PAGINATION_NEXT_SLOT_54) {
            int totalPages = Math.max(1, (int) Math.ceil(accessibleClaims.size() / (double) PAGE_SIZE));
            if (page < totalPages - 1) {
                plugin.getConfigManager().playSound(viewer, "gui-click");
                page++;
                setMenuItems();
            }
            return;
        }

        int slotIndex = indexOfContentSlot(slot);
        if (slotIndex < 0) return;
        int claimIndex = page * PAGE_SIZE + slotIndex;
        if (claimIndex >= accessibleClaims.size()) return;

        Claim target = accessibleClaims.get(claimIndex);
        plugin.getConfigManager().playSound(viewer, "gui-click");
        viewer.openInventory(new MainClaimGUI(plugin, viewer, target).getInventory());
    }

    private int indexOfContentSlot(int slot) {
        for (int i = 0; i < CONTENT_SLOTS_54.length; i++) {
            if (CONTENT_SLOTS_54[i] == slot) return i;
        }
        return -1;
    }
}
