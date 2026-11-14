package me.lovelace.loveclaims.listener;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.BiFunction;
import me.lovelace.loveclaims.model.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.Test;

class ContainerProtectionListenerTest {
  private final UUID visitor = UUID.randomUUID(),
      owner = UUID.randomUUID(),
      worldId = UUID.randomUUID();
  private final World world =
      proxy(
          World.class,
          (name, args) ->
              switch (name) {
                case "getUID" -> worldId;
                case "getName" -> "world";
                default -> throw new AssertionError(name);
              });
  private final InventoryView[] current = new InventoryView[1];
  private final AtomicInteger closed = new AtomicInteger();
  private final Player player =
      proxy(
          Player.class,
          (name, args) ->
              switch (name) {
                case "getUniqueId" -> visitor;
                case "getName" -> "Visitor";
                case "isOnline" -> true;
                case "getOpenInventory" -> current[0];
                case "closeInventory" -> {
                  closed.incrementAndGet();
                  yield null;
                }
                default -> throw new AssertionError(name);
              });

  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> call) {
    return (T)
        Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (self, method, args) ->
                switch (method.getName()) {
                  case "equals" -> self == args[0];
                  case "hashCode" -> System.identityHashCode(self);
                  case "toString" -> type.getSimpleName();
                  default -> call.apply(method.getName(), args);
                });
  }

  private Location where(int x) {
    return new Location(world, x, 64, 0);
  }

  private Inventory inventory(InventoryHolder holder) {
    return proxy(
        Inventory.class,
        (name, args) ->
            switch (name) {
              case "getHolder" -> holder;
              case "getType" -> InventoryType.CHEST;
              case "getSize" -> 27;
              default -> throw new AssertionError(name);
            });
  }

  private Inventory chest(int x) {
    Block block =
        proxy(
            Block.class,
            (name, args) ->
                switch (name) {
                  case "getLocation" -> where(x);
                  case "getType" -> Material.CHEST;
                  default -> throw new AssertionError(name);
                });
    return inventory(
        proxy(
            BlockInventoryHolder.class,
            (name, args) ->
                switch (name) {
                  case "getBlock" -> block;
                  default -> throw new AssertionError(name);
                }));
  }

  private InventoryView view(Inventory top) {
    InventoryView view =
        proxy(
            InventoryView.class,
            (name, args) ->
                switch (name) {
                  case "getTopInventory", "getInventory" -> top;
                  case "getPlayer" -> player;
                  case "getType" -> InventoryType.CHEST;
                  case "convertSlot" -> args[0];
                  default -> throw new AssertionError(name);
                });
    current[0] = view;
    return view;
  }

  private Claim claim() {
    return new Claim(UUID.randomUUID(), world, null, owner, null);
  }

  @Test
  void strangerCannotOpenARealContainerEvenWithoutAnInteractEvent() {
    Claim claim = claim();
    var listener =
        new ContainerProtectionListener(
            (p, at) -> ContainerProtectionListener.canOpenClaim(claim, p.getUniqueId()),
            p -> {},
            Runnable::run);
    var event = new InventoryOpenEvent(view(chest(1)));
    listener.open(event);
    assertTrue(event.isCancelled());
    assertFalse(claim.getMembers().containsKey(visitor));
    claim.setTrust(visitor, TrustLevel.ACCESS);
    event = new InventoryOpenEvent(current[0]);
    listener.open(event);
    assertTrue(event.isCancelled());
    claim.setPermission(visitor, ClaimPermission.CONTAINERS, true);
    event = new InventoryOpenEvent(current[0]);
    listener.open(event);
    assertFalse(event.isCancelled());
  }

  @Test
  void bothHalvesOfDoubleChestNeedAccessEvenIfClickedHalfIsOutside() {
    Inventory outside = chest(0), inside = chest(1);
    DoubleChestInventory doubleInventory =
        proxy(
            DoubleChestInventory.class,
            (name, args) ->
                switch (name) {
                  case "getLeftSide" -> outside;
                  case "getRightSide" -> inside;
                  default -> throw new AssertionError(name);
                });
    var top = inventory(new DoubleChest(doubleInventory));
    var seen = new ArrayList<Integer>();
    var listener =
        new ContainerProtectionListener(
            (p, at) -> {
              seen.add(at.getBlockX());
              return at.getBlockX() == 0;
            },
            p -> {},
            Runnable::run);
    var event = new InventoryOpenEvent(view(top));
    listener.open(event);
    assertTrue(event.isCancelled());
    assertEquals(List.of(0, 1), seen);
    assertEquals(2, ContainerProtectionListener.targets(top).locations().size());
  }

  @Test
  void permissionRevocationBlocksShiftClickAndDragAndClosesOnceOnNextTick() {
    AtomicBoolean allowed = new AtomicBoolean(true);
    var scheduled = new ArrayList<Runnable>();
    var listener =
        new ContainerProtectionListener((p, at) -> allowed.get(), p -> {}, scheduled::add);
    var top = chest(1);
    view(top);
    allowed.set(false);
    var click =
        new InventoryClickEvent(
            current[0],
            InventoryType.SlotType.CONTAINER,
            0,
            ClickType.SHIFT_LEFT,
            InventoryAction.MOVE_TO_OTHER_INVENTORY);
    listener.click(click);
    assertTrue(click.isCancelled());
    assertEquals(0, closed.get());
    var drag = new InventoryDragEvent(current[0], null, null, false, Map.of());
    listener.drag(drag);
    assertTrue(drag.isCancelled());
    assertEquals(1, scheduled.size());
    scheduled.removeFirst().run();
    assertEquals(1, closed.get());
  }

  @Test
  void delayedCloseDoesNotCloseANewMenuOrAnAccessRestoredContainer() {
    AtomicBoolean allowed = new AtomicBoolean(false);
    var scheduled = new ArrayList<Runnable>();
    var listener =
        new ContainerProtectionListener((p, at) -> allowed.get(), p -> {}, scheduled::add);
    view(chest(1));
    var first =
        new InventoryClickEvent(
            current[0],
            InventoryType.SlotType.CONTAINER,
            0,
            ClickType.LEFT,
            InventoryAction.PICKUP_ALL);
    listener.click(first);
    view(inventory(null));
    scheduled.removeFirst().run();
    assertEquals(0, closed.get());
    view(chest(1));
    var second =
        new InventoryClickEvent(
            current[0],
            InventoryType.SlotType.CONTAINER,
            0,
            ClickType.LEFT,
            InventoryAction.PICKUP_ALL);
    listener.click(second);
    allowed.set(true);
    scheduled.removeFirst().run();
    assertEquals(0, closed.get());
  }

  @Test
  void privateMenusAndPersonalInventoriesAreNotPhysicalStorage() {
    var listener = new ContainerProtectionListener((p, at) -> false, p -> {}, Runnable::run);
    for (var holder : Arrays.asList(null, player)) {
      var event = new InventoryOpenEvent(view(inventory(holder)));
      listener.open(event);
      assertFalse(event.isCancelled());
    }
  }

  @Test
  void ownerAndExplicitPublicRightsRemainValidButClanRuleIsNotWeakened() {
    Claim claim = claim();
    assertTrue(ContainerProtectionListener.canOpenClaim(claim, owner));
    assertFalse(ContainerProtectionListener.canOpenClaim(claim, visitor));
    claim.setDefaultPermission(ClaimPermission.CONTAINERS, true);
    assertTrue(ContainerProtectionListener.canOpenClaim(claim, visitor));
    claim.setClanTerritory(true);
    assertFalse(ContainerProtectionListener.canOpenClaim(claim, visitor));
  }

  @Test
  void protectedBlockAndHeldWeaponRemainIndependent() {
    for (Material held : List.of(Material.BOW, Material.TRIDENT, Material.POTION)) {
      var event =
          new PlayerInteractEvent(
              player, Action.RIGHT_CLICK_BLOCK, null, null, BlockFace.UP, EquipmentSlot.HAND) {
            @Override
            public Material getMaterial() {
              return held;
            }
          };
      event.setUseItemInHand(Event.Result.ALLOW);
      ProtectionListener.denyClick(event);
      assertEquals(Event.Result.DENY, event.useInteractedBlock());
      assertEquals(Event.Result.ALLOW, event.useItemInHand());
      event.setUseItemInHand(Event.Result.DENY);
      ProtectionListener.denyClick(event);
      assertEquals(Event.Result.DENY, event.useItemInHand());
    }
  }

  @Test
  void lookupFailureCannotTurnIntoPermissionToOpen() {
    var listener =
        new ContainerProtectionListener(
            (p, at) -> {
              throw new IllegalStateException("test");
            },
            p -> {},
            Runnable::run);
    var event = new InventoryOpenEvent(view(chest(1)));
    listener.open(event);
    assertTrue(event.isCancelled());
  }

  @Test
  void otherPluginCancellationIsNeverCleared() {
    var listener = new ContainerProtectionListener((p, at) -> true, p -> {}, Runnable::run);
    var event = new InventoryOpenEvent(view(chest(1)));
    event.setCancelled(true);
    listener.open(event);
    assertTrue(event.isCancelled());
  }
}
