package me.lovelace.loveclaims.listener;

import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.gui.AbstractGUI;
import me.lovelace.loveclaims.gui.RentalPlayerGUI;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class GuiListener implements Listener {
    private final LoveClaims plugin;

    public GuiListener(LoveClaims plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof AbstractGUI gui) {
            event.setCancelled(true);
            if (event.getClickedInventory() == null) return;
            if (event.getClickedInventory().equals(event.getView().getBottomInventory())) return;

            gui.handleClick(event);
        } else if (event.getInventory().getHolder() instanceof RentalPlayerGUI gui) {
            if (event.getClickedInventory() == null) return;
            if (event.getClickedInventory().equals(event.getView().getBottomInventory())) return;

            gui.handleClick(event);
        }
    }
}
