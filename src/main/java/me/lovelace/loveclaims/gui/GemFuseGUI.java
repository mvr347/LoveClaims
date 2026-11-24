package me.lovelace.loveclaims.gui;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import dev.lovelace.lovecore.api.economy.MoneyConfig;
import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.gems.GemFusion;
import me.lovelace.loveclaims.gems.GemStats;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.model.ClaimTier;
import me.lovelace.loveclaims.util.CoinFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static me.lovelace.loveclaims.textures.HeadTextures.*;

/**
 * Fuse a gem into an anchor for good (once per claim). Pick a claim (auto-selected when only one fits), then
 * click the gem in your own inventory; a confirmation follows. Opened from the anchor master's menu, so Back
 * returns there. 27 slots: claims are centred in the work row (gui_gen RULE 6).
 */
public class GemFuseGUI extends AbstractGUI {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final LoveClaims plugin;
    private final Player viewer;
    private final List<Claim> claims = new ArrayList<>();
    private int[] slots = new int[0];
    private Claim selected;

    public GemFuseGUI(LoveClaims plugin, Player viewer) {
        super(27, noItalic(MM.deserialize("<gold>Вшить гем в якорь</gold>")));
        this.plugin = plugin;
        this.viewer = viewer;
        setMenuItems();
    }

    @Override
    protected void setMenuItems() {
        inventory.clear();
        claims.clear();
        for (Claim c : plugin.getClaimManager().getClaimsByOwner(viewer.getUniqueId())) {
            if (c.isRentalPlot() || c.isClanTerritory() || c.getPermanentGem() != null) continue;
            claims.add(c);
        }
        if (selected != null && !claims.contains(selected)) selected = null;
        if (selected == null && claims.size() == 1) selected = claims.get(0);

        inventory.setItem(0, createHead(HEAD_NPC, noItalic(MM.deserialize("<gold>Вшивание гема</gold>")), List.of(
                noItalic(MM.deserialize("<dark_gray>Постоянное улучшение якоря")),
                Component.empty(),
                noItalic(MM.deserialize("<dark_gray>▪ <gray>Правила:")),
                noItalic(MM.deserialize("  <gray>Только <white>один</white> гем за всё время")),
                noItalic(MM.deserialize("  <gray>Занимает <white>1</white> слот, не режется")),
                noItalic(MM.deserialize("  <gray>Снять нельзя, переживает перенос")),
                noItalic(MM.deserialize("  <gray>Легендарный гем: якорь <white>тира 2+")),
                noItalic(MM.deserialize("  <gray>Тир 3: <white>доп. ингредиенты")))));

        if (claims.isEmpty()) {
            slots = centeredRowSlots(1);
            inventory.setItem(slots[0], createHead(HEAD_BARRIER, noItalic(MM.deserialize("<gray>Приват <dark_gray>|</dark_gray> <red>Недоступно")),
                    List.of(noItalic(MM.deserialize("<red>✕ Вшивание недоступно")),
                            noItalic(MM.deserialize("<red>Причина: <gray>нет привата без вшитого гема")))));
        } else {
            slots = centeredRowSlots(claims.size());
            for (int i = 0; i < claims.size() && i < slots.length; i++) {
                inventory.setItem(slots[i], claimIcon(claims.get(i)));
            }
        }

        setFooterButtons(null,
                createHead(HEAD_BACK, plugin.getConfigManager().getComponent("common.back"), null),
                createHead(HEAD_BARRIER, plugin.getConfigManager().getComponent("common.close"), null));
        fillFrameGlass();
    }

