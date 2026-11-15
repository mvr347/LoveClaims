package me.lovelace.loveclaims.listener;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.BiFunction;
import me.lovelace.loveclaims.model.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.Test;

class PropertyBoundaryTest {
  private final UUID owner = UUID.randomUUID(),
      visitor = UUID.randomUUID(),
      region = UUID.randomUUID();
  private final World world = proxy(World.class, (n, a) -> n.equals("getUID") ? region : "world");
  private final Player player =
      proxy(
          Player.class,
          (n, a) -> {
            if (n.equals("getUniqueId")) return visitor;
            throw new AssertionError(n);
          });

  // No server-backed item methods are used by these event handlers.
  private static class TestItem extends ItemStack {
    TestItem() {
      super();
    }
  }

  private final ItemStack item = new TestItem();

  private Location at(int x) {
    return new Location(world, x, 64, 0);
  }

  private Block block(int x) {
    return proxy(
        Block.class,
        (n, a) ->
            switch (n) {
              case "getLocation" -> at(x);
              case "getRelative" -> block(x + 1);
              case "getType" -> Material.STONE;
              default -> throw new AssertionError(n);
            });
  }

  private BlockState state(int x) {
    return proxy(
        BlockState.class,
        (n, a) ->
            switch (n) {
              case "getLocation" -> at(x);
              case "getBlock" -> block(x);
              default -> throw new AssertionError(n);
            });
  }

  private Inventory inventory(InventoryHolder holder) {
    return proxy(
        Inventory.class,
        (n, a) -> {
          if (n.equals("getHolder")) return holder;
          throw new AssertionError(n);
        });
  }

  private Inventory chest(int x) {
    return inventory(
        proxy(
            BlockInventoryHolder.class,
            (n, a) -> {
              if (n.equals("getBlock")) return block(x);
              throw new AssertionError(n);
            }));
  }

  private Claim claim() {
    return new Claim(region, world, null, owner, null);
  }

  private PropertyProtectionListener protection(Claim claim) {
    return new PropertyProtectionListener(
        (p, loc, flag) ->
            loc.getX() < 10
                || p != null && claim.hasPermission(p.getUniqueId(), ClaimPermission.BUILD));
  }

  private AutomationProtectionListener automation() {
    return new AutomationProtectionListener(loc -> loc.getX() < 10 ? null : region);
  }

  @Test
  void hopperCannotExtractOrInjectAcrossBoundary() {
    for (boolean reversed : new boolean[] {false, true}) {
      Inventory outside = chest(9), inside = chest(10);
      var event =
          new InventoryMoveItemEvent(
              reversed ? inside : outside, item, reversed ? outside : inside, false);
      automation().move(event);
      assertTrue(event.isCancelled());
      assertSame(item, event.getItem());
    }
  }

  @Test
  void internalHoppersAndWildernessRemainFunctional() {
    for (int x : new int[] {0, 10}) {
      var event = new InventoryMoveItemEvent(chest(x), item, chest(x + 1), true);
      automation().move(event);
      assertFalse(event.isCancelled());
    }
  }

  @Test
  void differentClaimsAreNotJoinedByHoppers() {
    UUID other = UUID.randomUUID();
    var listener = new AutomationProtectionListener(loc -> loc.getX() == 10 ? region : other);
    var event = new InventoryMoveItemEvent(chest(10), item, chest(11), true);
    listener.move(event);
    assertTrue(event.isCancelled());
  }

  @Test
  void hopperCartRolledInsideCannotStealFromProtectedChest() {
    var cart =
        proxy(
            HopperMinecart.class,
            (n, a) -> {
              if (n.equals("getLocation")) return at(10);
              throw new AssertionError(n);
            });
    var event = new InventoryMoveItemEvent(chest(10), item, inventory(cart), false);
    automation().move(event);
    assertTrue(event.isCancelled());
    assertEquals(List.of(at(10)), ContainerProtectionListener.targets(inventory(cart)).locations());
    var containers =
        new ContainerProtectionListener((p, loc) -> loc.getX() < 10, p -> {}, Runnable::run);
    assertTrue(containers.denied(player, inventory(cart)));
  }

