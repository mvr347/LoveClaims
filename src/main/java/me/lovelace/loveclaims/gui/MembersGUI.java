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

public class MembersGUI extends AbstractGUI {
    private static final int PAGE_SIZE = CONTENT_SLOTS_54.length;
    // Псевдо-запись "Добавить участника" маркируется этим UUID в отрисованном списке (у настоящих
    // участников claim.getMembers() UUID не бывает null).
    private static final UUID ADD_MEMBER_MARKER = null;

    private final LoveClaims plugin;
    private final Player viewer;
    private final Claim claim;
    private int page = 0;

    // Плоский список отрисованных записей текущей страницы: реальный UUID участника, либо
    // ADD_MEMBER_MARKER (null) для кнопки добавления — используется для сопоставления клика со слотом.
    private final List<UUID> renderedEntries = new ArrayList<>();

    public MembersGUI(LoveClaims plugin, Player viewer, Claim claim) {
        super(54, plugin.getConfigManager().getComponent("members.title"));
        this.plugin = plugin;
        this.viewer = viewer;
        this.claim = claim;
        setMenuItems();
    }

    @Override
    protected void setMenuItems() {
        inventory.clear();
        renderedEntries.clear();

        me.lovelace.loveclaims.model.UserData userData = plugin.getQuestManager().getUserData(claim.getOwnerUuid());
        int defaultLimit = plugin.getConfigManager().getConfig().getInt("claim.members.default-limit", 5);
        int absoluteLimit = plugin.getConfigManager().getConfig().getInt("claim.members.absolute-limit", 24);
        int maxMembers = Math.min(absoluteLimit, defaultLimit + userData.getBonusMemberLimit());

        boolean isManagerOrOwner = viewer.getUniqueId().equals(claim.getOwnerUuid()) ||
                claim.getTrust(viewer.getUniqueId()) == TrustLevel.MANAGER;

        // gui-gen-5 RULE 3: слот 0 — тематическая иконка (список участников привата).
        inventory.setItem(0, createHead(HEAD_MEMBERS, plugin.getConfigManager().getComponent("members.title"), null));

        // "Добавить участника" / барьер-заглушка — псевдо-запись в начале списка, участвует
        // в пагинации как любой другой К. Рендерится только если есть что показать: либо
        // возможность добавить (менеджер/владелец при наличии места), либо информационный барьер
        // для тех, у кого нет прав — как и в исходной версии, при заполненном лимите у
        // менеджера/владельца никакой псевдо-записи не показывается вовсе.
        boolean hasPseudoEntry = isManagerOrOwner ? claim.getMembers().size() < maxMembers : true;
        if (hasPseudoEntry) {
            renderedEntries.add(ADD_MEMBER_MARKER);
        }
        renderedEntries.addAll(claim.getMembers().keySet());

        int totalPages = Math.max(1, (int) Math.ceil(renderedEntries.size() / (double) PAGE_SIZE));
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, renderedEntries.size());

        for (int i = start; i < end; i++) {
            int contentSlot = CONTENT_SLOTS_54[i - start];
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

        // Пагинация (Исключение 2, только 54-слотовое): активна только если есть больше одной страницы.
        if (page > 0) {
            inventory.setItem(PAGINATION_PREV_SLOT_54, createHead(HEAD_ARROW_LEFT, Component.text("§6← Назад"), null));
        }
        if (end < renderedEntries.size()) {
            inventory.setItem(PAGINATION_NEXT_SLOT_54, createHead(HEAD_ARROW_RIGHT, Component.text("§6Вперёд →"), null));
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
            viewer.openInventory(new MainClaimGUI(plugin, viewer, claim).getInventory());
            return;
        }

        if (slot == PAGINATION_PREV_SLOT_54 && page > 0) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            page--;
            setMenuItems();
            return;
        }
        if (slot == PAGINATION_NEXT_SLOT_54) {
            int totalPages = Math.max(1, (int) Math.ceil(renderedEntries.size() / (double) PAGE_SIZE));
            if (page < totalPages - 1) {
                plugin.getConfigManager().playSound(viewer, "gui-click");
                page++;
                setMenuItems();
            }
            return;
        }

        int slotIndex = indexOfContentSlot(slot);
        if (slotIndex < 0) return;
        int entryIndex = page * PAGE_SIZE + slotIndex;
        if (entryIndex >= renderedEntries.size()) return;

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
            viewer.openInventory(new MemberActionGUI(plugin, viewer, claim, entryId).getInventory());
        }
    }

    private int indexOfContentSlot(int slot) {
        for (int i = 0; i < CONTENT_SLOTS_54.length; i++) {
            if (CONTENT_SLOTS_54[i] == slot) return i;
        }
        return -1;
    }

    public Claim getClaim() { return this.claim; }
}
