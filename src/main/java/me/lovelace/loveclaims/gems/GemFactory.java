package me.lovelace.loveclaims.gems;

import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public final class GemFactory {
    private final NamespacedKey keyGemId;
    private final NamespacedKey keyRarity;
    private final NamespacedKey keySlotCost;
    private final NamespacedKey keyRadius;
    private final NamespacedKey keyMembers;
    private final NamespacedKey keyCropMult;
    private final NamespacedKey keyFurnaceMult;
    private final NamespacedKey keyDoubleDrop;
    private final NamespacedKey keyBypassLimit;
    private final NamespacedKey keyEffects;
    private final NamespacedKey keyHybridOutcome;
    private final NamespacedKey keyPendingCraftRoll;

    public GemFactory(Plugin plugin) {
        this.keyGemId = new NamespacedKey(plugin, "gem_id");
        this.keyRarity = new NamespacedKey(plugin, "gem_rarity");
        this.keySlotCost = new NamespacedKey(plugin, "gem_slots");
        this.keyRadius = new NamespacedKey(plugin, "gem_radius");
        this.keyMembers = new NamespacedKey(plugin, "gem_members");
        this.keyCropMult = new NamespacedKey(plugin, "gem_crop_mult");
        this.keyFurnaceMult = new NamespacedKey(plugin, "gem_furnace_mult");
        this.keyDoubleDrop = new NamespacedKey(plugin, "gem_double_drop");
        this.keyBypassLimit = new NamespacedKey(plugin, "gem_bypass_limit");
        this.keyEffects = new NamespacedKey(plugin, "gem_effects");
        this.keyHybridOutcome = new NamespacedKey(plugin, "gem_hybrid_outcome");
        this.keyPendingCraftRoll = new NamespacedKey(plugin, "gem_pending_roll");
    }

    public HybridRollOutcome rollOutcome() {
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < 40) return HybridRollOutcome.POSITIVE;
        if (roll < 70) return HybridRollOutcome.NEUTRAL;
        return HybridRollOutcome.DEBUFF;
    }

    public ItemStack writeStats(ItemStack template, GemStats stats, Component displayName, List<Component> lore) {
        return writeStats(template, stats, displayName, lore, null, false);
    }

    public ItemStack writeStats(
            ItemStack template,
            GemStats stats,
            Component displayName,
            List<Component> lore,
            HybridRollOutcome outcome,
            boolean pendingCraftRoll
    ) {
        ItemStack item = template.clone();
        item.editMeta(meta -> {
            if (displayName != null) meta.displayName(displayName);
            if (lore != null) meta.lore(lore);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(keyGemId, PersistentDataType.STRING, stats.gemId());
            pdc.set(keyRarity, PersistentDataType.STRING, stats.rarity().name());
            pdc.set(keySlotCost, PersistentDataType.INTEGER, stats.slotCost());
            pdc.set(keyRadius, PersistentDataType.INTEGER, stats.radiusBonus());
            pdc.set(keyMembers, PersistentDataType.INTEGER, stats.membersBonus());
            pdc.set(keyCropMult, PersistentDataType.DOUBLE, stats.cropGrowthMultiplier());
            pdc.set(keyFurnaceMult, PersistentDataType.DOUBLE, stats.furnaceSpeedMultiplier());
            pdc.set(keyDoubleDrop, PersistentDataType.DOUBLE, stats.doubleDropChance());
            pdc.set(keyBypassLimit, PersistentDataType.BOOLEAN, stats.allowLimitBypass());
            pdc.set(keyEffects, PersistentDataType.STRING, serializeEffects(stats.potionEffects()));

            if (outcome != null) {
                pdc.set(keyHybridOutcome, PersistentDataType.STRING, outcome.name());
            } else {
                pdc.remove(keyHybridOutcome);
            }
            if (pendingCraftRoll) {
                pdc.set(keyPendingCraftRoll, PersistentDataType.BYTE, (byte) 1);
            } else {
                pdc.remove(keyPendingCraftRoll);
            }
        });
        return item;
    }

    public Optional<GemStats> readStats(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return Optional.empty();
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String gemId = pdc.get(keyGemId, PersistentDataType.STRING);
        if (gemId == null) return Optional.empty();

        GemRarity rarity;
        try {
            rarity = GemRarity.valueOf(
                    pdc.getOrDefault(keyRarity, PersistentDataType.STRING, GemRarity.BASIC.name())
            );
        } catch (IllegalArgumentException e) {
            rarity = GemRarity.BASIC;
        }
        int slots = pdc.getOrDefault(keySlotCost, PersistentDataType.INTEGER, rarity.slotCost());
        int radius = pdc.getOrDefault(keyRadius, PersistentDataType.INTEGER, 0);
        int members = pdc.getOrDefault(keyMembers, PersistentDataType.INTEGER, 0);
        double cropMult = pdc.getOrDefault(keyCropMult, PersistentDataType.DOUBLE, 0.0);
        double furnaceMult = pdc.getOrDefault(keyFurnaceMult, PersistentDataType.DOUBLE, 0.0);
        double doubleDrop = pdc.getOrDefault(keyDoubleDrop, PersistentDataType.DOUBLE, 0.0);
        boolean bypass = Boolean.TRUE.equals(pdc.get(keyBypassLimit, PersistentDataType.BOOLEAN));
        Map<PotionEffectType, Integer> effects = parseEffects(pdc.getOrDefault(keyEffects, PersistentDataType.STRING, ""));
        HybridRollOutcome outcome = readHybridOutcome(item).orElse(null);

        return Optional.of(new GemStats(
                gemId, rarity, slots, radius, members, cropMult, furnaceMult, doubleDrop, bypass, effects, outcome
        ));
    }

    public Optional<HybridRollOutcome> readHybridOutcome(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return Optional.empty();
        String raw = item.getItemMeta().getPersistentDataContainer().get(keyHybridOutcome, PersistentDataType.STRING);
        if (raw == null) return Optional.empty();
        try {
            return Optional.of(HybridRollOutcome.valueOf(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public boolean isPendingCraftRoll(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Byte flag = item.getItemMeta().getPersistentDataContainer().get(keyPendingCraftRoll, PersistentDataType.BYTE);
        return flag != null && flag == 1;
    }

    public boolean isGem(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(keyGemId, PersistentDataType.STRING);
    }

    public Map<String, String> readRawPdcTags(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return Map.of();
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        Map<String, String> out = new LinkedHashMap<>();
        for (NamespacedKey key : pdc.getKeys()) {
            if (pdc.has(key, PersistentDataType.STRING)) {
                out.put(key.asString(), String.valueOf(pdc.get(key, PersistentDataType.STRING)));
            } else if (pdc.has(key, PersistentDataType.INTEGER)) {
                out.put(key.asString(), String.valueOf(pdc.get(key, PersistentDataType.INTEGER)));
            } else if (pdc.has(key, PersistentDataType.DOUBLE)) {
                out.put(key.asString(), String.valueOf(pdc.get(key, PersistentDataType.DOUBLE)));
            } else if (pdc.has(key, PersistentDataType.BOOLEAN)) {
                out.put(key.asString(), String.valueOf(pdc.get(key, PersistentDataType.BOOLEAN)));
            } else if (pdc.has(key, PersistentDataType.BYTE)) {
                out.put(key.asString(), String.valueOf(pdc.get(key, PersistentDataType.BYTE)));
            } else {
                out.put(key.asString(), "<other>");
            }
        }
        return out;
    }

    /**
     * Compact storage string for SQLite claim_gems table.
     */
    public String serializeStatsForDb(GemStats stats) {
        return String.join("|",
                safeToken(stats.gemId()),
                stats.rarity().name(),
                String.valueOf(stats.slotCost()),
                String.valueOf(stats.radiusBonus()),
                String.valueOf(stats.membersBonus()),
                String.valueOf(stats.cropGrowthMultiplier()),
                String.valueOf(stats.furnaceSpeedMultiplier()),
                String.valueOf(stats.doubleDropChance()),
                String.valueOf(stats.allowLimitBypass()),
                serializeEffects(stats.potionEffects()),
                stats.hybridOutcome() != null ? stats.hybridOutcome().name() : ""
        );
    }

    public Optional<GemStats> deserializeStatsFromDb(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String[] parts = raw.split("\\|", -1);
        if (parts.length < 10) return Optional.empty();
        try {
            String gemId = parts[0];
            GemRarity rarity = GemRarity.valueOf(parts[1]);
            int slotCost = Integer.parseInt(parts[2]);
            int radius = Integer.parseInt(parts[3]);
            int members = Integer.parseInt(parts[4]);
            double crop = Double.parseDouble(parts[5]);
            double furnace = Double.parseDouble(parts[6]);
            double doubleDrop = Double.parseDouble(parts[7]);
            boolean bypass = Boolean.parseBoolean(parts[8]);
            Map<PotionEffectType, Integer> effects = parseEffects(parts[9]);
            HybridRollOutcome outcome = null;
            if (parts.length >= 11 && !parts[10].isBlank()) {
                try {
                    outcome = HybridRollOutcome.valueOf(parts[10]);
                } catch (IllegalArgumentException ignored) {
                }
            }
            return Optional.of(new GemStats(gemId, rarity, slotCost, radius, members, crop, furnace, doubleDrop, bypass, effects, outcome));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String safeToken(String value) {
        return value == null ? "" : value.replace("|", "_");
    }

    public static String serializeEffects(Map<PotionEffectType, Integer> effects) {
        if (effects == null || effects.isEmpty()) return "";
        return effects.entrySet().stream()
                .map(e -> e.getKey().key().asString() + "=" + e.getValue())
                .collect(Collectors.joining(";"));
    }

    public static Map<PotionEffectType, Integer> parseEffects(String rawEffects) {
        Map<PotionEffectType, Integer> effects = new HashMap<>();
        if (rawEffects == null || rawEffects.isBlank()) return effects;
        for (String entry : rawEffects.split(";")) {
            String[] parts = entry.split("=");
            if (parts.length != 2) continue;
            PotionEffectType type = resolvePotionType(parts[0].trim());
            if (type == null) continue;
            try {
                effects.put(type, Integer.parseInt(parts[1].trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return effects;
    }

    public static PotionEffectType resolvePotionType(String name) {
        if (name == null || name.isBlank()) return null;
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("slow")) normalized = "slowness";
        if (normalized.equals("fast_digging")) normalized = "haste";
        if (normalized.equals("slow_digging")) normalized = "mining_fatigue";
        if (normalized.equals("damage_resistance")) normalized = "resistance";

        NamespacedKey key = normalized.contains(":")
                ? NamespacedKey.fromString(normalized)
                : NamespacedKey.minecraft(normalized);
        return key != null ? Registry.POTION_EFFECT_TYPE.get(key) : null;
    }
}
