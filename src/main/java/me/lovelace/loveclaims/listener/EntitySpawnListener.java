package me.lovelace.loveclaims.listener;

import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.model.ClaimPermission;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Silently stops players from spawning entities (boats, minecarts, armor stands, end crystals,
 * item frames, paintings, spawn eggs) where they may not build: inside the spawn claim (unless the
 * {@code spawn-claim.flags.entities} flag is on) and inside someone else's claim (no BUILD
 * permission). No message is sent on purpose - the request was for a quiet denial.
 * <p>
 * Every spawn path is covered by two layers: the dedicated placement events (the authoritative
 * ones) and a {@link PlayerInteractEvent} check on the held item, which also catches placement on
 * water/air (no clicked block) and spawn eggs, neither of which fires a placement event.
 */
public class EntitySpawnListener implements Listener {

    private final LoveClaims plugin;

    public EntitySpawnListener(LoveClaims plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        Player player = event.getPlayer();
        if (player != null && isDenied(player, event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player != null && isDenied(player, event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    // LOWEST: must run before ProtectionListener#onInteract, which would otherwise cancel the same
    // click in someone else's claim and show its "deny-interact" message.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onUseSpawningItem(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || !spawnsEntity(item.getType())) {
            return;
        }
        Location where = event.getClickedBlock() != null
                ? event.getClickedBlock().getLocation()
                : event.getPlayer().getLocation();
        if (isDenied(event.getPlayer(), where)) {
            event.setUseItemInHand(Event.Result.DENY);
            event.setCancelled(true);
        }
    }

    /** A spawn egg used on a mob spawns a baby / new mob without any placement event. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawnEggOnEntity(PlayerInteractEntityEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
        if (item == null || !isSpawnEgg(item.getType())) {
            return;
        }
        if (isDenied(event.getPlayer(), event.getRightClicked().getLocation())) {
            event.setCancelled(true);
        }
    }

    private boolean isDenied(Player player, Location location) {
        if (hasBypass(player)) {
            return false;
        }
        Optional<Claim> claim = plugin.getClaimManager().getClaimAt(location);
        if (claim.isPresent()) {
            return !claim.get().hasPermission(player.getUniqueId(), ClaimPermission.BUILD);
        }
        return plugin.getConfigManager().isInsideSpawnClaim(location)
                && !plugin.getConfigManager().getSpawnFlag("entities");
    }

    private boolean hasBypass(Player player) {
        return player.hasPermission("loveclaims.bypass")
                || player.hasPermission("loveclaims.admin")
                || player.hasPermission("loveclaims.moderator");
    }

    private static boolean spawnsEntity(Material type) {
        if (isSpawnEgg(type)) {
            return true;
        }
        String name = type.name();
        return name.endsWith("_BOAT") || name.endsWith("_RAFT") || name.endsWith("MINECART")
                || name.endsWith("_CHEST_BOAT") || name.endsWith("_CHEST_RAFT")
                || type == Material.ARMOR_STAND || type == Material.END_CRYSTAL
                || type == Material.ITEM_FRAME || type == Material.GLOW_ITEM_FRAME
                || type == Material.PAINTING;
    }

    private static boolean isSpawnEgg(Material type) {
        return type.name().endsWith("_SPAWN_EGG");
    }
}
