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
 * Players, plain mobs and empty vehicles are never touched. Each group is switched by its own
 * {@code dupe-guard.*} key, all on by default.
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

    private boolean isCarrierOfDupe(Entity entity) {
        var config = plugin.getConfigManager().getConfig();
        if (entity instanceof FallingBlock) {
            return config.getBoolean("dupe-guard.block-falling-block-portals", true);
        }
        if (entity instanceof TNTPrimed) {
            return config.getBoolean("dupe-guard.block-tnt-portals", true);
        }
        if (entity instanceof StorageMinecart || entity instanceof HopperMinecart
                || entity instanceof ChestBoat || entity instanceof ChestedHorse) {
            return config.getBoolean("dupe-guard.block-container-entity-portals", true);
        }
        return false;
    }
}
