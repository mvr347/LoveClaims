package me.lovelace.loveclaims.listener;

import me.lovelace.loveclaims.LoveClaims;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.EnumSet;
import java.util.Set;

/**
 * Keeps unbreakable blocks unbreakable. Bedrock-breaking exploits (packet-level instant break,
 * modified clients, plugin-made explosions, piston tricks) all end in one of a few server-side
 * events, so the guard sits on those instead of trying to recognise each trick:
 * <ul>
 *   <li>{@link BlockDamageEvent} / {@link BlockBreakEvent} - a survival player can never legitimately
 *       finish breaking a block with negative hardness, so both are cancelled;</li>
 *   <li>explosion block lists - such blocks are removed from them;</li>
 *   <li>the Nether roof - the usual reason to break bedrock is to get above it, so teleports
 *       (pearl/chorus fruit) and block placement at or above y=128 in the Nether are refused.</li>
 * </ul>
 * All denials are silent: they only ever trigger on abnormal client behaviour. Creative players
 * with a bypass permission keep working as before. Piston-based tricks that never reach these
 * events are closed by Paper's {@code allow-permanent-block-break-exploits: false}.
 */
public class BedrockGuardListener implements Listener {

    /** Explicit list for the well-known ones; anything else with negative hardness is caught by {@link #isIndestructible}. */
    private static final Set<Material> INDESTRUCTIBLE = EnumSet.of(
            Material.BEDROCK, Material.BARRIER, Material.LIGHT,
            Material.END_PORTAL_FRAME, Material.END_PORTAL, Material.END_GATEWAY, Material.NETHER_PORTAL,
            Material.COMMAND_BLOCK, Material.CHAIN_COMMAND_BLOCK, Material.REPEATING_COMMAND_BLOCK,
            Material.STRUCTURE_BLOCK, Material.JIGSAW, Material.REINFORCED_DEEPSLATE);

    /** The Nether ceiling bedrock occupies y=123..127, so y=128 and above is outside the playable area. */
    private static final int NETHER_ROOF_Y = 128;

    private final LoveClaims plugin;

    public BedrockGuardListener(LoveClaims plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        if (!enabled("bedrock-guard.block-breaking")) {
            return;
        }
        if (isIndestructible(event.getBlock().getType()) && !mayBreakIndestructible(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!enabled("bedrock-guard.block-breaking")) {
            return;
        }
        if (isIndestructible(event.getBlock().getType()) && !mayBreakIndestructible(event.getPlayer())) {
            event.setCancelled(true);
            event.setDropItems(false);
            event.setExpToDrop(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (enabled("bedrock-guard.explosions")) {
            event.blockList().removeIf(block -> isIndestructible(block.getType()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (enabled("bedrock-guard.explosions")) {
            event.blockList().removeIf(block -> isIndestructible(block.getType()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!enabled("bedrock-guard.protect-nether-roof")) {
            return;
        }
        // Commands and plugins (admin /tp, warps) are trusted; only player-driven jumps matter.
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.COMMAND || cause == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            return;
        }
        Location to = event.getTo();
        if (to != null && isAboveNetherRoof(to) && !hasBypass(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!enabled("bedrock-guard.protect-nether-roof")) {
            return;
        }
        Block block = event.getBlockPlaced();
        if (isAboveNetherRoof(block.getLocation()) && !hasBypass(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    private boolean enabled(String path) {
        return plugin.getConfigManager().getConfig().getBoolean(path, true);
    }

    private static boolean isIndestructible(Material type) {
        if (INDESTRUCTIBLE.contains(type)) {
            return true;
        }
        return type.isBlock() && type.getHardness() < 0;
    }

    private static boolean isAboveNetherRoof(Location location) {
        World world = location.getWorld();
        return world != null && world.getEnvironment() == World.Environment.NETHER
                && location.getBlockY() >= NETHER_ROOF_Y;
    }

    /** Only a creative player holding a bypass permission may remove these blocks. */
    private boolean mayBreakIndestructible(Player player) {
        return player.getGameMode() == GameMode.CREATIVE && hasBypass(player);
    }

    private boolean hasBypass(Player player) {
        return player.hasPermission("loveclaims.bypass")
                || player.hasPermission("loveclaims.admin")
                || player.hasPermission("loveclaims.moderator");
    }
}
