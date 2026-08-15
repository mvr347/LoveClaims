package me.lovelace.loveclaims.gui;

import me.lovelace.loveclaims.LoveClaims;
import static me.lovelace.loveclaims.textures.HeadTextures.*;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.model.IndicatorType;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class RentalEditGUI extends AbstractGUI {
    private final Claim plot;
    protected final LoveClaims plugin;

    public RentalEditGUI(LoveClaims plugin, Claim plot) {
        super(27, plugin.getConfigManager().getComponent("rental-edit.title", "name", plot.getName()));
        this.plugin = plugin;
        this.plot = plot;
        setMenuItems();
    }

    @Override
    protected void setMenuItems() {
        String ownerName = "Сервер";
        if (plot.getOwnerUuid() != null) {
            String fetchedName = org.bukkit.Bukkit.getOfflinePlayer(plot.getOwnerUuid()).getName();
            if (fetchedName != null) ownerName = fetchedName;
        }

        String statusKey = plot.isRented() ? "rental-edit.info-lore-status-rented" : "rental-edit.info-lore-status-free";

        // gui-gen-5 RULE 3: слот 0 — тематическая иконка (админ-редактирование конкретного плота).
        ItemStack info = createHead(HEAD_INFO, plugin.getConfigManager().getComponent("rental-edit.info-name"), List.of(
                plugin.getConfigManager().getComponent("rental-edit.info-lore-owner", "owner", ownerName),
                plugin.getConfigManager().getComponent("rental-edit.info-lore-price", "price", String.valueOf(plot.getRentalPrice())),
                plugin.getConfigManager().getComponent(statusKey),
                Component.empty(),
                plugin.getConfigManager().getComponent("rental-list.plot-lore-click")
        ));
        inventory.setItem(0, info);

        // Рабочая зона (9-17): цена / переключатель Landlord, центрированы 11/15.
        ItemStack price = createItem(Material.GOLD_INGOT, plugin.getConfigManager().getComponent("rental-edit.price-name"), plugin.getConfigManager().getHelpMessage("rental-edit.price-lore"));
        inventory.setItem(11, price);

        String typeStr = switch (plot.getIndicatorType()) {
            case NPC -> "NPC (Житель)";
            case SIGN -> "Табличка";
            default -> "Выключено";
        };
        ItemStack landlordBtn = createHead(HEAD_SETTINGS, Component.text("§bВид Арендодателя (Landlord)"), List.of(
                Component.text("§7Текущий вид: §e" + typeStr),
                Component.text(""),
                Component.text("§aКликните, чтобы переключить")
        ));
        inventory.setItem(15, landlordBtn);

        // Footer Д (доп.кнопка, Исключение 3): опасное действие "удалить плот".
        ItemStack delete = createHead(HEAD_BARRIER, plugin.getConfigManager().getComponent("rental-edit.delete-name"), List.of(
                plugin.getConfigManager().getComponent("rental-edit.delete-lore"),
                Component.empty(),
                plugin.getConfigManager().getComponent("rental-edit.delete-lore-warning")
        ));
        setFooterButtons(
                delete,
                createHead(HEAD_BACK, plugin.getConfigManager().getComponent("rental-edit.back-name"), null),
                createHead(HEAD_BARRIER, plugin.getConfigManager().getComponent("common.close"), null)
        );
        fillFrameGlass();
    }

    private ItemStack createItem(Material m, Component name, List<Component> lore) {
        ItemStack i = new ItemStack(m);
        ItemMeta mt = i.getItemMeta();
        if (mt != null) {
            mt.displayName(name);
            if (lore != null) mt.lore(lore);
            i.setItemMeta(mt);
        }
        return i;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getSlot();

        if (slot == 26) {
            event.getWhoClicked().closeInventory();
        } else if (slot == 25) {
            event.getWhoClicked().openInventory(new RentalAdminListGUI(plugin).getInventory());
        } else if (slot == 24) {
            plugin.getStorage().deleteClaimAsync(plot.getId());
            plugin.getClaimManager().removeClaimFromCache(plot.getId());
            plugin.getRentalManager().unregisterPlotName(plot.getName());
            event.getWhoClicked().sendMessage(plugin.getConfigManager().getMessage("rental-deleted"));
            event.getWhoClicked().openInventory(new RentalAdminListGUI(plugin).getInventory());
        } else if (slot == 11) {
            plugin.getChatListener().setPendingPrice(event.getWhoClicked().getUniqueId(), plot);
            event.getWhoClicked().sendMessage(plugin.getConfigManager().getComponent("rental-edit.enter-price"));
            event.getWhoClicked().closeInventory();
        } else if (slot == 15) {
            IndicatorType current = plot.getIndicatorType();
            IndicatorType next = switch (current) {
                case NONE -> IndicatorType.SIGN;
                case SIGN -> IndicatorType.NPC;
                case NPC -> IndicatorType.NONE;
            };
            plot.setIndicatorType(next);
            plugin.getStorage().saveClaimAsync(plot);
            plugin.getRentalManager().updateIndicator(plot);
            plugin.getConfigManager().playSound((org.bukkit.entity.Player) event.getWhoClicked(), "gui-click");
            setMenuItems();
        }
    }
}
