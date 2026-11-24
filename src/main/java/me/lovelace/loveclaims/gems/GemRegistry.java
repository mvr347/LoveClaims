package me.lovelace.loveclaims.gems;

import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.model.ClaimTier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Loads gem definitions from gems.yml, builds gem items, and manages craftable recipes
 * (strictly excluding LEGENDARY gems).
 */
public final class GemRegistry {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public record AnchorUpgradeRecipe(
            NamespacedKey key,
            String targetTierId,
            int requiredAnchorLevel,
            char anchorChar
    ) {}

    public record PureGemCraftRecipe(
            NamespacedKey key,
            String targetGemId,
            String requiredBaseGemId
    ) {}

    private final LoveClaims plugin;
    private final GemFactory factory;

    private final Map<String, GemDescriptor> descriptors = new LinkedHashMap<>();
    private final Map<String, Material> gemMaterials = new HashMap<>();
    private final Map<String, Integer> gemCmds = new HashMap<>();
    private final Map<String, String> gemNames = new HashMap<>();
    private final Map<String, List<String>> gemBaseLore = new HashMap<>();

    private final List<NamespacedKey> registeredRecipeKeys = new ArrayList<>();
    private final Map<NamespacedKey, AnchorUpgradeRecipe> anchorRecipes = new HashMap<>();
    private final Map<NamespacedKey, PureGemCraftRecipe> pureGemRecipes = new HashMap<>();
    private final Map<NamespacedKey, HybridGemDescriptor> hybridRecipes = new HashMap<>();

    private int extraExpansionRadius = 10;

    public GemRegistry(LoveClaims plugin, GemFactory factory) {
        this.plugin = plugin;
        this.factory = factory;
        reload();
    }

    public GemFactory getFactory() {
        return factory;
    }

    public int getExtraExpansionRadius() {
        return extraExpansionRadius;
    }

    public void reload() {
        unregisterRecipes();
        descriptors.clear();
        gemMaterials.clear();
        gemCmds.clear();
        gemNames.clear();
        gemBaseLore.clear();

        FileConfiguration cfg = plugin.getConfigManager().getGems();
        this.extraExpansionRadius = Math.max(1, cfg.getInt("settings.imperium-extra-expansion-radius", 10));

        ConfigurationSection pureSection = cfg.getConfigurationSection("gems");
        if (pureSection != null) {
            for (String id : pureSection.getKeys(false)) {
                ConfigurationSection sec = pureSection.getConfigurationSection(id);
                if (sec == null) continue;
                loadPureGem(id.toLowerCase(Locale.ROOT), sec);
            }
        }

        ConfigurationSection hybridSection = cfg.getConfigurationSection("hybrids");
        if (hybridSection != null) {
            for (String id : hybridSection.getKeys(false)) {
                ConfigurationSection sec = hybridSection.getConfigurationSection(id);
                if (sec == null) continue;
                loadHybridGem(id.toLowerCase(Locale.ROOT), sec);
            }
        }

        registerRecipes(cfg);
        plugin.getLogger().info("Loaded " + descriptors.size() + " gems (" + registeredRecipeKeys.size() + " craft recipes).");
    }

    private void loadPureGem(String id, ConfigurationSection sec) {
        GemRarity rarity = parseRarity(sec.getString("rarity", "BASIC"));
        Material material = parseMaterial(sec.getString("material", "EMERALD"), Material.EMERALD);
        int cmd = sec.getInt("custom-model-data", 0);
        String name = sec.getString("name", "<aqua>" + id + "</aqua>");
        List<String> lore = sec.getStringList("lore");

        GemStats stats = readStatsSection(id, rarity, rarity.slotCost(), sec);
        descriptors.put(id, new PureGemDescriptor(id, rarity, stats));
        gemMaterials.put(id, material);
        gemCmds.put(id, cmd);
        gemNames.put(id, name);
        gemBaseLore.put(id, lore);
    }

