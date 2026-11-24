package me.lovelace.loveclaims.manager;

import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.gems.GemStats;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.model.ClaimPermission;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Merging two neighbouring claims into one. The survivor keeps its id, owner, tier and anchor; the absorbed
 * claim disappears: its anchor and gems go back to its owner, its members join the survivor. The merged area is
 * the bounding box of both claims, so it may only be done when that box takes no land of a third claim.
 */
public final class ClaimMerger {

    public enum Reason {
        OK, SAME_CLAIM, NOT_PLAYER_CLAIM, DIFFERENT_WORLD, NOT_NEIGHBOURS, DIAGONAL, HAS_RENTALS,
        BLOCKED_BY_OTHER_CLAIM, MEMBER_LIMIT, NEEDS_SEAL
    }

    /** Result of the pure geometry check. */
    public record Geometry(Reason reason, BoundingBox merged) {
        public boolean ok() {
            return reason == Reason.OK;
        }
    }

    /** A merge that passed the checks; both claims were valid when it was built. */
    public record Plan(Claim survivor, Claim absorbed, BoundingBox merged, boolean sameOwner,
                       boolean bothHavePermanentGem) {
    }

    /** A pending request to the other owner (different owners merge only if both agree). */
    public record Request(UUID survivorId, UUID absorbedId, UUID requester, long expiresAt) {
    }

    private static final long REQUEST_TTL_MS = 60_000L;

    private final LoveClaims plugin;
    private final Map<UUID, Request> requests = new ConcurrentHashMap<>();

    public ClaimMerger(LoveClaims plugin) {
        this.plugin = plugin;
    }

    // ===== pure geometry (unit tested) =====

    /** Gap between two boxes along one axis: 0 when their ranges overlap or touch, otherwise the distance. */
    static double gap(double minA, double maxA, double minB, double maxB) {
        return Math.max(0.0, Math.max(minA, minB) - Math.min(maxA, maxB));
    }

    /**
     * Two claims are neighbours when they touch or the free space between them is at most {@code maxGap}
     * blocks, along one axis only. Corner-to-corner neighbours are refused: the bounding box of two diagonal
     * claims would swallow a large area nobody owns.
     */
    public static Geometry geometry(BoundingBox a, BoundingBox b, double maxGap) {
        double gx = gap(a.getMinX(), a.getMaxX(), b.getMinX(), b.getMaxX());
        double gy = gap(a.getMinY(), a.getMaxY(), b.getMinY(), b.getMaxY());
        double gz = gap(a.getMinZ(), a.getMaxZ(), b.getMinZ(), b.getMaxZ());
        int separated = (gx > 0 ? 1 : 0) + (gy > 0 ? 1 : 0) + (gz > 0 ? 1 : 0);
        if (separated > 1) return new Geometry(Reason.DIAGONAL, null);
        if (Math.max(gx, Math.max(gy, gz)) > maxGap) return new Geometry(Reason.NOT_NEIGHBOURS, null);
        return new Geometry(Reason.OK, a.clone().union(b));
    }

    public double maxGap() {
        // Single-argument getter: a missing key falls back to the jar default (3), an explicit 0 stays 0
        // and then only touching claims may merge.
        return Math.max(0, plugin.getConfig().getInt("merge.max-gap"));
    }

    // ===== checks =====

    public boolean mergeable(Claim c) {
        return c != null && !c.isRentalPlot() && !c.isClanTerritory();
    }

    public Reason check(Claim survivor, Claim absorbed) {
        return plan(survivor, absorbed).map(p -> Reason.OK).orElseGet(() -> reasonOf(survivor, absorbed));
    }

    private Reason reasonOf(Claim survivor, Claim absorbed) {
        Reason r = checkInternal(survivor, absorbed, null);
        return r == null ? Reason.OK : r;
    }

    public Optional<Plan> plan(Claim survivor, Claim absorbed) {
        Plan[] holder = new Plan[1];
        Reason r = checkInternal(survivor, absorbed, holder);
        return r == null ? Optional.of(holder[0]) : Optional.empty();
    }

