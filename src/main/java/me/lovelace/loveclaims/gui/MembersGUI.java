package me.lovelace.loveclaims.gui;

import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.model.TrustLevel;
import me.lovelace.loveclaims.util.GuiText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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

import static me.lovelace.loveclaims.textures.HeadTextures.*;

/**
 * Members of a claim (54 slots, paginated). "Add player" is the Footer button before Back; without members the
 * centre of the work zone shows a "no members" head. A plain member sees only the way to leave the claim.
 */
public class MembersGUI extends AbstractGUI {
    private static final int PAGE_SIZE = CONTENT_SLOTS_54.length;
    /** Centre of the work zone: shown when the claim has no members. */
    private static final int EMPTY_SLOT = 31;

    private final LoveClaims plugin;
    private final Player viewer;
    private final Claim claim;
    private int page = 0;
    private final List<UUID> rendered = new ArrayList<>();

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
        rendered.clear();
        int maxMembers = plugin.getClaimManager().getMemberLimit(claim);
        boolean manager = claim.isManager(viewer.getUniqueId());

        inventory.setItem(0, createHead(HEAD_MEMBERS, plugin.getConfigManager().getComponent("members.title"),
                List.of(GuiText.name(plugin, "members.profile-lore"))));

        rendered.addAll(claim.getMembers().keySet());
        int totalPages = Math.max(1, (int) Math.ceil(rendered.size() / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, totalPages - 1));
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, rendered.size());

        if (rendered.isEmpty()) {
            inventory.setItem(EMPTY_SLOT, createHead(HEAD_BARRIER, GuiText.name(plugin, "members.empty-name"),
                    GuiText.lore(plugin, "members.empty-lore", "limit", String.valueOf(maxMembers))));
        }
        for (int i = start; i < end; i++) {
            UUID id = rendered.get(i);
            OfflinePlayer target = Bukkit.getOfflinePlayer(id);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                me.lovelace.loveclaims.util.PlayerHeads.apply(meta, target);
                String name = target.getName() != null ? target.getName() : plugin.getConfigManager().getString("members.unknown");
                meta.displayName(plugin.getConfigManager().getComponent("members.member-name", "player", GuiText.esc(name))
                        .decoration(TextDecoration.ITALIC, false));
                meta.lore(GuiText.lore(plugin, loreKey(id), "role", roleName(id)));
                head.setItemMeta(meta);
            }
            inventory.setItem(CONTENT_SLOTS_54[i - start], head);
        }

        if (page > 0) {
            inventory.setItem(PAGINATION_PREV_SLOT_54, createHead(HEAD_ARROW_LEFT, GuiText.name(plugin, "members.page-prev"), null));
        }
        if (end < rendered.size()) {
            inventory.setItem(PAGINATION_NEXT_SLOT_54, createHead(HEAD_ARROW_RIGHT, GuiText.name(plugin, "members.page-next"), null));
        }

        setFooterButtons(addButton(manager, maxMembers),
                createHead(HEAD_BACK, plugin.getConfigManager().getComponent("common.back"), null),
                createHead(HEAD_BARRIER, plugin.getConfigManager().getComponent("common.close"), null));
        fillFrameGlass();
    }

    /** What clicking this member's head does for the viewer decides its lore. */
    private String loreKey(UUID id) {
        if (id.equals(viewer.getUniqueId()) && !claim.isOwner(id)) return "members.member-lore-leave";
        if (claim.isManager(viewer.getUniqueId()) && !id.equals(viewer.getUniqueId())) return "members.member-lore-manage";
        return "members.member-lore-view";
    }

    private String roleName(UUID id) {
        TrustLevel level = claim.getTrust(id);
        String configured = plugin.getConfigManager().getConfig().getString("claim.roles." + level.name(), level.name());
        return configured.equals(level.name()) ? coloredRole(level) : configured;
    }

    private String coloredRole(TrustLevel level) {
        return plugin.getConfigManager().getString(switch (level) {
            case OWNER -> "members.role-owner";
            case MANAGER -> "members.role-manager";
            case BUILD -> "members.role-build";
            case CONTAINER -> "members.role-container";
            case ACCESS -> "members.role-access";
            case NONE -> "members.role-none";
        });
    }

    /** Footer button before Back; unavailable states keep the slot and say why (RULE 10.4). */
    private ItemStack addButton(boolean manager, int maxMembers) {
        int free = maxMembers - claim.getMembers().size();
        if (manager && free > 0) {
            return createHead(HEAD_ADD_MEMBER, GuiText.name(plugin, "members.add-name"),
                    GuiText.lore(plugin, "members.add-lore", "free", String.valueOf(free)));
        }
        String reason = plugin.getConfigManager().getString(manager ? "members.reason-full" : "members.reason-perm");
        return createHead(HEAD_BARRIER, GuiText.name(plugin, "members.add-blocked-name"),
                GuiText.lore(plugin, "members.add-blocked-lore", "reason", reason));
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
        if (slot == size - 3) {
            int free = plugin.getClaimManager().getMemberLimit(claim) - claim.getMembers().size();
            if (!claim.isManager(viewer.getUniqueId()) || free <= 0) {
                plugin.getConfigManager().playSound(viewer, "gui-error");
                return;
            }
            plugin.getConfigManager().playSound(viewer, "gui-click");
            MemberInviteDialog.open(plugin, viewer, claim);
            return;
        }
        if (slot == PAGINATION_PREV_SLOT_54 && page > 0) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            page--;
            setMenuItems();
            return;
        }
        if (slot == PAGINATION_NEXT_SLOT_54) {
            int totalPages = Math.max(1, (int) Math.ceil(rendered.size() / (double) PAGE_SIZE));
            if (page < totalPages - 1) {
                plugin.getConfigManager().playSound(viewer, "gui-click");
                page++;
                setMenuItems();
            }
            return;
        }

        int index = indexOfContentSlot(slot);
        if (index < 0) return;
        int entry = page * PAGE_SIZE + index;
        if (entry >= rendered.size()) return;
        UUID id = rendered.get(entry);

        // A member's own head leads to leaving the claim; a manager's click on another member leads to rights.
        if (id.equals(viewer.getUniqueId()) && !claim.isOwner(id)) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.openInventory(new LeaveConfirmGUI(plugin, viewer, claim).getInventory());
        } else if (claim.isManager(viewer.getUniqueId()) && !id.equals(viewer.getUniqueId())) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.openInventory(new MemberActionGUI(plugin, viewer, claim, id).getInventory());
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
