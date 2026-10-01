package me.lovelace.loveclaims.task;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.model.UserData;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.util.List;

/**
 * Подсказка границ чужих приватов для игрока, который ХОДИТ с якорем в руке (стоящему не показывается).
 * Партиклы не создают энтити на сервере и отрисовываются клиентом. Rental и торговые точки здесь не
 * участвуют: их контур показывает {@link ParticleBorder} по событию.
 */
public class ProximityBorderTask {
    private final LoveClaims plugin;
    private ScheduledTask task;
    private final java.util.Map<java.util.UUID, Location> lastPositions = new java.util.concurrent.ConcurrentHashMap<>();

    private static final Particle.DustOptions DUST_CYAN = new Particle.DustOptions(Color.fromRGB(0, 229, 255), 1.0f);
    private static final Particle.DustOptions DUST_GREEN = new Particle.DustOptions(Color.fromRGB(85, 255, 85), 1.0f);
    private static final Particle.DustOptions DUST_RED = new Particle.DustOptions(Color.fromRGB(255, 60, 60), 1.0f);

    public ProximityBorderTask(LoveClaims plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // Runs on the main thread: it iterates Bukkit.getOnlinePlayers() and reads player.getLocation(),
        // which is not safe from the async scheduler while players join, quit or move. The work per tick
        // is a cheap distance check, so there is nothing worth moving off-thread.
        task = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> {
            var cfg = plugin.getConfigManager().getConfig();
            if (!cfg.getBoolean("proximity-border.enabled", true)) return;

            double detectionDist = cfg.getDouble("proximity-border.detection-distance", 8.0);
            boolean requireAnchor = cfg.getBoolean("proximity-border.require-anchor", true);

            lastPositions.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.isOnline()) continue;

                UserData data = plugin.getUserManager().getUserData(player.getUniqueId());
                if (data != null && !data.isShowProximityBorder()) {
                    continue;
                }

                Location pLoc = player.getLocation();
                if (pLoc.getWorld() == null) continue;

                // A standing player sees nothing: the border is a hint while walking with an anchor, not decoration.
                Location previous = lastPositions.put(player.getUniqueId(), pLoc.clone());
                if (previous == null || !previous.getWorld().equals(pLoc.getWorld())
                        || previous.distanceSquared(pLoc) < 0.0025) {
                    continue;
                }
                if (requireAnchor && !holdsAnchor(player)) continue;
                // Spawn protection is a config circle, not a claim: never outlined.
                if (plugin.getConfigManager().isInsideSpawnClaim(pLoc)) continue;

                List<Claim> nearbyClaims = plugin.getClaimManager().getClaimsNear(pLoc, detectionDist + 4.0);
                for (Claim claim : nearbyClaims) {
                    if (claim.getWorld() == null || !claim.getWorld().equals(pLoc.getWorld())) continue;
                    // Rental plots and trade points are outlined only when someone is about to rent them.
                    if (claim.isRentalPlot()) continue;
                    // Own claim and claims the player is a member of need no warning.
                    if (claim.isOwner(player.getUniqueId()) || claim.getMembers().containsKey(player.getUniqueId())) continue;

                    BoundingBox box = claim.getBoundingBox();
                    double dist = distanceToBox(pLoc.getX(), pLoc.getY(), pLoc.getZ(), box);
                    if (dist > detectionDist) continue;

                    spawnBorderParticles(player, pLoc, box, claim, detectionDist);
                }
            }
        }, 10L, 10L); // Каждые 10 тиков (0.5 сек)
    }

    public void cancel() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    private void spawnBorderParticles(Player player, Location pLoc, BoundingBox box, Claim claim, double maxDist) {
        Particle.DustOptions dust;
        if (claim.isClanTerritory()) {
            dust = DUST_RED;
        } else if (claim.isOwner(player.getUniqueId()) || claim.getMembers().containsKey(player.getUniqueId())) {
            dust = DUST_GREEN;
        } else {
            dust = DUST_CYAN;
        }

        double minX = box.getMinX();
        double maxX = box.getMaxX();
        double minY = box.getMinY();
        double maxY = box.getMaxY();
        double minZ = box.getMinZ();
        double maxZ = box.getMaxZ();

        double px = pLoc.getX();
        double py = pLoc.getY();
        double pz = pLoc.getZ();

        double lowerY = Math.max(minY, py);
        double upperY = Math.min(maxY, py + 1.2);

        double maxDistSq = maxDist * maxDist;

        // Линия по оси X при Z = minZ
        if (Math.abs(pz - minZ) <= maxDist) {
            int startX = (int) Math.max(minX, Math.floor(px - maxDist));
            int endX = (int) Math.min(maxX, Math.ceil(px + maxDist));
            for (double x = startX; x <= endX; x += 1.0) {
                if (sq(x - px) + sq(minZ - pz) <= maxDistSq) {
                    player.spawnParticle(Particle.DUST, x, lowerY, minZ, 1, 0, 0, 0, 0, dust);
                    if (upperY > lowerY + 0.5) {
                        player.spawnParticle(Particle.DUST, x, upperY, minZ, 1, 0, 0, 0, 0, dust);
                    }
                }
            }
        }

        // Линия по оси X при Z = maxZ
        if (Math.abs(pz - maxZ) <= maxDist) {
            int startX = (int) Math.max(minX, Math.floor(px - maxDist));
            int endX = (int) Math.min(maxX, Math.ceil(px + maxDist));
            for (double x = startX; x <= endX; x += 1.0) {
                if (sq(x - px) + sq(maxZ - pz) <= maxDistSq) {
                    player.spawnParticle(Particle.DUST, x, lowerY, maxZ, 1, 0, 0, 0, 0, dust);
                    if (upperY > lowerY + 0.5) {
                        player.spawnParticle(Particle.DUST, x, upperY, maxZ, 1, 0, 0, 0, 0, dust);
                    }
                }
            }
        }

        // Линия по оси Z при X = minX
        if (Math.abs(px - minX) <= maxDist) {
            int startZ = (int) Math.max(minZ, Math.floor(pz - maxDist));
            int endZ = (int) Math.min(maxZ, Math.ceil(pz + maxDist));
            for (double z = startZ; z <= endZ; z += 1.0) {
                if (sq(minX - px) + sq(z - pz) <= maxDistSq) {
                    player.spawnParticle(Particle.DUST, minX, lowerY, z, 1, 0, 0, 0, 0, dust);
                    if (upperY > lowerY + 0.5) {
                        player.spawnParticle(Particle.DUST, minX, upperY, z, 1, 0, 0, 0, 0, dust);
                    }
                }
            }
        }

        // Линия по оси Z при X = maxX
        if (Math.abs(px - maxX) <= maxDist) {
            int startZ = (int) Math.max(minZ, Math.floor(pz - maxDist));
            int endZ = (int) Math.min(maxZ, Math.ceil(pz + maxDist));
            for (double z = startZ; z <= endZ; z += 1.0) {
                if (sq(maxX - px) + sq(z - pz) <= maxDistSq) {
                    player.spawnParticle(Particle.DUST, maxX, lowerY, z, 1, 0, 0, 0, 0, dust);
                    if (upperY > lowerY + 0.5) {
                        player.spawnParticle(Particle.DUST, maxX, upperY, z, 1, 0, 0, 0, 0, dust);
                    }
                }
            }
        }

        // Вертикальные колонны на 4 углах (если угол рядом с игроком)
        spawnCornerColumn(player, minX, minZ, lowerY, upperY, px, pz, maxDistSq, dust);
        spawnCornerColumn(player, minX, maxZ, lowerY, upperY, px, pz, maxDistSq, dust);
        spawnCornerColumn(player, maxX, minZ, lowerY, upperY, px, pz, maxDistSq, dust);
        spawnCornerColumn(player, maxX, maxZ, lowerY, upperY, px, pz, maxDistSq, dust);
    }

    private void spawnCornerColumn(Player player, double x, double z, double fromY, double toY, double px, double pz, double maxDistSq, Particle.DustOptions dust) {
        if (sq(x - px) + sq(z - pz) <= maxDistSq) {
            for (double y = Math.max(fromY - 1.0, 0); y <= toY + 1.0; y += 0.5) {
                player.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, dust);
            }
        }
    }

    private boolean holdsAnchor(Player player) {
        var anchors = plugin.getAnchorManager();
        return anchors.getTierFromItem(player.getInventory().getItemInMainHand()).isPresent()
                || anchors.getTierFromItem(player.getInventory().getItemInOffHand()).isPresent();
    }

    private double distanceToBox(double px, double py, double pz, BoundingBox box) {
        double dx = Math.max(box.getMinX() - px, Math.max(0, px - box.getMaxX()));
        double dy = Math.max(box.getMinY() - py, Math.max(0, py - box.getMaxY()));
        double dz = Math.max(box.getMinZ() - pz, Math.max(0, pz - box.getMaxZ()));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private double sq(double val) {
        return val * val;
    }
}
