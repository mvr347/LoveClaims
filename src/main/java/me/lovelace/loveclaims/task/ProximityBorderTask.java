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
import java.util.concurrent.TimeUnit;

/**
 * Фоновая задача для отображения границ привата при приближении игрока.
 * Высокопроизводительный режим на партиклах (по умолчанию) не создаёт энтити на сервере
 * и отрисовывается исключительно клиентом.
 */
public class ProximityBorderTask {
    private final LoveClaims plugin;
    private ScheduledTask task;

    private static final Particle.DustOptions DUST_CYAN = new Particle.DustOptions(Color.fromRGB(0, 229, 255), 1.0f);
    private static final Particle.DustOptions DUST_GREEN = new Particle.DustOptions(Color.fromRGB(85, 255, 85), 1.0f);
    private static final Particle.DustOptions DUST_RED = new Particle.DustOptions(Color.fromRGB(255, 60, 60), 1.0f);

    public ProximityBorderTask(LoveClaims plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin, scheduledTask -> {
            boolean enabled = plugin.getConfigManager().getConfig().getBoolean("proximity-border.enabled", true);
            if (!enabled) return;

            double detectionDist = plugin.getConfigManager().getConfig().getDouble("proximity-border.detection-distance", 8.0);
            String mode = plugin.getConfigManager().getConfig().getString("proximity-border.mode", "PARTICLES").toUpperCase();

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.isOnline()) continue;

                UserData data = plugin.getUserManager().getUserData(player.getUniqueId());
                if (data != null && !data.isShowProximityBorder()) {
                    continue;
                }

                Location pLoc = player.getLocation();
                if (pLoc.getWorld() == null) continue;

                List<Claim> nearbyClaims = plugin.getClaimManager().getClaimsNear(pLoc, detectionDist + 4.0);
                for (Claim claim : nearbyClaims) {
                    if (claim.getWorld() == null || !claim.getWorld().equals(pLoc.getWorld())) continue;

                    BoundingBox box = claim.getBoundingBox();
                    double dist = distanceToBox(pLoc.getX(), pLoc.getY(), pLoc.getZ(), box);
                    if (dist > detectionDist) continue;

                    if ("ITEM_DISPLAY".equals(mode)) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (player.isOnline()) {
                                BorderDisplayTask.showBorder(plugin, player, box, 30L, claim.getId());
                            }
                        });
                    } else {
                        // Режим PARTICLES: спавним легкие партиклы по ребрам на высоте игрока
                        spawnBorderParticles(player, pLoc, box, claim, detectionDist);
                    }
                }
            }
        }, 500, 500, TimeUnit.MILLISECONDS); // Каждые 10 тиков (0.5 сек)
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
