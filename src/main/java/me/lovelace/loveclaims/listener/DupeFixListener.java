package me.lovelace.loveclaims.listener;

import me.lovelace.loveclaims.LoveClaims;
import org.bukkit.entity.ChestBoat;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;

/**
 * Fixes for well-known portal duplication exploits. Each fix has its own switch under
 * {@code dupe-protection} in config.yml and is on by default.
 *
 * <ul>
 *   <li>Entities that hold an inventory (chest boats, chest/hopper minecarts, donkeys, mules and
 *       llamas with a chest) must not change dimension through a portal: the entity and its
 *       contents get saved in two chunks/worlds and can be kept in both.</li>
 *   <li>Falling blocks and primed TNT must not go through portals (the classic sand/TNT
 *       duplication).</li>
 *   <li>A player must not enter a portal while riding one of the inventory holders above.</li>
 * </ul>
 * Piston, rail/carpet and gravity-block duplication are server-level Paper settings
 * ({@code unsupported-settings.allow-*}) that have to stay at their default {@code false}.
 */
public class DupeFixListener implements Listener {
    private final LoveClaims plugin;

    public DupeFixListener(LoveClaims plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        Entity entity = event.getEntity();
        if (enabled("chested-entities-portal") && (holdsInventory(entity) || hasInventoryPassenger(entity))) {
            event.setCancelled(true);
        } else if (enabled("falling-blocks-portal") && (entity instanceof FallingBlock || entity instanceof TNTPrimed)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (!enabled("chested-entities-portal")) return;
        Player player = event.getPlayer();
        Entity vehicle = player.getVehicle();
        if (vehicle != null && holdsInventory(vehicle)) {
            event.setCancelled(true);
            player.sendActionBar(plugin.getConfigManager().getMessage("deny-portal-chested"));
        }
    }

    private boolean enabled(String key) {
        var config = plugin.getConfigManager().getConfig();
        return config.getBoolean("dupe-protection.enabled", true)
                && config.getBoolean("dupe-protection." + key, true);
    }

    private static boolean holdsInventory(Entity entity) {
        return entity instanceof ChestBoat
                || entity instanceof StorageMinecart
                || entity instanceof HopperMinecart
                || (entity instanceof ChestedHorse horse && horse.isCarryingChest());
    }

    private static boolean hasInventoryPassenger(Entity entity) {
        for (Entity passenger : entity.getPassengers()) {
            if (holdsInventory(passenger) || hasInventoryPassenger(passenger)) {
                return true;
            }
        }
        return false;
    }
}
