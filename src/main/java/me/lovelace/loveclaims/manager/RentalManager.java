package me.lovelace.loveclaims.manager;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.api.ReleaseReason;
import me.lovelace.loveclaims.api.TradePointRentOutcome;
import me.lovelace.loveclaims.api.TradePointRentPayer;
import me.lovelace.loveclaims.api.TradePointRules;
import me.lovelace.loveclaims.api.event.TradePointExpiryWarningEvent;
import me.lovelace.loveclaims.api.event.TradePointReleasedEvent;
import me.lovelace.loveclaims.api.event.TradePointRentRequestEvent;
import me.lovelace.loveclaims.api.event.TradePointRentedEvent;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.model.IndicatorType;
import me.lovelace.loveclaims.model.PlotType;
import me.lovelace.loveclaims.model.TrustLevel;
import me.lovelace.loveclaims.util.CoinFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RentalManager {
    private final LoveClaims plugin;

    private boolean citizensEnabled = false;
    private Object npcRegistry;
    private Method createNPCMethod;
    private Method spawnMethod;
    private Method destroyMethod;
    private Method getIdMethod;

    private final Map<String, UUID> plots = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> npcCache = new ConcurrentHashMap<>();

    public RentalManager(LoveClaims plugin) {
        this.plugin = plugin;
        initCitizensReflection();
    }

    public void loadPlots(Collection<Claim> claims) {
        plots.clear();
        for (Claim claim : claims) {
            if (claim.isRentalPlot()) {
                plots.put(claim.getName().toLowerCase(), claim.getId());
            }
        }
        plugin.getLogger().info("Loaded " + plots.size() + " rental plots from database.");
    }

    private void initCitizensReflection() {
        if (Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            try {
                Class<?> citizensApi = Class.forName("net.citizensnpcs.api.CitizensAPI");
                npcRegistry = citizensApi.getMethod("getNPCRegistry").invoke(null);
                Class<?> registryClass = npcRegistry.getClass();
                Class<?> entityTypeClass = org.bukkit.entity.EntityType.class;
                createNPCMethod = registryClass.getMethod("createNPC", entityTypeClass, String.class);

                Class<?> npcClass = Class.forName("net.citizensnpcs.api.npc.NPC");
                spawnMethod = npcClass.getMethod("spawn", Location.class);
                destroyMethod = npcClass.getMethod("destroy");
                getIdMethod = npcClass.getMethod("getId");

                citizensEnabled = true;
                plugin.getLogger().info("Citizens API hooked for Landlord & Taxer NPCs!");
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to hook Citizens: " + e.getMessage());
            }
        }
    }

    public void registerPlotName(String name, UUID id) { plots.put(name.toLowerCase(), id); }
    public void unregisterPlotName(String name) { plots.remove(name.toLowerCase()); }
    public UUID getPlotIdByName(String name) { return plots.get(name.toLowerCase()); }

    public void spawnTaxer(Location loc) {
        if (!citizensEnabled) return;
        try {
            String npcName = plugin.getConfigManager().getString("taxer-npc-name", "§eСборщик налогов");
            Object npc = createNPCMethod.invoke(npcRegistry, org.bukkit.entity.EntityType.PLAYER, npcName);
            Class<?> skinTraitClass = Class.forName("net.citizensnpcs.trait.SkinTrait");
            Object skinTrait = npc.getClass().getMethod("getOrAddTrait", Class.class).invoke(npc, skinTraitClass);
            skinTraitClass.getMethod("setSkinName", String.class).invoke(skinTrait, "LastHumanINmars");
            spawnMethod.invoke(npc, loc);
            taxerNpcId = (int) getIdMethod.invoke(npc);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn Taxer NPC: " + e.getMessage());
        }
    }

    public void removeLandlord(Claim claim) {
        if (claim.getHologramId() != null && claim.getHologramId().startsWith("landlord:")) {
            String[] parts = claim.getHologramId().replace("landlord:", "").split(";");
            if (parts.length >= 6) {
                World w = Bukkit.getWorld(parts[0]);
                if (w != null) {
                    Location loc = new Location(w, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
                    Block b = loc.getBlock();
                    if (b.getType().name().endsWith("SIGN")) b.setType(Material.AIR);
                }
            }
        }
        if (citizensEnabled && npcCache.containsKey(claim.getId())) {
            try {
                Object npc = npcRegistry.getClass().getMethod("getById", int.class).invoke(npcRegistry, npcCache.get(claim.getId()));
                if (npc != null) destroyMethod.invoke(npc);
            } catch (Exception e) {
                plugin.getLogger().warning("RentalManager: " + e.getMessage());
            }
            npcCache.remove(claim.getId());
        }

        claim.setHologramId(null);
        claim.setIndicatorType(IndicatorType.NONE);
        plugin.getStorage().saveClaimAsync(claim);
    }

    private Integer taxerNpcId = null;

    public void removeTaxer() {
        if (!citizensEnabled) return;
        if (taxerNpcId != null) {
            try {
                Object npc = npcRegistry.getClass().getMethod("getById", int.class).invoke(npcRegistry, taxerNpcId);
                if (npc != null) destroyMethod.invoke(npc);
            } catch (Exception e) {
                plugin.getLogger().warning("RentalManager: " + e.getMessage());
            }
            taxerNpcId = null;
        }
    }

    public void updateIndicator(Claim claim) {
        plugin.getStorage().saveClaimAsync(claim);

        String holoId = claim.getHologramId();
        if (holoId == null || !holoId.startsWith("landlord:")) return;

        String[] parts = holoId.replace("landlord:", "").split(";");
        if (parts.length < 6) return;

        World w = Bukkit.getWorld(parts[0]);
        if (w == null) return;
        Location loc = new Location(w, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]), Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));

        boolean isRented = claim.isRented();
        IndicatorType type = claim.getIndicatorType();

        Block b = loc.getBlock();
        if (b.getType().name().endsWith("SIGN")) b.setType(Material.AIR);

        if (citizensEnabled && npcCache.containsKey(claim.getId())) {
            try {
                Object npc = npcRegistry.getClass().getMethod("getById", int.class).invoke(npcRegistry, npcCache.get(claim.getId()));
                if (npc != null) destroyMethod.invoke(npc);
            } catch (Exception e) {
                plugin.getLogger().warning("RentalManager: " + e.getMessage());
            }
            npcCache.remove(claim.getId());
        }

        if (isRented || type == IndicatorType.NONE) return;

        if (type == IndicatorType.SIGN) {
            b.setType(Material.OAK_SIGN);
            if (b.getBlockData() instanceof org.bukkit.block.data.type.Sign signData) {
                org.bukkit.block.BlockFace face = getClosestFace(loc.getYaw());
                signData.setRotation(face);
                b.setBlockData(signData);
            }
            if (b.getState() instanceof org.bukkit.block.Sign sign) {
                sign.line(0, plugin.getConfigManager().getComponent("rental-sign-header"));
                sign.line(1, net.kyori.adventure.text.Component.text("§e" + claim.getName()));
                sign.line(2, plugin.getConfigManager().getComponent("rental-sign-price", "price", CoinFormat.formatGlyphs(claim.getRentalPrice())));
                sign.line(3, plugin.getConfigManager().getComponent("rental-sign-click"));
                sign.update();
            }
        }

        if (type == IndicatorType.NPC && citizensEnabled) {
            try {
                String header = plugin.getConfigManager().getString("rental-sign-header", "§b[Аренда]");
                Object npc = createNPCMethod.invoke(npcRegistry, org.bukkit.entity.EntityType.PLAYER, header + " §e" + claim.getName());
                Class<?> skinTraitClass = Class.forName("net.citizensnpcs.trait.SkinTrait");
                Object skinTrait = npc.getClass().getMethod("getOrAddTrait", Class.class).invoke(npc, skinTraitClass);
                skinTraitClass.getMethod("setSkinName", String.class).invoke(skinTrait, "xJosueGutt");
                spawnMethod.invoke(npc, loc);
                int id = (int) getIdMethod.invoke(npc);
                npcCache.put(claim.getId(), id);
            } catch (Exception e) {
                plugin.getLogger().warning("RentalManager: " + e.getMessage());
            }
        }
    }

    private org.bukkit.block.BlockFace getClosestFace(float yaw) {
        yaw = (yaw % 360 + 360) % 360;
        int direction = (int) ((yaw + 11.25) / 22.5);
        return switch (direction) {
            case 1 -> org.bukkit.block.BlockFace.SOUTH_SOUTH_WEST;
            case 2 -> org.bukkit.block.BlockFace.SOUTH_WEST;
            case 3 -> org.bukkit.block.BlockFace.WEST_SOUTH_WEST;
            case 4 -> org.bukkit.block.BlockFace.WEST;
            case 5 -> org.bukkit.block.BlockFace.WEST_NORTH_WEST;
            case 6 -> org.bukkit.block.BlockFace.NORTH_WEST;
            case 7 -> org.bukkit.block.BlockFace.NORTH_NORTH_WEST;
            case 8 -> org.bukkit.block.BlockFace.NORTH;
            case 9 -> org.bukkit.block.BlockFace.NORTH_NORTH_EAST;
            case 10 -> org.bukkit.block.BlockFace.NORTH_EAST;
            case 11 -> org.bukkit.block.BlockFace.EAST_NORTH_EAST;
            case 12 -> org.bukkit.block.BlockFace.EAST;
            case 13 -> org.bukkit.block.BlockFace.EAST_SOUTH_EAST;
            case 14 -> org.bukkit.block.BlockFace.SOUTH_EAST;
            case 15 -> org.bukkit.block.BlockFace.SOUTH_SOUTH_EAST;
            default -> org.bukkit.block.BlockFace.SOUTH;
        };
    }

    public double getTaxPercentage() { return plugin.getConfigManager().getConfig().getDouble("rental.tax-percentage", 5.0); }
    public int getTaxDays() { return plugin.getConfigManager().getConfig().getInt("rental.tax-days", 3); }

    // =====================================================================================
    //  Rental state changes. EVERY change of who rents a plot or until when goes through
    //  assign / extend / release below - GUIs, commands and the expiry task never touch
    //  ownerUuid / rentalEndTime directly, so trade-point events cannot be missed.
    // =====================================================================================

    /**
     * Gives {@code plot} to {@code renter} until {@code endTime}. The caller has already taken the
     * payment (or is an admin). Main thread only.
     */
    public void assign(Claim plot, UUID renter, long endTime) {
        // Leftovers of a previous tenant must never carry over to the new one.
        clearMembers(plot);
        plot.setOwnerUuid(renter);
        plot.setRentalEndTime(endTime);
        plot.setExpiryWarned(false);
        if (plot.isTradePoint()) {
            plot.setLastTaxTime(System.currentTimeMillis());
        }
        plugin.getClaimManager().syncTrustGranted(plot, renter);
        plugin.getStorage().saveMemberAsync(plot.getId(), renter, TrustLevel.OWNER);
        updateIndicator(plot);
        if (plot.isTradePoint()) {
            Bukkit.getPluginManager().callEvent(new TradePointRentedEvent(renter, plot));
        }
    }

    /** Pushes the end of the current term back by {@code millis}. Main thread only. */
    public void extend(Claim plot, long millis) {
        plot.setRentalEndTime(plot.getRentalEndTime() + millis);
        plot.setExpiryWarned(false);
        updateIndicator(plot);
    }

    /**
     * Reassigns an actively rented plot to a new tenant without releasing or resetting expiry time.
     * Main thread only.
     */
    public void transferTenant(Claim plot, UUID newRenter) {
        clearMembers(plot);
        plot.setOwnerUuid(newRenter);
        plugin.getClaimManager().syncTrustGranted(plot, newRenter);
        plugin.getStorage().saveMemberAsync(plot.getId(), newRenter, TrustLevel.OWNER);
        plugin.getStorage().saveClaimAsync(plot);
        updateIndicator(plot);
    }

    /** Takes the plot back to the landlord. Main thread only. */
    public void release(Claim plot, ReleaseReason reason) {
        UUID former = plot.getOwnerUuid();
        UUID landlord = plot.getParentClaimId();
        plot.setRentalEndTime(0);
        plot.setOwnerUuid(landlord);
        plot.setExpiryWarned(false);
        clearMembers(plot);
        updateIndicator(plot);
        if (plot.isTradePoint() && former != null && !former.equals(landlord)) {
            Bukkit.getPluginManager().callEvent(new TradePointReleasedEvent(former, plot, reason));
        }
    }

    private void clearMembers(Claim plot) {
        for (UUID member : new ArrayList<>(plot.getMembers().keySet())) {
            plugin.getClaimManager().syncTrustRevoked(plot, member);
            plugin.getStorage().removeMemberAsync(plot.getId(), member);
        }
        plot.getMembers().clear();
    }

    // =====================================================================================
    //  Trade points
    // =====================================================================================

    private volatile TradePointRentPayer tradePointRentPayer;

    public void setTradePointRentPayer(TradePointRentPayer payer) { this.tradePointRentPayer = payer; }
    public TradePointRentPayer getTradePointRentPayer() { return tradePointRentPayer; }

    private org.bukkit.configuration.file.FileConfiguration cfg() { return plugin.getConfigManager().getConfig(); }

    public boolean isTradePointsEnabled() { return cfg().getBoolean("rental.trade-points.enabled", true); }
    public int getTradePointMaxPerPlayer() { return Math.max(1, cfg().getInt("rental.trade-points.max-per-player", 1)); }
    public long getTradePointPeriodMillis() { return Math.max(1L, cfg().getLong("rental.trade-points.period-days", 7)) * 86_400_000L; }
    public long getTradePointGraceMillis() { return Math.max(0L, cfg().getLong("rental.trade-points.grace-hours", 12)) * 3_600_000L; }
    public long getTradePointWarnMillis() { return Math.max(0L, cfg().getLong("rental.trade-points.warn-hours", 24)) * 3_600_000L; }
    public long getTradePointRenewLeadMillis() { return Math.max(0L, cfg().getLong("rental.trade-points.auto-renew-lead-hours", 2)) * 3_600_000L; }
    public boolean isTradePointAutoRenew() { return cfg().getBoolean("rental.trade-points.auto-renew", true); }
    public int getTradePointMaxPrepaidPeriods() { return Math.max(1, cfg().getInt("rental.trade-points.max-prepaid-periods", 4)); }
    public double getTradePointRenewPercent() { return Math.max(0.0, cfg().getDouble("rental.trade-points.renewal-percent", 100.0)); }

    /** Length of one rental period in millis: the trade-point period or the classic tax period. */
    public long getPeriodMillis(Claim plot) {
        return plot.isTradePoint() ? getTradePointPeriodMillis() : getTaxDays() * 86_400_000L;
    }

    /**
     * What one extension costs. Classic plots pay a tax percentage of the rental price; a trade
     * point pays rent per period ({@code renewal-percent} of its price, 100% by default).
     */
    public long getRenewCost(Claim plot) {
        double percent = plot.isTradePoint() ? getTradePointRenewPercent() : getTaxPercentage();
        return Math.round(plot.getRentalPrice() * (percent / 100.0));
    }

    /** {@code false} once a trade point is already prepaid for {@code max-prepaid-periods}. */
    public boolean canExtend(Claim plot) {
        if (!plot.isTradePoint()) return true;
        long ahead = plot.getRentalEndTime() - System.currentTimeMillis();
        return ahead < getTradePointPeriodMillis() * getTradePointMaxPrepaidPeriods();
    }

    /** True when the plot has a tenant whose term is over but who is still inside the grace time. */
    public boolean isInGrace(Claim plot) {
        if (!plot.isTradePoint() || plot.getRentalEndTime() <= 0) return false;
        long now = System.currentTimeMillis();
        UUID owner = plot.getOwnerUuid();
        return owner != null && !owner.equals(plot.getParentClaimId())
                && plot.getRentalEndTime() <= now
                && now <= plot.getRentalEndTime() + getTradePointGraceMillis();
    }

    /** {@code true} when the plot currently has a tenant (rented, or in the grace period). */
    public boolean hasTenant(Claim plot) {
        UUID owner = plot.getOwnerUuid();
        return owner != null && !owner.equals(plot.getParentClaimId())
                && (plot.isRented() || isInGrace(plot));
    }

    /**
     * Trade-point rules that must hold before the buyer is charged. Classic plots return empty
     * (their limits stay where they were). Returns the message to show when renting is refused.
     */
    public Optional<Component> checkRentAllowed(Player player, Claim plot) {
        if (!plot.isTradePoint()) return Optional.empty();
        if (hasTenant(plot)) {
            // Term is over but the tenant is still inside the grace period: not up for rent yet.
            boolean self = player.getUniqueId().equals(plot.getOwnerUuid());
            return Optional.of(plugin.getConfigManager().getMessage(self ? "trade-point-use-taxer" : "rental-already-taken"));
        }
        if (!isTradePointsEnabled()) {
            return Optional.of(plugin.getConfigManager().getMessage("trade-point-disabled"));
        }
        if (!player.hasPermission("loveclaims.rental.bypasslimit")) {
            long owned = plugin.getClaimManager().getAllClaims().stream()
                    .filter(Claim::isTradePoint)
                    .filter(c -> player.getUniqueId().equals(c.getOwnerUuid()))
                    .filter(this::hasTenant)
                    .count();
            if (owned >= getTradePointMaxPerPlayer()) {
                return Optional.of(plugin.getConfigManager().getMessage("trade-point-limit-reached"));
            }
        }
        TradePointRentRequestEvent request = new TradePointRentRequestEvent(player, plot);
        Bukkit.getPluginManager().callEvent(request);
        if (request.isCancelled()) {
            String custom = request.getDenyMessage();
            return Optional.of(custom != null && !custom.isBlank()
                    ? MiniMessage.miniMessage().deserialize(custom)
                    : plugin.getConfigManager().getMessage("trade-point-denied"));
        }
        return Optional.empty();
    }

    // ----- money -------------------------------------------------------------------------

    /** LoveCore is a soft dependency: touch its classes only from here and only when it is enabled. */
    private Optional<LoveEconomy> tradeEconomy() {
        if (!Bukkit.getPluginManager().isPluginEnabled("LoveCore")) return Optional.empty();
        try {
            return LoveCore.service(LoveEconomy.class);
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    /** Whether rent can be paid at all: every rental is paid in LoveEconomy coins, there is no item fallback. */
    public boolean paymentsAvailable() {
        return tradeEconomy().isPresent();
    }

    /** {@code true} when the player can pay {@code amount}; always {@code false} without LoveCore. */
    public boolean hasFunds(Player player, Claim plot, long amount) {
        if (amount <= 0) return true;
        Optional<LoveEconomy> economy = tradeEconomy();
        return economy.isPresent() && economy.get().has(player, amount);
    }

    /** Takes {@code amount} from the player. {@code false} means nothing was taken. */
    public boolean charge(Player player, Claim plot, long amount) {
        if (amount <= 0) return true;
        Optional<LoveEconomy> economy = tradeEconomy();
        return economy.isPresent() && economy.get().charge(player, amount);
    }

    /** Text for "you need N": the amount split into coin glyphs, never a bare number. */
    public String describeAmount(Claim plot, long amount) {
        return CoinFormat.formatGlyphs(tradeEconomy().orElse(null), amount);
    }

    /**
     * Renews a trade point for one more period: from the till the tenant keeps in LoveShops first
     * (works while the tenant is offline), then from the tenant's inventory if they are online.
     * Main thread only.
     *
     * @return {@code true} if the rent was paid and the term extended
     */
    public boolean renewTradePoint(Claim plot) {
        if (!canExtend(plot)) return false;
        long cost = getRenewCost(plot);
        boolean paid = cost <= 0;
        if (!paid) {
            TradePointRentPayer payer = tradePointRentPayer;
            if (payer != null) {
                try {
                    paid = payer.payFromTill(plot, cost);
                } catch (Throwable t) {
                    plugin.getLogger().warning("TradePointRentPayer failed for " + plot.getName() + ": " + t.getMessage());
                }
            }
            if (!paid) {
                Player tenant = plot.getOwnerUuid() == null ? null : Bukkit.getPlayer(plot.getOwnerUuid());
                if (tenant != null && hasFunds(tenant, plot, cost)) {
                    paid = charge(tenant, plot, cost);
                }
            }
        }
        if (!paid) return false;
        extend(plot, getTradePointPeriodMillis());
        Player tenant = plot.getOwnerUuid() == null ? null : Bukkit.getPlayer(plot.getOwnerUuid());
        if (tenant != null) {
            tenant.sendMessage(plugin.getConfigManager().getMessage("trade-point-renewed",
                    "name", String.valueOf(plot.getName()), "amount", describeAmount(plot, cost)));
        }
        return true;
    }

    /** Fires the pre-expiry warning once per term. Main thread only. */
    public void warnExpiry(Claim plot) {
        if (plot.isExpiryWarned()) return;
        UUID owner = plot.getOwnerUuid();
        if (owner == null) return;
        plot.setExpiryWarned(true);
        Bukkit.getPluginManager().callEvent(new TradePointExpiryWarningEvent(
                owner, plot, Math.max(0L, plot.getRentalEndTime() - System.currentTimeMillis())));
    }

    // ----- trade points: rent through the API (landlord NPC of LoveShops) -------------------

    /** How many periods a free trade point may be rented for at once. */
    public int getTradePointMaxRentPeriods() {
        return getTradePointMaxPrepaidPeriods();
    }

    /** What renting {@code plot} for {@code periods} periods costs. */
    public long getTradePointRentCost(Claim plot, int periods) {
        return TradePointRules.rentCost(plot.getRentalPrice(), getRenewCost(plot), periods);
    }

    /**
     * Rents a free trade point to {@code player} for {@code periods} periods, with the very same
     * checks as the sign (limit per player, vetoes of other plugins, grace period of a previous
     * tenant) and the same payment in LoveEconomy. Main thread only.
     */
    public TradePointRentOutcome rentTradePoint(Player player, Claim plot, int periods) {
        if (plot == null || !plot.isTradePoint()) return TradePointRentOutcome.of(TradePointRentOutcome.Status.NOT_TRADE_POINT);
        if (periods < 1 || periods > getTradePointMaxRentPeriods()) return TradePointRentOutcome.of(TradePointRentOutcome.Status.BAD_PERIODS);
        Optional<Component> denied = checkRentAllowed(player, plot);
        if (denied.isPresent()) return new TradePointRentOutcome(TradePointRentOutcome.Status.DENIED, denied.get(), 0L);
        if (!paymentsAvailable()) return TradePointRentOutcome.of(TradePointRentOutcome.Status.NO_ECONOMY);
        long cost = getTradePointRentCost(plot, periods);
        if (!hasFunds(player, plot, cost) || !charge(player, plot, cost)) {
            return new TradePointRentOutcome(TradePointRentOutcome.Status.NO_FUNDS, null, cost);
        }
        assign(plot, player.getUniqueId(), System.currentTimeMillis() + periods * getTradePointPeriodMillis());
        plugin.getStorage().saveClaimAsync(plot);
        return new TradePointRentOutcome(TradePointRentOutcome.Status.OK, null, cost);
    }

    /** How many more periods the tenant may add right now ({@code 0} when already prepaid to the limit). */
    public int getTradePointMaxExtendPeriods(Claim plot) {
        return TradePointRules.maxExtendPeriods(plot.getRentalEndTime() - System.currentTimeMillis(),
                getTradePointPeriodMillis(), getTradePointMaxPrepaidPeriods());
    }

    /**
     * The tenant pays {@code periods} more periods of rent in advance (at the renewal price each).
     * Main thread only.
     */
    public TradePointRentOutcome extendTradePointPaid(Player player, Claim plot, int periods) {
        if (plot == null || !plot.isTradePoint()) return TradePointRentOutcome.of(TradePointRentOutcome.Status.NOT_TRADE_POINT);
        if (!hasTenant(plot) || !player.getUniqueId().equals(plot.getOwnerUuid())) {
            return TradePointRentOutcome.of(TradePointRentOutcome.Status.NOT_TENANT);
        }
        int allowed = getTradePointMaxExtendPeriods(plot);
        if (periods < 1 || periods > allowed) return TradePointRentOutcome.of(TradePointRentOutcome.Status.BAD_PERIODS);
        if (!paymentsAvailable()) return TradePointRentOutcome.of(TradePointRentOutcome.Status.NO_ECONOMY);
        long cost = TradePointRules.rentCost(getRenewCost(plot), getRenewCost(plot), periods);
        if (!hasFunds(player, plot, cost) || !charge(player, plot, cost)) {
            return new TradePointRentOutcome(TradePointRentOutcome.Status.NO_FUNDS, null, cost);
        }
        // A tenant in the grace period gets the term counted from now, not from the long-gone end.
        long now = System.currentTimeMillis();
        if (plot.getRentalEndTime() < now) plot.setRentalEndTime(now);
        extend(plot, periods * getTradePointPeriodMillis());
        plugin.getStorage().saveClaimAsync(plot);
        return new TradePointRentOutcome(TradePointRentOutcome.Status.OK, null, cost);
    }

    // ----- taxer NPC (found by name, so it also works after a restart) ------------------------

    /** Citizens NPCs that are taxers: their name contains the configured taxer name (as the click handler matches). */
    private java.util.List<Object> findTaxerNpcs() {
        java.util.List<Object> found = new ArrayList<>();
        if (!citizensEnabled || !(npcRegistry instanceof Iterable<?> registry)) return found;
        String wanted = org.bukkit.ChatColor.stripColor(plugin.getConfigManager().getString("taxer-npc-name", "Сборщик налогов"));
        if (wanted == null || wanted.isBlank()) return found;
        try {
            Method getName = Class.forName("net.citizensnpcs.api.npc.NPC").getMethod("getName");
            for (Object npc : registry) {
                String name = org.bukkit.ChatColor.stripColor(String.valueOf(getName.invoke(npc)));
                if (name != null && name.contains(wanted)) found.add(npc);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("RentalManager: taxer lookup failed: " + e.getMessage());
        }
        return found;
    }

    /** Where a taxer stands (the first one found), if there is one. */
    public Optional<Location> findTaxerLocation() {
        try {
            Method stored = Class.forName("net.citizensnpcs.api.npc.NPC").getMethod("getStoredLocation");
            for (Object npc : findTaxerNpcs()) {
                Object loc = stored.invoke(npc);
                if (loc instanceof Location l && l.getWorld() != null) return Optional.of(l);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("RentalManager: taxer location failed: " + e.getMessage());
        }
        return Optional.empty();
    }

    /** Removes every taxer NPC (also ones left over from before a restart); returns how many were removed. */
    public int removeAllTaxers() {
        int removed = 0;
        for (Object npc : findTaxerNpcs()) {
            try {
                destroyMethod.invoke(npc);
                removed++;
            } catch (Exception e) {
                plugin.getLogger().warning("RentalManager: " + e.getMessage());
            }
        }
        taxerNpcId = null;
        return removed;
    }
}