    private void loadHybridGem(String id, ConfigurationSection sec) {
        Material material = parseMaterial(sec.getString("material", "ECHO_SHARD"), Material.ECHO_SHARD);
        int cmd = sec.getInt("custom-model-data", 0);
        String name = sec.getString("name", "<light_purple>" + id + "</light_purple>");
        List<String> lore = sec.getStringList("lore");

        String firstBase = sec.getString("first-base", "").toLowerCase(Locale.ROOT);
        String secondBase = sec.getString("second-base", "").toLowerCase(Locale.ROOT);
        String firstMod = sec.getString("first-modified", "modified_" + firstBase).toLowerCase(Locale.ROOT);
        String secondMod = sec.getString("second-modified", "modified_" + secondBase).toLowerCase(Locale.ROOT);

        List<GemStats> debuffs = new ArrayList<>();
        for (Map<?, ?> entry : sec.getMapList("debuffs")) {
            int radius = getInt(entry, "radius-bonus", 0);
            int members = getInt(entry, "members-bonus", 0);
            double crop = getDouble(entry, "crop-growth-multiplier", 0.0);
            double furnace = getDouble(entry, "furnace-speed-multiplier", 0.0);
            Map<PotionEffectType, Integer> effects = new HashMap<>();
            Object rawEffects = entry.get("effects");
            if (rawEffects instanceof Map<?, ?> effMap) {
                for (Map.Entry<?, ?> e : effMap.entrySet()) {
                    PotionEffectType type = GemFactory.resolvePotionType(String.valueOf(e.getKey()));
                    if (type != null && e.getValue() instanceof Number n) {
                        effects.put(type, Math.max(0, n.intValue() - 1));
                    }
                }
            }
            debuffs.add(new GemStats(id, GemRarity.MODIFIED, GemRarity.MODIFIED.slotCost(),
                    radius, members, crop, furnace, 0.0, false, effects));
        }

        descriptors.put(id, new HybridGemDescriptor(id, firstBase, secondBase, firstMod, secondMod, debuffs));
        gemMaterials.put(id, material);
        gemCmds.put(id, cmd);
        gemNames.put(id, name);
        gemBaseLore.put(id, lore);
    }

    private GemStats readStatsSection(String id, GemRarity rarity, int slotCost, ConfigurationSection sec) {
        int radius = sec.getInt("radius-bonus", 0);
        int members = sec.getInt("members-bonus", 0);
        double crop = sec.getDouble("crop-growth-multiplier", 0.0);
        double furnace = sec.getDouble("furnace-speed-multiplier", 0.0);
        double doubleDrop = sec.getDouble("double-drop-chance", 0.0);
        boolean bypass = sec.getBoolean("allow-limit-bypass", false);

        Map<PotionEffectType, Integer> effects = new HashMap<>();
        ConfigurationSection effSec = sec.getConfigurationSection("effects");
        if (effSec != null) {
            for (String key : effSec.getKeys(false)) {
                PotionEffectType type = GemFactory.resolvePotionType(key);
                if (type != null) {
                    int level = Math.max(1, effSec.getInt(key, 1));
                    effects.put(type, level - 1);
                }
            }
        }

        return new GemStats(id, rarity, slotCost, radius, members, crop, furnace, doubleDrop, bypass, effects);
    }

