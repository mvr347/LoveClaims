package me.lovelace.loveclaims.listener;

import java.util.UUID;
import java.util.function.Function;
import me.lovelace.loveclaims.LoveClaims;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

/** Hoppers may operate inside one claim, but may not import/export across its boundary. */
public final class AutomationProtectionListener implements Listener {
  private static final UUID SPAWN =
      UUID.nameUUIDFromBytes(
          "LoveClaims:spawn-boundary".getBytes(java.nio.charset.StandardCharsets.UTF_8));
  private final Function<Location, UUID> region;

  public AutomationProtectionListener(LoveClaims plugin) {
    this(
        location -> {
          if (plugin.getConfigManager().isInsideSpawnClaim(location)) return SPAWN;
          return plugin
              .getClaimManager()
              .getClaimAt(location)
              .map(claim -> claim.getId())
              .orElse(null);
        });
  }

  AutomationProtectionListener(Function<Location, UUID> region) {
    this.region = region;
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void move(InventoryMoveItemEvent event) {
    var source = ContainerProtectionListener.targets(event.getSource());
    var destination = ContainerProtectionListener.targets(event.getDestination());
    if (source.incomplete() || destination.incomplete()) {
      event.setCancelled(true);
      return;
    }
    for (Location from : source.locations()) {
      UUID origin = region.apply(from);
      // A cart rolled in from outside must not extract the contents of a protected chest.
      if (origin != null && event.getDestination().getHolder(false) instanceof Entity) {
        event.setCancelled(true);
        return;
      }
      for (Location to : destination.locations()) {
        if (!java.util.Objects.equals(origin, region.apply(to))) {
          event.setCancelled(true);
          return;
        }
      }
    }
  }
}
