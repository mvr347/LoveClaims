package me.lovelace.loveclaims.gui;

import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.model.Claim;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static me.lovelace.loveclaims.textures.HeadTextures.*;

/**
 * Mini menu shown by /claims when a player has several claims: pick the one to open.
 * Size follows the amount: up to 7 claims in 27 slots (one centred row), up to 14 in 36,
 * more in 54 with pagination (gui_gen RULE 1, 6, 8). Root menu: no Back button.
 */
public class ClaimPickerGUI extends AbstractGUI {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final LoveClaims plugin;
    private final Player viewer;
    private final List<Claim> claims;
    private int page = 0;
    /** true: a click starts a teleport to the claim (/claim home); false: it opens the claim menu (/claim). */
    private final boolean homeMode;

    public ClaimPickerGUI(LoveClaims plugin, Player viewer, List<Claim> claims) {
        this(plugin, viewer, claims, false);
    }

    public ClaimPickerGUI(LoveClaims plugin, Player viewer, List<Claim> claims, boolean homeMode) {
        super(sizeFor(claims.size()), homeMode ? me.lovelace.loveclaims.util.GuiText.name(plugin, "picker.home-title")
                : me.lovelace.loveclaims.util.GuiText.name(plugin, "picker.title"));
        this.homeMode = homeMode;
        this.plugin = plugin;
        this.viewer = viewer;
        this.claims = new ArrayList<>(claims);
        // Owned claims first, then by creation order of the anchor position for a stable layout.
        this.claims.sort(Comparator
                .comparing((Claim c) -> !c.isOwner(viewer.getUniqueId()))
                .thenComparingInt(c -> c.getAnchorLocation() != null ? c.getAnchorLocation().getBlockX() : 0)
                .thenComparingInt(c -> c.getAnchorLocation() != null ? c.getAnchorLocation().getBlockZ() : 0));
        setMenuItems();
    }

    static int sizeFor(int count) {
        if (count <= 7) return 27;
        if (count <= 14) return 36;
        return 54;
    }

