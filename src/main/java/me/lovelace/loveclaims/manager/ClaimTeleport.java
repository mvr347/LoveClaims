package me.lovelace.loveclaims.manager;

import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.model.Claim;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Teleport to a claim's point: a warm-up (teleport.delay-seconds) that is cancelled by moving or taking damage,
 * a cooldown after each teleport, and no teleport to the claim the player already stands in. Main thread only.
 */
public final class ClaimTeleport implements Listener {
    private static final double MOVE_LIMIT_SQUARED = 1.0;

    private record Pending(BukkitTask task, Location start) {
    }

    private final LoveClaims plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldownUntil = new ConcurrentHashMap<>();

    public ClaimTeleport(LoveClaims plugin) {
        this.plugin = plugin;
    }

    public int delaySeconds() {
        return Math.max(0, plugin.getConfig().getInt("teleport.delay-seconds"));
    }

    public int cooldownSeconds() {
        return Math.max(0, plugin.getConfig().getInt("teleport.cooldown-seconds"));
    }

    /** The point a teleport to this claim lands on. */
    public Location destination(Claim claim) {
        Location home = claim.getHomeLocation();
        if (home == null || home.getWorld() == null) {
            double x = (claim.getBoundingBox().getMinX() + claim.getBoundingBox().getMaxX()) / 2.0;
            double z = (claim.getBoundingBox().getMinZ() + claim.getBoundingBox().getMaxZ()) / 2.0;
            return new Location(claim.getWorld(), x, claim.getWorld().getHighestBlockYAt((int) x, (int) z) + 1, z);
        }
        Location anchor = claim.getAnchorLocation();
        if (anchor != null && home.getBlockX() == anchor.getBlockX() && home.getBlockY() == anchor.getBlockY()
                && home.getBlockZ() == anchor.getBlockZ()) {
            return home.clone().add(0.5, 1.0, 0.5);
        }
        return home.clone();
    }

    public boolean isInside(Player player, Claim claim) {
        Location at = player.getLocation();
        return claim.getWorld() != null && claim.getWorld().equals(at.getWorld())
                && claim.getBoundingBox().contains(at.getX(), at.getY(), at.getZ());
    }

    /** Starts the warm-up (or teleports at once with the bypass permission); tells the player when it cannot start. */
    public void request(Player player, Claim claim) {
        var lang = plugin.getConfigManager();
        if (pending.containsKey(player.getUniqueId())) {
            player.sendMessage(lang.getMessage("teleport.pending"));
            return;
        }
        if (isInside(player, claim)) {
            plugin.getEffects().playFor(player, "gui-error");
            player.sendMessage(lang.getMessage("teleport.already-here"));
            return;
        }
        // An undeclared permission is true for every operator, so the bypass is opt-in in the config:
        // an owner who tests as an OP must see the real warm-up.
        boolean bypass = plugin.getConfig().getBoolean("teleport.allow-bypass")
                && player.hasPermission("loveclaims.teleport.bypass");
        long left = cooldownUntil.getOrDefault(player.getUniqueId(), 0L) - System.currentTimeMillis();
        if (!bypass && left > 0) {
            plugin.getEffects().playFor(player, "gui-error");
            player.sendMessage(lang.getMessage("teleport.cooldown", "left", String.valueOf((left + 999) / 1000)));
            return;
        }
        int delay = bypass ? 0 : delaySeconds();
        if (delay == 0) {
            finish(player, claim);
            return;
        }
        player.sendMessage(lang.getMessage("teleport.start", "delay", String.valueOf(delay)));
        Location start = player.getLocation().clone();
        int[] secondsLeft = {delay};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                cancel(player.getUniqueId());
                return;
            }
            if (start.getWorld() != player.getWorld() || start.distanceSquared(player.getLocation()) > MOVE_LIMIT_SQUARED) {
                cancel(player.getUniqueId());
                player.sendMessage(lang.getMessage("teleport.cancelled-move"));
                return;
            }
            if (--secondsLeft[0] <= 0) {
                cancel(player.getUniqueId());
                // The claim may have been removed while waiting.
                if (plugin.getClaimManager().getClaimById(claim.getId()).orElse(null) != claim) return;
                finish(player, claim);
                return;
            }
            player.sendActionBar(lang.getComponent("teleport.countdown", "left", String.valueOf(secondsLeft[0])));
        }, 20L, 20L);
        pending.put(player.getUniqueId(), new Pending(task, start));
    }

    private void finish(Player player, Claim claim) {
        Location target = destination(claim);
        player.teleportAsync(target);
        startCooldown(player.getUniqueId());
        player.sendMessage(plugin.getConfigManager().getMessage("teleport.done"));
        plugin.getConfigManager().playSound(player, "anchor-place");
    }

    public void startCooldown(UUID id) {
        cooldownUntil.put(id, System.currentTimeMillis() + cooldownSeconds() * 1000L);
    }

    /** Seconds left before the player may teleport again (0 when free). */
    public long cooldownLeft(UUID id) {
        long left = cooldownUntil.getOrDefault(id, 0L) - System.currentTimeMillis();
        return left <= 0 ? 0 : (left + 999) / 1000;
    }

    private void cancel(UUID id) {
        Pending p = pending.remove(id);
        if (p != null) p.task().cancel();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && pending.containsKey(player.getUniqueId())) {
            cancel(player.getUniqueId());
            player.sendMessage(plugin.getConfigManager().getMessage("teleport.cancelled-damage"));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer().getUniqueId());
        cooldownUntil.remove(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        pending.values().forEach(p -> p.task().cancel());
        pending.clear();
    }
}
