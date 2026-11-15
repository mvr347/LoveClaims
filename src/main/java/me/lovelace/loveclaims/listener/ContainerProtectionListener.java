package me.lovelace.loveclaims.listener;

import java.util.*;
import java.util.function.*;
import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.model.ClaimPermission;
import me.lovelace.loveclaims.util.ClaimBypass;
import me.lovelace.loveclaims.util.Notify;
import org.bukkit.Location;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;

/** Authorizes the actual physical inventory, independently of click cancellation and held items. */
public final class ContainerProtectionListener implements Listener {
  private final BiPredicate<Player, Location> allowed;
  private final Consumer<Player> notifyDenied;
  private final Consumer<Runnable> nextTick;
  private final Set<UUID> pendingClose = new HashSet<>();
  private long lastFailureWarning;

  public ContainerProtectionListener(LoveClaims plugin) {
    this(
        (player, location) -> {
          if (ClaimBypass.allowed(player)) return true;
          var claim = plugin.getClaimManager().getClaimAt(location);
          if (claim.isPresent()) return canOpenClaim(claim.get(), player.getUniqueId());
          return !plugin.getConfigManager().isInsideSpawnClaim(location)
              || plugin.getConfigManager().getSpawnFlag("interact");
        },
        player -> Notify.event(player, plugin.getConfigManager().getMessage("deny-interact")),
        task -> plugin.getServer().getScheduler().runTask(plugin, task));
  }

  ContainerProtectionListener(
      BiPredicate<Player, Location> allowed,
      Consumer<Player> notifyDenied,
      Consumer<Runnable> nextTick) {
    this.allowed = allowed;
    this.notifyDenied = notifyDenied;
    this.nextTick = nextTick;
  }

  static boolean canOpenClaim(Claim claim, UUID player) {
    // Match the existing clan-territory rule; this guard never creates a siege-looting exception.
    return !claim.isClanTerritory() && claim.hasPermission(player, ClaimPermission.CONTAINERS);
  }

  record Targets(List<Location> locations, boolean incomplete) {}

  static Targets targets(Inventory inventory) {
    var locations = new ArrayList<Location>();
    boolean valid = collect(inventory.getHolder(false), locations);
    return new Targets(List.copyOf(new LinkedHashSet<>(locations)), !valid);
  }

  private static boolean collect(InventoryHolder holder, List<Location> locations) {
    if (holder instanceof DoubleChest chest) {
      var left = chest.getLeftSide(false);
      var right = chest.getRightSide(false);
      if (!(left instanceof BlockInventoryHolder) || !(right instanceof BlockInventoryHolder))
        return false;
      boolean leftValid = collect(left, locations), rightValid = collect(right, locations);
      return leftValid && rightValid;
    }
    if (holder instanceof BlockInventoryHolder physical) {
      var block = physical.getBlock();
      if (block == null) return false;
      var location = block.getLocation();
      if (location == null || location.getWorld() == null) return false;
      locations.add(location);
      return true;
    }
    // Player/ender-chest inventories and virtual menus are not somebody else's block storage.
    return true;
  }

  boolean denied(Player player, Inventory inventory) {
    try {
      var targets = targets(inventory);
      return targets.incomplete()
          || targets.locations().stream().anyMatch(location -> !allowed.test(player, location));
    } catch (RuntimeException failure) {
      long now = System.nanoTime();
      if (lastFailureWarning == 0 || now - lastFailureWarning > 10_000_000_000L) {
        lastFailureWarning = now;
        java.util.logging.Logger.getLogger("LoveClaims")
            .warning(
                "Доступ к контейнеру отклонён: проверка прав недоступна ("
                    + failure.getClass().getSimpleName()
                    + ")");
      }
      return true;
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void open(InventoryOpenEvent event) {
    if (event.getPlayer() instanceof Player player && denied(player, event.getInventory())) {
      event.setCancelled(true);
      notifyDenied.accept(player);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void click(InventoryClickEvent event) {
    if (event.getWhoClicked() instanceof Player player && denied(player, event.getInventory())) {
      event.setCancelled(true);
      closeNextTick(player, event.getInventory());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void drag(InventoryDragEvent event) {
    if (event.getWhoClicked() instanceof Player player && denied(player, event.getInventory())) {
      event.setCancelled(true);
      closeNextTick(player, event.getInventory());
    }
  }

  private void closeNextTick(Player player, Inventory inventory) {
    UUID id = player.getUniqueId();
    if (!pendingClose.add(id)) return;
    try {
      nextTick.accept(
          () -> {
            try {
              if (player.isOnline()
                  && player.getOpenInventory().getTopInventory() == inventory
                  && denied(player, inventory)) player.closeInventory();
            } finally {
              pendingClose.remove(id);
            }
          });
    } catch (RuntimeException error) {
      pendingClose.remove(id);
      throw error;
    }
  }
}
