package me.lovelace.loveclaims.gui;

import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.util.GuiText;
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
        super(confirmSize(viewer), confirmTitle(viewer, GuiText.name(plugin, "gemOverloadConfirmGUI.t1")));
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
        lore.add(GuiText.name(plugin, "gemOverloadConfirmGUI.t2"));
        lore.add(Component.empty());
        lore.add(GuiText.name(plugin, "gemOverloadConfirmGUI.t3"));
        if (gap > 0) {
            lore.add(noItalic(MM.deserialize("  <red>✕ <gray>Баффы гема: <white>" + percent + "% <dark_gray>(<red>разрыв " + gap + " тира<dark_gray>)")));
        }
        lore.add(noItalic(MM.deserialize("  <gray>Перегруз якоря: <white>" + overload + " <dark_gray>/ <white>" + GemOverload.explodeThreshold())));
        if (chance > 0) {
            lore.add(noItalic(MM.deserialize("  <red>✕ <gray>Шанс слома ядра: <red>" + Math.round(chance * 100) + "%")));
            lore.add(GuiText.name(plugin, "gemOverloadConfirmGUI.t4"));
            lore.add(GuiText.name(plugin, "gemOverloadConfirmGUI.t5"));
        } else {
            lore.add(GuiText.name(plugin, "gemOverloadConfirmGUI.t6"));
        }
        lore.add(Component.empty());
        lore.add(noItalic(MM.deserialize("<dark_gray><st>─────────────────────</st></dark_gray>")));
        lore.add(GuiText.name(plugin, "gemOverloadConfirmGUI.t7"));

        placeConfirm(GuiText.name(plugin, "gemOverloadConfirmGUI.t8"), lore,
                GuiText.name(plugin, "gemOverloadConfirmGUI.t9"),
                List.of(Component.empty(), noItalic(MM.deserialize("<dark_gray><st>─────────────────────</st></dark_gray>")),
                        GuiText.name(plugin, "gemOverloadConfirmGUI.t10")));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        if (isConfirmSlot(slot)) {
            ItemStack current = viewer.getInventory().getItem(inventorySlot);
            if (current == null || !current.isSimilar(gemItemSnapshot) || current.getAmount() < 1) {
                plugin.getConfigManager().playSound(viewer, "gui-error");
                viewer.sendMessage(GuiText.name(plugin, "gemOverloadConfirmGUI.t11"));
                viewer.openInventory(new AnchorGemsGUI(plugin, viewer, claim).getInventory());
                return;
            }
            AnchorGemsGUI gems = new AnchorGemsGUI(plugin, viewer, claim);
            viewer.openInventory(gems.getInventory());
            gems.insertConfirmed(inventorySlot, current, gem, shownChance);
        } else if (isCancelSlot(slot)) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.openInventory(new AnchorGemsGUI(plugin, viewer, claim).getInventory());
        }
    }

    private static Component noItalic(Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }
}
