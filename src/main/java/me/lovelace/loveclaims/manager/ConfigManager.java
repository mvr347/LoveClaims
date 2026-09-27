package me.lovelace.loveclaims.manager;

import me.lovelace.loveclaims.LoveClaims;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ConfigManager {
    private final LoveClaims plugin;
    private FileConfiguration config;
    private FileConfiguration lang;
    private FileConfiguration anchors;
    private final MiniMessage mm = MiniMessage.miniMessage();

    // Кэшированные настройки
    private boolean debugMode;
    private String language;

    public ConfigManager(LoveClaims plugin) {
        this.plugin = plugin;
        loadAll();
    }

    public void loadAll() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

        this.debugMode = config.getBoolean("misc.debug", false);
        this.language = config.getString("misc.language", "ru");

        File langFile = new File(plugin.getDataFolder(), "lang.yml");
        if (!langFile.exists()) plugin.saveResource("lang.yml", false);
        lang = YamlConfiguration.loadConfiguration(langFile);

        File anchorsFile = new File(plugin.getDataFolder(), "anchors.yml");
        if (!anchorsFile.exists()) plugin.saveResource("anchors.yml", false);
        anchors = YamlConfiguration.loadConfiguration(anchorsFile);

        if (debugMode) {
            plugin.getLogger().info("Debug mode enabled");
        }
        plugin.getLogger().info("Language: " + language);

        if (config.getBoolean("spawn-claim.enabled", false)) {
            String spawnWorldName = config.getString("spawn-claim.world", "world");
            if (org.bukkit.Bukkit.getWorld(spawnWorldName) == null) {
                plugin.getLogger().warning("spawn-claim.world '" + spawnWorldName + "' не найден среди загруженных миров - "
                        + "защита спавна НЕ БУДЕТ работать, пока имя мира в config.yml не будет исправлено!");
            }
        }
    }

    public FileConfiguration getConfig() { return config; }
    public FileConfiguration getAnchors() { return anchors; }
    public FileConfiguration getLang() { return lang; }

    public boolean isDebugMode() {
        return debugMode;
    }

    public String getLanguage() {
        return language;
    }

    public boolean isUseEntityBlocks() {
        return config.getBoolean("border.use-entity-blocks", true);
    }

    public Material getBorderMaterial() {
        String matName = config.getString("border.material", "BARRIER");
        Material mat = Material.getMaterial(matName.toUpperCase());
        return mat != null ? mat : Material.BARRIER;
    }

    public boolean isBorderGlowing() {
        return config.getBoolean("border.glowing", true);
    }

    public void debug(String message) {
        if (debugMode) {
            plugin.getLogger().info("[DEBUG] " + message);
        }
    }

    public void debugWarn(String message) {
        if (debugMode) {
            plugin.getLogger().warning("[DEBUG] " + message);
        }
    }

    private Optional<Sound> getSound(String soundKey) {
        String soundName = config.getString("sounds." + soundKey, "NONE");
        if (soundName.equalsIgnoreCase("NONE") || soundName.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(Registry.SOUNDS.get(NamespacedKey.minecraft(soundName.toLowerCase())));
    }

    public void playSound(Player player, String soundKey) {
        getSound(soundKey).ifPresent(sound -> player.playSound(player.getLocation(), sound, 1f, 1f));
    }

    public void playSound(Player player, String soundKey, float volume, float pitch) {
        getSound(soundKey).ifPresent(sound -> player.playSound(player.getLocation(), sound, volume, pitch));
    }

    public void playSoundForNearby(org.bukkit.Location location, String soundKey, double radius) {
        getSound(soundKey).ifPresent(sound -> {
            if (location.getWorld() != null) {
                location.getWorld().playSound(location, sound, 1f, 1f);
            }
        });
    }

    public boolean isSoundEnabled(String soundKey) {
        String soundName = config.getString("sounds." + soundKey, "NONE");
        return !soundName.equalsIgnoreCase("NONE") && !soundName.trim().isEmpty();
    }

    private String convertLegacyToMiniMessage(String text) {
        if (text == null) return "";
        return text.replace("&0", "<black>").replace("\u00a70", "<black>")
                .replace("&1", "<dark_blue>").replace("\u00a71", "<dark_blue>")
                .replace("&2", "<dark_green>").replace("\u00a72", "<dark_green>")
                .replace("&3", "<dark_aqua>").replace("\u00a73", "<dark_aqua>")
                .replace("&4", "<dark_red>").replace("\u00a74", "<dark_red>")
                .replace("&5", "<dark_purple>").replace("\u00a75", "<dark_purple>")
                .replace("&6", "<gold>").replace("\u00a76", "<gold>")
                .replace("&7", "<gray>").replace("\u00a77", "<gray>")
                .replace("&8", "<dark_gray>").replace("\u00a78", "<dark_gray>")
                .replace("&9", "<blue>").replace("\u00a79", "<blue>")
                .replace("&a", "<green>").replace("\u00a7a", "<green>")
                .replace("&b", "<aqua>").replace("\u00a7b", "<aqua>")
                .replace("&c", "<red>").replace("\u00a7c", "<red>")
                .replace("&d", "<light_purple>").replace("\u00a7d", "<light_purple>")
                .replace("&e", "<yellow>").replace("\u00a7e", "<yellow>")
                .replace("&f", "<white>").replace("\u00a7f", "<white>")
                .replace("&k", "<obfuscated>").replace("\u00a7k", "<obfuscated>")
                .replace("&l", "<bold>").replace("\u00a7l", "<bold>")
                .replace("&m", "<strikethrough>").replace("\u00a7m", "<strikethrough>")
                .replace("&n", "<underlined>").replace("\u00a7n", "<underlined>")
                .replace("&o", "<italic>").replace("\u00a7o", "<italic>")
                .replace("&r", "<reset>").replace("\u00a7r", "<reset>");
    }

    public Component getMessage(String path, String... placeholders) {
        String prefix = lang.getString("prefix", "<dark_gray>[ <gold>AC <dark_gray>] <white> ");
        List<String> list = lang.getStringList(path);
        String msg;
        if (list.isEmpty()) {
            msg = lang.getString(path, "<red>Error: " + path);
        } else {
            msg = String.join("\n", list);
        }
        if (msg.equalsIgnoreCase("NONE") || msg.equalsIgnoreCase("none") || msg.trim().isEmpty()) {
            return Component.empty();
        }
        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                msg = msg.replace("{" + placeholders[i] + "}", placeholders[i+1]);
            }
        }
        return mm.deserialize(convertLegacyToMiniMessage(prefix + msg));
    }

    public String getGuiText(String path, String... placeholders) {
        List<String> list = lang.getStringList(path);
        String text;
        if (list.isEmpty()) {
            text = lang.getString(path, "<red>Error: " + path);
        } else {
            text = String.join("\n", list);
        }
        if (text.equalsIgnoreCase("NONE") || text.equalsIgnoreCase("none") || text.trim().isEmpty()) {
            return "";
        }
        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                text = text.replace("{" + placeholders[i] + "}", placeholders[i+1]);
            }
        }
        Component comp = mm.deserialize(convertLegacyToMiniMessage(text));
        return LegacyComponentSerializer.legacySection().serialize(comp);
    }

    public List<String> getGuiLore(String path, String... placeholders) {
        List<String> list = lang.getStringList(path);
        List<String> result = new ArrayList<>();
        for (String line : list) {
            for (int i = 0; i < placeholders.length; i += 2) {
                if (i + 1 < placeholders.length) {
                    line = line.replace("{" + placeholders[i] + "}", placeholders[i+1]);
                }
            }
            Component comp = mm.deserialize(convertLegacyToMiniMessage(line));
            result.add(LegacyComponentSerializer.legacySection().serialize(comp));
        }
        return result;
    }

    public List<Component> getHelpMessage(String path, String... placeholders) {
        List<String> list = lang.getStringList(path);
        List<Component> result = new ArrayList<>();
        for (String line : list) {
            for (int i = 0; i < placeholders.length; i += 2) {
                if (i + 1 < placeholders.length) {
                    line = line.replace("{" + placeholders[i] + "}", placeholders[i+1]);
                }
            }
            result.add(mm.deserialize(convertLegacyToMiniMessage(line)));
        }
        return result;
    }

    public boolean isInsideSpawnClaim(org.bukkit.Location loc) {
        if (!config.getBoolean("spawn-claim.enabled", false)) return false;
        String worldName = config.getString("spawn-claim.world", "world");
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(worldName)) return false;
        int cx = config.getInt("spawn-claim.x", 0);
        int cz = config.getInt("spawn-claim.z", 0);
        int radius = config.getInt("spawn-claim.radius", 300);
        int dx = loc.getBlockX() - cx;
        int dz = loc.getBlockZ() - cz;
        return Math.abs(dx) <= radius && Math.abs(dz) <= radius;
    }

    public boolean getSpawnFlag(String flag) {
        return config.getBoolean("spawn-claim.flags." + flag, false);
    }

    /**
     * Доля событий уменьшения голода (FoodLevelChangeEvent), которые гасятся на территории
     * спавна - см. spawn-claim.food-depletion-reduction. 0.0 = ваниль, 1.0 = голод на спавне не падает вообще.
     * @return доля подавляемых событий, 0.0-1.0
     */
    public double getSpawnFoodDepletionReduction() {
        double value = config.getDouble("spawn-claim.food-depletion-reduction", 1.0);
        if (value < 0.0) return 0.0;
        if (value > 1.0) return 1.0;
        return value;
    }

    public String getString(String path, String... placeholders) {
        List<String> list = lang.getStringList(path);
        String text;
        if (list.isEmpty()) {
            text = lang.getString(path, "<red>Error: " + path);
        } else {
            text = String.join("\n", list);
        }
        if (text.equalsIgnoreCase("NONE") || text.equalsIgnoreCase("none") || text.trim().isEmpty()) {
            return "";
        }
        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                text = text.replace("{" + placeholders[i] + "}", placeholders[i+1]);
            }
        }
        Component comp = mm.deserialize(convertLegacyToMiniMessage(text));
        return LegacyComponentSerializer.legacySection().serialize(comp);
    }

    public Component getComponent(String path, String... placeholders) {
        List<String> list = lang.getStringList(path);
        String text;
        if (list.isEmpty()) {
            text = lang.getString(path, "<red>Error: " + path);
        } else {
            text = String.join("\n", list);
        }
        if (text.equalsIgnoreCase("NONE") || text.equalsIgnoreCase("none") || text.trim().isEmpty()) {
            return Component.empty();
        }
        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                text = text.replace("{" + placeholders[i] + "}", placeholders[i+1]);
            }
        }
        return mm.deserialize(convertLegacyToMiniMessage(text));
    }
}
