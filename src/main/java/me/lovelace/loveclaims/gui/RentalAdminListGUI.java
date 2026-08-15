package me.lovelace.loveclaims.gui;

import me.lovelace.loveclaims.LoveClaims;
import static me.lovelace.loveclaims.textures.HeadTextures.*;
import me.lovelace.loveclaims.model.Claim;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

public class RentalAdminListGUI extends AbstractGUI {
    private static final int PAGE_SIZE = CONTENT_SLOTS_54.length;

    protected final LoveClaims plugin;
    private final List<Claim> plots;
    private int page = 0;

    public RentalAdminListGUI(LoveClaims plugin) {
        super(54, plugin.getConfigManager().getComponent("rental-list.title"));
        this.plugin = plugin;
        this.plots = plugin.getClaimManager().getAllClaims().stream()
                .filter(Claim::isRentalPlot)
                .toList();
        setMenuItems();
    }

    @Override
    protected void setMenuItems() {
        inventory.clear();

        // gui-gen-5 RULE 3: слот 0 — тематическая иконка (админ-список арендных плотов сервера).
        inventory.setItem(0, createHead(HEAD_SETTINGS, plugin.getConfigManager().getComponent("rental-list.title"), null));

        NamespacedKey key = new NamespacedKey(plugin, "plot_id");

        int totalPages = Math.max(1, (int) Math.ceil(plots.size() / (double) PAGE_SIZE));
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, plots.size());

        for (int i = start; i < end; i++) {
            Claim plot = plots.get(i);
            int contentSlot = CONTENT_SLOTS_54[i - start];

            String ownerName = "Сервер";
            if (plot.getOwnerUuid() != null) {
                String fetchedName = org.bukkit.Bukkit.getOfflinePlayer(plot.getOwnerUuid()).getName();
                if (fetchedName != null) ownerName = fetchedName;
            }

            String statusKey = plot.isRented() ? "rental-list.plot-lore-status-rented" : "rental-list.plot-lore-status-free";

            ItemStack item = createHead(HEAD_INFO,
                    plugin.getConfigManager().getComponent("rental-list.plot-name", "name", plot.getName()),
                    List.of(
                            plugin.getConfigManager().getComponent("rental-list.plot-lore-owner", "owner", ownerName),
                            plugin.getConfigManager().getComponent("rental-list.plot-lore-price", "price", String.valueOf(plot.getRentalPrice())),
                            plugin.getConfigManager().getComponent(statusKey),
                            Component.empty(),
                            plugin.getConfigManager().getComponent("rental-list.plot-lore-click")
                    ));

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, plot.getId().toString());
                item.setItemMeta(meta);
            }

            inventory.setItem(contentSlot, item);
        }

        // Пагинация (Исключение 2, только 54-слотовое): активна только если есть больше одной страницы.
        if (page > 0) {
            inventory.setItem(PAGINATION_PREV_SLOT_54, createHead(HEAD_ARROW_LEFT, Component.text("§6← Назад"), null));
        }
        if (end < plots.size()) {
            inventory.setItem(PAGINATION_NEXT_SLOT_54, createHead(HEAD_ARROW_RIGHT, Component.text("§6Вперёд →"), null));
        }

        // Standalone-меню (открывается напрямую админ-командой /rental list) — Back неактивен.
        setFooterButtons(null, null, createHead(HEAD_BARRIER, plugin.getConfigManager().getComponent("common.close"), null));
        fillFrameGlass();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getSlot();
        int size = inventory.getSize();

        if (slot == size - 1) {
            event.getWhoClicked().closeInventory();
            return;
        }

        if (slot == PAGINATION_PREV_SLOT_54 && page > 0) {
            plugin.getConfigManager().playSound((org.bukkit.entity.Player) event.getWhoClicked(), "gui-click");
            page--;
            setMenuItems();
            return;
        }
        if (slot == PAGINATION_NEXT_SLOT_54) {
            int totalPages = Math.max(1, (int) Math.ceil(plots.size() / (double) PAGE_SIZE));
            if (page < totalPages - 1) {
                plugin.getConfigManager().playSound((org.bukkit.entity.Player) event.getWhoClicked(), "gui-click");
                page++;
                setMenuItems();
            }
            return;
        }

        ItemStack item = event.getCurrentItem();
        if (item == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        NamespacedKey key = new NamespacedKey(plugin, "plot_id");
        String idStr = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);

        if (idStr != null) {
            UUID plotId = UUID.fromString(idStr);
            plugin.getClaimManager().getClaimById(plotId).ifPresent(plot -> {
                event.getWhoClicked().openInventory(new RentalEditGUI(plugin, plot).getInventory());
            });
        }
    }
}
