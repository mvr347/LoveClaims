package me.lovelace.loveclaims.model;

import me.lovelace.loveclaims.gems.ClaimModifiers;
import me.lovelace.loveclaims.gems.GemStats;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class Claim {

    public enum ClaimType {
        PLAYER,
        CLAN
    }

    private final UUID id;
    private final World world;
    private volatile BoundingBox boundingBox;
    private volatile UUID ownerUuid;
    private final Location anchorLocation;
    private String name;
    private String description;
    private Location homeLocation;
    private long lastActive;
    private final Map<UUID, Set<ClaimPermission>> members = new ConcurrentHashMap<>();
    private final Set<ClaimPermission> defaultPermissions = EnumSet.noneOf(ClaimPermission.class);
    private final Map<ClaimFlag, Boolean> flags = new EnumMap<>(ClaimFlag.class);
    private ClaimType claimType = ClaimType.PLAYER; // По умолчанию - приват игрока
    private boolean isClanTerritory = false; // Новое поле
    private boolean isUnderSiege = false; // Новое поле
    // Отображаемое имя владельца-клана (тег/название), передаётся из LoveClans при создании/переименовании.
    // Null для приватов игроков и для старых клановых территорий, ещё не получивших это поле.
    private String ownerDisplayName = null;

    // Rental fields
    private long rentalPrice = 0;
    private long rentalEndTime = 0;
    private UUID parentClaimId = null;
    private PlotType plotType = PlotType.RENTAL_PLOT;
    /** Rental-period warning already sent for this term (not persisted: a restart may warn again). */
    private volatile boolean expiryWarned = false;
    private IndicatorType indicatorType = IndicatorType.NONE;
    private String hologramId = null;
    private long lastTaxTime = 0;

    // Anchor & Gem system fields
    private volatile String anchorTierId = null;
    private volatile int behaviorBonusRadius = 0;
    private volatile int adminExpandBonus = 0;
    private volatile int appliedRadiusBonus = 0;
    private volatile boolean extraExpansionUnlocked = false;
    private final List<GemStats> installedGems = new CopyOnWriteArrayList<>();
    private volatile ClaimModifiers modifiers = ClaimModifiers.EMPTY;

    private final java.util.concurrent.atomic.AtomicLong modificationVersion =
            new java.util.concurrent.atomic.AtomicLong(1);
    private volatile long savedVersion;

    public Claim(UUID id, World world, BoundingBox boundingBox, UUID ownerUuid, Location anchorLocation) {
        this.id = id;
        this.world = world;
        this.boundingBox = boundingBox;
        this.ownerUuid = ownerUuid;
        this.anchorLocation = anchorLocation;
        this.lastActive = System.currentTimeMillis();

        // Name is deliberately left null here: deriving it from Bukkit.getOfflinePlayer(owner).getName()
        // produced "Приват null" for owners that were never cached. Callers set a name explicitly or
        // fall back to a display default when getName() is null.

        for (ClaimFlag flag : ClaimFlag.values()) {
            flags.put(flag, flag.getDefaultState());
        }
    }

    // Getters & Setters
    public UUID getId() { return id; }
    public World getWorld() { return world; }
    public BoundingBox getBoundingBox() { return boundingBox; }

    public void setBoundingBox(BoundingBox box) {
        this.boundingBox = box;
        markModified();
    }

    public UUID getOwnerUuid() { return ownerUuid; }
    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
        markModified();
    }

    public Location getAnchorLocation() { return anchorLocation; }

    public String getName() { return name; }

    /**
     * Legacy rows persisted names such as "Приват null" (owner name was unresolved at creation).
     * Treat any name ending in " null" (or equal to "null") as absent.
     */
    public static String sanitizeName(String name) {
        if (name == null) return null;
        String t = name.trim();
        if (t.isEmpty() || t.equalsIgnoreCase("null") || t.toLowerCase(java.util.Locale.ROOT).endsWith(" null")) return null;
        return name;
    }
    public void setName(String name) {
        this.name = sanitizeName(name);
        markModified();
    }

    public String getDescription() { return description; }
    public void setDescription(String description) {
        this.description = description;
        markModified();
    }

    public Location getHomeLocation() {
        return homeLocation == null ? anchorLocation : homeLocation;
    }

    public void setHomeLocation(Location homeLocation) {
        this.homeLocation = homeLocation;
        markModified();
    }

    public long getLastActive() { return lastActive; }
    public void setLastActive(long lastActive) {
        this.lastActive = lastActive;
    }
    public void updateLastActive() {
        this.lastActive = System.currentTimeMillis();
        markModified();
    }

    public boolean hasPermission(UUID playerUuid, ClaimPermission permission) {
        if (playerUuid == null) return false;
        // A trade point is a fixed market stall: nobody (tenant included) builds in it or opens
        // containers - the goods live in the shop's database, not in chests. Admin bypass is
        // handled by the listeners before this is asked.
        if (plotType == PlotType.TRADE_POINT
                && (permission == ClaimPermission.BUILD || permission == ClaimPermission.CONTAINERS)) {
            return false;
        }
        if (playerUuid.equals(ownerUuid)) return true;
        Set<ClaimPermission> perms = members.get(playerUuid);
        if (perms != null) {
            return perms.contains(permission);
        }
        return permission != ClaimPermission.MANAGE && defaultPermissions.contains(permission);
    }

    public boolean isOwner(UUID playerUuid) {
        return playerUuid != null && playerUuid.equals(ownerUuid);
    }

    public boolean isManager(UUID playerUuid) {
        if (isOwner(playerUuid)) return true;
        Set<ClaimPermission> permissions = playerUuid == null ? null : members.get(playerUuid);
        return permissions != null && permissions.contains(ClaimPermission.MANAGE);
    }

    public Set<ClaimPermission> getPermissions(UUID playerUuid) {
        if (isOwner(playerUuid)) return EnumSet.allOf(ClaimPermission.class);
        Set<ClaimPermission> permissions = playerUuid == null ? null : members.get(playerUuid);
        return permissions == null || permissions.isEmpty()
                ? EnumSet.noneOf(ClaimPermission.class) : EnumSet.copyOf(permissions);
    }

    public void setPermission(UUID playerUuid, ClaimPermission permission, boolean state) {
        if (isOwner(playerUuid)) return;
        if (plotType == PlotType.TRADE_POINT) return; // a trade point is personal: no members
        Set<ClaimPermission> perms = members.computeIfAbsent(playerUuid, k -> EnumSet.noneOf(ClaimPermission.class));
        if (state) perms.add(permission);
        else perms.remove(permission);
        markModified();
    }

    public Set<ClaimPermission> getDefaultPermissions() {
        return defaultPermissions;
    }

    public void setDefaultPermissions(Set<ClaimPermission> perms) {
        this.defaultPermissions.clear();
        if (perms != null) {
            this.defaultPermissions.addAll(perms);
        }
        markModified();
    }

    public void setDefaultPermission(ClaimPermission permission, boolean state) {
        if (state) defaultPermissions.add(permission);
        else defaultPermissions.remove(permission);
        markModified();
    }

    public void setTrust(UUID player, TrustLevel level) {
        if (player.equals(ownerUuid)) return;
        if (plotType == PlotType.TRADE_POINT) return; // a trade point is personal: no members
        Set<ClaimPermission> perms = EnumSet.noneOf(ClaimPermission.class);
        switch (level) {
            case OWNER, MANAGER -> perms.addAll(EnumSet.allOf(ClaimPermission.class));
            case BUILD -> {
                perms.add(ClaimPermission.BUILD);
                perms.add(ClaimPermission.CONTAINERS);
                perms.add(ClaimPermission.INTERACT);
            }
            case CONTAINER -> {
                perms.add(ClaimPermission.CONTAINERS);
                perms.add(ClaimPermission.INTERACT);
            }
            case ACCESS -> perms.add(ClaimPermission.INTERACT);
            case NONE -> {}
        }
        members.put(player, perms);
        markModified();
    }

    public TrustLevel getTrust(UUID player) {
        if (player == null) return TrustLevel.NONE;
        if (player.equals(ownerUuid)) return TrustLevel.OWNER;
        Set<ClaimPermission> perms = members.get(player);
        if (perms == null) return TrustLevel.NONE;
        if (perms.contains(ClaimPermission.MANAGE)) return TrustLevel.MANAGER;
        if (perms.contains(ClaimPermission.BUILD)) return TrustLevel.BUILD;
        if (perms.contains(ClaimPermission.CONTAINERS)) return TrustLevel.CONTAINER;
        if (perms.contains(ClaimPermission.INTERACT)) return TrustLevel.ACCESS;
        return TrustLevel.NONE;
    }

    public Map<UUID, Set<ClaimPermission>> getMembers() { return members; }

    /**
     * Удаляет игрока из списка участников привата и устанавливает флаг modified.
     * @param playerUuid UUID игрока, которого нужно удалить.
     */
    public void removePlayer(UUID playerUuid) {
        if (members.remove(playerUuid) != null) { // Проверяем, был ли игрок в списке
            markModified();
        }
    }

    public boolean getFlag(ClaimFlag flag) {
        return flags.getOrDefault(flag, flag.getDefaultState());
    }

    public void setFlag(ClaimFlag flag, boolean state) {
        flags.put(flag, state);
        markModified();
    }

    public Map<ClaimFlag, Boolean> getFlags() { return flags; }

    public ClaimType getClaimType() { return claimType; }
    public void setClaimType(ClaimType claimType) {
        this.claimType = claimType;
        markModified();
    }

    public boolean isClanTerritory() { return isClanTerritory; }
    public void setClanTerritory(boolean clanTerritory) {
        isClanTerritory = clanTerritory;
        markModified();
    }

    public boolean isUnderSiege() { return isUnderSiege; }
    public void setUnderSiege(boolean underSiege) {
        isUnderSiege = underSiege;
        markModified();
    }

    public String getOwnerDisplayName() { return ownerDisplayName; }
    public void setOwnerDisplayName(String ownerDisplayName) {
        this.ownerDisplayName = ownerDisplayName;
        markModified();
    }

    public boolean overlaps(BoundingBox other) {
        return this.boundingBox.overlaps(other);
    }

    public boolean isModified() { return modificationVersion.get() != savedVersion; }
    public void setModified(boolean modified) {
        if (modified) markModified();
        else savedVersion = modificationVersion.get();
    }
    private void markModified() { modificationVersion.incrementAndGet(); }
    public long getModificationVersion() { return modificationVersion.get(); }
    /** A commit may clear only the version captured before preparing its SQL row. */
    public void markSaved(long version) {
        if (modificationVersion.get() == version) savedVersion = version;
    }

    // Rental Getters/Setters
    public long getRentalPrice() { return rentalPrice; }
    public void setRentalPrice(long rentalPrice) {
        this.rentalPrice = rentalPrice;
        markModified();
    }

    public long getRentalEndTime() { return rentalEndTime; }
    public void setRentalEndTime(long rentalEndTime) {
        this.rentalEndTime = rentalEndTime;
        markModified();
    }

    public UUID getParentClaimId() { return parentClaimId; }
    public void setParentClaimId(UUID parentClaimId) {
        this.parentClaimId = parentClaimId;
        markModified();
    }

    public IndicatorType getIndicatorType() { return indicatorType; }
    public void setIndicatorType(IndicatorType indicatorType) {
        this.indicatorType = indicatorType;
        markModified();
    }

    public String getHologramId() { return hologramId; }
    public void setHologramId(String hologramId) {
        this.hologramId = hologramId;
        markModified();
    }

    public boolean isRentalPlot() { return parentClaimId != null; }

    public PlotType getPlotType() { return plotType; }
    public void setPlotType(PlotType plotType) {
        this.plotType = plotType == null ? PlotType.RENTAL_PLOT : plotType;
        markModified();
    }
    public boolean isTradePoint() { return isRentalPlot() && plotType == PlotType.TRADE_POINT; }

    public boolean isExpiryWarned() { return expiryWarned; }
    public void setExpiryWarned(boolean expiryWarned) { this.expiryWarned = expiryWarned; }
    public boolean isRented() {
        return rentalEndTime > System.currentTimeMillis() && rentalEndTime > 0;
    }

    public long getLastTaxTime() { return lastTaxTime; }
    public void setLastTaxTime(long lastTaxTime) {
        this.lastTaxTime = lastTaxTime;
        markModified();
    }

    // ===== Anchor & Gem Getters/Setters =====

    public String getAnchorTierId() { return anchorTierId; }
    public void setAnchorTierId(String anchorTierId) {
        this.anchorTierId = anchorTierId;
        markModified();
    }

    public int getBehaviorBonusRadius() { return behaviorBonusRadius; }
    public void setBehaviorBonusRadius(int behaviorBonusRadius) {
        this.behaviorBonusRadius = Math.max(0, behaviorBonusRadius);
        markModified();
    }

    public int getAdminExpandBonus() { return adminExpandBonus; }
    public void addAdminExpandBonus(int delta) {
        this.adminExpandBonus += delta;
        markModified();
    }
    public void setAdminExpandBonus(int adminExpandBonus) {
        this.adminExpandBonus = adminExpandBonus;
        markModified();
    }

    public int getAppliedRadiusBonus() { return appliedRadiusBonus; }
    public void setAppliedRadiusBonus(int appliedRadiusBonus) {
        this.appliedRadiusBonus = appliedRadiusBonus;
        markModified();
    }

    public boolean isExtraExpansionUnlocked() { return extraExpansionUnlocked; }
    public void setExtraExpansionUnlocked(boolean extraExpansionUnlocked) {
        this.extraExpansionUnlocked = extraExpansionUnlocked;
        markModified();
    }

    public List<GemStats> getInstalledGems() {
        return Collections.unmodifiableList(installedGems);
    }

    public void addInstalledGem(GemStats stats) {
        if (stats != null) {
            installedGems.add(stats);
            this.modifiers = ClaimModifiers.fromGems(installedGems);
            markModified();
        }
    }

    public GemStats removeInstalledGem(int index) {
        if (index < 0 || index >= installedGems.size()) return null;
        GemStats removed = installedGems.remove(index);
        this.modifiers = ClaimModifiers.fromGems(installedGems);
        markModified();
        return removed;
    }

    public void loadInstalledGems(List<GemStats> gems) {
        installedGems.clear();
        if (gems != null) {
            installedGems.addAll(gems);
        }
        this.modifiers = ClaimModifiers.fromGems(installedGems);
    }

    public ClaimModifiers getModifiers() {
        return modifiers;
    }

    public void setModifiers(ClaimModifiers modifiers) {
        this.modifiers = modifiers == null ? ClaimModifiers.EMPTY : modifiers;
    }
}