    private ItemStack claimIcon(Claim claim) {
        ClaimTier tier = plugin.getAnchorManager().resolveClaimTier(claim);
        int level = tier != null ? tier.anchorLevel() : 1;
        boolean chosen = claim == selected;
        String name = claim.getName() != null && !claim.getName().isBlank() ? MM.escapeTags(claim.getName())
                : claim.getAnchorLocation().getBlockX() + ", " + claim.getAnchorLocation().getBlockZ();
        List<Component> lore = new ArrayList<>();
        lore.add(noItalic(MM.deserialize("<dark_gray>Ваш приват")));
        lore.add(Component.empty());
        lore.add(noItalic(MM.deserialize("<dark_gray>▪ <gray>Якорь:")));
        lore.add(noItalic(MM.deserialize("  <gray>Тир: <white>" + level)));
        if (claim.isAnchorBroken()) {
            lore.add(noItalic(MM.deserialize("  <red>✕ Ядро якоря треснуло")));
        }
        lore.add(noItalic(MM.deserialize("<dark_gray><st>─────────────────────</st></dark_gray>")));
        if (chosen) {
            lore.add(noItalic(MM.deserialize("<green>✓ Выбран")));
            lore.add(noItalic(MM.deserialize("<dark_gray>Кликните по гему в своём инвентаре")));
        } else {
            lore.add(noItalic(MM.deserialize("<green>ЛКМ <dark_gray>— <gray>выбрать приват")));
        }
        return createHead(chosen ? HEAD_DELETE_YES : HEAD_INFO,
                noItalic(MM.deserialize("<gold>" + name + "</gold> <dark_gray>|</dark_gray> "
                        + (chosen ? "<green>Выбран</green>" : "<yellow>Тир " + level + "</yellow>"))), lore);
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
            viewer.openInventory(new AnchorUpgradeGUI(plugin, viewer).getInventory());
            return;
        }
        for (int i = 0; i < claims.size() && i < slots.length; i++) {
            if (slots[i] == slot) {
                selected = claims.get(i);
                plugin.getConfigManager().playSound(viewer, "gui-click");
                setMenuItems();
                return;
            }
        }
    }

    @Override
    public void handleBottomClick(InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || plugin.getGemFactory() == null) return;
        var statsOpt = plugin.getGemFactory().readStats(clicked);
        if (statsOpt.isEmpty()) return;
        if (selected == null) {
            plugin.getConfigManager().playSound(viewer, "gui-error");
            viewer.sendMessage(noItalic(MM.deserialize("<red>Сначала выберите приват.</red>")));
            return;
        }
        GemFusion.Check check = check(selected, statsOpt.get());
        if (check != GemFusion.Check.OK) {
            plugin.getConfigManager().playSound(viewer, "gui-error");
            viewer.sendMessage(noItalic(MM.deserialize("<red>" + reason(check) + "</red>")));
            return;
        }
        viewer.openInventory(new GemFuseConfirmGUI(plugin, viewer, selected, statsOpt.get(),
                event.getSlot(), clicked).getInventory());
    }

    GemFusion.Check check(Claim claim, GemStats gem) {
        plugin.getClaimManager().syncAnchorLevel(claim);
        ClaimTier tier = plugin.getAnchorManager().resolveClaimTier(claim);
        int max = tier != null ? tier.gemSlots() : 0;
        return GemFusion.check(claim.getAnchorLevel(), gem, claim.getPermanentGem() != null,
                claim.isAnchorBroken(), claim.getModifiers().usedSlots(), max);
    }

    static String reason(GemFusion.Check check) {
        return switch (check) {
            case OK -> "";
            case ALREADY_FUSED -> "В этот якорь уже вшит гем, повторно нельзя.";
            case LEGENDARY_TOO_STRONG -> "Легендарный гем можно вшить только в якорь тира 2 и выше.";
            case NO_FREE_SLOT -> "Нет свободного слота под вшитый гем.";
            case ANCHOR_BROKEN -> "Ядро якоря треснуло: сначала замените ядро.";
        };
    }

    // ===== Tier 3 extra cost (config gems.fuse) =====

    /** Ingredients required at tier 3 (material to amount), from config; empty when none are configured. */
    static Map<Material, Integer> extraIngredients(LoveClaims plugin) {
        Map<Material, Integer> out = new LinkedHashMap<>();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("gems.fuse.tier-3-ingredients");
        if (sec == null) return out;
        for (String key : sec.getKeys(false)) {
            Material m = Material.matchMaterial(key);
            int amount = sec.getInt(key);
            if (m != null && amount > 0) out.put(m, amount);
        }
        return out;
    }

    static long extraCoins(LoveClaims plugin) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("gems.fuse");
        if (sec == null) return 0L;
        try {
            return MoneyConfig.get(sec, "tier-3-cost", 0L);
        } catch (Throwable t) {
            return 0L;
        }
    }

    static boolean hasCoins(Player player, long coins) {
        if (coins <= 0) return true;
        var eco = LoveCore.service(LoveEconomy.class);
        return eco.isPresent() && eco.get().has(player, coins);
    }

    static String coinText(long coins) {
        return coins > 0 ? CoinFormat.formatGlyphs(coins) : "бесплатно";
    }

    private static Component noItalic(Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }
}