    /** null = fine (plan filled in), otherwise the reason. */
    private Reason checkInternal(Claim survivor, Claim absorbed, Plan[] out) {
        if (survivor == null || absorbed == null || survivor.getId().equals(absorbed.getId())) return Reason.SAME_CLAIM;
        if (!mergeable(survivor) || !mergeable(absorbed)) return Reason.NOT_PLAYER_CLAIM;
        if (survivor.getWorld() == null || !survivor.getWorld().equals(absorbed.getWorld())) return Reason.DIFFERENT_WORLD;
        Geometry geo = geometry(survivor.getBoundingBox(), absorbed.getBoundingBox(), maxGap());
        if (!geo.ok()) return geo.reason();

        for (Claim rental : plugin.getClaimManager().getAllRentalPlots()) {
            UUID parent = rental.getParentClaimId();
            if (parent != null && (parent.equals(survivor.getId()) || parent.equals(absorbed.getId()))) {
                return Reason.HAS_RENTALS;
            }
        }
        for (Claim other : plugin.getClaimManager().getAllClaims()) {
            if (other.getId().equals(survivor.getId()) || other.getId().equals(absorbed.getId())) continue;
            // Rentals count too: a standalone rental plot (admin-created) in the gap must not be swallowed.
            if (other.getWorld() == null || !other.getWorld().equals(survivor.getWorld())) continue;
            if (geo.merged().overlaps(other.getBoundingBox())) return Reason.BLOCKED_BY_OTHER_CLAIM;
        }
        if (mergedMemberCount(survivor, absorbed) > plugin.getClaimManager().getMemberLimit(survivor)) {
            return Reason.MEMBER_LIMIT;
        }
        boolean sameOwner = survivor.getOwnerUuid() != null && survivor.getOwnerUuid().equals(absorbed.getOwnerUuid());
        if (out != null) {
            out[0] = new Plan(survivor, absorbed, geo.merged(), sameOwner,
                    survivor.getPermanentGem() != null && absorbed.getPermanentGem() != null);
        }
        return null;
    }

    private static int mergedMemberCount(Claim survivor, Claim absorbed) {
        Set<UUID> all = new HashSet<>(survivor.getMembers().keySet());
        all.addAll(absorbed.getMembers().keySet());
        if (absorbed.getOwnerUuid() != null) all.add(absorbed.getOwnerUuid());
        all.remove(survivor.getOwnerUuid());
        return all.size();
    }

    // ===== requests between different owners =====

    public void request(Claim survivor, Claim absorbed, Player requester) {
        requests.put(absorbed.getOwnerUuid(), new Request(survivor.getId(), absorbed.getId(),
                requester.getUniqueId(), System.currentTimeMillis() + REQUEST_TTL_MS));
    }

    /** The live request addressed to this owner, or null (expired requests are dropped). */
    public Request takeRequest(UUID owner) {
        Request r = requests.remove(owner);
        if (r == null || r.expiresAt() < System.currentTimeMillis()) return null;
        return r;
    }

    public Request peekRequest(UUID owner) {
        Request r = requests.get(owner);
        if (r != null && r.expiresAt() < System.currentTimeMillis()) {
            requests.remove(owner);
            return null;
        }
        return r;
    }

    // ===== execution =====

