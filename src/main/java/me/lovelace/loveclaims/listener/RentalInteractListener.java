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

    @EventHandler
    public void onRentalInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        plugin.getClaimManager().getClaimAt(event.getClickedBlock().getLocation())
                .ifPresent(claim -> {
                    if (claim.isRentalPlot() && !claim.isRented()) {
                        event.setCancelled(true);
                        event.getPlayer().openInventory(new RentalPaymentGUI(plugin, claim).getInventory());
                    }
                });
    }
}
