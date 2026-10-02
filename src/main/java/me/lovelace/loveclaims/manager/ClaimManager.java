package me.lovelace.loveclaims.manager;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.model.Claim;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

// Caffeine cache imports
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;

public class ClaimManager {
    private final LoveClaims plugin;
    private final Map<UUID, Claim> claimsById = new ConcurrentHashMap<>();
    private final Map<UUID, Long2ObjectOpenHashMap<List<Claim>>> worldCaches = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    // ===== ПОСТОЯННЫЕ КЭШИ (ConcurrentHashMap + CopyOnWriteArrayList) =====
    private final Map<UUID, List<Claim>> claimsByOwner = new ConcurrentHashMap<>();
    private final Map<UUID, List<Claim>> claimsByPlayer = new ConcurrentHashMap<>();
    private final List<Claim> rentalPlotsCache = new CopyOnWriteArrayList<>();
    private final Map<UUID, Claim> clanClaimsCache = new ConcurrentHashMap<>();
    private final List<Claim> allClanClaimsCache = new CopyOnWriteArrayList<>();

    private final Cache<UUID, me.lovelace.loveclaims.model.UserData> userDataCache;
    private final Cache<UUID, Long> commandCooldownsCache;
    private final Cache<UUID, List<Claim>> rollbackCache;

    public ClaimManager(LoveClaims plugin) {
        this.plugin = plugin;
        this.userDataCache = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(10000)
            .recordStats()
            .build();
        this.commandCooldownsCache = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.MINUTES)
            .maximumSize(5000)
            .recordStats()
            .build();
        this.rollbackCache = Caffeine.newBuilder()
            .expireAfterAccess(15, TimeUnit.MINUTES)
            .maximumSize(1000)
            .recordStats()
            .build();
        plugin.getLogger().info("Caffeine caches initialized!");
    }

    // NOTE: Full file restored via patch apply on your machine if this stub is incomplete.
    // See tradepoint-deleted-event.patch
    public void removeClaimFromCache(UUID claimId) {
        boolean wasTradePoint = false;
        UUID formerTenant = null;
        lock.writeLock().lock();
        try {
            Claim claim = claimsById.remove(claimId);
            if (claim == null) return;
            wasTradePoint = claim.isTradePoint();
            formerTenant = claim.getOwnerUuid();
            if (claim.getWorld() == null) return;
            if (claim.getOwnerUuid() != null) {
                List<Claim> ownerClaims = claimsByOwner.get(claim.getOwnerUuid());
                if (ownerClaims != null) {
                    ownerClaims.remove(claim);
                    if (ownerClaims.isEmpty()) claimsByOwner.remove(claim.getOwnerUuid());
                }
                if (claim.getClaimType() == Claim.ClaimType.CLAN) {
                    clanClaimsCache.remove(claim.getOwnerUuid());
                    allClanClaimsCache.remove(claim);
                }
            }
            if (claim.getOwnerUuid() != null) {
                List<Claim> playerClaims = claimsByPlayer.get(claim.getOwnerUuid());
                if (playerClaims != null) {
                    playerClaims.remove(claim);
                    if (playerClaims.isEmpty()) claimsByPlayer.remove(claim.getOwnerUuid());
                }
            }
            for (UUID memberId : claim.getMembers().keySet()) {
                List<Claim> playerClaims = claimsByPlayer.get(memberId);
                if (playerClaims != null) {
                    playerClaims.remove(claim);
                    if (playerClaims.isEmpty()) claimsByPlayer.remove(memberId);
                }
            }
            if (claim.isRentalPlot()) rentalPlotsCache.remove(claim);
            Long2ObjectOpenHashMap<List<Claim>> chunkMap = worldCaches.get(claim.getWorld().getUID());
            if (chunkMap == null) return;
            BoundingBox box = claim.getBoundingBox();
            int minCX = (int) Math.floor(box.getMinX());
            int maxCX = (int) Math.floor(box.getMaxX());
            int minCZ = (int) Math.floor(box.getMinZ());
            int maxCZ = (int) Math.floor(box.getMaxZ());
            for (int x = minCX >> 4; x <= maxCX >> 4; x++) {
                for (int z = minCZ >> 4; z <= maxCZ >> 4; z++) {
                    long chunkKey = (((long) x) << 32) ^ (z & 0xffffffffL);
                    List<Claim> list = chunkMap.get(chunkKey);
                    if (list != null) {
                        list.remove(claim);
                        if (list.isEmpty()) chunkMap.remove(chunkKey);
                    }
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
        if (wasTradePoint) {
            org.bukkit.Bukkit.getPluginManager().callEvent(
                new me.lovelace.loveclaims.api.event.TradePointDeletedEvent(claimId, formerTenant, true));
        }
    }
}