  @Test
  void doubleChestMustCheckBothHalvesDuringAutomaticExtraction() {
    Inventory inside = chest(10), outside = chest(9);
    var pair =
        proxy(
            DoubleChestInventory.class,
            (n, a) ->
                switch (n) {
                  case "getLeftSide" -> outside;
                  case "getRightSide" -> inside;
                  default -> throw new AssertionError(n);
                });
    var event = new InventoryMoveItemEvent(inventory(new DoubleChest(pair)), item, chest(8), false);
    automation().move(event);
    assertTrue(event.isCancelled());
  }

  @Test
  void bucketChecksActualWaterloggedOrRayTracedTargetNotClickedNeighbour() {
    var guard = protection(claim());
    var empty =
        new PlayerBucketEmptyEvent(
            player,
            block(10),
            block(9),
            BlockFace.EAST,
            Material.WATER_BUCKET,
            item,
            EquipmentSlot.HAND);
    guard.empty(empty);
    assertTrue(empty.isCancelled());
    var fill =
        new PlayerBucketFillEvent(
            player, block(10), block(9), BlockFace.EAST, Material.BUCKET, item, EquipmentSlot.HAND);
    guard.fill(fill);
    assertTrue(fill.isCancelled());
    // Waterlogging affects the clicked block, not the next block along the face.
    var outside =
        new PlayerBucketEmptyEvent(
            player,
            block(9),
            block(9),
            BlockFace.EAST,
            Material.WATER_BUCKET,
            item,
            EquipmentSlot.HAND);
    guard.empty(outside);
    assertFalse(outside.isCancelled());
  }

  @Test
  void bedAcrossBoundaryIsRejectedButBuilderCanPlaceIt() {
    Claim claim = claim();
    var event =
        new BlockMultiPlaceEvent(List.of(state(9), state(10)), block(9), item, player, true);
    protection(claim).multiPlace(event);
    assertTrue(event.isCancelled());
    claim.setTrust(visitor, TrustLevel.BUILD);
    event = new BlockMultiPlaceEvent(List.of(state(9), state(10)), block(9), item, player, true);
    protection(claim).multiPlace(event);
    assertFalse(event.isCancelled());
  }

  @Test
  void standEquipmentCannotBeTakenWithOnlyAccessOrContainerPermission() {
    Claim claim = claim();
    ArmorStand stand = proxy(ArmorStand.class, (n, a) -> at(10));
    for (TrustLevel trust : new TrustLevel[] {TrustLevel.NONE, TrustLevel.ACCESS}) {
      claim.setTrust(visitor, trust);
      claim.setPermission(visitor, ClaimPermission.CONTAINERS, true);
      var event =
          new PlayerArmorStandManipulateEvent(
              player, stand, item, item, EquipmentSlot.HEAD, EquipmentSlot.HAND);
      protection(claim).manipulate(event);
      assertTrue(event.isCancelled());
    }
    claim.setTrust(visitor, TrustLevel.BUILD);
    var allowed =
        new PlayerArmorStandManipulateEvent(
            player, stand, item, item, EquipmentSlot.HEAD, EquipmentSlot.HAND);
    protection(claim).manipulate(allowed);
    assertFalse(allowed.isCancelled());
  }

  @Test
  void standDamageFromVisitorAndExplosionIsBlockedWithoutAffectingPvp() {
    Claim claim = claim();
    ArmorStand stand = proxy(ArmorStand.class, (n, a) -> at(10));
    var source = proxy(org.bukkit.damage.DamageSource.class, (n, a) -> null);
    var hit =
        new EntityDamageByEntityEvent(
            player, stand, EntityDamageEvent.DamageCause.ENTITY_ATTACK, source, 2);
    protection(claim).damage(hit);
    assertTrue(hit.isCancelled());
    var blast =
        new EntityDamageEvent(stand, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION, source, 2);
    protection(claim).damage(blast);
    assertTrue(blast.isCancelled());
    var pvp =
        new EntityDamageByEntityEvent(
            player, player, EntityDamageEvent.DamageCause.ENTITY_ATTACK, source, 2);
    protection(claim).damage(pvp);
    assertFalse(pvp.isCancelled());
  }

  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> call) {
    return (T)
        Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (self, m, args) ->
                switch (m.getName()) {
                  case "equals" -> self == args[0];
                  case "hashCode" -> System.identityHashCode(self);
                  case "toString" -> type.getSimpleName();
                  default -> call.apply(m.getName(), args);
                });
  }
}
