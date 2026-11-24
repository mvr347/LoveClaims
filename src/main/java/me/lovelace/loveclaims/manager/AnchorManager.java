package me.lovelace.loveclaims.manager;

import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.model.ClaimTier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Менеджер якорей клеймов.
 * Управляет уровнями якорей, созданием предметов и проверкой прав.
 */
public class AnchorManager {
    private final LoveClaims plugin;
    private final Map<String, ClaimTier> tiers = new HashMap<>();
    private final NamespacedKey anchorKey;
    private final NamespacedKey anchorOwnerKey;
    private final NamespacedKey fusedGemKey;
    private final NamespacedKey brokenKey;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public AnchorManager(LoveClaims plugin) {
        this.plugin = plugin;
        this.anchorKey = new NamespacedKey(plugin, "anchor_tier");
        this.anchorOwnerKey = new NamespacedKey(plugin, "anchor_owner");
        this.fusedGemKey = new NamespacedKey(plugin, "anchor_fused_gem");
        this.brokenKey = new NamespacedKey(plugin, "anchor_broken");
        loadTiers();
    }

    /**
     * Загрузить все тиры якорей из anchors.yml.
     */
    public void loadTiers() {
        tiers.clear();
        ConfigurationSection section = plugin.getConfigManager().getAnchors().getConfigurationSection("tiers");
        if (section == null) {
            plugin.getLogger().warning("No tiers section in anchors.yml!");
            return;
        }

        List<ClaimTier> tempTiers = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            try {
                // Чтение материала
                String materialName = section.getString(key + ".material", "CAMPFIRE");
                Material material = Material.getMaterial(materialName.toUpperCase());
                if (material == null) {
                    plugin.getLogger().warning("Invalid material '" + materialName + "' for tier " + key + ", using CAMPFIRE");
                    material = Material.CAMPFIRE;
                }

                // Чтение названия и описания
                String name = section.getString(key + ".name", "<green>Claim Anchor");
                List<String> lore = section.getStringList(key + ".lore");

                // Чтение радиусов
                int rX = section.getInt(key + ".radius-x", 8);
                int rY = section.getInt(key + ".radius-y", 8);
                int rZ = section.getInt(key + ".radius-z", 8);
                int cmd = section.getInt(key + ".custom-model-data", 0);

                // Чтение дополнительных настроек
                String permission = section.getString(key + ".permission", "none");
                long createCost = readCost(section, key + ".create-cost");

                int anchorLevel = section.getInt(key + ".anchor-level", ClaimTier.inferLevelFromId(key));
                boolean modified = section.getBoolean(key + ".modified", key.contains("mod"));
                // gems.bonus-slots is added in code, so servers with an old anchors.yml get the extra slot too
                int gemSlots = section.getInt(key + ".gem-slots", ClaimTier.defaultSlotsFor(anchorLevel, modified))
                        + Math.max(0, plugin.getConfig().getInt("gems.bonus-slots"));
                String upgradeTarget = section.getString(key + ".upgrade-target", null);
                long upgradeCost = readCost(section, key + ".upgrade-cost");

                // Чтение цвета границы
                int borderRed = 0, borderGreen = 255, borderBlue = 0;
                if (section.contains(key + ".border-color")) {
                    borderRed = section.getInt(key + ".border-color.red", 0);
                    borderGreen = section.getInt(key + ".border-color.green", 255);
                    borderBlue = section.getInt(key + ".border-color.blue", 0);
                }

                // Чтение звуков
                String placeSound = section.getString(key + ".place-sound", "BLOCK_BEACON_ACTIVATE");
                String breakSound = section.getString(key + ".break-sound", "BLOCK_NOTE_BLOCK_BASS");

                tempTiers.add(new ClaimTier(
                    key, material, name, lore,
                    rX, rY, rZ, cmd,
                    permission, createCost,
                    borderRed, borderGreen, borderBlue,
                    placeSound, breakSound,
                    anchorLevel, modified, gemSlots, upgradeTarget, upgradeCost
                ));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load tier " + key + ": " + e.getMessage());
            }
        }

        // Сортировка тиров по радиусу (от меньшего к большему)
        tempTiers.sort(Comparator.comparingInt(ClaimTier::radiusX));

        for (ClaimTier tier : tempTiers) {
            tiers.put(tier.id(), tier);
        }

        plugin.getLogger().info("Loaded " + tiers.size() + " anchor tiers.");