    /**
     * Performs the merge. The caller checked {@link #plan}; everything is re-checked here, because a menu or a
     * request may have been open for a while. Returns the reason it failed, or OK.
     */
    public Reason execute(Plan plan) {
        Claim survivor = plan.survivor();
        Claim absorbed = plan.absorbed();
        ClaimManager claims = plugin.getClaimManager();
        if (claims.getClaimById(survivor.getId()).orElse(null) != survivor
                || claims.getClaimById(absorbed.getId()).orElse(null) != absorbed) {
            return Reason.SAME_CLAIM;
        }
        Plan fresh = plan(survivor, absorbed).orElse(null);
        if (fresh == null) return reasonOf(survivor, absorbed);

        // 1. members: higher permission wins; the absorbed owner joins as a manager when owners differ
        UUID absorbedOwner = absorbed.getOwnerUuid();
        for (Map.Entry<UUID, Set<ClaimPermission>> e : absorbed.getMembers().entrySet()) {
            if (e.getKey().equals(survivor.getOwnerUuid())) continue;
            Set<ClaimPermission> merged = EnumSet.noneOf(ClaimPermission.class);
            Set<ClaimPermission> existing = survivor.getMembers().get(e.getKey());
            if (existing != null) merged.addAll(existing);
            merged.addAll(e.getValue());
            survivor.getMembers().put(e.getKey(), merged);
            claims.syncTrustGranted(survivor, e.getKey());
            plugin.getStorage().saveMemberPermissionsAsync(survivor.getId(), e.getKey(), merged);
        }
        if (absorbedOwner != null && !absorbedOwner.equals(survivor.getOwnerUuid())) {
            survivor.setTrust(absorbedOwner, me.lovelace.loveclaims.model.TrustLevel.MANAGER);
            claims.syncTrustGranted(survivor, absorbedOwner);
            Set<ClaimPermission> ownerPerms = survivor.getMembers().get(absorbedOwner);
            if (ownerPerms != null) plugin.getStorage().saveMemberPermissionsAsync(survivor.getId(), absorbedOwner, ownerPerms);
        }

        // 2. stricter default permissions win
        Set<ClaimPermission> defaults = EnumSet.noneOf(ClaimPermission.class);
        defaults.addAll(survivor.getDefaultPermissions());
        defaults.retainAll(absorbed.getDefaultPermissions());
        survivor.setDefaultPermissions(defaults);

        // 3. fused gem: one survives, chosen at random when both claims had one
        GemStats chosen = survivor.getPermanentGem();
        if (chosen == null) {
            chosen = absorbed.getPermanentGem();
        } else if (absorbed.getPermanentGem() != null && ThreadLocalRandom.current().nextBoolean()) {
            chosen = absorbed.getPermanentGem();
        }

        // 4. hand the absorbed claim's anchor and ordinary gems back to its owner
        giveBack(absorbed);
        Location anchor = absorbed.getAnchorLocation();
        if (anchor != null && anchor.getWorld() != null && anchor.getBlock().getType() != Material.AIR) {
            anchor.getBlock().setType(Material.AIR);
        }

        // 5. geometry, caches and storage: survivor first, then the absorbed claim goes
        BoundingBox oldBox = survivor.getBoundingBox().clone();
        survivor.setBoundingBox(fresh.merged());
        claims.resizeClaimInCache(survivor, oldBox);
        // A fused gem taken over from the absorbed claim may add radius that would reach a neighbour of the
        // merged area: then the survivor keeps its own state and the gem comes back as an ordinary item.
        GemStats own = survivor.getPermanentGem();
        if (chosen != own && !claims.canApplyCandidateGems(survivor, new java.util.ArrayList<>(survivor.getInstalledGems()), chosen)) {
            var registry = plugin.getGemRegistry();
            ItemStack back = registry != null ? registry.createItemFromStats(chosen) : null;
            if (back != null) giveItem(absorbed, back);
            chosen = own;
        }
        survivor.setPermanentGem(chosen);
        claims.recalculateClaimModifiers(survivor);
        plugin.getStorage().saveClaimAsync(survivor);
        claims.removeClaimFromCache(absorbed.getId());
        plugin.getStorage().deleteClaimAsync(absorbed.getId());

        plugin.getEffects().playAt(survivor.getAnchorLocation(), "claim-merge");
        if (anchor != null) plugin.getEffects().playAt(anchor, "claim-merge");
        return Reason.OK;
    }

    private void giveBack(Claim absorbed) {
        var tier = plugin.getAnchorManager().resolveClaimTier(absorbed);
        ItemStack anchorItem = plugin.getAnchorManager().createAnchorItem(tier != null ? tier.id() : "tier-1");
        java.util.List<ItemStack> items = new java.util.ArrayList<>();
        if (anchorItem != null) items.add(anchorItem);
        var registry = plugin.getGemRegistry();
        if (registry != null) {
            for (GemStats g : absorbed.getInstalledGems()) {
                ItemStack gi = registry.createItemFromStats(g);
                if (gi != null) items.add(gi);
            }
        }
        for (ItemStack item : items) giveItem(absorbed, item);
    }

    /** Puts the item into the absorbed claim's owner inventory, or drops it at the old anchor when they are offline. */
    private void giveItem(Claim absorbed, ItemStack item) {
        Player owner = absorbed.getOwnerUuid() != null ? plugin.getServer().getPlayer(absorbed.getOwnerUuid()) : null;
        Location at = absorbed.getAnchorLocation();
        if (owner != null) {
            owner.getInventory().addItem(item).values()
                    .forEach(left -> owner.getWorld().dropItemNaturally(owner.getLocation(), left));
        } else if (at != null && at.getWorld() != null) {
            at.getWorld().dropItemNaturally(at, item);
        }
    }
}
