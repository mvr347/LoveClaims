package me.lovelace.loveclaims.gui;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractGUI implements InventoryHolder {
    protected Inventory inventory;

    // Все base64-текстуры голов вынесены в единую точку правды: me.lovelace.loveclaims.textures.HeadTextures

    public AbstractGUI(int size, Component title) {
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    public AbstractGUI(org.bukkit.event.inventory.InventoryType type, Component title) {
        this.inventory = Bukkit.createInventory(this, type, title);
    }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }

    public abstract void handleClick(InventoryClickEvent event);
    protected abstract void setMenuItems();

    // Генератор кастомных голов через Paper API
    protected ItemStack createHead(String base64, Component name, java.util.List<Component> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
        if (meta != null) {
            com.destroystokyo.paper.profile.PlayerProfile profile = org.bukkit.Bukkit.createProfile(java.util.UUID.randomUUID());
            profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", base64));
            meta.setPlayerProfile(profile);
            if (name != null) meta.displayName(name);
            if (lore != null && !lore.isEmpty()) meta.lore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    protected void fillEmptySlots() {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        org.bukkit.inventory.meta.ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            glass.setItemMeta(meta);
        }
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType() == Material.AIR) {
                inventory.setItem(i, glass);
            }
        }
    }
}