        // Вывод информации о каждом тире
        for (ClaimTier tier : tiers.values()) {
            plugin.getLogger().info("  - " + tier.id() + ": " +
                tier.getSizeX() + "x" + tier.getSizeY() + "x" + tier.getSizeZ());
        }
    }

    /**
     * Создать предмет якоря для указанного тира.
     * @param tierId ID тира
     * @return ItemStack якоря или null если тир не найден
     */
    public ItemStack createAnchorItem(String tierId) {
        return createAnchorItem(tierId, null);
    }

    /**
     * Создать предмет якоря с владельцем.
     * @param tierId ID тира
     * @param ownerUUID UUID владельца (опционально)
     * @return ItemStack якоря или null
     */
    public ItemStack createAnchorItem(String tierId, UUID ownerUUID) {
        ClaimTier tier = tiers.get(tierId);
        if (tier == null) {
            plugin.getLogger().warning("Attempted to create anchor for unknown tier: " + tierId);
            return null;
        }

        ItemStack item = new ItemStack(tier.material());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Установка названия
        meta.displayName(miniMessage.deserialize(tier.name()));

        // Установка описания
        List<Component> componentLore = new ArrayList<>();
        for (String line : tier.lore()) {
            // The slot count shown on the item is the real one (anchors.yml may hold the number without the bonus)
            line = line.replaceAll("(Слоты гемов: <aqua>)\\d+(</aqua>)", "$1" + tier.gemSlots() + "$2");
            componentLore.add(miniMessage.deserialize(line));
        }
        meta.lore(componentLore);

        // CustomModelData
        if (tier.customModelData() > 0) {
            meta.setCustomModelData(tier.customModelData());
        }

        // Сохранение ID тира в NBT
        meta.getPersistentDataContainer().set(anchorKey, PersistentDataType.STRING, tier.id());

        // Сохранение владельца в NBT (если указан)
        if (ownerUUID != null) {
            meta.getPersistentDataContainer().set(anchorOwnerKey, PersistentDataType.STRING, ownerUUID.toString());
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Anchor item that carries the gem fused into it, so moving or re-crafting an anchor keeps the fusion.
     * The fused gem is stored as serialized stats; a null gem gives a plain anchor.
     */
    public ItemStack createAnchorItem(String tierId, UUID ownerUUID, me.lovelace.loveclaims.gems.GemStats fused) {
        return createAnchorItem(tierId, ownerUUID, fused, false);
    }

    /**
     * Anchor item that also carries a cracked core: moving or dropping a cracked anchor must not repair it for
     * free, so the state travels with the item and is restored when the claim is placed again.
     */
    public ItemStack createAnchorItem(String tierId, UUID ownerUUID, me.lovelace.loveclaims.gems.GemStats fused, boolean broken) {
        ItemStack item = createAnchorItem(tierId, ownerUUID);
        if (item == null) return item;
        boolean withGem = fused != null && plugin.getGemFactory() != null;
        if (!withGem && !broken) return item;
        item.editMeta(meta -> {
            List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            if (withGem) {
                meta.getPersistentDataContainer().set(fusedGemKey, PersistentDataType.STRING,
                        plugin.getGemFactory().serializeStatsForDb(fused));
                lore.add(Component.empty());
                lore.add(miniMessage.deserialize("<dark_gray>▪ <gray>Вшитый гем: <light_purple>" + fused.gemId()));
            }
            if (broken) {
                meta.getPersistentDataContainer().set(brokenKey, PersistentDataType.BYTE, (byte) 1);
                if (!withGem) lore.add(Component.empty());
                lore.add(miniMessage.deserialize("<red>✕ Ядро якоря треснуло"));
                lore.add(miniMessage.deserialize("<red>Причина: <gray>перегруз сильными гемами"));
            }
            meta.lore(lore.stream().map(c -> c.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)).toList());
        });
        return item;
    }

    /** True when this anchor item carries a cracked core. */
    public boolean readBroken(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(brokenKey, PersistentDataType.BYTE);
    }

    /** The gem fused into this anchor item, if any. */
    public Optional<me.lovelace.loveclaims.gems.GemStats> readFusedGem(ItemStack item) {
        if (item == null || !item.hasItemMeta() || plugin.getGemFactory() == null) return Optional.empty();
        String raw = item.getItemMeta().getPersistentDataContainer().get(fusedGemKey, PersistentDataType.STRING);
        return raw == null ? Optional.empty() : plugin.getGemFactory().deserializeStatsFromDb(raw);
    }

    /**
     * Получить тир якоря из предмета.
     * @param item ItemStack для проверки
     * @return Optional с ClaimTier или пустой
     */
    public Optional<ClaimTier> getTierFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return Optional.empty();

        ItemMeta meta = item.getItemMeta();
        String tierId = meta.getPersistentDataContainer().get(anchorKey, PersistentDataType.STRING);
        if (tierId == null) return Optional.empty();

        return Optional.ofNullable(tiers.get(tierId));
    }

    /**
     * Получить тир по ID.
     * @param id ID тира
     * @return ClaimTier или null
     */
    public ClaimTier getTier(String id) {
        return tiers.get(id);
    }

    /**
     * Получить все тиры.
     * @return Коллекция всех тиров
     */
    public Collection<ClaimTier> getAllTiers() {
        return Collections.unmodifiableCollection(tiers.values());
    }

    /**
     * Получить тир по размеру клейма.
     * @param size Размер клейма
     * @return Подходящий тир или null
     */
    public ClaimTier getTierBySize(int size) {
        ClaimTier bestMatch = null;
        for (ClaimTier tier : tiers.values()) {
            int tierMinSize = Math.min(tier.getSizeX(), tier.radiusX() * 2);
            if (tierMinSize <= size) {
                if (bestMatch == null || tier.radiusX() > bestMatch.radiusX()) {
                    bestMatch = tier;
                }
            }
        }
        if (bestMatch != null) {
            return bestMatch;
        }
        // Fallback: если размер меньше базового или тир не найден, возвращаем начальный тир (tier-1)
        return tiers.values().stream()
                .min(Comparator.comparingInt(ClaimTier::radiusX))
                .orElse(null);
    }

    /**
     * Получить следующий тир для текущего.
     * @param currentTier Текущий тир
     * @return Optional со следующим тиром
     */
    public Optional<ClaimTier> getNextTier(ClaimTier currentTier) {
        if (currentTier == null) {
            return tiers.values().stream().min(Comparator.comparingInt(ClaimTier::radiusX));
        }
        List<ClaimTier> sortedTiers = new ArrayList<>(tiers.values());
        sortedTiers.sort(Comparator.comparingInt(ClaimTier::radiusX));

        int currentIndex = sortedTiers.indexOf(currentTier);
        if (currentIndex >= 0 && currentIndex < sortedTiers.size() - 1) {
            return Optional.of(sortedTiers.get(currentIndex + 1));
        }
        return Optional.empty();
    }

    /**
     * Проверить, может ли игрок использовать этот тир.
     * @param tier Тир для проверки
     * @param playerUUID UUID игрока
     * @return true если есть доступ
     */
    public boolean canUseTier(ClaimTier tier, UUID playerUUID) {
        if (tier.isPermissionFree()) return true;

        org.bukkit.OfflinePlayer player = org.bukkit.Bukkit.getOfflinePlayer(playerUUID);
        if (player.isOnline()) {
            return player.getPlayer().hasPermission("loveclaims.admin") ||
                   player.getPlayer().hasPermission(tier.permission());
        }
        return false;
    }

    /**
     * Получить владельца якоря из предмета.
     * @param item ItemStack якоря
     * @return Optional с UUID владельца
     */
    public Optional<UUID> getAnchorOwner(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return Optional.empty();

        ItemMeta meta = item.getItemMeta();
        String ownerStr = meta.getPersistentDataContainer().get(anchorOwnerKey, PersistentDataType.STRING);
        if (ownerStr == null || ownerStr.isEmpty()) return Optional.empty();

        try {
            return Optional.of(UUID.fromString(ownerStr));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Установить владельца якоря.
     * @param item ItemStack якоря
     * @param ownerUUID UUID владельца
     */
    public void setAnchorOwner(ItemStack item, UUID ownerUUID) {
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(anchorOwnerKey, PersistentDataType.STRING, ownerUUID.toString());
        item.setItemMeta(meta);
    }

    /**
     * Проверить, является ли предмет якорем.
     * @param item ItemStack для проверки
     * @return true если это якорь
     */
    public boolean isAnchor(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(anchorKey, PersistentDataType.STRING);
    }

    /**
     * Получить минимальный тир.
     * @return ClaimTier минимального уровня
     */
    public ClaimTier getLowestTier() {
        return tiers.values().stream()
            .min(Comparator.comparingInt(ClaimTier::radiusX))
            .orElse(null);
    }

    /**
     * Получить максимальный тир.
     * @return ClaimTier максимального уровня
     */
    public ClaimTier getHighestTier() {
        return tiers.values().stream()
            .max(Comparator.comparingInt(ClaimTier::radiusX))
            .orElse(null);
    }

    /**
     * Получить тир по материалу.
     * @param material Материал для поиска
     * @return Optional с ClaimTier
     */
    public Optional<ClaimTier> getTierByMaterial(Material material) {
        return tiers.values().stream()
            .filter(tier -> tier.material() == material)
            .findFirst();
    }

    /**
     * Получить тир якоря для существующего клейма (по сохранённому ID или фолбек по размеру).
     */
    public ClaimTier resolveClaimTier(me.lovelace.loveclaims.model.Claim claim) {
        if (claim == null) return getLowestTier();
        if (claim.getAnchorTierId() != null) {
            ClaimTier direct = tiers.get(claim.getAnchorTierId());
            if (direct != null) return direct;
        }
        int currentSize = (int) Math.round(claim.getBoundingBox().getMaxX() - claim.getBoundingBox().getMinX())
                - ((claim.getAppliedRadiusBonus() + claim.getBehaviorBonusRadius() + claim.getAdminExpandBonus()) * 2);
        ClaimTier bySize = getTierBySize(Math.max(1, currentSize));
        return bySize != null ? bySize : getLowestTier();
    }

    /**
     * Апгрейд установленного якоря клейма (Обычный -> Модифицированный) через NPC.
     *
     * @return результат операции
     */
    public UpgradeOutcome upgradeClaimAnchor(me.lovelace.loveclaims.model.Claim claim) {
        if (claim == null || claim.getWorld() == null) return UpgradeOutcome.INVALID_CLAIM;
        ClaimTier current = resolveClaimTier(claim);
        if (current == null || !current.canUpgradeAtNpc()) return UpgradeOutcome.ALREADY_MODIFIED;
        ClaimTier target = tiers.get(current.upgradeTargetId());
        if (target == null) return UpgradeOutcome.TARGET_NOT_FOUND;

        int deltaX = target.radiusX() - current.radiusX();
        int deltaY = target.radiusY() - current.radiusY();
        int deltaZ = target.radiusZ() - current.radiusZ();

        org.bukkit.util.BoundingBox oldBox = claim.getBoundingBox().clone();
        org.bukkit.util.BoundingBox newBox = oldBox.clone().expand(deltaX, deltaY, deltaZ);
        if (plugin.getClaimManager().checkOverlap(claim.getWorld(), newBox, claim.getId())) {
            return UpgradeOutcome.OVERLAP;
        }

        claim.setAnchorTierId(target.id());
        claim.setBoundingBox(newBox);
        plugin.getClaimManager().resizeClaimInCache(claim, oldBox);
        if (!claim.getFlag(me.lovelace.loveclaims.model.ClaimFlag.HIDE_ANCHOR) && claim.getAnchorLocation() != null) {
            claim.getAnchorLocation().getBlock().setType(target.material());
        }
        plugin.getStorage().saveClaimAsync(claim);
        return UpgradeOutcome.SUCCESS;
    }

    /**
     * Выдать игроку якорь и все вставленные в него гемы (при удалении или переносе клейма).
     */
    public void giveClaimAnchorAndGems(me.lovelace.loveclaims.model.Claim claim, org.bukkit.entity.Player receiver) {
        if (claim == null || receiver == null) return;
        ClaimTier tier = resolveClaimTier(claim);
        String tierId = tier != null ? tier.id() : "tier-1";
        ItemStack anchor = createAnchorItem(tierId, null, claim.getPermanentGem(), claim.isAnchorBroken());
        if (anchor != null) {
            receiver.getInventory().addItem(anchor).values()
                    .forEach(left -> receiver.getWorld().dropItemNaturally(receiver.getLocation(), left));
        }
        var registry = plugin.getGemRegistry();
        if (registry != null) {
            for (var gemStats : claim.getInstalledGems()) {
                ItemStack gemItem = registry.createItemFromStats(gemStats);
                if (gemItem != null) {
                    receiver.getInventory().addItem(gemItem).values()
                            .forEach(left -> receiver.getWorld().dropItemNaturally(receiver.getLocation(), left));
                }
            }
        }
    }

    /**
     * Выбросить якорь и все вставленные гемы в точке якоря (при автоудалении).
     */
    public void dropClaimAnchorAndGems(me.lovelace.loveclaims.model.Claim claim) {
        if (claim == null || claim.getWorld() == null || claim.getAnchorLocation() == null) return;
        ClaimTier tier = resolveClaimTier(claim);
        String tierId = tier != null ? tier.id() : "tier-1";
        ItemStack anchor = createAnchorItem(tierId, null, claim.getPermanentGem(), claim.isAnchorBroken());
        if (anchor != null) {
            claim.getWorld().dropItemNaturally(claim.getAnchorLocation(), anchor);
        }
        var registry = plugin.getGemRegistry();
        if (registry != null) {
            for (var gemStats : claim.getInstalledGems()) {
                ItemStack gemItem = registry.createItemFromStats(gemStats);
                if (gemItem != null) {
                    claim.getWorld().dropItemNaturally(claim.getAnchorLocation(), gemItem);
                }
            }
        }
    }

    public enum UpgradeOutcome {
        SUCCESS,
        INVALID_CLAIM,
        ALREADY_MODIFIED,
        TARGET_NOT_FOUND,
        OVERLAP
    }

    /** create-cost: a number or a money string like "5i" (LoveCore MoneyConfig); unreadable -> 0 (free). */
    private long readCost(org.bukkit.configuration.ConfigurationSection section, String path) {
        try {
            return dev.lovelace.lovecore.api.economy.MoneyConfig.get(section, path, 0L);
        } catch (Throwable t) {
            return section.getLong(path, 0L);
        }
    }
}
