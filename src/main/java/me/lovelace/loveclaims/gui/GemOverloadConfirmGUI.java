package me.lovelace.loveclaims.gui;

import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.gems.GemOverload;
import me.lovelace.loveclaims.gems.GemStats;
import me.lovelace.loveclaims.model.Claim;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static me.lovelace.loveclaims.textures.HeadTextures.*;

/**
 * Confirmation for inserting a gem that is stronger than the anchor (gui_gen exception 1, 9-slot confirm):
 * [С][✓][С][П][П][П][С][✗][С]. Shows what will be cut and how risky the insertion is.
 */
public class GemOverloadConfirmGUI extends AbstractGUI {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int CONFIRM_SLOT = 1;
    private static final int CANCEL_SLOT = 7;

    private final LoveClaims plugin;
    private final Player viewer;
    private final Claim claim;
    private final GemStats gem;
    private final int inventorySlot;
    private final ItemStack gemItemSnapshot;
    /** Break chance shown in the window: the insertion is refused if it no longer holds at confirm time. */
    private double shownChance;

    public GemOverloadConfirmGUI(LoveClaims plugin, Player viewer, Claim claim, GemStats gem,
                                 int inventorySlot, ItemStack gemItemSnapshot) {
        super(9, noItalic(MM.deserialize("<red>Гем сильнее якоря</red>")));
        this.plugin = plugin;
        this.viewer = viewer;
        this.claim = claim;
        this.gem = gem;
        this.inventorySlot = inventorySlot;
        this.gemItemSnapshot = gemItemSnapshot.clone();
        setMenuItems();
        plugin.getEffects().playFor(viewer, claim.getAnchorLocation(), "gem-overload-warning");
    }

    @Override
    protected void setMenuItems() {
        int level = claim.getAnchorLevel();
        int gap = GemOverload.gap(gem, level);
        List<GemStats> after = new ArrayList<>(claim.getInstalledGems());
        after.add(gem);
        int overload = GemOverload.totalOverload(after, level);
        double chance = GemOverload.breakChance(overload);
        shownChance = chance;
        int percent = (int) Math.round(GemOverload.factor(gap) * 100);

        List<Component> lore = new ArrayList<>();
        lore.add(noItalic(MM.deserialize("<dark_gray>Подтверждение вставки")));
        lore.add(Component.empty());
        lore.add(noItalic(MM.deserialize("<dark_gray>▪ <gray>Последствия:")));
        if (gap > 0) {
            lore.add(noItalic(MM.deserialize("  <red>✕ <gray>Баффы гема: <white>" + percent + "% <dark_gray>(<red>разрыв " + gap + " тира<dark_gray>)")));
        }
        lore.add(noItalic(MM.deserialize("  <gray>Перегруз якоря: <white>" + overload + " <dark_gray>/ <white>" + GemOverload.explodeThreshold())));
        if (chance > 0) {
            lore.add(noItalic(MM.deserialize("  <red>✕ <gray>Шанс слома ядра: <red>" + Math.round(chance * 100) + "%")));
            lore.add(noItalic(MM.deserialize("  <gray>При сломе гем <red>пропадает<gray>, баффы выключаются")));
            lore.add(noItalic(MM.deserialize("  <gray>до замены ядра якоря")));
        } else {
            lore.add(noItalic(MM.deserialize("  <green>✓ <gray>Риск слома: <green>нет")));
        }
        lore.add(noItalic(MM.deserialize("<dark_gray><st>─────────────────────</st></dark_gray>")));
        lore.add(noItalic(MM.deserialize("<green>ЛКМ <dark_gray>— <gray>вставить гем")));

        inventory.setItem(CONFIRM_SLOT, createHead(HEAD_DELETE_YES,
                noItalic(MM.deserialize("<green>Подтвердить")), lore));
        inventory.setItem(CANCEL_SLOT, createHead(HEAD_DELETE_NO,
                noItalic(MM.deserialize("<red>Отмена")),
                List.of(noItalic(MM.deserialize("<dark_gray><st>─────────────────────</st></dark_gray>")),
                        noItalic(MM.deserialize("<green>ЛКМ <dark_gray>— <gray>вернуться к гемам")))));
        setGlass(0, 2, 6, 8);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        if (slot == CONFIRM_SLOT) {
            ItemStack current = viewer.getInventory().getItem(inventorySlot);
            if (current == null || !current.isSimilar(gemItemSnapshot) || current.getAmount() < 1) {
                plugin.getConfigManager().playSound(viewer, "gui-error");
                viewer.sendMessage(noItalic(MM.deserialize("<red>Гем в инвентаре изменился, повторите вставку.</red>")));
                viewer.openInventory(new AnchorGemsGUI(plugin, viewer, claim).getInventory());
                return;
            }
            AnchorGemsGUI gems = new AnchorGemsGUI(plugin, viewer, claim);
            viewer.openInventory(gems.getInventory());
            gems.insertConfirmed(inventorySlot, current, gem, shownChance);
        } else if (slot == CANCEL_SLOT) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.openInventory(new AnchorGemsGUI(plugin, viewer, claim).getInventory());
        }
    }

    private static Component noItalic(Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }
}
