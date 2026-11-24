package me.lovelace.loveclaims.gui;

import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.gems.ClaimModifiers;
import me.lovelace.loveclaims.gems.GemFactory;
import me.lovelace.loveclaims.gems.GemRegistry;
import me.lovelace.loveclaims.gems.GemStats;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.model.ClaimTier;
import me.lovelace.loveclaims.util.GuiText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static me.lovelace.loveclaims.textures.HeadTextures.*;

/**
 * 36-slot GUI for viewing, inserting, and extracting Gems in a Claim's Anchor.
 * Follows gui-gen-6 layout: Header (0-8), Work zone (10-16, 19-25; walls 9/17/18/26 empty), Footer (27-35).
 */
public class AnchorGemsGUI extends AbstractGUI {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int[] WORK_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25
    };

    private final LoveClaims plugin;
    private final Player viewer;
    private final Claim claim;
    private final List<Integer> slotToGemIndex = new ArrayList<>();

    public AnchorGemsGUI(LoveClaims plugin, Player viewer, Claim claim) {
        super(36, GuiText.name(plugin, "gems.title"));
        this.plugin = plugin;
        this.viewer = viewer;
        this.claim = claim;
        setMenuItems();
    }

    @Override
    protected void setMenuItems() {
        inventory.clear();
        slotToGemIndex.clear();

        ClaimTier tier = plugin.getAnchorManager().resolveClaimTier(claim);
        int maxSlots = tier != null ? tier.gemSlots() : 3;
        int anchorLevel = tier != null ? tier.anchorLevel() : 1;
        ClaimModifiers mods = claim.getModifiers();
        int usedSlots = mods.usedSlots();
        int freeSlots = Math.max(0, maxSlots - usedSlots);

        int overload = me.lovelace.loveclaims.gems.GemOverload.totalOverload(claim.getInstalledGems(), anchorLevel);
        List<Component> infoLore = new ArrayList<>(GuiText.lore(plugin, "gems.info-lore",
                "level", String.valueOf(anchorLevel),
                "mod", tier != null && tier.modified() ? plugin.getConfigManager().getString("gems.mod-tag") : "",
                "used", String.valueOf(usedSlots), "max", String.valueOf(maxSlots),
                "overload", String.valueOf(overload),
                "threshold", String.valueOf(me.lovelace.loveclaims.gems.GemOverload.explodeThreshold())));
        if (claim.isAnchorBroken()) {
            infoLore.addAll(GuiText.lore(plugin, "gems.info-broken"));
        }
        infoLore.addAll(GuiText.lore(plugin, "gems.info-stats",
                "radius", signed(mods.radiusBonus()), "members", signed(mods.membersBonus()),
                "crops", signedPct(mods.cropGrowthMultiplier()), "furnace", signedPct(mods.furnaceSpeedMultiplier())));
        if (mods.doubleDropChance() > 1e-6) {
            infoLore.addAll(GuiText.lore(plugin, "gems.info-double", "chance", String.valueOf(Math.round(mods.doubleDropChance() * 100))));
        }
        mods.potionEffects().forEach((type, amp) -> infoLore.addAll(GuiText.lore(plugin, "gems.info-potion",
                "effect", GemRegistry.formatPotionName(type) + " " + GemRegistry.toRoman(amp + 1))));

        inventory.setItem(0, createHead(HEAD_INFO, GuiText.name(plugin, "gems.info-name"), infoLore));

        List<GemStats> installed = claim.getInstalledGems();
        GemRegistry registry = plugin.getGemRegistry();

        int idx = 0;
        GemStats permanent = claim.getPermanentGem();
        if (permanent != null) {
            ItemStack fusedItem = registry != null ? registry.createItemFromStats(permanent) : new ItemStack(Material.EMERALD);
            if (fusedItem != null) {
                fusedItem.editMeta(meta -> {
                    List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                    lore.addAll(GuiText.lore(plugin, "gems.fused-add"));
                    meta.lore(lore);
                });
                inventory.setItem(WORK_SLOTS[idx], fusedItem);
                slotToGemIndex.add(-2); // fused gem: shown, never clickable
                idx++;
            }
        }
        for (int gemIdx = 0; gemIdx < installed.size() && idx < WORK_SLOTS.length; gemIdx++) {
            GemStats gs = installed.get(gemIdx);
            ItemStack item = registry != null ? registry.createItemFromStats(gs) : new ItemStack(Material.EMERALD);
            if (item != null) {
                item.editMeta(meta -> {
                    List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                    lore.addAll(GuiText.lore(plugin, "gems.installed-add", "cost", String.valueOf(gs.slotCost())));
                    meta.lore(lore);
                });
                inventory.setItem(WORK_SLOTS[idx], item);
                slotToGemIndex.add(gemIdx);
                idx++;
            }
        }

        for (int f = 0; f < freeSlots && idx < WORK_SLOTS.length; f++) {
            inventory.setItem(WORK_SLOTS[idx], createHead(HEAD_ADD_MEMBER, GuiText.name(plugin, "gems.free-name"),
                    GuiText.lore(plugin, "gems.free-lore", "level", String.valueOf(anchorLevel))));
            slotToGemIndex.add(-1);
            idx++;
        }

        ItemStack extraBtn = null;
        if (mods.allowLimitBypass()) {
            String bonusRad = String.valueOf(registry != null ? registry.getExtraExpansionRadius() : 10);
            extraBtn = claim.isExtraExpansionUnlocked()
                    ? createHead(HEAD_DELETE_YES, GuiText.name(plugin, "gems.extra-active-name"),
                            GuiText.lore(plugin, "gems.extra-active-lore", "radius", bonusRad))
                    : createHead(HEAD_PROMOTE, GuiText.name(plugin, "gems.extra-available-name"),
                            GuiText.lore(plugin, "gems.extra-available-lore", "radius", bonusRad));
        }

        setFooterButtons(
                extraBtn,
                createHead(HEAD_BACK, plugin.getConfigManager().getComponent("common.back"), null),
                createHead(HEAD_BARRIER, plugin.getConfigManager().getComponent("common.close"), null)
        );
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
        if (slot == size - 2) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.openInventory(new MainClaimGUI(plugin, viewer, claim).getInventory());
            return;
        }
        if (slot == size - 3 && claim.getModifiers().allowLimitBypass() && !claim.isExtraExpansionUnlocked()) {
            if (!validateClaimAccess()) return;
            claim.setExtraExpansionUnlocked(true);
            if (!plugin.getClaimManager().recalculateClaimModifiers(claim)) {
                claim.setExtraExpansionUnlocked(false);
                plugin.getConfigManager().playSound(viewer, "gui-error");
                viewer.sendMessage(plugin.getConfigManager().getMessage("claim-overlap"));
                return;
            }
            plugin.getStorage().saveClaimAsync(claim);
            plugin.getConfigManager().playSound(viewer, "anchor-place");
            viewer.sendMessage(plugin.getConfigManager().getMessage("gems.extra-done"));
            setMenuItems();
            return;
        }

        int workIdx = indexOfWorkSlot(slot);
        if (workIdx < 0 || workIdx >= slotToGemIndex.size()) return;
        int gemIdx = slotToGemIndex.get(workIdx);
        if (gemIdx < 0) return;

        // Left click on an installed gem extracts it (right click inserts a gem from the inventory).
        if (!event.isLeftClick()) return;
        if (!validateClaimAccess()) return;
        if (gemIdx >= claim.getInstalledGems().size()) {
            setMenuItems();
            return;
        }

        if (viewer.getInventory().firstEmpty() == -1) {
            plugin.getConfigManager().playSound(viewer, "gui-error");
            viewer.sendMessage(plugin.getConfigManager().getMessage("gems.inventory-full"));
            return;
        }

        List<GemStats> candidate = new ArrayList<>(claim.getInstalledGems());
        if (gemIdx >= candidate.size()) {
            setMenuItems();
            return;
        }
        candidate.remove(gemIdx);
        if (!plugin.getClaimManager().canApplyCandidateGems(claim, candidate)) {
            // Removing a negative-radius debuff increases radius and could overlap a neighbor!
            plugin.getConfigManager().playSound(viewer, "gui-error");
            viewer.sendMessage(plugin.getConfigManager().getMessage("claim-overlap"));
            return;
        }

        GemStats removed = claim.removeInstalledGem(gemIdx);
        if (removed == null) return;
        plugin.getClaimManager().recalculateClaimModifiers(claim);

        ItemStack gemItem = plugin.getGemRegistry().createItemFromStats(removed);
        if (gemItem != null) {
            viewer.getInventory().addItem(gemItem);
        }
        plugin.getStorage().saveClaimAsync(claim);
        plugin.getEffects().playFor(viewer, claim.getAnchorLocation(), "gem-extract");
        setMenuItems();
    }

    @Override
    public void handleBottomClick(InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        // Anchor core: repairs a cracked anchor
        var coreTier = me.lovelace.loveclaims.gems.AnchorCores.readTier(plugin, clicked);
        if (coreTier.isPresent()) {
            handleCoreClick(event, clicked, coreTier.getAsInt());
            return;
        }

        GemFactory factory = plugin.getGemFactory();
        if (factory == null) return;
        var statsOpt = factory.readStats(clicked);
        if (statsOpt.isEmpty()) return;

        // Right click inserts; a plain left click on a gem in the inventory only reminds how.
        if (!event.isRightClick()) {
            plugin.getConfigManager().playSound(viewer, "gui-error");
            viewer.sendMessage(plugin.getConfigManager().getMessage("gems.hint-insert"));
            return;
        }
        if (!validateClaimAccess()) return;
        if (claim.isAnchorBroken()) {
            plugin.getConfigManager().playSound(viewer, "gui-error");
            viewer.sendMessage(plugin.getConfigManager().getMessage("gems.core-cracked"));
            return;
        }

        GemStats stats = statsOpt.get();
        plugin.getClaimManager().syncAnchorLevel(claim);
        ClaimTier tier = plugin.getAnchorManager().resolveClaimTier(claim);
        int maxSlots = tier != null ? tier.gemSlots() : 3;

        int usedSlots = claim.getModifiers().usedSlots();
        if (usedSlots + stats.slotCost() > maxSlots) {
            plugin.getConfigManager().playSound(viewer, "gui-error");
            viewer.sendMessage(plugin.getConfigManager().getMessage("gems.no-slots", "need", String.valueOf(stats.slotCost()),
                    "free", String.valueOf(Math.max(0, maxSlots - usedSlots))));
            return;
        }

        List<GemStats> candidate = new ArrayList<>(claim.getInstalledGems());
        candidate.add(stats);
        if (!plugin.getClaimManager().canApplyCandidateGems(claim, candidate)) {
            plugin.getConfigManager().playSound(viewer, "gui-error");
            viewer.sendMessage(plugin.getConfigManager().getMessage("claim-overlap"));
            return;
        }

        // A gem above the anchor tier, or one that pushes overload into the risk zone, needs confirmation.
        int level = claim.getAnchorLevel();
        boolean tooStrong = me.lovelace.loveclaims.gems.GemOverload.gap(stats, level) > 0;
        boolean risky = me.lovelace.loveclaims.gems.GemOverload.breakChance(
                me.lovelace.loveclaims.gems.GemOverload.totalOverload(candidate, level)) > 0;
        if (tooStrong || risky) {
            viewer.openInventory(new GemOverloadConfirmGUI(plugin, viewer, claim, stats,
                    event.getSlot(), clicked).getInventory());
            return;
        }
        insertConfirmed(event.getSlot(), clicked, stats, null);
    }

    private void handleCoreClick(InventoryClickEvent event, ItemStack core, int coreTier) {
        if (!validateClaimAccess()) return;
        plugin.getClaimManager().syncAnchorLevel(claim);
        if (!claim.isAnchorBroken()) {
            plugin.getConfigManager().playSound(viewer, "gui-error");
            viewer.sendMessage(plugin.getConfigManager().getMessage("gems.core-intact"));
            return;
        }
        if (coreTier < claim.getAnchorLevel()) {
            plugin.getConfigManager().playSound(viewer, "gui-error");
            viewer.sendMessage(plugin.getConfigManager().getMessage("gems.core-tier", "need", String.valueOf(claim.getAnchorLevel()),
                    "have", String.valueOf(coreTier)));
            return;
        }
        if (core.getAmount() > 1) {
            core.setAmount(core.getAmount() - 1);
        } else {
            event.getClickedInventory().setItem(event.getSlot(), null);
        }
        claim.setAnchorBroken(false);
        plugin.getClaimManager().recalculateClaimModifiers(claim);
        plugin.getStorage().saveClaimAsync(claim);
        plugin.getEffects().playFor(viewer, claim.getAnchorLocation(), "core-replace");
        viewer.sendMessage(plugin.getConfigManager().getMessage("gems.core-replaced"));
        setMenuItems();
    }

    /**
     * Inserts the gem after all checks and confirmations passed: takes one item from the viewer's inventory slot,
     * applies the gem and rolls the anchor break if the claim is overloaded.
     */
    void insertConfirmed(int inventorySlot, ItemStack source, GemStats stats, Double expectedChance) {
        if (!validateClaimAccess() || claim.isAnchorBroken()) return;
        ItemStack current = viewer.getInventory().getItem(inventorySlot);
        if (current == null || !current.isSimilar(source)) {
            plugin.getConfigManager().playSound(viewer, "gui-error");
            return;
        }
        // The confirmation window may have been open for a while: another manager could have filled the slots.
        plugin.getClaimManager().syncAnchorLevel(claim);
        ClaimTier tierNow = plugin.getAnchorManager().resolveClaimTier(claim);
        int maxSlotsNow = tierNow != null ? tierNow.gemSlots() : 3;
        if (claim.getModifiers().usedSlots() + stats.slotCost() > maxSlotsNow) {
            plugin.getConfigManager().playSound(viewer, "gui-error");
            viewer.sendMessage(plugin.getConfigManager().getMessage("gems.slots-taken"));
            setMenuItems();
            return;
        }
        List<GemStats> candidate = new ArrayList<>(claim.getInstalledGems());
        candidate.add(stats);
        if (!plugin.getClaimManager().canApplyCandidateGems(claim, candidate)) {
            plugin.getConfigManager().playSound(viewer, "gui-error");
            viewer.sendMessage(plugin.getConfigManager().getMessage("claim-overlap"));
            return;
        }

        // The risk the player confirmed must be the risk they get: if another manager changed the gem set while
        // the window was open, show the new consequences and ask again.
        if (expectedChance != null) {
            double actual = me.lovelace.loveclaims.gems.GemOverload.breakChance(
                    me.lovelace.loveclaims.gems.GemOverload.totalOverload(candidate, claim.getAnchorLevel()));
            if (Math.abs(actual - expectedChance) > 1e-9) {
                plugin.getConfigManager().playSound(viewer, "gui-error");
                viewer.sendMessage(plugin.getConfigManager().getMessage("gems.conditions-changed"));
                viewer.openInventory(new GemOverloadConfirmGUI(plugin, viewer, claim, stats, inventorySlot, source).getInventory());
                return;
            }
        }

        claim.addInstalledGem(stats);
        plugin.getClaimManager().recalculateClaimModifiers(claim);
        if (current.getAmount() > 1) {
            current.setAmount(current.getAmount() - 1);
        } else {
            viewer.getInventory().setItem(inventorySlot, null);
        }

        int overload = me.lovelace.loveclaims.gems.GemOverload.totalOverload(claim.getInstalledGems(), claim.getAnchorLevel());
        double chance = me.lovelace.loveclaims.gems.GemOverload.breakChance(overload);
        if (chance > 0 && java.util.concurrent.ThreadLocalRandom.current().nextDouble() < chance) {
            breakAnchor();
        } else {
            plugin.getEffects().playFor(viewer, claim.getAnchorLocation(), "gem-insert");
        }
        plugin.getStorage().saveClaimAsync(claim);
        setMenuItems();
    }

    /** The strongest gem (largest gap, then slot cost) is destroyed and the core cracks. */
    private void breakAnchor() {
        int level = claim.getAnchorLevel();
        List<GemStats> gems = claim.getInstalledGems();
        int worst = -1;
        long worstScore = Long.MIN_VALUE;
        for (int i = 0; i < gems.size(); i++) {
            GemStats g = gems.get(i);
            long score = (long) me.lovelace.loveclaims.gems.GemOverload.gap(g, level) * 100 + g.slotCost();
            if (score >= worstScore) {
                worstScore = score;
                worst = i;
            }
        }
        if (worst >= 0) claim.removeInstalledGem(worst);
        claim.setAnchorBroken(true);
        plugin.getClaimManager().recalculateClaimModifiers(claim);
        plugin.getEffects().playFor(viewer, claim.getAnchorLocation(), "anchor-break");
        viewer.sendMessage(plugin.getConfigManager().getMessage("gems.anchor-broke"));
    }

    private boolean validateClaimAccess() {
        if (plugin.getClaimManager().getClaimById(claim.getId()).orElse(null) != claim
                || claim.isRentalPlot() || claim.isClanTerritory()
                || !claim.canManageCore(viewer.getUniqueId())) {
            viewer.closeInventory();
            viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            plugin.getConfigManager().playSound(viewer, "gui-error");
            return false;
        }
        return true;
    }

    private static int indexOfWorkSlot(int slot) {
        for (int i = 0; i < WORK_SLOTS.length; i++) {
            if (WORK_SLOTS[i] == slot) return i;
        }
        return -1;
    }

    private static String signed(int v) {
        return v >= 0 ? "<green>+" + v + "</green>" : "<red>" + v + "</red>";
    }

    private static String signedPct(double mult) {
        int pct = (int) Math.round(mult * 100.0);
        return pct >= 0 ? "<green>+" + pct + "%</green>" : "<red>" + pct + "%</red>";
    }

    private static Component noItalic(Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }
}
