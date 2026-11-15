package me.lovelace.loveclaims.listener;

import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.model.ClaimPermission;
import me.lovelace.loveclaims.util.ClaimBypass;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;

/** Guards the actual changed blocks and stand, independently of the initial click. */
public final class PropertyProtectionListener implements Listener {
  @FunctionalInterface
  interface Access {
    boolean allowed(Player player, Location location, String spawnFlag);
  }

  private final Access access;
  private final java.util.function.BiConsumer<Player, String> deny;

  public PropertyProtectionListener(LoveClaims plugin) {
    this(
        (player, location, flag) -> {
          if (player != null && ClaimBypass.allowed(player)) return true;
          var claim = plugin.getClaimManager().getClaimAt(location);
          if (claim.isPresent()) {
            return player != null
                && !claim.get().isClanTerritory()
                && claim.get().hasPermission(player.getUniqueId(), ClaimPermission.BUILD);
          }
          return !plugin.getConfigManager().isInsideSpawnClaim(location)
              || plugin.getConfigManager().getSpawnFlag(flag);
        },
        (player, flag) ->
            me.lovelace.loveclaims.util.Notify.event(
                player,
                plugin
                    .getConfigManager()
                    .getMessage(flag.equals("break-blocks") ? "deny-break" : "deny-place")));
  }

  PropertyProtectionListener(Access access) {
    this(access, (player, flag) -> {});
  }

  private PropertyProtectionListener(
      Access access, java.util.function.BiConsumer<Player, String> deny) {
    this.access = access;
    this.deny = deny;
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void multiPlace(BlockMultiPlaceEvent event) {
    for (var state : event.getReplacedBlockStates()) {
      if (!access.allowed(event.getPlayer(), state.getLocation(), "place-blocks")) {
        event.setCancelled(true);
        return;
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void empty(PlayerBucketEmptyEvent event) {
    if (!access.allowed(event.getPlayer(), event.getBlock().getLocation(), "place-blocks")) {
      event.setCancelled(true);
      deny.accept(event.getPlayer(), "place-blocks");
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void fill(PlayerBucketFillEvent event) {
    if (!access.allowed(event.getPlayer(), event.getBlock().getLocation(), "break-blocks")) {
      event.setCancelled(true);
      deny.accept(event.getPlayer(), "break-blocks");
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void manipulate(PlayerArmorStandManipulateEvent event) {
    if (!access.allowed(event.getPlayer(), event.getRightClicked().getLocation(), "interact"))
      event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void damage(EntityDamageEvent event) {
    if (!(event.getEntity() instanceof ArmorStand)) return;
    Player actor = null;
    if (event instanceof EntityDamageByEntityEvent attack) {
      if (attack.getDamager() instanceof Player player) actor = player;
      else if (attack.getDamager() instanceof Projectile projectile
          && projectile.getShooter() instanceof Player player) actor = player;
    }
    if (!access.allowed(actor, event.getEntity().getLocation(), "break-blocks"))
      event.setCancelled(true);
  }
}
