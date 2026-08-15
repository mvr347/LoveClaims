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

    /**
     * gui-gen-5: физические слоты рабочей зоны 54-слотового меню, доступные под контент (К),
     * в порядке рендера (3 ряда по 7 интерьерных слотов, третий ряд делит место с пагинацией
     * на слотах 36/44 — см. RULE 6/RULE 8 и Исключение 2 стандарта gui-gen-5).
     */
    protected static final int[] CONTENT_SLOTS_54 = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    protected static final int PAGINATION_PREV_SLOT_54 = 36;
    protected static final int PAGINATION_NEXT_SLOT_54 = 44;

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

    // ===== gui-gen-5: общие хелперы Header/Footer-рамки =====
    // Стекло существует только в Header/Row1/Footer (RULE 2). В рабочей зоне стекла быть не может —
    // боковые стенки и незанятые интерьерные слоты рабочей зоны остаются AIR и этими хелперами не трогаются.

    /** Стеклянная панель-разделитель (GRAY_STAINED_GLASS_PANE, пустое имя) — базовый элемент рамки. */
    protected ItemStack glassPane() {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        org.bukkit.inventory.meta.ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            glass.setItemMeta(meta);
        }
        return glass;
    }

    /** Принудительно ставит стекло в указанные слоты (используется для стеклянных границ 1/8 Header'а и т.п.). */
    protected void setGlass(int... slots) {
        ItemStack glass = glassPane();
        for (int slot : slots) {
            inventory.setItem(slot, glass);
        }
    }

    /** Заполняет стеклом только пустые (AIR) слоты в диапазоне [fromInclusive, toExclusive). Кнопка в приоритете над стеклом (RULE 2.4) — уже поставленные элементы не трогаются. */
    protected void fillGlassRange(int fromInclusive, int toExclusive) {
        ItemStack glass = glassPane();
        int max = Math.min(toExclusive, inventory.getSize());
        for (int i = Math.max(0, fromInclusive); i < max; i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType() == Material.AIR) {
                inventory.setItem(i, glass);
            }
        }
    }

    /**
     * gui-gen-5 RULE 8: достраивает стеклянную рамку Header'а (0-8), Row1 (9-17, только у меню от
     * 45 слотов) и Footer'а (последние 9 слотов, всегда 1 ряд) — заполняя стеклом всё, что в этих
     * зонах ещё не занято функциональным элементом. Слот 0 (голова), Вк-кнопки (2-7) и Footer-кнопки
     * (доп./Back/Close) должны быть выставлены ДО вызова этого метода. Рабочую зону не трогает.
     * Применимо только к стандартным chest-размерам (27/36/45/54) — 9-слотовые/хоппер/диспенсер меню
     * собирают свою рамку вручную (см. Исключение 1 стандарта gui-gen-5).
     */
    protected void fillFrameGlass() {
        int size = inventory.getSize();
        if (size < 27) return;
        fillGlassRange(0, 9);
        if (size >= 45) {
            fillGlassRange(9, 18);
        }
        fillGlassRange(size - 9, size);
    }

    /**
     * gui-gen-5 RULE 7: ставит Footer-кнопки в их фиксированные позиции (последние 3 слота меню):
     * доп.кнопка (Д) на позиции size-3, Back (B) на size-2, Close (C) всегда на size-1. Передайте
     * {@code null} для extra/back, если функция в моменте неактивна/не нужна — на слот вместо неё
     * позже ляжет стекло через {@link #fillFrameGlass()} (RULE 2.5 — динамическое возвращение стекла).
     */
    protected void setFooterButtons(ItemStack extra, ItemStack back, ItemStack close) {
        int size = inventory.getSize();
        if (extra != null) inventory.setItem(size - 3, extra);
        if (back != null) inventory.setItem(size - 2, back);
        inventory.setItem(size - 1, close != null ? close : glassPane());
    }
}
