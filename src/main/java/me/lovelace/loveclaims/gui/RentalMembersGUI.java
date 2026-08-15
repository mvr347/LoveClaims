package me.lovelace.loveclaims.gui;

import me.lovelace.loveclaims.LoveClaims;
import static me.lovelace.loveclaims.textures.HeadTextures.*;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.model.TrustLevel;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RentalMembersGUI extends AbstractGUI {
    // Рабочая зона 36-слотового меню: 2 ряда по 7 интерьерных слотов (10-16, 19-25).
    private static final int[] CONTENT_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    private static final UUID ADD_MEMBER_MARKER = null;

    private final LoveClaims plugin;
    private final Player viewer;
    private final Claim claim;
    private final List<UUID> renderedEntries = new ArrayList<>();

    public RentalMembersGUI(LoveClaims plugin, Player viewer, Claim claim) {
        super(36, plugin.getConfigManager().getComponent("members.title"));
        this.plugin = plugin;
        this.viewer = viewer;
        this.claim = claim;
        setMenuItems();
    }

    @Override
    protected void setMenuItems() {
        inventory.clear();
        renderedEntries.clear();

        // Для аренды используем фиксированный лимит, без бонусов игрока — как и раньше.
        int maxMembers = 10;

        boolean isManagerOrOwner = viewer.getUniqueId().equals(claim.getOwnerUuid()) ||
                claim.getTrust(viewer.getUniqueId()) == TrustLevel.MANAGER;

        // gui-gen-5 RULE 3: слот 0 — тематическая иконка (участники арендного плота).
        inventory.setItem(0, createHead(HEAD_MEMBERS, plugin.getConfigManager().getComponent("members.title"), null));

        boolean hasPseudoEntry = isManagerOrOwner ? claim.getMembers().size() < maxMembers : true;
        if (hasPseudoEntry) renderedEntries.add(ADD_MEMBER_MARKER);
        renderedEntries.addAll(claim.getMembers().keySet());

        for (int i = 0; i < renderedEntries.size() && i < CONTENT_SLOTS.length; i++) {
            int contentSlot = CONTENT_SLOTS[i];
            UUID entryId = renderedEntries.get(i);

            if (entryId == ADD_MEMBER_MARKER) {
                if (isManagerOrOwner) {
                    List<Component> lore = claim.getMembers().isEmpty()
                            ? plugin.getConfigManager().getHelpMessage("members.empty-lore", "limit", String.valueOf(maxMembers))
                            : plugin.getConfigManager().getHelpMessage("members.add-lore", "free", String.valueOf(maxMembers - claim.getMembers().size()));
                    inventory.setItem(contentSlot, createHead(HEAD_ADD_MEMBER, plugin.getConfigManager().getComponent("members.add-name"), lore));
                } else {
                    List<Component> lore = plugin.getConfigManager().getHelpMessage("members.empty-lore", "limit", String.valueOf(maxMembers));
                    inventory.setItem(contentSlot, createHead(HEAD_BARRIER, plugin.getConfigManager().getComponent("members.no-perm-barrier"), lore));
                }
                continue;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(entryId);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(target);
                String name = target.getName() != null ? target.getName() : "Неизвестный";
                TrustLevel level = claim.getMembers().get(entryId);
                String roleName = plugin.getConfigManager().getConfig().getString("claim.roles." + level.name(), level.name());

                if (roleName.equals(level.name())) {
                    roleName = getColoredRoleName(level);
                }

                meta.displayName(plugin.getConfigManager().getComponent("members.member-name", "player", name));
                meta.lore(plugin.getConfigManager().getHelpMessage("members.member-lore", "role", roleName));
                head.setItemMeta(meta);
            }
            inventory.setItem(contentSlot, head);
        }

        setFooterButtons(
                null,
                createHead(HEAD_BACK, plugin.getConfigManager().getComponent("common.back"), null),
                createHead(HEAD_BARRIER, plugin.getConfigManager().getComponent("common.close"), null)
        );
        fillFrameGlass();
    }

    private String getColoredRoleName(TrustLevel level) {
        return plugin.getConfigManager().getString(switch (level) {
            case OWNER -> "members.role-owner";
            case MANAGER -> "members.role-manager";
            case BUILD -> "members.role-build";
            case CONTAINER -> "members.role-container";
            case ACCESS -> "members.role-access";
            case NONE -> "members.role-none";
        });
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
            viewer.openInventory(new RentalPlayerGUI(plugin, viewer, claim).getInventory());
            return;
        }

        int entryIndex = indexOfContentSlot(slot);
        if (entryIndex < 0 || entryIndex >= renderedEntries.size()) return;

        boolean isManagerOrOwner = viewer.getUniqueId().equals(claim.getOwnerUuid()) ||
                claim.getTrust(viewer.getUniqueId()) == TrustLevel.MANAGER;

        UUID entryId = renderedEntries.get(entryIndex);
        if (entryId == ADD_MEMBER_MARKER) {
            if (!isManagerOrOwner) {
                plugin.getConfigManager().playSound(viewer, "gui-error");
                viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                return;
            }
            viewer.closeInventory();
            plugin.getConfigManager().playSound(viewer, "gui-click");

            plugin.getChatListener().setPendingMember(viewer.getUniqueId(), claim.getId());
            viewer.sendMessage(plugin.getConfigManager().getComponent("chat-enter-member"));
            return;
        }

        if (entryId.equals(viewer.getUniqueId()) && claim.getTrust(viewer.getUniqueId()) == TrustLevel.MANAGER) {
            viewer.sendMessage(plugin.getConfigManager().getMessage("manager-role-error"));
            plugin.getConfigManager().playSound(viewer, "gui-error");
            return;
        }

        if (isManagerOrOwner) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            // Используем тот же MemberActionGUI, но он должен знать, куда возвращаться
            viewer.openInventory(new MemberActionGUI(plugin, viewer, claim, entryId).getInventory());
        }
    }

    private int indexOfContentSlot(int slot) {
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            if (CONTENT_SLOTS[i] == slot) return i;
        }
        return -1;
    }
}
