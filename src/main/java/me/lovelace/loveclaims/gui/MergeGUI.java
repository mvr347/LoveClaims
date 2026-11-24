package me.lovelace.loveclaims.gui;

import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.manager.ClaimMerger;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.util.MergeSeal;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static me.lovelace.loveclaims.textures.HeadTextures.*;

/**
 * Pick a neighbouring claim to merge with. Lists the claims that touch this one or lie within the configured gap;
 * each shows whether the merge is possible and, if not, why. Opened from the claim menu (so Back goes there).
 */
public class MergeGUI extends AbstractGUI {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int MAX_ENTRIES = 14;

    private final LoveClaims plugin;
    private final Player viewer;
    private final Claim claim;
    private final List<Claim> neighbours = new ArrayList<>();
    private int[] slots = new int[0];

    public MergeGUI(LoveClaims plugin, Player viewer, Claim claim) {
        super(neighbourCount(plugin, claim) > 7 ? 36 : 27, noItalic(MM.deserialize("<gold>Слияние приватов</gold>")));
        this.plugin = plugin;
        this.viewer = viewer;
        this.claim = claim;
        setMenuItems();
    }

    private static List<Claim> findNeighbours(LoveClaims plugin, Claim claim) {
        List<Claim> out = new ArrayList<>();
        ClaimMerger merger = plugin.getClaimMerger();
        for (Claim other : plugin.getClaimManager().getAllClaims()) {
            if (other.getId().equals(claim.getId()) || !merger.mergeable(other)) continue;
            if (other.getWorld() == null || !other.getWorld().equals(claim.getWorld())) continue;
            if (ClaimMerger.geometry(claim.getBoundingBox(), other.getBoundingBox(), merger.maxGap()).ok()) {
                out.add(other);
            }
            if (out.size() >= MAX_ENTRIES) break;
        }
        return out;
    }

    private static int neighbourCount(LoveClaims plugin, Claim claim) {
        return findNeighbours(plugin, claim).size();
    }

    @Override
    protected void setMenuItems() {
        inventory.clear();
        neighbours.clear();
        neighbours.addAll(findNeighbours(plugin, claim));

        inventory.setItem(0, createHead(HEAD_INFO, noItalic(MM.deserialize("<gold>Слияние приватов</gold>")), List.of(
                noItalic(MM.deserialize("<dark_gray>Объединить два привата в один")),
                Component.empty(),
                noItalic(MM.deserialize("<dark_gray>▪ <gray>Условия:")),
                noItalic(MM.deserialize("  <gray>Приваты касаются или рядом: до <white>" + (int) plugin.getClaimMerger().maxGap() + "</white> бл.")),
                noItalic(MM.deserialize("  <gray>Общая область не задевает чужие приваты")),
                noItalic(MM.deserialize("  <gray>Чужой приват: нужна <white>Печать слияния</white>")),
                noItalic(MM.deserialize("  <gray>и согласие его владельца")))));

        if (neighbours.isEmpty()) {
            slots = centeredRowSlots(1);
            inventory.setItem(slots[0], createHead(HEAD_BARRIER, noItalic(MM.deserialize("<gray>Соседи <dark_gray>|</dark_gray> <red>Недоступно")),
                    List.of(noItalic(MM.deserialize("<red>✕ Слияние недоступно")),
                            noItalic(MM.deserialize("<red>Причина: <gray>рядом нет привата для слияния")))));
        } else {
            int count = neighbours.size();
            int[] a = centeredRowSlots(Math.min(count, 7));
            int[] b = centeredRowSlots(Math.max(0, count - 7));
            slots = new int[a.length + b.length];
            System.arraycopy(a, 0, slots, 0, a.length);
            for (int i = 0; i < b.length; i++) slots[a.length + i] = b[i] + 9;
            for (int i = 0; i < neighbours.size(); i++) {
                inventory.setItem(slots[i], neighbourIcon(neighbours.get(i)));
            }
        }

        setFooterButtons(null,
                createHead(HEAD_BACK, plugin.getConfigManager().getComponent("common.back"), null),
                createHead(HEAD_BARRIER, plugin.getConfigManager().getComponent("common.close"), null));
        fillFrameGlass();
    }

