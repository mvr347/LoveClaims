package me.lovelace.loveclaims.gui;

import me.lovelace.loveclaims.LoveClaims;
import static me.lovelace.loveclaims.textures.HeadTextures.*;
import me.lovelace.loveclaims.model.Claim;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

public class RentalPlayerGUI extends AbstractGUI {
    private final LoveClaims plugin;
    private final Player viewer;
    private final Claim plot;
    private boolean showRefuse;
    // Work-row slots computed at render time and reused by handleClick (-1 = button not shown).
    private int membersSlot = -1;
    private int refuseSlot = -1;

    public RentalPlayerGUI(LoveClaims plugin, Player viewer, Claim plot) {
        super(27, plugin.getConfigManager().getComponent("gui.rental-player.title", "name", plot.getName()));
        this.plugin = plugin;
        this.viewer = viewer;
        this.plot = plot;
        setMenuItems();
    }

    @Override
    protected void setMenuItems() {
        String ownerName = "Сервер";
        if (plot.getOwnerUuid() != null) {
            String fetchedName = Bukkit.getOfflinePlayer(plot.getOwnerUuid()).getName();
            if (fetchedName != null) ownerName = fetchedName;
        }

        long timeLeft = (plot.getRentalEndTime() - System.currentTimeMillis()) / 1000;
        long days = Math.max(0, timeLeft / 86400);
        long hours = Math.max(0, (timeLeft % 86400) / 3600);

        // gui-gen-5 RULE 3: слот 0 — тематическая иконка (инфо арендного плота, ЛКМ=телепорт, ПКМ=границы).
        inventory.setItem(0, createHead(HEAD_INFO,
                plugin.getConfigManager().getComponent("gui.rental-player.info-name"),
                plugin.getConfigManager().getHelpMessage("gui.rental-player.info-lore", "owner", ownerName, "days", String.valueOf(days), "hours", String.valueOf(hours))));

        showRefuse = plot.getOwnerUuid() != null && plot.getOwnerUuid().equals(viewer.getUniqueId());

        // Рабочая зона (9-17): "Участники" + (владельцу) "Отказаться", центрированы динамически.
        int[] rowSlots = centeredRowSlots(showRefuse ? 2 : 1);
        membersSlot = rowSlots[0];
        refuseSlot = showRefuse ? rowSlots[1] : -1;
        inventory.setItem(membersSlot, createHead(HEAD_MEMBERS,
                plugin.getConfigManager().getComponent("gui.rental-player.members-title"),
                plugin.getConfigManager().getHelpMessage("gui.rental-player.members-lore-1", "count", String.valueOf(plot.getMembers().size()), "max", "10")));

        if (showRefuse) {
            inventory.setItem(refuseSlot, createHead(HEAD_BARRIER,
                    plugin.getConfigManager().getComponent("gui.rental-player.refuse-name"),
                    plugin.getConfigManager().getHelpMessage("gui.rental-player.refuse-lore-1")));
        }

        // Standalone-меню (открывается напрямую командой /rental) — Back неактивен, только Close.
        setFooterButtons(null, null, createHead(HEAD_BARRIER, plugin.getConfigManager().getComponent("common.close"), null));
        fillFrameGlass();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getSlot();

        if (slot == 0) {
            if (event.isLeftClick()) {
                plugin.getConfigManager().playSound(viewer, "gui-click");
                viewer.closeInventory();
                double x = (plot.getBoundingBox().getMinX() + plot.getBoundingBox().getMaxX()) / 2.0;
                double z = (plot.getBoundingBox().getMinZ() + plot.getBoundingBox().getMaxZ()) / 2.0;

                // Для арендного плота берем мир из него самого
                org.bukkit.World targetWorld = plot.getWorld();
                if (targetWorld != null) {
                    double y = targetWorld.getHighestBlockYAt((int)x, (int)z) + 1;
                    viewer.teleport(new org.bukkit.Location(targetWorld, x, y, z));
                }
            } else if (event.isRightClick()) {
                plugin.getConfigManager().playSound(viewer, "gui-click");
                viewer.closeInventory();
                me.lovelace.loveclaims.task.BorderDisplayTask.showBorder(plugin, viewer, plot.getBoundingBox(), 200L);
            }
        }
        if (slot == membersSlot) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.openInventory(new RentalMembersGUI(plugin, viewer, plot).getInventory());
        }
        if (showRefuse && slot == refuseSlot) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.openInventory(new RentalAbandonConfirmGUI(plugin, plot).getInventory());
        }
        if (slot == inventory.getSize() - 1) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.closeInventory();
        }
    }
}
