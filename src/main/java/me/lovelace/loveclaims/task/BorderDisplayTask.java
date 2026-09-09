package me.lovelace.loveclaims.task;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.textures.HeadTextures;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Задача для отображения границ привата.
 * Использует ItemDisplay entities (голова зеленого стекла, точно как при создании привата в BlockPreviewTask),
 * либо sendMultiBlockChange как запасной вариант при выключенном use-entity-blocks.
 */
public class BorderDisplayTask {
    private static final Map<UUID, BorderSession> activeBorders = new ConcurrentHashMap<>();

    record BorderSession(Map<Location, BlockData> originalBlocks,
                         List<ItemDisplay> entities,
                         ScheduledTask endTask,
                         ScheduledTask refreshTask) {}

    public static void hideBorder(Player player) {
        BorderSession session = activeBorders.remove(player.getUniqueId());
        if (session != null) {
            if (session.endTask() != null && !session.endTask().isCancelled()) {
                session.endTask().cancel();
            }
            if (session.refreshTask() != null && !session.refreshTask().isCancelled()) {
                session.refreshTask().cancel();
            }
            if (session.entities() != null) {
                for (ItemDisplay entity : session.entities()) {
                    if (entity != null && entity.isValid() && !entity.isDead()) {
                        entity.remove();
                    }
                }
            }
            if (session.originalBlocks() != null && !session.originalBlocks().isEmpty() && player.isOnline()) {
                player.sendMultiBlockChange(session.originalBlocks());
            }
        }
    }

    public static void showBorder(LoveClaims plugin, Player player, BoundingBox box, long durationTicks) {
        showBorder(plugin, player, box, durationTicks, null);
    }

    public static void showBorder(LoveClaims plugin, Player player, BoundingBox box, long durationTicks, UUID claimId) {
        hideBorder(player);

        boolean useEntity = plugin.getConfigManager().isUseEntityBlocks();
        boolean glowing = plugin.getConfigManager().isBorderGlowing();

        final boolean isClan = claimId != null && plugin.getClaimManager().getClaimById(claimId)
                .map(Claim::isClanTerritory)
                .orElse(false);

        int minX = (int) Math.floor(box.getMinX());
        int minY = (int) Math.floor(box.getMinY());
        int minZ = (int) Math.floor(box.getMinZ());
        int maxX = (int) Math.floor(box.getMaxX()) - 1;
        int maxY = (int) Math.floor(box.getMaxY()) - 1;
        int maxZ = (int) Math.floor(box.getMaxZ()) - 1;

        Map<Location, BlockData> originalBlocks = new HashMap<>();
        Map<Location, BlockData> fakeBlocks = new HashMap<>();
        List<ItemDisplay> entities = new ArrayList<>();

        int maxBlocks = plugin.getConfigManager().getConfig().getInt("border.max-blocks", 5000);
        Set<Location> borderLocations = new LinkedHashSet<>();

        // Нижняя и верхняя грань (по оси X)
        for (int x = minX; x <= maxX; x++) {
            borderLocations.add(new Location(player.getWorld(), x + 0.5, minY, minZ + 0.5));
            borderLocations.add(new Location(player.getWorld(), x + 0.5, minY, maxZ + 0.5));
            borderLocations.add(new Location(player.getWorld(), x + 0.5, maxY, minZ + 0.5));
            borderLocations.add(new Location(player.getWorld(), x + 0.5, maxY, maxZ + 0.5));
        }

        // Нижняя и верхняя грань (по оси Z)
        for (int z = minZ + 1; z < maxZ; z++) {
            borderLocations.add(new Location(player.getWorld(), minX + 0.5, minY, z + 0.5));
            borderLocations.add(new Location(player.getWorld(), maxX + 0.5, minY, z + 0.5));
            borderLocations.add(new Location(player.getWorld(), minX + 0.5, maxY, z + 0.5));
            borderLocations.add(new Location(player.getWorld(), maxX + 0.5, maxY, z + 0.5));
        }

        // Вертикальные углы (по оси Y)
        for (int y = minY + 1; y < maxY; y++) {
            borderLocations.add(new Location(player.getWorld(), minX + 0.5, y, minZ + 0.5));
            borderLocations.add(new Location(player.getWorld(), maxX + 0.5, y, minZ + 0.5));
            borderLocations.add(new Location(player.getWorld(), minX + 0.5, y, maxZ + 0.5));
            borderLocations.add(new Location(player.getWorld(), maxX + 0.5, y, maxZ + 0.5));
        }

        int count = 0;
        BlockData fakeBorderData = isClan
                ? Material.RED_STAINED_GLASS.createBlockData()
                : plugin.getConfigManager().getBorderMaterial().createBlockData();

        for (Location loc : borderLocations) {
            if (count++ >= maxBlocks) break;

            if (!useEntity) {
                Location blockLoc = loc.clone().subtract(0.5, 0, 0.5);
                if (!originalBlocks.containsKey(blockLoc)) {
                    originalBlocks.put(blockLoc, blockLoc.getBlock().getBlockData());
                    fakeBlocks.put(blockLoc, fakeBorderData);
                }
            } else {
                ItemDisplay display = player.getWorld().spawn(loc, ItemDisplay.class, entity -> {
                    entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD);
                    if (isClan) {
                        entity.setItemStack(new ItemStack(Material.RED_STAINED_GLASS));
                    } else {
                        setHeadTexture(entity, HeadTextures.PREVIEW_GLASS_GREEN);
                    }
                    entity.setBillboard(Display.Billboard.FIXED);

                    Transformation transform = entity.getTransformation();
                    transform.getLeftRotation().set(0, 0, 0, 1);
                    transform.getRightRotation().set(0, 0, 0, 1);
                    transform.getScale().set(0.8f, 0.8f, 0.8f);
                    entity.setTransformation(transform);

                    if (glowing) {
                        entity.setGlowing(true);
                        entity.setBrightness(new Display.Brightness(15, 15));
                    }

                    entity.setInterpolationDuration(1);
                    entity.setTeleportDuration(1);

                    entity.setVisibleByDefault(false);
                    player.showEntity(plugin, entity);
                });
                entities.add(display);
            }
        }

        ScheduledTask refreshTask = null;
        if (!useEntity) {
            player.sendMultiBlockChange(fakeBlocks);
            refreshTask = player.getScheduler().runAtFixedRate(plugin, task -> {
                if (player.isOnline() && activeBorders.containsKey(player.getUniqueId())) {
                    player.sendMultiBlockChange(fakeBlocks);
                } else {
                    task.cancel();
                }
            }, null, 20L, 20L);
        }

        ScheduledTask endTask = player.getScheduler().runDelayed(plugin, task -> {
            hideBorder(player);
        }, null, durationTicks);

        activeBorders.put(player.getUniqueId(), new BorderSession(originalBlocks, entities, endTask, refreshTask));
    }

    private static void setHeadTexture(ItemDisplay display, String textureBase64) {
        try {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                UUID uuid = UUID.randomUUID();
                com.destroystokyo.paper.profile.PlayerProfile profile = Bukkit.createProfile(uuid, "Border");
                com.destroystokyo.paper.profile.ProfileProperty textureProperty =
                        new com.destroystokyo.paper.profile.ProfileProperty("textures", textureBase64);
                profile.setProperty(textureProperty);
                meta.setOwnerProfile(profile);
                head.setItemMeta(meta);
                display.setItemStack(head);
            }
        } catch (NoClassDefFoundError | Exception e) {
            display.setItemStack(new ItemStack(Material.LIME_STAINED_GLASS));
        }
    }
}