    private ItemStack neighbourIcon(Claim other) {
        boolean own = other.getOwnerUuid() != null && other.getOwnerUuid().equals(viewer.getUniqueId());
        String ownerName = other.getOwnerUuid() == null ? "?" : Bukkit.getOfflinePlayer(other.getOwnerUuid()).getName();
        if (ownerName == null) ownerName = "?";
        String name = other.getName() != null && !other.getName().isBlank() ? MM.escapeTags(other.getName())
                : other.getAnchorLocation().getBlockX() + ", " + other.getAnchorLocation().getBlockZ();
        ClaimMerger.Reason reason = plugin.getClaimMerger().check(claim, other);
        boolean ok = reason == ClaimMerger.Reason.OK;

        List<Component> lore = new ArrayList<>();
        lore.add(noItalic(MM.deserialize("<dark_gray>" + (own ? "Ваш приват" : "Чужой приват"))));
        lore.add(Component.empty());
        lore.add(noItalic(MM.deserialize("<dark_gray>▪ <gray>Приват:")));
        lore.add(noItalic(MM.deserialize("  <gray>Владелец: <white>" + MM.escapeTags(ownerName))));
        lore.add(noItalic(MM.deserialize("  <gray>Якорь: <white>" + other.getAnchorLocation().getBlockX() + " "
                + other.getAnchorLocation().getBlockY() + " " + other.getAnchorLocation().getBlockZ())));
        if (!own) {
            boolean seal = MergeSeal.has(plugin, viewer);
            lore.add(noItalic(MM.deserialize((seal ? "  <green>✓ " : "  <red>✕ ") + "<gray>Печать слияния: " + (seal ? "<green>есть" : "<red>нет"))));
        }
        lore.add(noItalic(MM.deserialize("<dark_gray><st>─────────────────────</st></dark_gray>")));
        if (ok) {
            lore.add(noItalic(MM.deserialize("<green>ЛКМ <dark_gray>— <gray>объединить")));
        } else {
            lore.add(noItalic(MM.deserialize("<red>✕ Слияние недоступно")));
            lore.add(noItalic(MM.deserialize("<red>Причина: <gray>" + reasonText(reason))));
            lore.add(noItalic(MM.deserialize("<dark_gray>ЛКМ — объединить (заблокировано)")));
        }
        return createHead(ok ? HEAD_INFO : HEAD_BARRIER, noItalic(MM.deserialize("<gold>" + name + "</gold> <dark_gray>|</dark_gray> "
                + (ok ? "<green>Можно объединить</green>" : "<red>Недоступно</red>"))), lore);
    }

    static String reasonText(ClaimMerger.Reason reason) {
        return switch (reason) {
            case OK -> "";
            case SAME_CLAIM -> "это тот же приват";
            case NOT_PLAYER_CLAIM -> "аренда и клановые приваты не объединяются";
            case DIFFERENT_WORLD -> "приваты в разных мирах";
            case NOT_NEIGHBOURS -> "приваты слишком далеко друг от друга";
            case DIAGONAL -> "приваты соприкасаются только углом";
            case HAS_RENTALS -> "в приватах есть арендные участки";
            case BLOCKED_BY_OTHER_CLAIM -> "общая область заденет чужой приват";
            case MEMBER_LIMIT -> "слишком много участников после слияния";
            case NEEDS_SEAL -> "нужна Печать слияния";
        };
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
        for (int i = 0; i < neighbours.size() && i < slots.length; i++) {
            if (slots[i] != slot) continue;
            Claim other = neighbours.get(i);
            ClaimMerger.Reason reason = plugin.getClaimMerger().check(claim, other);
            if (reason != ClaimMerger.Reason.OK) {
                plugin.getEffects().playFor(viewer, "claim-merge-denied");
                viewer.sendMessage(noItalic(MM.deserialize("<red>" + reasonText(reason) + ".</red>")));
                return;
            }
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.openInventory(new MergeConfirmGUI(plugin, viewer, claim, other).getInventory());
            return;
        }
    }

    private static Component noItalic(Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }
}
