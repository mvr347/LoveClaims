package me.lovelace.loveclaims.gui;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.gems.GemFusion;
import me.lovelace.loveclaims.gems.GemStats;
import me.lovelace.loveclaims.model.Claim;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static me.lovelace.loveclaims.textures.HeadTextures.*;

/** Confirmation for fusing a gem into an anchor (gui_gen exception 1, 9 slots): the choice is final. */
public class GemFuseConfirmGUI extends AbstractGUI {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int CONFIRM_SLOT = 1;
    private static final int CANCEL_SLOT = 7;

    private final LoveClaims plugin;
    private final Player viewer;
    private final Claim claim;
    private final GemStats gem;
    private final int inventorySlot;
    private final ItemStack gemSnapshot;

    public GemFuseConfirmGUI(LoveClaims plugin, Player viewer, Claim claim, GemStats gem,
                             int inventorySlot, ItemStack gemSnapshot) {
        super(9, noItalic(MM.deserialize("<red>Вшить гем навсегда</red>")));
        this.plugin = plugin;
        this.viewer = viewer;
        this.claim = claim;
        this.gem = gem;
        this.inventorySlot = inventorySlot;
        this.gemSnapshot = gemSnapshot.clone();
        setMenuItems();
    }

    private boolean tier3() {
        return GemFusion.needsExtraCost(claim.getAnchorLevel());
    }

    @Override
    protected void setMenuItems() {
        List<Component> lore = new ArrayList<>();
        lore.add(noItalic(MM.deserialize("<dark_gray>Подтверждение вшивания")));
        lore.add(Component.empty());
        lore.add(noItalic(MM.deserialize("<dark_gray>▪ <gray>Гем: <light_purple>" + MM.escapeTags(gem.gemId()))));
        lore.add(noItalic(MM.deserialize("<dark_gray>▪ <gray>Последствия:")));
        lore.add(noItalic(MM.deserialize("  <red>✕ <gray>Снять гем нельзя")));
        lore.add(noItalic(MM.deserialize("  <red>✕ <gray>Повторно вшить в этот якорь нельзя")));
        lore.add(noItalic(MM.deserialize("  <green>✓ <gray>Занимает <white>1</white> слот, баффы не режутся")));
        if (tier3()) {
            lore.add(Component.empty());
            lore.add(noItalic(MM.deserialize("<dark_gray>▪ <gray>Требования:")));
            long coins = GemFuseGUI.extraCoins(plugin);
            if (coins > 0) {
                boolean ok = GemFuseGUI.hasCoins(viewer, coins);
                lore.add(noItalic(MM.deserialize((ok ? "  <green>✓ " : "  <red>✕ ") + "<gray>Казна: <white>"
                        + GemFuseGUI.coinText(coins))));
            }
            for (Map.Entry<Material, Integer> e : GemFuseGUI.extraIngredients(plugin).entrySet()) {
                int have = count(e.getKey());
                boolean ok = have >= e.getValue();
                lore.add(noItalic(MM.deserialize((ok ? "  <green>✓ " : "  <red>✕ ") + "<gray>" + e.getKey().name()
                        + ": " + (ok ? "<white>" : "<red>") + have + " <dark_gray>/ <white>" + e.getValue())));
            }
        }
        lore.add(noItalic(MM.deserialize("<dark_gray><st>─────────────────────</st></dark_gray>")));
        lore.add(noItalic(MM.deserialize("<green>ЛКМ <dark_gray>— <gray>вшить навсегда")));
        inventory.setItem(CONFIRM_SLOT, createHead(HEAD_DELETE_YES, noItalic(MM.deserialize("<green>Подтвердить")), lore));
        inventory.setItem(CANCEL_SLOT, createHead(HEAD_DELETE_NO, noItalic(MM.deserialize("<red>Отмена")),
                List.of(noItalic(MM.deserialize("<dark_gray><st>─────────────────────</st></dark_gray>")),
                        noItalic(MM.deserialize("<green>ЛКМ <dark_gray>— <gray>вернуться")))));
        setGlass(0, 2, 6, 8);
    }

    private int count(Material m) {
        int total = 0;
        for (ItemStack it : viewer.getInventory().getContents()) {
            if (it != null && it.isSimilar(new ItemStack(m))) total += it.getAmount();
        }
        return total;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getSlot() == CANCEL_SLOT) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.openInventory(new GemFuseGUI(plugin, viewer).getInventory());
        } else if (event.getSlot() == CONFIRM_SLOT) {
            fuse();
        }
    }

    private void fuse() {
        GemFuseGUI back = new GemFuseGUI(plugin, viewer);
        // Re-validate everything: the menu may have been open for a while.
        if (plugin.getClaimManager().getClaimById(claim.getId()).orElse(null) != claim
                || !claim.isOwner(viewer.getUniqueId()) || claim.isRentalPlot() || claim.isClanTerritory()) {
            fail(back, "<red>Приват недоступен.</red>");
            return;
        }
        GemFusion.Check check = back.check(claim, gem);
        if (check != GemFusion.Check.OK) {
            fail(back, "<red>" + GemFuseGUI.reason(check) + "</red>");
            return;
        }
        ItemStack current = viewer.getInventory().getItem(inventorySlot);
        if (current == null || !current.isSimilar(gemSnapshot)) {
            fail(back, "<red>Гем в инвентаре изменился, повторите.</red>");
            return;
        }
        if (!plugin.getClaimManager().canApplyCandidateGems(claim, new ArrayList<>(claim.getInstalledGems()), gem)) {
            plugin.getConfigManager().playSound(viewer, "gui-error");
            viewer.sendMessage(plugin.getConfigManager().getMessage("claim-overlap"));
            viewer.openInventory(back.getInventory());
            return;
        }

        long coins = tier3() ? GemFuseGUI.extraCoins(plugin) : 0L;
        Map<Material, Integer> ingredients = tier3() ? GemFuseGUI.extraIngredients(plugin) : Map.of();
        for (Map.Entry<Material, Integer> e : ingredients.entrySet()) {
            if (count(e.getKey()) < e.getValue()) {
                fail(back, "<red>Не хватает ингредиентов: <white>" + e.getKey().name() + "</white>.</red>");
                return;
            }
        }
        if (coins > 0) {
            var eco = LoveCore.service(LoveEconomy.class);
            if (eco.isEmpty() || !eco.get().has(viewer, coins) || !eco.get().charge(viewer, coins)) {
                fail(back, "<red>Не хватает средств.</red>");
                return;
            }
        }
        for (Map.Entry<Material, Integer> e : ingredients.entrySet()) {
            viewer.getInventory().removeItem(new ItemStack(e.getKey(), e.getValue()));
        }
        if (current.getAmount() > 1) {
            current.setAmount(current.getAmount() - 1);
        } else {
            viewer.getInventory().setItem(inventorySlot, null);
        }

        claim.setPermanentGem(gem);
        plugin.getClaimManager().recalculateClaimModifiers(claim);
        plugin.getStorage().saveClaimAsync(claim);
        plugin.getEffects().playFor(viewer, claim.getAnchorLocation(), "gem-fuse");
        viewer.sendMessage(noItalic(MM.deserialize("<green>Гем вшит в якорь навсегда.</green>")));
        viewer.openInventory(new GemFuseGUI(plugin, viewer).getInventory());
    }

    private void fail(GemFuseGUI back, String message) {
        plugin.getConfigManager().playSound(viewer, "gui-error");
        viewer.sendMessage(noItalic(MM.deserialize(message)));
        viewer.openInventory(back.getInventory());
    }

    private static Component noItalic(Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }
}
