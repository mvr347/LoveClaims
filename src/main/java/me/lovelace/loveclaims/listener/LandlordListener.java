package me.lovelace.loveclaims.listener;

import dev.lovelace.lovecore.api.npc.NpcClick;
import dev.lovelace.lovecore.api.npc.NpcClickEvent;
import dev.lovelace.lovecore.api.npc.NpcHandle;
import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.integration.NpcTags;
import me.lovelace.loveclaims.gui.TaxerGUI;
import me.lovelace.loveclaims.model.Claim;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class LandlordListener implements Listener {
    private final LoveClaims plugin;

    public LandlordListener(LoveClaims plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSignClick(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;

        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block b = e.getClickedBlock();
            if (b != null && b.getType().name().endsWith("SIGN")) {
                checkLandlordClick(e.getPlayer(), b.getLocation());
            }
        }
    }

    /** Clicks on NPCs of every provider arrive here; only the configured provider's NPCs are ours. */
    @EventHandler(ignoreCancelled = true)
    public void onNpcClick(NpcClickEvent e) {
        if (e.click() != NpcClick.RIGHT) return;
        NpcHandle npc = e.npc();
        if (!plugin.getRentalManager().getNpcBridge().isConfigured(npc.providerId())) return;

        String taxerName = plugin.getConfigManager().getString("taxer-npc-name", "Сборщик налогов");
        if (NpcTags.isTaxer(npc, taxerName)) {
            e.setCancelled(true);
            plugin.getConfigManager().playSound(e.getPlayer(), "gui-click");
            e.getPlayer().openInventory(new TaxerGUI(plugin, e.getPlayer()).getInventory());
            return;
        }

        Location loc = npc.location();
        if (loc != null && loc.getWorld() != null) checkLandlordClick(e.getPlayer(), loc);
    }

    private void checkLandlordClick(org.bukkit.entity.Player player, Location loc) {
        for (Claim c : plugin.getClaimManager().getAllClaims()) {
            if (c.isRentalPlot() && !c.isRented() && c.getHologramId() != null && c.getHologramId().startsWith("landlord:")) {
                String[] parts = c.getHologramId().replace("landlord:", "").split(";");
                if (parts.length >= 4) {
                    double bx = Double.parseDouble(parts[1]);
                    double by = Double.parseDouble(parts[2]);
                    double bz = Double.parseDouble(parts[3]);

                    if (loc.getWorld().getName().equals(parts[0]) && loc.distanceSquared(new Location(loc.getWorld(), bx, by, bz)) < 4.0) {

                        // Проверяем лимиты перед открытием (чтобы игрок не купил 2 участка)
                        long ownedCount = plugin.getClaimManager().getAllClaims().stream()
                                .filter(Claim::isRentalPlot)
                                .filter(plot -> plot.getOwnerUuid() != null && plot.getOwnerUuid().equals(player.getUniqueId()))
                                .count();

                        if (ownedCount >= 1 && !player.hasPermission("loveclaims.rental.bypasslimit")) {
                            player.sendMessage(plugin.getConfigManager().getMessage("rental-limit-reached"));
                            return;
                        }

                        // Открываем GUI напрямую (игнорируя проверку позиции)
                        player.openInventory(new me.lovelace.loveclaims.gui.RentalPaymentGUI(plugin, c).getInventory());
                        return;
                    }
                }
            }
        }
    }
}