    private void registerRecipes(FileConfiguration gemsCfg) {
        // 1. Craftable pure gems (STRICTLY BASIC & MODIFIED only, never LEGENDARY)
        ConfigurationSection pureSection = gemsCfg.getConfigurationSection("gems");
        if (pureSection != null) {
            for (String id : pureSection.getKeys(false)) {
                String normId = id.toLowerCase(Locale.ROOT);
                GemDescriptor desc = descriptors.get(normId);
                if (!(desc instanceof PureGemDescriptor pure)) continue;
                if (!pure.rarity().isCraftable()) continue; // Hard block for LEGENDARY

                ConfigurationSection recipeSec = pureSection.getConfigurationSection(id + ".recipe");
                if (recipeSec == null || !recipeSec.getBoolean("enabled", false)) continue;

                List<String> shape = recipeSec.getStringList("shape");
                ConfigurationSection ingSec = recipeSec.getConfigurationSection("ingredients");
                if (shape.isEmpty() || ingSec == null) continue;

                NamespacedKey key = new NamespacedKey(plugin, "gem_craft_" + normId);
                ItemStack result = createPureItem(pure, 1);
                ShapedRecipe recipe = new ShapedRecipe(key, result);
                recipe.shape(shape.toArray(new String[0]));

                boolean valid = true;
                for (String sym : ingSec.getKeys(false)) {
                    if (sym.length() != 1) continue;
                    Material mat = Material.getMaterial(ingSec.getString(sym, "").toUpperCase(Locale.ROOT));
                    if (mat == null || mat.isAir()) {
                        valid = false;
                        break;
                    }
                    recipe.setIngredient(sym.charAt(0), mat);
                }
                if (!valid) continue;

                addRecipeSafely(key, recipe);
                String baseGem = recipeSec.getString("base-gem", "");
                if (!baseGem.isBlank()) {
                    pureGemRecipes.put(key, new PureGemCraftRecipe(key, normId, baseGem.toLowerCase(Locale.ROOT)));
                }
            }
        }

        // 2. Hybrid shapeless recipes (2 different BASIC gems -> MODIFIED hybrid)
        for (GemDescriptor desc : descriptors.values()) {
            if (!(desc instanceof HybridGemDescriptor hybrid)) continue;
            if (!hybrid.rarity().isCraftable()) continue;
            Material m1 = gemMaterials.get(hybrid.firstBaseGemId());
            Material m2 = gemMaterials.get(hybrid.secondBaseGemId());
            if (m1 == null || m2 == null) continue;

            NamespacedKey key = new NamespacedKey(plugin, "gem_hybrid_" + hybrid.id());
            ShapelessRecipe recipe = new ShapelessRecipe(key, createHybridItem(hybrid, HybridRollOutcome.NEUTRAL, true));
            recipe.addIngredient(m1);
            recipe.addIngredient(m2);
            addRecipeSafely(key, recipe);
            hybridRecipes.put(key, hybrid);
        }

        // 3. Anchor level upgrade recipes (I -> II -> III)
        ConfigurationSection anchorRecipesSec = plugin.getConfigManager().getAnchors().getConfigurationSection("recipes");
        if (anchorRecipesSec != null) {
            for (String targetTierId : anchorRecipesSec.getKeys(false)) {
                ConfigurationSection sec = anchorRecipesSec.getConfigurationSection(targetTierId);
                if (sec == null || !sec.getBoolean("enabled", true)) continue;
                ClaimTier targetTier = plugin.getAnchorManager().getTier(targetTierId);
                if (targetTier == null) continue;

                // 0 = the recipe makes a tier without an older anchor (tier 1)
                int reqLevel = sec.getInt("requires-anchor-level", Math.max(1, targetTier.anchorLevel() - 1));
                char anchorChar = sec.getString("anchor-char", "A").charAt(0);
                List<String> shape = sec.getStringList("shape");
                ConfigurationSection ingSec = sec.getConfigurationSection("ingredients");
                if (shape.isEmpty() || ingSec == null) continue;

                // Register one recipe per anchor material at reqLevel (normal & modified share the craft transition)
                Set<Material> sourceMaterials = new LinkedHashSet<>();
                if (reqLevel <= 0) {
                    sourceMaterials.add(null); // single recipe, no anchor in the grid
                } else {
                    for (ClaimTier candidate : plugin.getAnchorManager().getAllTiers()) {
                        if (candidate.anchorLevel() == reqLevel) {
                            sourceMaterials.add(candidate.material());
                        }
                    }
                }
                int idx = 0;
                for (Material srcMat : sourceMaterials) {
                    NamespacedKey key = new NamespacedKey(plugin, "anchor_upgrade_" + targetTierId.replace('-', '_') + "_" + idx++);
                    ItemStack result = plugin.getAnchorManager().createAnchorItem(targetTierId);
                    if (result == null) continue;

                    ShapedRecipe recipe = new ShapedRecipe(key, result);
                    recipe.shape(shape.toArray(new String[0]));
                    if (srcMat != null) recipe.setIngredient(anchorChar, srcMat);
                    boolean valid = true;
                    for (String sym : ingSec.getKeys(false)) {
                        if (sym.length() != 1) continue;
                        RecipeChoice choice = ingredientChoice(ingSec.get(sym));
                        if (choice == null) {
                            plugin.getLogger().warning("anchors.yml recipes." + targetTierId + ": unknown ingredient for '" + sym + "'");
                            valid = false;
                            break;
                        }
                        recipe.setIngredient(sym.charAt(0), choice);
                    }
                    if (!valid) continue;

                    addRecipeSafely(key, recipe);
                    anchorRecipes.put(key, new AnchorUpgradeRecipe(key, targetTierId, reqLevel, anchorChar));
                }
            }
        }
        AnchorCores.registerRecipes(plugin, this::addRecipeSafely);
    }

