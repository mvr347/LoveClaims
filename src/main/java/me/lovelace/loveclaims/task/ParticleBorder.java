package me.lovelace.loveclaims.task;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.lovelace.loveclaims.LoveClaims;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Timed particle outline of a whole claim box, visible only to one player. Used for short, event-driven
 * hints (about to rent a plot, anchor overlapped someone else's claim) instead of a permanent proximity border.
 * One outline per player: a new one replaces the previous.
 */
public final class ParticleBorder {

    public static final Particle.DustOptions DUST_GOLD = new Particle.DustOptions(Color.fromRGB(255, 200, 40), 1.2f);
    public static final Particle.DustOptions DUST_CYAN = new Particle.DustOptions(Color.fromRGB(0, 229, 255), 1.2f);
    public static final Particle.DustOptions DUST_RED = new Particle.DustOptions(Color.fromRGB(255, 60, 60), 1.2f);

    private static final long PERIOD_TICKS = 5L;
    private static final Map<UUID, ScheduledTask> ACTIVE = new ConcurrentHashMap<>();

    private ParticleBorder() {
    }

    /**
     * Shows the outline for {@code ticks}. Nothing is drawn inside the spawn protection area, which is
     * not a claim and must never get a border.
     */
    public static void show(LoveClaims plugin, Player player, BoundingBox box, long ticks, Particle.DustOptions dust) {
        cancel(player.getUniqueId());
        if (ticks <= 0 || box == null || !player.isOnline()) return;
        if (plugin.getConfigManager().isInsideSpawnClaim(player.getLocation())) return;

        int maxPoints = Math.max(50, plugin.getConfigManager().getConfig().getInt("proximity-border.particle-max-points", 600));
        List<double[]> points = outlinePoints(box, maxPoints);
        UUID id = player.getUniqueId();
        org.bukkit.World world = player.getWorld();
        long[] left = {ticks};

        ScheduledTask task = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, t -> {
            if (!player.isOnline() || left[0] <= 0 || !player.getWorld().equals(world)) {
                t.cancel();
                ACTIVE.remove(id, t);
                return;
            }
            for (double[] p : points) {
                player.spawnParticle(Particle.DUST, p[0], p[1], p[2], 1, 0, 0, 0, 0, dust);
            }
            left[0] -= PERIOD_TICKS;
        }, 1L, PERIOD_TICKS);
        ACTIVE.put(id, task);
    }

    public static void cancel(UUID id) {
        ScheduledTask old = ACTIVE.remove(id);
        if (old != null && !old.isCancelled()) old.cancel();
    }

    public static void cancelAll() {
        for (UUID id : new ArrayList<>(ACTIVE.keySet())) cancel(id);
    }

    /**
     * Points along the 12 edges of the box, about one per block, thinned so the result never exceeds
     * {@code maxPoints} (big plots would otherwise flood the client).
     */
    public static List<double[]> outlinePoints(BoundingBox box, int maxPoints) {
        double[] xs = {box.getMinX(), box.getMaxX()};
        double[] ys = {box.getMinY(), box.getMaxY()};
        double[] zs = {box.getMinZ(), box.getMaxZ()};
        double lenX = xs[1] - xs[0];
        double lenY = ys[1] - ys[0];
        double lenZ = zs[1] - zs[0];

        double total = 4 * (lenX + lenY + lenZ) + 8;
        double step = Math.max(1.0, Math.ceil(total / Math.max(1, maxPoints - 8)));

        List<double[]> out = new ArrayList<>();
        for (double y : ys) {
            for (double z : zs) {
                for (double x = xs[0]; x <= xs[1]; x += step) out.add(new double[]{x, y, z});
            }
        }
        for (double y : ys) {
            for (double x : xs) {
                for (double z = zs[0]; z <= zs[1]; z += step) out.add(new double[]{x, y, z});
            }
        }
        for (double x : xs) {
            for (double z : zs) {
                for (double y = ys[0]; y <= ys[1]; y += step) out.add(new double[]{x, y, z});
            }
        }
        return out;
    }
}
