package me.lovelace.loveclaims.manager;

import me.lovelace.loveclaims.LoveClaims;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

import java.util.*;

public class ItemCurrencyManager {
    private final LoveClaims plugin;
    private final List<CurrencyUnit> currencyUnits = new ArrayList<>();

    public ItemCurrencyManager(LoveClaims plugin) {
        this.plugin = plugin;
        loadCurrencyUnits();
    }

    private void loadCurrencyUnits() {
        currencyUnits.clear();
        List<Map<?, ?>> units = plugin.getConfigManager().getConfig().getMapList("rental.currency.units");

        if (units.isEmpty()) {
            // Fallback для старого конфига
            String mat = plugin.getConfigManager().getConfig().getString("rental.currency.material", "COPPER_INGOT");
            String data = plugin.getConfigManager().getConfig().getString("rental.currency.custom-model-data", "0");
            Material material = Material.getMaterial(mat != null ? mat.toUpperCase() : "COPPER_INGOT");
            if (material == null) material = Material.COPPER_INGOT;
            currencyUnits.add(new CurrencyUnit(material, data != null ? data : "0", 1));
        } else {
            for (Map<?, ?> unit : units) {
                Object matObj = unit.get("material");
                if (matObj == null) continue;
                String matName = matObj.toString();
                Object dataObj = unit.get("data");
                String data = dataObj != null ? dataObj.toString() : "0";
                Object valueObj = unit.get("value");
                int value;
                if (valueObj instanceof Number) {
                    value = ((Number) valueObj).intValue();
                } else if (valueObj != null) {
                    try { value = Integer.parseInt(valueObj.toString()); }
                    catch (NumberFormatException e) {
                        plugin.getLogger().warning("Invalid currency unit value: " + valueObj);
                        continue;
                    }
                } else continue;
                if (value <= 0) {
                    plugin.getLogger().warning("Currency unit value must be > 0, skipped: " + matName);
                    continue;
                }
                Material material = Material.getMaterial(matName.toUpperCase());
                if (material != null) currencyUnits.add(new CurrencyUnit(material, data, value));
                else plugin.getLogger().warning("Unknown currency material: " + matName);
            }
        }
        // Сортируем от дорогих к дешевым для правильной выдачи сдачи
        currencyUnits.sort((a, b) -> Integer.compare(b.value, a.value));
    }

    private record CurrencyUnit(Material material, String data, int value) {}

    private int getCurrencyValue(ItemStack item) {
        if (item == null || item.getType().isAir()) return 0;
        ItemMeta meta = item.getItemMeta();

        for (CurrencyUnit unit : currencyUnits) {
            if (item.getType() == unit.material) {
                if (checkModelData(meta, unit.data)) {
                    return unit.value;
                }
            }
        }
        return 0;
    }

    private boolean checkModelData(ItemMeta meta, String targetId) {
        // Если "0" или пусто - принимаем любой предмет этого материала
        if (targetId == null || targetId.equals("0") || targetId.isEmpty()) return true;
        if (meta == null) return false;

        // 1. Проверка через новый CustomModelDataComponent (1.21.5+)
        if (meta.hasCustomModelDataComponent()) {
            CustomModelDataComponent component = meta.getCustomModelDataComponent();

            try {
                // А. Пробуем как число (заменяет старый CustomModelData)
                float targetFloat = Float.parseFloat(targetId);
                if (!component.getFloats().isEmpty() && component.getFloats().getFirst() == targetFloat) {
                    return true;
                }
            } catch (NumberFormatException e) {
                // Б. Если не парсится как число, значит это строка (например "coppercoin")
                if (!component.getStrings().isEmpty() && component.getStrings().getFirst().equalsIgnoreCase(targetId)) {
                    return true;
                }
            }
        }

        // 2. Проверка через ItemModel (1.21.4+)
        if (meta.hasItemModel() && meta.getItemModel() != null) {
            String modelKey = meta.getItemModel().getKey();
            String modelString = meta.getItemModel().asString();

            if (modelKey.equalsIgnoreCase(targetId) || modelString.equalsIgnoreCase(targetId)) {
                return true;
            }
        }

        return false;
    }

    public long calculateValue(org.bukkit.inventory.Inventory inv) {
        long total = 0;
        for (ItemStack item : inv.getContents()) {
            int val = getCurrencyValue(item);
            if (val > 0) {
                total += (long) val * item.getAmount();
            }
        }
        return total;
    }

    public boolean hasEnough(Player player, long amount) {
        return calculateValue(player.getInventory()) >= amount;
    }

    public boolean takeCurrency(Player player, long cost) {
        if (player == null || cost < 0) return false;
        if (cost == 0) return true;
        if (!org.bukkit.Bukkit.isPrimaryThread()) {
            plugin.getLogger().severe("takeCurrency called off main thread! Rejecting to prevent inventory corruption.");
            return false;
        }
        long totalBalance = calculateValue(player.getInventory());
        if (totalBalance < cost) return false;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack != null && getCurrencyValue(stack) > 0) {
                player.getInventory().setItem(i, null);
            }
        }
        long change = totalBalance - cost;
        if (change > 0) giveChange(player, change);
        return true;
    }

    private void giveChange(Player player, long amount) {
        long remaining = amount;

        // currencyUnits уже отсортированы от дорогих к дешевым
        for (CurrencyUnit unit : currencyUnits) {
            if (remaining <= 0) break;

            int count = (int) (remaining / unit.value);
            if (count > 0) {
                ItemStack stack = new ItemStack(unit.material, count);
                ItemMeta meta = stack.getItemMeta();

                // Применяем данные модели к предмету сдачи
                applyModelData(meta, unit.data);

                // Устанавливаем displayName чтобы предмет не был "фиолетово-чёрным кубиком"
                String coinName = plugin.getConfigManager().getString("currency-coin-name", "amount", String.valueOf(unit.value));
                if (coinName != null && !coinName.isEmpty()) {
                    meta.displayName(net.kyori.adventure.text.Component.text(coinName));
                }

                stack.setItemMeta(meta);

                // Выдаем игроку (то, что не влезает - падает на пол)
                HashMap<Integer, ItemStack> left = player.getInventory().addItem(stack);
                for (ItemStack drop : left.values()) {
                    player.getWorld().dropItem(player.getLocation(), drop);
                }

                remaining %= unit.value;
            }
        }
    }

    private void applyModelData(ItemMeta meta, String data) {
        if (data == null || data.equals("0") || data.isEmpty()) return;

        // 1. Применяем через новый CustomModelDataComponent (1.21.5+)
        CustomModelDataComponent component = meta.getCustomModelDataComponent();

        try {
            // А. Если число (старый формат)
            float floatVal = Float.parseFloat(data);
            component.setFloats(List.of(floatVal));
        } catch (NumberFormatException e) {
            // Б. Если строка ("coppercoin")
            component.setStrings(List.of(data));
        }

        meta.setCustomModelDataComponent(component);

        // 2. Дополнительно: если указан namespace с двоеточием (например, "myplugin:coppercoin"),
        // устанавливаем ItemModel для совместимости с новейшими ресурс-паками.
        if (data.contains(":")) {
            try {
                org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(data);
                if (key != null) {
                    meta.setItemModel(key);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid NamespacedKey for currency model: " + data);
            }
        }
    }

    public String getNeededCoinsString(long amount) {
        return plugin.getConfigManager().getString("currency-unit-format", "amount", String.valueOf(amount));
    }
}