    /** Slots for {@code count} items of this page: rows are centred (gui_gen RULE 6). */
    private int[] slotsFor(int count) {
        int size = inventory.getSize();
        if (size == 54) {
            int n = Math.min(count, CONTENT_SLOTS_54.length);
            int[] slots = new int[n];
            System.arraycopy(CONTENT_SLOTS_54, 0, slots, 0, n);
            return slots;
        }
        if (size == 27) return centeredRowSlots(count);
        // 36: two rows (10-16 and 19-25), filled row by row, each row centred
        int first = Math.min(count, 7);
        int second = Math.max(0, count - 7);
        int[] a = centeredRowSlots(first);
        int[] b = centeredRowSlots(second);
        int[] out = new int[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        for (int i = 0; i < b.length; i++) out[a.length + i] = b[i] + 9;
        return out;
    }

    private int pageSize() {
        return switch (inventory.getSize()) {
            case 27 -> 7;
            case 36 -> 14;
            default -> CONTENT_SLOTS_54.length;
        };
    }

    @Override
    protected void setMenuItems() {
        inventory.clear();

        // Slot 0: the player's own head (the menu is about their claims, gui_gen RULE 3)
        ItemStack self = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta selfMeta = (SkullMeta) self.getItemMeta();
        if (selfMeta != null) {
            me.lovelace.loveclaims.util.PlayerHeads.apply(selfMeta, viewer);
            selfMeta.displayName(noItalic(MM.deserialize("<gold>" + MM.escapeTags(viewer.getName()) + "</gold>")));
            selfMeta.lore(List.of(me.lovelace.loveclaims.util.GuiText.name(plugin, "picker.profile-lore", "count", String.valueOf(claims.size()))));
            self.setItemMeta(selfMeta);
        }
        inventory.setItem(0, self);

        int per = pageSize();
        int totalPages = Math.max(1, (int) Math.ceil(claims.size() / (double) per));
        page = Math.max(0, Math.min(page, totalPages - 1));
        int start = page * per;
        int end = Math.min(start + per, claims.size());
        int[] slots = slotsFor(end - start);
        for (int i = start; i < end; i++) {
            inventory.setItem(slots[i - start], claimItem(claims.get(i), i + 1));
        }

        if (inventory.getSize() == 54) {
            if (page > 0) {
                inventory.setItem(PAGINATION_PREV_SLOT_54, createHead(HEAD_ARROW_LEFT,
                        me.lovelace.loveclaims.util.GuiText.name(plugin, "picker.page-prev"),
                        me.lovelace.loveclaims.util.GuiText.lore(plugin, "picker.page-prev-lore")));
            }
            if (page < totalPages - 1) {
                inventory.setItem(PAGINATION_NEXT_SLOT_54, createHead(HEAD_ARROW_RIGHT,
                        me.lovelace.loveclaims.util.GuiText.name(plugin, "picker.page-next"),
                        me.lovelace.loveclaims.util.GuiText.lore(plugin, "picker.page-next-lore")));
            }
        }

        // Root menu: Back is absent, Close always present (RULE 7)
        setFooterButtons(null, null, createHead(HEAD_BARRIER, plugin.getConfigManager().getComponent("common.close"), null));
        fillFrameGlass();
    }

    private ItemStack claimItem(Claim claim, int number) {
        boolean owner = claim.isOwner(viewer.getUniqueId());
        String name = claim.getName() != null && !claim.getName().isBlank() ? claim.getName()
                : plugin.getConfigManager().getString("picker.default-name", "n", String.valueOf(number));
        var anchor = claim.getAnchorLocation() != null ? claim.getAnchorLocation() : claim.getBoundingBox().getCenter().toLocation(claim.getWorld());
        var tier = plugin.getAnchorManager().resolveClaimTier(claim);
        int used = claim.getModifiers().usedSlots();
        int max = tier != null ? tier.gemSlots() : 0;
        String x = String.valueOf(anchor.getBlockX()), y = String.valueOf(anchor.getBlockY()), z = String.valueOf(anchor.getBlockZ());

        // The world is deliberately not shown: a player's claims all lie in the same world.
        List<Component> lore = new ArrayList<>();
        lore.add(me.lovelace.loveclaims.util.GuiText.name(plugin, owner ? "picker.own" : "picker.member"));
        lore.add(Component.empty());
        if (homeMode) {
            lore.addAll(me.lovelace.loveclaims.util.GuiText.lore(plugin, "picker.home-lore", "x", x, "y", y, "z", z,
                    "delay", String.valueOf(plugin.getClaimTeleport().delaySeconds())));
        } else {
            lore.addAll(me.lovelace.loveclaims.util.GuiText.lore(plugin, "picker.lore", "x", x, "y", y, "z", z,
                    "tier", String.valueOf(tier != null ? tier.anchorLevel() : "?"), "used", String.valueOf(used), "max", String.valueOf(max)));
            if (claim.isAnchorBroken()) {
                lore.add(1, me.lovelace.loveclaims.util.GuiText.name(plugin, "picker.lore-broken"));
            }
        }
        return createHead(HEAD_CLAIM, noItalic(MM.deserialize("<gold>" + escape(name) + "</gold>")), lore);
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
        int per = pageSize();
        int totalPages = Math.max(1, (int) Math.ceil(claims.size() / (double) per));
        if (size == 54) {
            if (slot == PAGINATION_PREV_SLOT_54 && page > 0) {
                page--;
                plugin.getConfigManager().playSound(viewer, "gui-click");
                setMenuItems();
                return;
            }
            if (slot == PAGINATION_NEXT_SLOT_54 && page < totalPages - 1) {
                page++;
                plugin.getConfigManager().playSound(viewer, "gui-click");
                setMenuItems();
                return;
            }
        }
        int start = page * per;
        int end = Math.min(start + per, claims.size());
        int[] slots = slotsFor(end - start);
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != slot) continue;
            Claim target = claims.get(start + i);
            if (plugin.getClaimManager().getClaimById(target.getId()).orElse(null) != target) {
                plugin.getConfigManager().playSound(viewer, "gui-error");
                viewer.closeInventory();
                return;
            }
            plugin.getConfigManager().playSound(viewer, "gui-click");
            if (homeMode) {
                viewer.closeInventory();
                plugin.getClaimTeleport().request(viewer, target);
            } else {
                viewer.openInventory(new MainClaimGUI(plugin, viewer, target).getInventory());
            }
            return;
        }
    }

    /** Claim names are player input: keep them from injecting MiniMessage tags into the menu. */
    private static String escape(String s) {
        return MM.escapeTags(s);
    }

    private static Component noItalic(Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }
}
