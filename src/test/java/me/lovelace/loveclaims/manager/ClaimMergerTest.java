package me.lovelace.loveclaims.manager;

import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.gems.GemRarity;
import me.lovelace.loveclaims.gems.GemStats;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.model.TrustLevel;
import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Map;
import java.util.UUID;

import static me.lovelace.loveclaims.manager.ClaimMerger.Reason.*;
import static org.junit.jupiter.api.Assertions.*;

class ClaimMergerTest {
    ServerMock server;
    LoveClaims plugin;
    PlayerMock owner, other;
    org.bukkit.World world;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        MockBukkit.createMockPlugin("LoveCore");
        plugin = MockBukkit.load(LoveClaims.class);
        owner = server.addPlayer("Owner");
        other = server.addPlayer("Other");
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    private Claim claim(PlayerMock who, double minX, double maxX) {
        Location anchor = new Location(world, (minX + maxX) / 2, 64, 5);
        Claim c = new Claim(UUID.randomUUID(), world, new BoundingBox(minX, 0, 0, maxX, 100, 10), who.getUniqueId(), anchor);
        c.setAnchorTierId("tier-1");
        plugin.getClaimManager().addClaimToCache(c);
        return c;
    }

    private static GemStats gem(String id) {
        return new GemStats(id, GemRarity.BASIC, 1, 0, 0, 0.0, 0.0, 0.0, false, Map.of(), null);
    }

    @Test
    void geometryAcceptsTouchingAndGapUpToLimitOnly() {
        var a = new BoundingBox(0, 0, 0, 10, 100, 10);
        assertTrue(ClaimMerger.geometry(a, new BoundingBox(10, 0, 0, 20, 100, 10), 3).ok(), "touching");
        assertTrue(ClaimMerger.geometry(a, new BoundingBox(13, 0, 0, 23, 100, 10), 3).ok(), "gap 3");
        assertEquals(NOT_NEIGHBOURS, ClaimMerger.geometry(a, new BoundingBox(14, 0, 0, 24, 100, 10), 3).reason(), "gap 4");
        assertEquals(DIAGONAL, ClaimMerger.geometry(a, new BoundingBox(12, 0, 12, 22, 100, 22), 3).reason(), "corner only");
        var merged = ClaimMerger.geometry(a, new BoundingBox(13, 0, 0, 23, 100, 10), 3).merged();
        assertEquals(0, merged.getMinX());
        assertEquals(23, merged.getMaxX());
    }

    @Test
    void blockedWhenAThirdClaimLiesBetween() {
        Claim a = claim(owner, 0, 10);
        Claim b = claim(owner, 13, 23);
        // a third claim sits in the 3 block gap: the bounding box of a and b would take its land
        Claim middle = new Claim(UUID.randomUUID(), world, new BoundingBox(10.5, 0, 0, 12.5, 100, 10), other.getUniqueId(),
                new Location(world, 11, 64, 5));
        plugin.getClaimManager().addClaimToCache(middle);
        assertEquals(BLOCKED_BY_OTHER_CLAIM, plugin.getClaimMerger().check(a, b));
    }

    @Test
    void blockedByAStandaloneRentalPlotInTheGap() {
        Claim a = claim(owner, 0, 10);
        Claim b = claim(owner, 13, 23);
        Claim rental = new Claim(UUID.randomUUID(), world, new BoundingBox(10.5, 0, 0, 12.5, 100, 10), other.getUniqueId(),
                new Location(world, 11, 64, 5));
        rental.setParentClaimId(UUID.randomUUID()); // standalone rental: sentinel parent, not one of the two claims
        plugin.getClaimManager().addClaimToCache(rental);
        assertEquals(BLOCKED_BY_OTHER_CLAIM, plugin.getClaimMerger().check(a, b));
    }

    @Test
    void sameOwnerMergeKeepsSurvivorAndReturnsAbsorbedAnchor() {
        Claim a = claim(owner, 0, 10);
        Claim b = claim(owner, 12, 22);
        b.setTrust(other.getUniqueId(), TrustLevel.BUILD);
        var plan = plugin.getClaimMerger().plan(a, b).orElseThrow();
        assertTrue(plan.sameOwner());
        assertEquals(OK, plugin.getClaimMerger().execute(plan));

        assertEquals(22, a.getBoundingBox().getMaxX());
        assertEquals(0, a.getBoundingBox().getMinX());
        assertTrue(plugin.getClaimManager().getClaimById(b.getId()).isEmpty(), "absorbed claim is gone");
        assertTrue(plugin.getClaimManager().getClaimById(a.getId()).isPresent());
        assertEquals(TrustLevel.BUILD, a.getTrust(other.getUniqueId()), "member carried over");
        assertTrue(plugin.getClaimManager().getClaimAt(new Location(world, 21, 50, 5)).isPresent());
        assertTrue(java.util.Arrays.stream(owner.getInventory().getContents()).anyMatch(i -> i != null
                && plugin.getAnchorManager().isAnchor(i)), "absorbed anchor returned to its owner");
    }

    @Test
    void fusedGemSurvivesFromTheOnlyClaimThatHasOne() {
        Claim a = claim(owner, 0, 10);
        Claim b = claim(owner, 12, 22);
        b.setPermanentGem(gem("domain"));
        assertEquals(OK, plugin.getClaimMerger().execute(plugin.getClaimMerger().plan(a, b).orElseThrow()));
        assertNotNull(a.getPermanentGem());
    }

    @Test
    void bothFusedGemsLeaveExactlyOne() {
        Claim a = claim(owner, 0, 10);
        Claim b = claim(owner, 12, 22);
        a.setPermanentGem(gem("domain"));
        b.setPermanentGem(gem("farm"));
        var plan = plugin.getClaimMerger().plan(a, b).orElseThrow();
        assertTrue(plan.bothHavePermanentGem());
        assertEquals(OK, plugin.getClaimMerger().execute(plan));
        assertNotNull(a.getPermanentGem());
        assertTrue(a.getPermanentGem().gemId().equals("domain") || a.getPermanentGem().gemId().equals("farm"));
    }

    @Test
    void explicitZeroGapAllowsOnlyTouchingClaims() {
        plugin.getConfig().set("merge.max-gap", 0);
        assertEquals(0.0, plugin.getClaimMerger().maxGap());
        Claim a = claim(owner, 0, 10);
        Claim gap2 = claim(owner, 12, 22);
        assertEquals(NOT_NEIGHBOURS, plugin.getClaimMerger().check(a, gap2));
        plugin.getConfig().set("merge.max-gap", 3);
        assertEquals(OK, plugin.getClaimMerger().check(a, gap2));
    }

    @Test
    void aClaimCannotMergeWithItself() {
        Claim a = claim(owner, 0, 10);
        assertEquals(SAME_CLAIM, plugin.getClaimMerger().check(a, a));
    }
}
