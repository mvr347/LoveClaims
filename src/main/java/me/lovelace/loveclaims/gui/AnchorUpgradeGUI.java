package me.lovelace.loveclaims.gui;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.manager.AnchorManager;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.model.ClaimTier;
import me.lovelace.loveclaims.util.CoinFormat;
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
 * NPC menu for upgrading an anchor inside its tier (Обычный -> Модифицированный).
 * Grants +1 gem slot and +25% base radius.
 */
public class AnchorUpgradeGUI extends AbstractGUI {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final LoveClaims plugin;
    private final Player viewer;
    private final List<Claim> upgradeableClaims = new ArrayList<>();
    private int[] contentSlots = new int[0];
    private boolean hasHeldAnchorUpgrade = false;
    private int heldAnchorSlot = -1;

    public AnchorUpgradeGUI(LoveClaims plugin, Player viewer) {
        super(27, noItalic(MM.deserialize("<gold>Мастер Якорей</gold>")));
        this.plugin = plugin;
        this.viewer = viewer;
        setMenuItems();
    }

    @Override
    protected void setMenuItems() {
        inventory.clear();
        upgradeableClaims.clear();
        hasHeldAnchorUpgrade = false;
        heldAnchorSlot = -1;

        inventory.setItem(0, createHead(HEAD_NPC,
                noItalic(MM.deserialize("<gold>Мастер Якорей</gold>")),
                List.of(
                        noItalic(MM.deserialize("<dark_gray>Модификация якорей</dark_gray>")),
                        Component.empty(),
                        noItalic(MM.deserialize("<gray>Улучшает <white>Обычный</white> якорь в <light_purple>Модифицированный</light_purple>:")),
                        noItalic(MM.deserialize("<green>+1 <gray>слот под гемы и <green>+25% <gray>к базовому радиусу"))
                )));

        for (Claim c : plugin.getClaimManager().getClaimsByOwner(viewer.getUniqueId())) {
            if (c.isRentalPlot() || c.isClanTerritory()) continue;
            ClaimTier tier = plugin.getAnchorManager().resolveClaimTier(c);
            if (tier != null && tier.canUpgradeAtNpc()) {
                upgradeableClaims.add(c);
            }
        }

        ItemStack inHand = viewer.getInventory().getItemInMainHand();
        var heldTierOpt = plugin.getAnchorManager().getTierFromItem(inHand)
                .filter(ClaimTier::canUpgradeAtNpc);
        hasHeldAnchorUpgrade = heldTierOpt.isPresent();

        int totalEntries = upgradeableClaims.size() + (hasHeldAnchorUpgrade ? 1 : 0);
        if (totalEntries == 0) {
            contentSlots = centeredRowSlots(1);
            inventory.setItem(contentSlots[0], createHead(HEAD_BARRIER,
                    noItalic(MM.deserialize("<red>Нет якорей для модификации</red>")),
                    List.of(
                            noItalic(MM.deserialize("<gray>Возьмите обычный якорь в руку или")),
                            noItalic(MM.deserialize("<gray>установите его для улучшения."))
                    )));
        } else {
            contentSlots = centeredRowSlots(totalEntries);
            int idx = 0;
            for (int i = 0; i < upgradeableClaims.size() && idx < contentSlots.length; i++, idx++) {
                Claim claim = upgradeableClaims.get(i);
                ClaimTier current = plugin.getAnchorManager().resolveClaimTier(claim);
                ClaimTier target = plugin.getAnchorManager().getTier(current.upgradeTargetId());
                if (target == null) continue;
                String claimLabel = claim.getName() != null ? claim.getName()
                        : claim.getAnchorLocation().getBlockX() + "," + claim.getAnchorLocation().getBlockZ();
                inventory.setItem(contentSlots[idx], buildUpgradeIcon(
                        "<aqua>Приват: <white>" + claimLabel, current, target));
            }
            if (hasHeldAnchorUpgrade && idx < contentSlots.length) {
                ClaimTier current = heldTierOpt.get();
                ClaimTier target = plugin.getAnchorManager().getTier(current.upgradeTargetId());
                if (target != null) {
                    heldAnchorSlot = contentSlots[idx];
                    inventory.setItem(heldAnchorSlot, buildUpgradeIcon(
                            "<yellow>Якорь в руке", current, target));
                }
            }
        }

        // Header control button (gui_gen RULE 4: one button -> slot 4)
        inventory.setItem(headerControlSlots(1)[0], createHead(HEAD_PROMOTE,
                noItalic(MM.deserialize("<gold>Вшить гем <dark_gray>|</dark_gray> <yellow>Навсегда</yellow>")),
                List.of(
                        noItalic(MM.deserialize("<dark_gray>Постоянное улучшение якоря")),
                        Component.empty(),
                        noItalic(MM.deserialize("<gray>Один гем на якорь, занимает <white>1</white> слот")),
                        noItalic(MM.deserialize("<dark_gray><st>─────────────────────</st></dark_gray>")),
                        noItalic(MM.deserialize("<green>ЛКМ <dark_gray>— <gray>открыть вшивание")))));

        setFooterButtons(null, null, createHead(HEAD_BARRIER, plugin.getConfigManager().getComponent("common.close"), null));
        fillFrameGlass();
    }

