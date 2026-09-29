package me.lovelace.loveclaims.listener;

import me.lovelace.loveclaims.LoveClaims;
import org.bukkit.entity.ChestBoat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.Material;

/**
 * Closes the entity-through-portal item duplication family. The exploit works because the entity
 * (or the block it carries) is saved in one dimension while it is already transferred to the
 * other, so it exists twice after a chunk/save race:
 * <ul>
 *   <li>falling blocks (sand, gravel, anvils, dragon egg...) through a nether/end portal;</li>
 *   <li>primed TNT through a portal;</li>
 *   <li>entities that carry an inventory - chest/hopper minecarts, chest boats, donkeys, mules and
 *       llamas.</li>
 * </ul>
 * The same carriers are also stopped at End gateways, and a player who logs out while riding an
 * inventory-carrying vehicle is dropped from it first. Players, plain mobs and empty vehicles are
 * never touched. Each group is switched by its own {@code dupe-guard.*} key, all on by default.
 */
public class DupeGuardListener implements Listener {

    private final LoveClaims plugin;

    public DupeGuardListener(LoveClaims plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        if (isCarrierOfDupe(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /**
     * End gateways move entities with a plain teleport, not a portal event, so the same carriers
     * slip through there. The cheap instanceof test runs first because this event fires for every
     * enderman/fox/shulker teleport on the server.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityTeleport(EntityTeleportEvent event) {
        if (event instanceof EntityPortalEvent || !isCarrierOfDupe(event.getEntity())) {
            return;
        }
        if (event.getFrom().getBlock().getType() == Material.END_GATEWAY) {
            event.setCancelled(true);
        }
    }

    /**
     * Logging out while riding an inventory-carrying vehicle keeps the vehicle and its contents tied
     * to a player that is being saved and unloaded at the same moment - the donkey/chest-boat logout
     * duplication. Dropping the rider first makes the vehicle an ordinary standalone entity.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        Entity vehicle = event.getPlayer().getVehicle();
        if (vehicle != null && isContainerEntity(vehicle)
                && plugin.getConfigManager().getConfig().getBoolean("dupe-guard.eject-on-quit", true)) {
            event.getPlayer().leaveVehicle();
        }
    }

    private boolean isCarrierOfDupe(Entity entity) {
        var config = plugin.getConfigManager().getConfig();
        if (entity instanceof FallingBlock) {
            return config.getBoolean("dupe-guard.block-falling-block-portals", true);
        }
        if (entity instanceof TNTPrimed) {
            return config.getBoolean("dupe-guard.block-tnt-portals", true);
        }
        if (isContainerEntity(entity)) {
            return config.getBoolean("dupe-guard.block-container-entity-portals", true);
        }
        return false;
    }

    private static boolean isContainerEntity(Entity entity) {
        return entity instanceof StorageMinecart || entity instanceof HopperMinecart
                || entity instanceof ChestBoat || entity instanceof ChestedHorse;
    }
}
