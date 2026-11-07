package me.lovelace.loveclaims.listener;

import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.gui.RentalPaymentGUI;
import me.lovelace.loveclaims.model.Claim;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;

public class RentalInteractListener implements Listener {
    private final LoveClaims plugin;

    public RentalInteractListener(LoveClaims plugin) {
        this.plugin = plugin;
    }

    /**
     * Whether a right click inside a free trade point is refused with the "Feudal only" text. Only the sign is: an
     * administrator who rebuilds the point (places a block after breaking one by accident) must not be blocked, and a
     * click on any other block is left to the regular claim protection (2026-11-07).
     */
    static boolean refusesTradePointClick(boolean administrator, boolean clickedSign) {
        return clickedSign && !administrator;
    }

    @EventHandler
    public void onRentalInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        plugin.getClaimManager().getClaimAt(event.getClickedBlock().getLocation())
                .ifPresent(claim -> {
                    if (claim.isRentalPlot() && !claim.isRented()) {
                        // Trade points are rented only through the Feudal: a click on the sign never opens payment.
                        if (claim.isTradePoint()) {
                            var player = event.getPlayer();
                            boolean admin = player.hasPermission("loveclaims.bypass") || player.hasPermission("loveclaims.admin")
                                    || player.hasPermission("loveclaims.rental.admin");
                            boolean sign = org.bukkit.Tag.SIGNS.isTagged(event.getClickedBlock().getType());
                            if (refusesTradePointClick(admin, sign)) {
                                event.setCancelled(true);
                                player.sendMessage(plugin.getConfigManager().getMessage("rental-trade-point-feudal-only"));
                            }
                            return;
                        }
                        event.setCancelled(true);
                        event.getPlayer().openInventory(new RentalPaymentGUI(plugin, claim).getInventory());
                        // Short outline so the buyer sees exactly which plot is on offer.
                        me.lovelace.loveclaims.task.ParticleBorder.show(plugin, event.getPlayer(), claim.getBoundingBox(),
                                Math.max(1L, plugin.getConfigManager().getConfig().getLong("proximity-border.rental-preview-seconds", 3L)) * 20L,
                                me.lovelace.loveclaims.task.ParticleBorder.DUST_GOLD);
                    }
                });
    }
}
