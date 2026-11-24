package me.lovelace.loveclaims.gems;

import me.lovelace.loveclaims.LoveClaims;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.Recipe;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.OptionalInt;
import java.util.function.BiConsumer;

/**
 * Anchor core: the part of an anchor that overloaded gems can crack. A cracked anchor is repaired by
 * putting a core of the same or a higher tier into the gems menu. Core tier 2 and 3 are upgrades of the
 * previous core, so the whole anchor chain costs more as the tier grows.
 */
public final class AnchorCores {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String[] ROMAN = {"", "I", "II", "III"};

    private AnchorCores() {
    }

    private static NamespacedKey tierKey(LoveClaims plugin) {
        return new NamespacedKey(plugin, "anchor_core_tier");
    }

    public static ItemStack create(LoveClaims plugin, int tier) {
        int t = Math.max(1, Math.min(3, tier));
        ItemStack item = new ItemStack(switch (t) {
            case 1 -> Material.COPPER_INGOT;
            case 2 -> Material.AMETHYST_SHARD;
            default -> Material.ECHO_SHARD;
        });
        item.editMeta(meta -> {
            meta.displayName(noItalic(MM.deserialize("<gold>Ядро якоря <dark_gray>|</dark_gray> <yellow>Тир " + ROMAN[t] + "</yellow>")));
            meta.lore(List.of(
                    noItalic(MM.deserialize("<dark_gray>Часть якоря")),
                    Component.empty(),
                    noItalic(MM.deserialize("<gray>Заменяет треснувшее ядро якоря")),
                    noItalic(MM.deserialize("<gray>того же или более низкого тира.")),
                    noItalic(MM.deserialize("<dark_gray><st>─────────────────────</st></dark_gray>")),
                    noItalic(MM.deserialize("<green>ЛКМ <dark_gray>— <gray>по ядру в инвентаре при открытом меню гемов"))
            ));
            meta.getPersistentDataContainer().set(tierKey(plugin), PersistentDataType.INTEGER, t);
        });
        return item;
    }

    public static OptionalInt readTier(LoveClaims plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return OptionalInt.empty();
        Integer t = item.getItemMeta().getPersistentDataContainer().get(tierKey(plugin), PersistentDataType.INTEGER);
        return t == null ? OptionalInt.empty() : OptionalInt.of(t);
    }

    public static boolean isCore(LoveClaims plugin, ItemStack item) {
        return readTier(plugin, item).isPresent();
    }

    /** True for the recipes registered by {@link #registerRecipes}. */
    public static boolean isCoreRecipe(LoveClaims plugin, NamespacedKey key) {
        return key != null && key.getNamespace().equals(new NamespacedKey(plugin, "x").getNamespace())
                && key.getKey().startsWith("core_craft_");
    }

    /** Recipes: tier 1 from cheap materials, tiers 2 and 3 upgrade the previous core (it sits in the centre). */
    public static void registerRecipes(LoveClaims plugin, BiConsumer<NamespacedKey, Recipe> sink) {
        NamespacedKey k1 = new NamespacedKey(plugin, "core_craft_1");
        ShapedRecipe r1 = new ShapedRecipe(k1, create(plugin, 1));
        r1.shape("IRI", "RCR", "IRI");
        r1.setIngredient('I', Material.IRON_INGOT);
        r1.setIngredient('R', Material.REDSTONE);
        r1.setIngredient('C', new RecipeChoice.MaterialChoice(Material.IRON_BLOCK, Material.COPPER_BLOCK));
        sink.accept(k1, r1);

        NamespacedKey k2 = new NamespacedKey(plugin, "core_craft_2");
        ShapedRecipe r2 = new ShapedRecipe(k2, create(plugin, 2));
        r2.shape("LGL", "GCG", "LGL");
        r2.setIngredient('L', new RecipeChoice.MaterialChoice(Material.LAPIS_BLOCK, Material.AMETHYST_BLOCK));
        r2.setIngredient('G', Material.GOLD_BLOCK);
        r2.setIngredient('C', new RecipeChoice.ExactChoice(create(plugin, 1)));
        sink.accept(k2, r2);

        NamespacedKey k3 = new NamespacedKey(plugin, "core_craft_3");
        ShapedRecipe r3 = new ShapedRecipe(k3, create(plugin, 3));
        r3.shape("DND", "NCN", "DND");
        r3.setIngredient('D', new RecipeChoice.MaterialChoice(Material.DIAMOND_BLOCK, Material.NETHERITE_SCRAP));
        r3.setIngredient('N', Material.CRYING_OBSIDIAN);
        r3.setIngredient('C', new RecipeChoice.ExactChoice(create(plugin, 2)));
        sink.accept(k3, r3);
    }

    private static Component noItalic(Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }
}
