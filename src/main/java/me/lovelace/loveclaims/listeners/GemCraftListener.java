package me.lovelace.loveclaims.listeners;

import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.gems.*;
import me.lovelace.loveclaims.manager.AnchorManager;
import me.lovelace.loveclaims.model.ClaimTier;
import org.bukkit.Keyed;
import org.bukkit.block.Crafter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class GemCraftListener implements Listener {
    private final LoveClaims plugin;
    private final GemRegistry registry;
    private final GemFactory factory;

    public GemCraftListener(LoveClaims plugin, GemRegistry registry, GemFactory factory) {
        this.plugin = plugin;
        this.registry = registry;
        this.factory = factory;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareGemCraft(PrepareItemCraftEvent event) {
        CraftingInventory inv = event.getInventory();
        AnchorManager anchorManager = plugin.getAnchorManager();

        List<GemStats> inputGems = new ArrayList<>();
        List<ClaimTier> inputAnchors = new ArrayList<>();
        int nonAirCount = 0;
        boolean hasCore = false;

        for (ItemStack matrixItem : inv.getMatrix()) {
            if (matrixItem == null || matrixItem.getType().isAir()) continue;
            nonAirCount++;
            hasCore |= AnchorCores.isCore(plugin, matrixItem);
            factory.readStats(matrixItem).ifPresent(inputGems::add);
            if (anchorManager != null) {
                anchorManager.getTierFromItem(matrixItem).ifPresent(inputAnchors::add);
            }
        }

        // 0. An anchor core is made of ordinary materials (copper, amethyst, echo shard), so it would otherwise be
        // consumed by unrelated vanilla recipes. It may only be used in its own upgrade recipes.
        if (hasCore && !(event.getRecipe() instanceof Keyed coreKeyed
                && (AnchorCores.isCoreRecipe(plugin, coreKeyed.getKey())
                || registry.getAnchorRecipe(coreKeyed.getKey()).isPresent()))) {
            inv.setResult(null);
            return;
        }

        // 1. Запрет крафта с участием LEGENDARY гемов
        for (GemStats gem : inputGems) {
            if (gem.rarity() == GemRarity.LEGENDARY) {
                inv.setResult(null);
                return;
            }
        }

        Recipe recipe = event.getRecipe();
        if (recipe instanceof Keyed keyed) {
            var key = keyed.getKey();

            // 2. Переход якорей I -> II -> III (требует настоящий якорь нужного уровня)
            Optional<GemRegistry.AnchorUpgradeRecipe> anchorRecipeOpt = registry.getAnchorRecipe(key);
            if (anchorRecipeOpt.isPresent()) {
                var anchorRecipe = anchorRecipeOpt.get();
                boolean noAnchorRecipe = anchorRecipe.requiredAnchorLevel() <= 0;
                if (!inputGems.isEmpty()
                        || (noAnchorRecipe ? !inputAnchors.isEmpty()
                        : inputAnchors.size() != 1 || inputAnchors.get(0).anchorLevel() != anchorRecipe.requiredAnchorLevel())) {
                    inv.setResult(null);
                    return;
                }
                // The fusion is part of the anchor: an upgraded anchor keeps the gem fused into the old one.
                for (ItemStack matrixItem : inv.getMatrix()) {
                    var fused = anchorManager != null ? anchorManager.readFusedGem(matrixItem) : Optional.<GemStats>empty();
                    if (fused.isPresent()) {
                        ItemStack carried = anchorManager.createAnchorItem(anchorRecipe.targetTierId(), null, fused.get());
                        if (carried != null) inv.setResult(carried);
                        break;
                    }
                }
                return;
            }

            // 3. Крафт модифицированного чистого гема (требует настоящие базовые гемы, не ванильное сырье)
            Optional<GemRegistry.PureGemCraftRecipe> pureRecipeOpt = registry.getPureGemRecipe(key);
            if (pureRecipeOpt.isPresent()) {
                var pureRecipe = pureRecipeOpt.get();
                long matchingBaseGems = inputGems.stream()
                        .filter(g -> g.gemId().equalsIgnoreCase(pureRecipe.requiredBaseGemId()))
                        .count();
                if (matchingBaseGems < 2 || inputGems.size() != matchingBaseGems || !inputAnchors.isEmpty()) {
                    inv.setResult(null);
                }
                return;
            }

            // 4. Крафт гибридов по зарегистрированному бесформенному рецепту
            Optional<HybridGemDescriptor> hybridRecipeOpt = registry.getHybridRecipe(key);
            if (hybridRecipeOpt.isPresent()) {
                if (nonAirCount == 2 && inputGems.size() == 2 && inputAnchors.isEmpty()) {
                    GemStats first = inputGems.get(0);
                    GemStats second = inputGems.get(1);
                    if (first.rarity() == GemRarity.BASIC && second.rarity() == GemRarity.BASIC
                            && !first.gemId().equalsIgnoreCase(second.gemId())
                            && hybridRecipeOpt.get().matchesIngredients(first.gemId(), second.gemId())) {
                        inv.setResult(registry.createHybridItem(hybridRecipeOpt.get(), HybridRollOutcome.NEUTRAL, true));
                        return;
                    }
                }
                inv.setResult(null);
                return;
            }

            // 5. Разрешаем обычные крафты наших базовых гемов, если в сетке нет чужих кастомных предметов
            if (registry.isPluginRecipe(key)) {
                if (!inputGems.isEmpty() || !inputAnchors.isEmpty()) {
                    inv.setResult(null);
                }
                return;
            }
        }

        // 6. Если в сетке ровно 2 разных базовых гема (даже без совпадения внешнего рецепта) -> гибрид
        if (nonAirCount == 2 && inputGems.size() == 2 && inputAnchors.isEmpty()) {
            GemStats first = inputGems.get(0);
            GemStats second = inputGems.get(1);
            if (first.rarity() == GemRarity.BASIC && second.rarity() == GemRarity.BASIC
                    && !first.gemId().equalsIgnoreCase(second.gemId())) {
                Optional<HybridGemDescriptor> match = registry.findHybridByIngredients(first.gemId(), second.gemId());
                if (match.isPresent()) {
                    inv.setResult(registry.createHybridItem(match.get(), HybridRollOutcome.NEUTRAL, true));
                    return;
                }
            }
        }

        // 7. Запрет использования кастомных гемов и якорей в сторонних/ванильных рецептах
        if (!inputGems.isEmpty() || !inputAnchors.isEmpty()) {
            inv.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrafterCraft(CrafterCraftEvent event) {
        // Automated Crafter blocks bypass PrepareItemCraftEvent/CraftItemEvent; block custom recipes & custom inputs.
        if (registry.isPluginRecipe(event.getRecipe().getKey())) {
            event.setCancelled(true);
            return;
        }
        if (event.getBlock().getState() instanceof Crafter crafter) {
            AnchorManager anchorManager = plugin.getAnchorManager();
            for (ItemStack item : crafter.getInventory().getContents()) {
                if (item == null || item.getType().isAir()) continue;
                if (factory.isGem(item) || AnchorCores.isCore(plugin, item)
                        || (anchorManager != null && anchorManager.isAnchor(item))) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraftGem(CraftItemEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null || result.getType().isAir()) {
            result = event.getCurrentItem();
        }
        if (result == null || result.getType().isAir()) return;

        Optional<GemStats> resultStatsOpt = factory.readStats(result);
        if (resultStatsOpt.isEmpty()) return;

        GemStats resultStats = resultStatsOpt.get();
        if (resultStats.rarity() == GemRarity.LEGENDARY) {
            event.setCancelled(true);
            return;
        }

        if (!factory.isPendingCraftRoll(result)) {
            return;
        }

        Optional<HybridGemDescriptor> hybridOpt = registry.getDescriptor(resultStats.gemId())
                .filter(d -> d instanceof HybridGemDescriptor)
                .map(d -> (HybridGemDescriptor) d);
        if (hybridOpt.isEmpty()) {
            event.setCancelled(true);
            return;
        }

        HybridGemDescriptor hybrid = hybridOpt.get();
        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }

        if (event.isShiftClick()) {
            event.setCancelled(true);
            CraftingInventory inv = event.getInventory();
            ItemStack[] matrix = inv.getMatrix();
            int maxCrafts = Integer.MAX_VALUE;
            for (ItemStack slot : matrix) {
                if (slot != null && !slot.getType().isAir()) {
                    maxCrafts = Math.min(maxCrafts, slot.getAmount());
                }
            }
            if (maxCrafts <= 0 || maxCrafts == Integer.MAX_VALUE) return;

            int crafted = 0;
            for (int i = 0; i < maxCrafts; i++) {
                if (player.getInventory().firstEmpty() == -1) break;
                ItemStack rolled = registry.createHybridItem(hybrid, null, false);
                player.getInventory().addItem(rolled);
                crafted++;
            }
            if (crafted > 0) {
                plugin.getEffects().playFor(player, player.getLocation(), "gem-fuse");
                for (int idx = 0; idx < matrix.length; idx++) {
                    ItemStack slot = matrix[idx];
                    if (slot != null && !slot.getType().isAir()) {
                        int remaining = slot.getAmount() - crafted;
                        matrix[idx] = remaining > 0 ? slot.asQuantity(remaining) : null;
                    }
                }
                inv.setMatrix(matrix);
                boolean hasRemaining = crafted < maxCrafts;
                inv.setResult(hasRemaining ? registry.createHybridItem(hybrid, HybridRollOutcome.NEUTRAL, true) : null);
            }
            return;
        }

        ClickType click = event.getClick();
        if (click == ClickType.NUMBER_KEY || click == ClickType.SWAP_OFFHAND
                || click == ClickType.DROP || click == ClickType.CONTROL_DROP
                || event.getAction() == InventoryAction.NOTHING) {
            event.setCancelled(true);
            return;
        }

        if (!event.getCursor().getType().isAir()) {
            event.setCancelled(true);
            return;
        }

        event.setCurrentItem(registry.createHybridItem(hybrid, null, false));
        plugin.getEffects().playFor(player, player.getLocation(), "gem-fuse");
    }
}
