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
    private FileConfiguration gems;
    private final MiniMessage mm = MiniMessage.miniMessage();

    // Кэшированные настройки
    private boolean debugMode;
    private boolean spawnCheckPending;
    private String language;

    public ConfigManager(LoveClaims plugin) {
        this.plugin = plugin;
        loadAll();
    }

    public void loadAll() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
        me.lovelace.loveclaims.gems.GemOverload.load(config);
        me.lovelace.loveclaims.util.IaItems.setModelFallback(config.getBoolean("itemsadder.model-fallback"));

        this.debugMode = config.getBoolean("misc.debug", false);
        this.language = config.getString("misc.language", "ru");

        File langFile = new File(plugin.getDataFolder(), "lang.yml");
        if (!langFile.exists()) plugin.saveResource("lang.yml", false);
        refreshOutdatedLang(langFile);
        lang = YamlConfiguration.loadConfiguration(langFile);
        // Keys added in later versions must not show up as "Error: <key>" on servers that keep an
        // older lang.yml: the bundled file is the fallback for every key the server file lacks.
        try (java.io.InputStream bundled = plugin.getResource("lang.yml")) {
            if (bundled != null) {
                lang.setDefaults(YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(bundled, java.nio.charset.StandardCharsets.UTF_8)));
            }
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("Could not read bundled lang.yml defaults: " + e.getMessage());
        }

        File anchorsFile = new File(plugin.getDataFolder(), "anchors.yml");
        if (!anchorsFile.exists()) plugin.saveResource("anchors.yml", false);
        anchors = YamlConfiguration.loadConfiguration(anchorsFile);

        File gemsFile = new File(plugin.getDataFolder(), "gems.yml");
        if (!gemsFile.exists()) plugin.saveResource("gems.yml", false);
        gems = YamlConfiguration.loadConfiguration(gemsFile);
        addMissingGems(gemsFile);
        try (java.io.InputStream bundledGems = plugin.getResource("gems.yml")) {
            if (bundledGems != null) {
                gems.setDefaults(YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(bundledGems, java.nio.charset.StandardCharsets.UTF_8)));
            }
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("Could not read bundled gems.yml defaults: " + e.getMessage());
        }

        if (debugMode) {
            plugin.getLogger().info("Debug mode enabled");
        }
        plugin.getLogger().info("Language: " + language);

        scheduleSpawnWorldCheck();
    }

    /**
     * 2026-11-24: gems added in a later version (improved_*) are copied from the bundled gems.yml into a server file
     * that lacks them, so a server with an old gems.yml gets the new gem tier without losing its own edits.
     */
    private void addMissingGems(File gemsFile) {
        try (java.io.InputStream bundled = plugin.getResource("gems.yml")) {
            if (bundled == null) return;
            FileConfiguration fresh = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(bundled,
                    java.nio.charset.StandardCharsets.UTF_8));
            var freshGems = fresh.getConfigurationSection("gems");
            if (freshGems == null) return;
            boolean changed = false;
            for (String id : freshGems.getKeys(false)) {
                if (id.startsWith("improved_") && !gems.contains("gems." + id)) {
                    gems.set("gems." + id, freshGems.getConfigurationSection(id));
                    changed = true;
                    continue;
                }
                // ItemsAdder ids of existing gems are added once; a key the admin set (even blank) stays as it is.
                String ia = freshGems.getString(id + ".itemsadder", "");
                if (!ia.isBlank() && gems.contains("gems." + id) && !gems.contains("gems." + id + ".itemsadder")) {
                    gems.set("gems." + id + ".itemsadder", ia);
                    changed = true;
                }
            }
            var freshHybrids = fresh.getConfigurationSection("hybrids");
            if (freshHybrids != null) {
                for (String id : freshHybrids.getKeys(false)) {
                    if (id.equals("hybrid_demos_ceres") && !gems.contains("hybrids." + id)) {
                        gems.set("hybrids." + id, freshHybrids.getConfigurationSection(id));
                        changed = true;
                        continue;
                    }
                    String ia = freshHybrids.getString(id + ".itemsadder", "");
                    if (!ia.isBlank() && gems.contains("hybrids." + id) && !gems.contains("hybrids." + id + ".itemsadder")) {
                        gems.set("hybrids." + id + ".itemsadder", ia);
                        changed = true;
                    }
                }
            }
            if (changed) {
                gems.save(gemsFile);
                plugin.getLogger().info("gems.yml: added the improved gems and ItemsAdder ids");
            }
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("Could not update gems.yml: " + e.getMessage());
        }
    }

    /**
     * 2026-11-24: menu texts were rewritten (claims instead of privates, new lore). A lang.yml that keeps old values
     * would hide every change, because the bundled file is only a fallback for missing keys. When the server file
     * carries a lower {@code lang-revision}, it is saved next to itself and replaced by the bundled one.
     */
    private void refreshOutdatedLang(File langFile) {
        try (java.io.InputStream bundled = plugin.getResource("lang.yml")) {
            if (bundled == null) return;
            int bundledRevision = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(bundled,
                    java.nio.charset.StandardCharsets.UTF_8)).getInt("lang-revision", 0);
            int current = YamlConfiguration.loadConfiguration(langFile).getInt("lang-revision", 0);
            if (current >= bundledRevision) return;
            java.nio.file.Files.copy(langFile.toPath(), new File(plugin.getDataFolder(), "lang.yml.bak-" + current).toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            plugin.saveResource("lang.yml", true);
            plugin.getLogger().info("lang.yml updated to revision " + bundledRevision + "; the old file is lang.yml.bak-" + current);
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("Could not refresh lang.yml: " + e.getMessage());
        }
    }

    /**
     * The spawn world is created by the server after the plugins have enabled, so asking for it inside loadAll() made
     * a correctly configured name look missing (and was printed once per loadAll call). isInsideSpawnClaim matches the
     * world by name, so protection works either way; this check only exists to catch a real typo in the config.
     */
    private void scheduleSpawnWorldCheck() {
        if (spawnCheckPending || !config.getBoolean("spawn-claim.enabled", false)) return;
        spawnCheckPending = true;
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            spawnCheckPending = false;
            if (!config.getBoolean("spawn-claim.enabled", false)) return;
            String spawnWorldName = config.getString("spawn-claim.world", "world");
            if (org.bukkit.Bukkit.getWorld(spawnWorldName) == null) {
                plugin.getLogger().warning("spawn-claim.world '" + spawnWorldName + "' не найден среди загруженных миров - "
                        + "защита спавна НЕ БУДЕТ работать, пока имя мира в config.yml не будет исправлено!");
            }
        }, 200L);
    }

    public FileConfiguration getConfig() { return config; }
    public FileConfiguration getAnchors() { return anchors; }
    public FileConfiguration getGems() { return gems; }
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
        // Single-argument getter on purpose: it falls back to the jar defaults, an explicit default would hide them
        // on servers whose config.yml predates the sounds section.
        String soundName = config.getString("sounds." + soundKey);
        if (soundName == null || soundName.equalsIgnoreCase("NONE") || soundName.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(me.lovelace.loveclaims.effects.ClaimEffects.resolveSound(soundName));
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
        String soundName = config.getString("sounds." + soundKey);
        return soundName != null && !soundName.equalsIgnoreCase("NONE") && !soundName.trim().isEmpty();
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

    /** Lower bound of a rental price set by a plot owner ({@code rental.min-price}, money value, price index applied). */
    public long getMinRentalPrice() {
        return Math.max(0L, readMoney("rental.min-price", 100L));
    }

    /** Upper bound of a rental price ({@code rental.max-price}). */
    public long getMaxRentalPrice() {
        return Math.max(getMinRentalPrice(), readMoney("rental.max-price", 1_000_000L));
    }

    private long readMoney(String path, long def) {
        try {
            return dev.lovelace.lovecore.api.economy.MoneyConfig.getScaled(plugin.getConfig(), path, def);
        } catch (Throwable t) {
            // LoveCore API missing: fall back to a plain number
            return plugin.getConfig().getLong(path, def);
        }
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
        List<String> lines = lang.getStringList(path);
        if (lines.isEmpty()) lines = List.of(lang.getString(path, ""));
        List<Component> result = new ArrayList<>();
        for (String value : lines) {
            for (int i = 0; i + 1 < placeholders.length; i += 2)
                value = value.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
            for (String line : value.replace("\\n", "\n").split("\n", -1))
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

    /** Player claims, rentals and clan territories allow PvP; spawn keeps its own configured protection. */
    public boolean isPvpAllowedAt(org.bukkit.Location location) {
        return !isInsideSpawnClaim(location) || getSpawnFlag("pvp");
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