    /**
     * An ingredient from anchors.yml: a material name, a list of material names (any of them works) or
     * {@code core:N} for the anchor core of tier N. Null when nothing in it can be resolved.
     */
    private RecipeChoice ingredientChoice(Object raw) {
        if (raw instanceof List<?> list) {
            List<Material> materials = new ArrayList<>();
            for (Object o : list) {
                Material m = Material.getMaterial(String.valueOf(o).toUpperCase(Locale.ROOT));
                if (m != null && !m.isAir()) materials.add(m);
            }
            return materials.isEmpty() ? null : new RecipeChoice.MaterialChoice(materials);
        }
        if (raw == null) return null;
        String text = String.valueOf(raw).trim();
        if (text.toLowerCase(Locale.ROOT).startsWith("core:")) {
            try {
                int tier = Integer.parseInt(text.substring(5).trim());
                if (tier < 1 || tier > 3) return null;
                return new RecipeChoice.ExactChoice(AnchorCores.create(plugin, tier));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        Material m = Material.getMaterial(text.toUpperCase(Locale.ROOT));
        return m == null || m.isAir() ? null : new RecipeChoice.MaterialChoice(m);
    }

    private void addRecipeSafely(NamespacedKey key, org.bukkit.inventory.Recipe recipe) {
        try {
            Bukkit.removeRecipe(key);
            if (Bukkit.addRecipe(recipe)) {
                registeredRecipeKeys.add(key);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to register recipe " + key + ": " + t.getMessage());
        }
    }

    public void unregisterRecipes() {
        for (NamespacedKey key : registeredRecipeKeys) {
            try {
                Bukkit.removeRecipe(key);
            } catch (Throwable ignored) {
            }
        }
        registeredRecipeKeys.clear();
        anchorRecipes.clear();
        pureGemRecipes.clear();
        hybridRecipes.clear();
    }

    public Optional<GemDescriptor> getDescriptor(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(descriptors.get(id.toLowerCase(Locale.ROOT)));
    }

    public Collection<GemDescriptor> getAllDescriptors() {
        return Collections.unmodifiableCollection(descriptors.values());
    }

    public List<String> getAllGemIds() {
        return new ArrayList<>(descriptors.keySet());
    }

    public List<String> getHybridGemIds() {
        return descriptors.values().stream()
                .filter(d -> d instanceof HybridGemDescriptor)
                .map(GemDescriptor::id)
                .toList();
    }

    public Optional<HybridGemDescriptor> findHybridByIngredients(String firstGemId, String secondGemId) {
        if (firstGemId == null || secondGemId == null || firstGemId.equalsIgnoreCase(secondGemId)) {
            return Optional.empty();
        }
        for (GemDescriptor desc : descriptors.values()) {
            if (desc instanceof HybridGemDescriptor hybrid && hybrid.matchesIngredients(firstGemId, secondGemId)) {
                return Optional.of(hybrid);
            }
        }
        return Optional.empty();
    }

    public Optional<AnchorUpgradeRecipe> getAnchorRecipe(NamespacedKey key) {
        return Optional.ofNullable(anchorRecipes.get(key));
    }

    public Optional<PureGemCraftRecipe> getPureGemRecipe(NamespacedKey key) {
        return Optional.ofNullable(pureGemRecipes.get(key));
    }

    public Optional<HybridGemDescriptor> getHybridRecipe(NamespacedKey key) {
        return Optional.ofNullable(hybridRecipes.get(key));
    }

    public boolean isPluginRecipe(NamespacedKey key) {
        return key != null && registeredRecipeKeys.contains(key);
    }

    public ItemStack createGemItem(String gemId, int amount) {
        Optional<GemDescriptor> descOpt = getDescriptor(gemId);
        if (descOpt.isEmpty()) return null;
        return switch (descOpt.get()) {
            case PureGemDescriptor pure -> createPureItem(pure, amount);
            case HybridGemDescriptor hybrid -> {
                ItemStack stack = createHybridItem(hybrid, null, false);
                stack.setAmount(Math.max(1, amount));
                yield stack;
            }
        };
    }

    public ItemStack createPureItem(PureGemDescriptor pure, int amount) {
        ItemStack template = buildTemplate(pure.id());
        template.setAmount(Math.max(1, amount));
        Component displayName = noItalic(MM.deserialize(gemNames.getOrDefault(pure.id(), pure.id())));
        List<Component> lore = buildGemLore(pure.id(), pure.baseStats(), null, false);
        return factory.writeStats(template, pure.baseStats(), displayName, lore, null, false);
    }

    public ItemStack createHybridItem(HybridGemDescriptor hybrid, HybridRollOutcome forcedOutcome) {
        return createHybridItem(hybrid, forcedOutcome, false);
    }

    public ItemStack createHybridItem(HybridGemDescriptor hybrid, HybridRollOutcome forcedOutcome, boolean pendingCraftPreview) {
        HybridRollOutcome outcome = forcedOutcome != null ? forcedOutcome : factory.rollOutcome();
        GemStats rolledStats = rollHybridStats(hybrid, outcome);

        ItemStack template = buildTemplate(hybrid.id());
        template.editMeta(meta -> meta.setMaxStackSize(1));
        Component displayName = noItalic(MM.deserialize(gemNames.getOrDefault(hybrid.id(), hybrid.id())));
        List<Component> lore = buildGemLore(hybrid.id(), rolledStats, outcome, pendingCraftPreview);
        return factory.writeStats(template, rolledStats, displayName, lore, outcome, pendingCraftPreview);
    }

    /**
     * Rebuilds an ItemStack from persisted GemStats (preserving exact hybrid rolls on extraction/drop).
     */
    public ItemStack createItemFromStats(GemStats stats) {
        if (stats == null) return null;
        ItemStack template = buildTemplate(stats.gemId());
        if (getDescriptor(stats.gemId()).orElse(null) instanceof HybridGemDescriptor) {
            template.editMeta(meta -> meta.setMaxStackSize(1));
        }
        Component displayName = noItalic(MM.deserialize(gemNames.getOrDefault(stats.gemId(), "<aqua>" + stats.gemId())));
        List<Component> lore = buildGemLore(stats.gemId(), stats, stats.hybridOutcome(), false);
        return factory.writeStats(template, stats, displayName, lore, stats.hybridOutcome(), false);
    }

    public GemStats rollHybridStats(HybridGemDescriptor hybrid, HybridRollOutcome outcome) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        boolean firstIsPrimary = rng.nextBoolean();

        String primaryModId = firstIsPrimary ? hybrid.firstModifiedGemId() : hybrid.secondModifiedGemId();
        String secondaryBaseId = firstIsPrimary ? hybrid.secondBaseGemId() : hybrid.firstBaseGemId();

        GemStats primaryStats = getPureStats(primaryModId)
                .orElseGet(() -> GemStats.empty(hybrid.id(), GemRarity.MODIFIED));

        GemStats combined = new GemStats(
                hybrid.id(),
                GemRarity.MODIFIED,
                GemRarity.MODIFIED.slotCost(),
                primaryStats.radiusBonus(),
                primaryStats.membersBonus(),
                primaryStats.cropGrowthMultiplier(),
                primaryStats.furnaceSpeedMultiplier(),
                primaryStats.doubleDropChance(),
                primaryStats.allowLimitBypass(),
                primaryStats.potionEffects(),
                outcome
        );

        switch (outcome) {
            case POSITIVE -> {
                GemStats weakBonus = getPureStats(secondaryBaseId)
                        .orElseGet(() -> GemStats.empty(secondaryBaseId, GemRarity.BASIC));
                combined = combined.combineWith(weakBonus);
            }
            case NEUTRAL -> {
                // Второй эффект отсутствует
            }
            case DEBUFF -> {
                List<GemStats> pool = hybrid.debuffPool();
                if (!pool.isEmpty()) {
                    GemStats debuff = pool.get(rng.nextInt(pool.size()));
                    combined = combined.combineWith(debuff);
                }
            }
        }
        return combined;
    }

    private Optional<GemStats> getPureStats(String gemId) {
        return getDescriptor(gemId)
                .filter(d -> d instanceof PureGemDescriptor)
                .map(d -> ((PureGemDescriptor) d).baseStats());
    }

    private ItemStack buildTemplate(String gemId) {
        Material mat = gemMaterials.getOrDefault(gemId.toLowerCase(Locale.ROOT), Material.EMERALD);
        int cmd = gemCmds.getOrDefault(gemId.toLowerCase(Locale.ROOT), 0);
        ItemStack item = new ItemStack(mat);
        if (cmd > 0) {
            item.editMeta(meta -> meta.setCustomModelData(cmd));
        }
        return item;
    }

    public List<Component> buildGemLore(String gemId, GemStats stats, HybridRollOutcome outcome, boolean pendingCraftPreview) {
        List<Component> lore = new ArrayList<>();
        for (String line : gemBaseLore.getOrDefault(gemId.toLowerCase(Locale.ROOT), List.of())) {
            lore.add(noItalic(MM.deserialize(line)));
        }

        lore.add(noItalic(MM.deserialize("<dark_gray><st>─────────────────────</st></dark_gray>")));
        if (pendingCraftPreview) {
            lore.add(noItalic(MM.deserialize("<yellow>? Характеристики определятся при крафте</yellow>")));
            lore.add(noItalic(MM.deserialize("<gray>Редкость: <light_purple>Модифицированный</light_purple> <dark_gray>(" + stats.slotCost() + " сл.)</dark_gray></gray>")));
            return lore;
        }

        lore.add(noItalic(MM.deserialize("<gray>Треб. якорь: <white>Тир " + stats.rarity().minAnchorLevel()
                + "+</white> <dark_gray>(" + stats.slotCost() + " сл.)</dark_gray></gray>")));

        if (outcome != null) {
            String outcomeLabel = switch (outcome) {
                case POSITIVE -> "<green>Удачный ролл (POSITIVE)</green>";
                case NEUTRAL -> "<gray>Стабильный ролл (NEUTRAL)</gray>";
                case DEBUFF -> "<red>Нестабильный ролл (DEBUFF)</red>";
            };
            lore.add(noItalic(MM.deserialize("<gray>Исход: " + outcomeLabel)));
        }

        if (stats.radiusBonus() != 0) {
            lore.add(noItalic(formatSignedInt("Радиус привата", stats.radiusBonus(), " бл.")));
        }
        if (stats.membersBonus() != 0) {
            lore.add(noItalic(formatSignedInt("Слоты участников", stats.membersBonus(), "")));
        }
        if (Math.abs(stats.cropGrowthMultiplier()) > 1e-6) {
            lore.add(noItalic(formatSignedPercent("Скорость ферм", stats.cropGrowthMultiplier())));
        }
        if (Math.abs(stats.furnaceSpeedMultiplier()) > 1e-6) {
            lore.add(noItalic(formatSignedPercent("Скорость печей/зелий", stats.furnaceSpeedMultiplier())));
        }
        if (stats.doubleDropChance() > 1e-6) {
            int pct = (int) Math.round(stats.doubleDropChance() * 100.0);
            lore.add(noItalic(MM.deserialize("<green>+ <gray>Шанс x2 урожая: <white>" + pct + "%</white>")));
        }
        if (stats.allowLimitBypass()) {
            lore.add(noItalic(MM.deserialize("<gold>★ <gray>Разрешает <white>1</white> расширение сверх лимита якоря</gray>")));
        }
        for (Map.Entry<PotionEffectType, Integer> entry : stats.potionEffects().entrySet()) {
            String effName = formatPotionName(entry.getKey());
            String roman = toRoman(entry.getValue() + 1);
            boolean harmful = isHarmful(entry.getKey());
            String prefix = harmful ? "<red>- " : "<green>+ ";
            String color = harmful ? "<red>" : "<aqua>";
            lore.add(noItalic(MM.deserialize(prefix + "<gray>Эффект: " + color + effName + " " + roman)));
        }
        return lore;
    }

    private static Component formatSignedInt(String label, int val, String suffix) {
        if (val >= 0) {
            return MM.deserialize("<green>+ <gray>" + label + ": <green>+" + val + suffix);
        }
        return MM.deserialize("<red>- <gray>" + label + ": <red>" + val + suffix);
    }

    private static Component formatSignedPercent(String label, double mult) {
        int pct = (int) Math.round(mult * 100.0);
        if (pct >= 0) {
            return MM.deserialize("<green>+ <gray>" + label + ": <green>+" + pct + "%");
        }
        return MM.deserialize("<red>- <gray>" + label + ": <red>" + pct + "%");
    }

    public static String formatPotionName(PotionEffectType type) {
        if (type == null) return "Unknown";
        String key = type.key().value().toUpperCase(Locale.ROOT);
        return switch (key) {
            case "SPEED" -> "Скорость";
            case "SLOWNESS" -> "Замедление";
            case "HASTE" -> "Спешка";
            case "MINING_FATIGUE" -> "Утомление";
            case "REGENERATION" -> "Регенерация";
            case "RESISTANCE" -> "Сопротивление";
            case "HUNGER" -> "Голод";
            default -> key;
        };
    }

    public static boolean isHarmful(PotionEffectType type) {
        if (type == null) return false;
        try {
            return type.getEffectCategory() == PotionEffectType.Category.HARMFUL;
        } catch (Throwable ignored) {
            String k = type.key().value();
            return k.equals("hunger") || k.equals("mining_fatigue") || k.equals("slowness")
                    || k.equals("weakness") || k.equals("poison") || k.equals("wither");
        }
    }

    public static String toRoman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(level);
        };
    }

    private static Component noItalic(Component comp) {
        return comp.decoration(TextDecoration.ITALIC, false);
    }

    private static GemRarity parseRarity(String raw) {
        try {
            return GemRarity.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return GemRarity.BASIC;
        }
    }

    private static Material parseMaterial(String raw, Material fallback) {
        Material m = Material.getMaterial(raw.trim().toUpperCase(Locale.ROOT));
        return m != null && !m.isAir() ? m : fallback;
    }

    private static int getInt(Map<?, ?> map, String key, int def) {
        Object val = map.get(key);
        return val instanceof Number n ? n.intValue() : def;
    }

    private static double getDouble(Map<?, ?> map, String key, double def) {
        Object val = map.get(key);
        return val instanceof Number n ? n.doubleValue() : def;
    }
}