    private ItemStack buildUpgradeIcon(String title, ClaimTier current, ClaimTier target) {
        long cost = current.upgradeCost();
        List<Component> lore = new ArrayList<>();
        lore.add(noItalic(MM.deserialize("<dark_gray>Модификация тира " + current.anchorLevel() + "</dark_gray>")));
        lore.add(Component.empty());
        lore.add(noItalic(MM.deserialize("<gray>Слоты гемов: <white>" + current.gemSlots() + " <dark_gray>→ <green>" + target.gemSlots() + " <dark_gray>(<green>+1</green>)</dark_gray>")));
        lore.add(noItalic(MM.deserialize("<gray>Базовый радиус: <white>" + current.radiusX() + " <dark_gray>→ <green>" + target.radiusX() + " <dark_gray>(<green>+25%</green>)</dark_gray>")));
        if (cost > 0) {
            lore.add(noItalic(MM.deserialize("<gray>Стоимость: <white>" + CoinFormat.formatGlyphs(cost))));
        } else {
            lore.add(noItalic(MM.deserialize("<gray>Стоимость: <green>Бесплатно")));
        }
        lore.add(noItalic(MM.deserialize("<dark_gray><st>─────────────────────</st></dark_gray>")));
        lore.add(noItalic(MM.deserialize("<green>ЛКМ <dark_gray>— <gray>улучшить якорь")));
        return createHead(HEAD_PROMOTE, noItalic(MM.deserialize(title)), lore);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        if (slot == inventory.getSize() - 1) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.closeInventory();
            return;
        }

        if (slot == headerControlSlots(1)[0]) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.openInventory(new GemFuseGUI(plugin, viewer).getInventory());
            return;
        }

        if (hasHeldAnchorUpgrade && slot == heldAnchorSlot) {
            upgradeHeldAnchor();
            return;
        }

        for (int i = 0; i < upgradeableClaims.size() && i < contentSlots.length; i++) {
            if (contentSlots[i] == slot) {
                upgradePlacedClaim(upgradeableClaims.get(i));
                return;
            }
        }
    }

    private void upgradePlacedClaim(Claim claim) {
        if (plugin.getClaimManager().getClaimById(claim.getId()).orElse(null) != claim
                || !claim.isOwner(viewer.getUniqueId())) {
            setMenuItems();
            return;
        }
        ClaimTier current = plugin.getAnchorManager().resolveClaimTier(claim);
        if (current == null || !current.canUpgradeAtNpc()) {
            setMenuItems();
            return;
        }
        long cost = current.upgradeCost();
        if (!checkAndCharge(cost)) return;

        AnchorManager.UpgradeOutcome outcome = plugin.getAnchorManager().upgradeClaimAnchor(claim);
        if (outcome == AnchorManager.UpgradeOutcome.OVERLAP) {
            refundIfCharged(cost);
            plugin.getConfigManager().playSound(viewer, "gui-error");
            viewer.sendMessage(plugin.getConfigManager().getMessage("claim-overlap"));
            return;
        }
        if (outcome != AnchorManager.UpgradeOutcome.SUCCESS) {
            refundIfCharged(cost);
            plugin.getConfigManager().playSound(viewer, "gui-error");
            setMenuItems();
            return;
        }

        plugin.getEffects().playFor(viewer, claim.getAnchorLocation(), "anchor-upgrade");
        viewer.sendMessage(noItalic(MM.deserialize("<green>Якорь привата улучшен до <light_purple>Модифицированного</light_purple>!</green>")));
        setMenuItems();
    }

    private void upgradeHeldAnchor() {
        ItemStack inHand = viewer.getInventory().getItemInMainHand();
        var tierOpt = plugin.getAnchorManager().getTierFromItem(inHand).filter(ClaimTier::canUpgradeAtNpc);
        if (tierOpt.isEmpty()) {
            setMenuItems();
            return;
        }
        ClaimTier current = tierOpt.get();
        ClaimTier target = plugin.getAnchorManager().getTier(current.upgradeTargetId());
        if (target == null) return;

        long cost = current.upgradeCost();
        if (!checkAndCharge(cost)) return;

        ItemStack upgraded = plugin.getAnchorManager().createAnchorItem(target.id(), viewer.getUniqueId(),
                plugin.getAnchorManager().readFusedGem(inHand).orElse(null),
                plugin.getAnchorManager().readBroken(inHand));
        if (upgraded == null) {
            refundIfCharged(cost);
            return;
        }

        if (inHand.getAmount() > 1) {
            inHand.setAmount(inHand.getAmount() - 1);
            viewer.getInventory().addItem(upgraded).values()
                    .forEach(left -> viewer.getWorld().dropItemNaturally(viewer.getLocation(), left));
        } else {
            viewer.getInventory().setItemInMainHand(upgraded);
        }

        plugin.getEffects().playFor(viewer, viewer.getLocation(), "anchor-upgrade");
        viewer.sendMessage(noItalic(MM.deserialize("<green>Якорь в руке улучшен до <light_purple>Модифицированного</light_purple>!</green>")));
        setMenuItems();
    }

    private boolean checkAndCharge(long cost) {
        if (cost <= 0) return true;
        var ecoOpt = LoveCore.service(LoveEconomy.class);
        if (ecoOpt.isEmpty()) {
            plugin.getConfigManager().playSound(viewer, "anchor-error");
            viewer.sendMessage(plugin.getConfigManager().getMessage("rental-no-economy"));
            return false;
        }
        LoveEconomy eco = ecoOpt.get();
        if (!eco.has(viewer, cost) || !eco.charge(viewer, cost)) {
            plugin.getConfigManager().playSound(viewer, "anchor-error");
            viewer.sendMessage(plugin.getConfigManager().getMessage("rental-insufficient-funds",
                    "price", CoinFormat.formatGlyphs(cost)));
            return false;
        }
        return true;
    }

    private void refundIfCharged(long cost) {
        if (cost <= 0) return;
        LoveCore.service(LoveEconomy.class).ifPresent(eco -> eco.give(viewer, cost));
    }

    private static Component noItalic(Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }
}
