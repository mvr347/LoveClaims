package me.lovelace.loveclaims.gui;
import static me.lovelace.loveclaims.textures.HeadTextures.*;
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

    /**
     * gui-gen v2.1: centered slots inside the work row (10-16) of a 27-slot menu. Side walls 9/17
     * stay empty. Render and click code must both take their slot numbers from here.
     * 1 -> 13; 2 -> 12,14; 3 -> 11,13,15; 4 -> 10,12,14,16; counts above 7 are capped at 7.
     */
    protected static int[] centeredRowSlots(int count) {
        return switch (Math.max(0, Math.min(count, 7))) {
            case 0 -> new int[0];
            case 1 -> new int[]{13};
            case 2 -> new int[]{12, 14};
            case 3 -> new int[]{11, 13, 15};
            case 4 -> new int[]{10, 12, 14, 16};
            case 5 -> new int[]{11, 12, 13, 14, 15};
            case 6 -> new int[]{10, 11, 12, 14, 15, 16};
            default -> new int[]{10, 11, 12, 13, 14, 15, 16};
        };
    }

    /**
     * gui_gen v2.1 RULE 4: slots of menu-control buttons (Vk) inside the Header range 2-7.
     * 1 -> 4; 2 -> 3,5; 3 -> 2,4,6; 4 -> 2,3,5,6; 5 -> 2,3,4,6,7; 6 -> 2..7. Render and click code
     * must both take their slot numbers from here. Free slots of the range are filled with glass
     * by {@link #fillFrameGlass()}.
     */
    protected static int[] headerControlSlots(int count) {
        return switch (Math.max(0, Math.min(count, 6))) {
            case 0 -> new int[0];
            case 1 -> new int[]{4};
            case 2 -> new int[]{3, 5};
            case 3 -> new int[]{2, 4, 6};
            case 4 -> new int[]{2, 3, 5, 6};
            case 5 -> new int[]{2, 3, 4, 6, 7};
            default -> new int[]{2, 3, 4, 5, 6, 7};
        };
    }

    public AbstractGUI(int size, Component title) {
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    public AbstractGUI(org.bukkit.event.inventory.InventoryType type, Component title) {
        this.inventory = Bukkit.createInventory(this, type, title);
    }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }

    public abstract void handleClick(InventoryClickEvent event);
    public void handleBottomClick(InventoryClickEvent event) {}

    /**
     * True for menus in which the player may still rearrange their own inventory with the mouse (the gem menus):
     * only clicks that are menu actions ({@link #isBottomAction}) and clicks that could reach the menu's own slots
     * (shift click, double click) are cancelled.
     */
    protected boolean bottomRearrangeAllowed() {
        return false;
    }

    /** In menus that allow rearranging: whether this click on the player's inventory is a menu action. */
    protected boolean isBottomAction(InventoryClickEvent event) {
        return true;
    }

    /** True when the click on the player's own inventory is plain item moving and must not be cancelled. */
    public final boolean letsBottomClickThrough(InventoryClickEvent event) {
        if (!bottomRearrangeAllowed() || isBottomAction(event)) return false;
        return switch (event.getAction()) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME, PLACE_ALL, PLACE_ONE, PLACE_SOME,
                 SWAP_WITH_CURSOR, HOTBAR_SWAP, NOTHING -> true;
            default -> false;
        };
    }
    protected abstract void setMenuItems();

    // Генератор кастомных голов через Paper API
    protected ItemStack createHead(String base64, Component name, java.util.List<Component> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
        if (meta != null) {
            com.destroystokyo.paper.profile.PlayerProfile profile =
                    org.bukkit.Bukkit.createProfile(
                            java.util.UUID.nameUUIDFromBytes(
                                    base64.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                            "LoveTexture");
            profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", base64));
            meta.setPlayerProfile(profile);
            if (name != null) meta.displayName(name);
            if (lore != null && !lore.isEmpty()) meta.lore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    protected static String permissionTexture(me.lovelace.loveclaims.model.ClaimPermission permission) {
        return switch (permission) {
            case BUILD -> HEAD_PERMISSION_BUILD;
            case CONTAINERS -> HEAD_PERMISSION_CONTAINERS;
            case INTERACT -> HEAD_PERMISSION_INTERACT;
            case MANAGE -> HEAD_PERMISSION_MANAGE;
            case CORE -> HEAD_PERMISSION_CORE;
        };
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

    // ===== Confirmation windows: the server's ItemsAdder confirm menu when available, else the 9-slot template =====

    protected static int confirmSize(org.bukkit.entity.Player viewer) {
        return ConfirmMenuLayout.available(viewer) ? ConfirmMenuLayout.SIZE : 9;
    }

    protected static Component confirmTitle(org.bukkit.entity.Player viewer, Component fallback) {
        return ConfirmMenuLayout.available(viewer) ? ConfirmMenuLayout.title(viewer) : fallback;
    }

    protected boolean largeConfirm() {
        return inventory.getSize() == ConfirmMenuLayout.SIZE;
    }

    protected boolean isConfirmSlot(int slot) {
        return largeConfirm() ? ConfirmMenuLayout.isConfirm(slot) : slot == 1;
    }

    protected boolean isCancelSlot(int slot) {
        return largeConfirm() ? ConfirmMenuLayout.isCancel(slot) : slot == 7;
    }

    /** Places the two confirmation buttons in whichever layout this window has. */
    protected void placeConfirm(Component yesName, java.util.List<Component> yesLore,
                                Component noName, java.util.List<Component> noLore) {
        if (largeConfirm()) {
            ItemStack yes = ConfirmMenuLayout.button(yesName, yesLore);
            ItemStack no = ConfirmMenuLayout.button(noName, noLore);
            if (yes != null && no != null) {
                ConfirmMenuLayout.fill(inventory, yes, no);
                return;
            }
        }
        inventory.setItem(1, createHead(HEAD_DELETE_YES, yesName, yesLore));
        inventory.setItem(7, createHead(HEAD_DELETE_NO, noName, noLore));
        setGlass(0, 2, 6, 8);
    }
}
