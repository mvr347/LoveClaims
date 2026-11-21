package me.lovelace.loveclaims.listener;

import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.model.Claim;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BrewingStartEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Applies local gem modifiers for crops, breeding, furnaces, brewing stands, and crop double drops.
 */
public final class GemEffectListener implements Listener {
    private static final EnumSet<Material> TRACKED_PLACED_CROPS = EnumSet.of(
            Material.MELON, Material.PUMPKIN, Material.SUGAR_CANE,
            Material.CACTUS, Material.BAMBOO, Material.KELP, Material.KELP_PLANT,
            Material.CHORUS_PLANT, Material.CHORUS_FLOWER
    );

    private static final EnumSet<Material> AGEABLE_HARVEST_CROPS = EnumSet.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.NETHER_WART, Material.COCOA, Material.SWEET_BERRY_BUSH,
            Material.TORCHFLOWER_CROP, Material.PITCHER_CROP
    );

    private final LoveClaims plugin;
    private final NamespacedKey placedCropsKey;

    public GemEffectListener(LoveClaims plugin) {
        this.plugin = plugin;
        this.placedCropsKey = new NamespacedKey(plugin, "placed_crop_blocks");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        Optional<Claim> claimOpt = plugin.getClaimManager().getClaimAt(event.getBlock().getLocation());
        if (claimOpt.isEmpty()) return;
        Claim claim = claimOpt.get();
        if (claim.isClanTerritory() || claim.isRentalPlot()) return;

        double mult = claim.getModifiers().cropGrowthMultiplier();
        if (Math.abs(mult) < 1e-6) return;

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        if (mult < 0) {
            if (rng.nextDouble() < Math.min(0.95, -mult)) {
                event.setCancelled(true);
            }
            return;
        }

        if (event.getNewState().getBlockData() instanceof Ageable ageable) {
            int guaranteed = (int) Math.floor(mult);
            double fractional = mult - guaranteed;
            int extra = guaranteed + (rng.nextDouble() < fractional ? 1 : 0);
            if (extra > 0) {
                int nextAge = Math.min(ageable.getMaximumAge(), ageable.getAge() + extra);
                ageable.setAge(nextAge);
                event.getNewState().setBlockData(ageable);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityBreed(EntityBreedEvent event) {
        Optional<Claim> claimOpt = plugin.getClaimManager().getClaimAt(event.getEntity().getLocation());
        if (claimOpt.isEmpty()) return;
        Claim claim = claimOpt.get();
        if (claim.isClanTerritory() || claim.isRentalPlot()) return;

        double mult = claim.getModifiers().cropGrowthMultiplier();
        if (mult <= 1e-6) return;

        double factor = 1.0 + mult;
        var baby = event.getEntity();
        var mother = event.getMother();
        var father = event.getFather();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (baby instanceof org.bukkit.entity.Ageable ageableBaby && baby.isValid() && ageableBaby.getAge() < 0) {
                ageableBaby.setAge((int) Math.round(ageableBaby.getAge() / factor));
            }
            if (mother instanceof Animals m && m.isValid() && m.getAge() > 0) {
                m.setAge((int) Math.max(20, Math.round(m.getAge() / factor)));
            }
            if (father instanceof Animals f && f.isValid() && f.getAge() > 0) {
                f.setAge((int) Math.max(20, Math.round(f.getAge() / factor)));
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFurnaceStartSmelt(FurnaceStartSmeltEvent event) {
        Optional<Claim> claimOpt = plugin.getClaimManager().getClaimAt(event.getBlock().getLocation());
        if (claimOpt.isEmpty()) return;
        Claim claim = claimOpt.get();
        if (claim.isClanTerritory() || claim.isRentalPlot()) return;

        double mult = claim.getModifiers().furnaceSpeedMultiplier();
        if (Math.abs(mult) < 1e-6) return;

        double factor = Math.max(0.1, 1.0 + mult);
        int adjusted = Math.max(1, (int) Math.round(event.getTotalCookTime() / factor));
        event.setTotalCookTime(adjusted);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBrewingStart(BrewingStartEvent event) {
        Optional<Claim> claimOpt = plugin.getClaimManager().getClaimAt(event.getBlock().getLocation());
        if (claimOpt.isEmpty()) return;
        Claim claim = claimOpt.get();
        if (claim.isClanTerritory() || claim.isRentalPlot()) return;

        double mult = claim.getModifiers().furnaceSpeedMultiplier();
        if (Math.abs(mult) < 1e-6) return;

        double factor = Math.max(0.1, 1.0 + mult);
        int baseTime = event.getRecipeBrewTime() > 0 ? event.getRecipeBrewTime() : event.getBrewingTime();
        int adjusted = Math.max(1, (int) Math.round(baseTime / factor));
        event.setBrewingTime(adjusted);
        event.setRecipeBrewTime(adjusted);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (TRACKED_PLACED_CROPS.contains(block.getType())) {
            markPlayerPlaced(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        transferTrackedOnPiston(event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        transferTrackedOnPiston(event.getBlocks(), event.getDirection());
    }

    private void transferTrackedOnPiston(List<Block> blocks, BlockFace direction) {
        if (blocks.isEmpty()) return;
        List<Block> toMark = new ArrayList<>();
        for (Block b : blocks) {
            if (unmarkPlayerPlaced(b)) {
                toMark.add(b.getRelative(direction));
            }
        }
        for (Block moved : toMark) {
            markPlayerPlaced(moved);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        boolean placedByPlayer = unmarkPlayerPlaced(block);
        if (placedByPlayer || !event.isDropItems()) return;

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;

        Optional<Claim> claimOpt = plugin.getClaimManager().getClaimAt(block.getLocation());
        if (claimOpt.isEmpty()) return;
        Claim claim = claimOpt.get();
        if (claim.isClanTerritory() || claim.isRentalPlot()) return;

        double chance = claim.getModifiers().doubleDropChance();
        if (chance <= 1e-6) return;

        if (!isMatureNaturalHarvest(block)) return;

        if (ThreadLocalRandom.current().nextDouble() < chance) {
            ItemStack tool = player.getInventory().getItemInMainHand();
            Collection<ItemStack> drops = block.getDrops(tool, player);
            var dropLoc = block.getLocation().add(0.5, 0.5, 0.5);
            for (ItemStack drop : drops) {
                if (drop != null && !drop.getType().isAir() && drop.getAmount() > 0) {
                    block.getWorld().dropItemNaturally(dropLoc, drop.clone());
                }
            }
        }
    }

    private static boolean isMatureNaturalHarvest(Block block) {
        Material type = block.getType();
        if (AGEABLE_HARVEST_CROPS.contains(type) && block.getBlockData() instanceof Ageable ageable) {
            return ageable.getAge() >= ageable.getMaximumAge();
        }
        return TRACKED_PLACED_CROPS.contains(type);
    }

    private void markPlayerPlaced(Block block) {
        Chunk chunk = block.getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        Set<String> coords = readCoords(pdc);
        if (coords.size() >= 2048) return; // Safety cap per chunk
        if (coords.add(coordKey(block))) {
            writeCoords(pdc, coords);
        }
    }

    private boolean unmarkPlayerPlaced(Block block) {
        if (!TRACKED_PLACED_CROPS.contains(block.getType())) return false;
        Chunk chunk = block.getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        Set<String> coords = readCoords(pdc);
        if (coords.isEmpty()) return false;
        boolean removed = coords.remove(coordKey(block));
        if (removed) {
            writeCoords(pdc, coords);
        }
        return removed;
    }

    private Set<String> readCoords(PersistentDataContainer pdc) {
        String raw = pdc.get(placedCropsKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) return new LinkedHashSet<>();
        Set<String> set = new LinkedHashSet<>();
        Collections.addAll(set, raw.split(";"));
        set.remove("");
        return set;
    }

    private void writeCoords(PersistentDataContainer pdc, Set<String> coords) {
        if (coords.isEmpty()) {
            pdc.remove(placedCropsKey);
        } else {
            pdc.set(placedCropsKey, PersistentDataType.STRING, String.join(";", coords));
        }
    }

    private static String coordKey(Block b) {
        return b.getX() + "," + b.getY() + "," + b.getZ();
    }
}
