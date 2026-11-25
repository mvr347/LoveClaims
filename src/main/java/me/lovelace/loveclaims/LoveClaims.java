package me.lovelace.loveclaims;

import me.lovelace.loveclaims.command.LoveClaimsAdminCommand;
import me.lovelace.loveclaims.command.MainCommand;
import me.lovelace.loveclaims.command.RentalCommand;
import me.lovelace.loveclaims.gems.GemFactory;
import me.lovelace.loveclaims.gems.GemRegistry;
import me.lovelace.loveclaims.hook.PAPIExpansion;
import me.lovelace.loveclaims.listener.ChatListener;
import me.lovelace.loveclaims.listener.BedrockGuardListener;
import me.lovelace.loveclaims.listener.DupeGuardListener;
import me.lovelace.loveclaims.listener.EntitySpawnListener;
import me.lovelace.loveclaims.listener.AnchorListener;
import me.lovelace.loveclaims.listener.GemEffectListener;
import me.lovelace.loveclaims.listener.GuiListener;
import me.lovelace.loveclaims.listener.PlayerDataListener;
import me.lovelace.loveclaims.listener.ProtectionListener;
import me.lovelace.loveclaims.listener.PlayerMoveListener;
import me.lovelace.loveclaims.listener.RentalInteractListener;
import me.lovelace.loveclaims.listener.SpawnFoodListener;
import me.lovelace.loveclaims.listeners.GemCraftListener;
import me.lovelace.loveclaims.manager.ConfigManager;
import me.lovelace.loveclaims.manager.ClaimManager;
import me.lovelace.loveclaims.manager.AnchorManager;
import me.lovelace.loveclaims.manager.UserManager;
import me.lovelace.loveclaims.manager.RentalManager;
import me.lovelace.loveclaims.task.AutoDeleteTask;
import me.lovelace.loveclaims.task.GemEffectTask;
import me.lovelace.loveclaims.task.GlobalTickManager;
import me.lovelace.loveclaims.task.ProximityBorderTask;
import me.lovelace.loveclaims.task.RentalExpirationTask;
import me.lovelace.loveclaims.storage.SQLiteStorage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public class LoveClaims extends JavaPlugin {
    private static LoveClaims instance;
    private SQLiteStorage storage;
    private ConfigManager configManager;
    private me.lovelace.loveclaims.effects.ClaimEffects effects;
    private me.lovelace.loveclaims.manager.ClaimMerger claimMerger;
    private me.lovelace.loveclaims.manager.ClaimTeleport claimTeleport;
    private ClaimManager claimManager;
    private AnchorManager anchorManager;
    private UserManager userManager;
    private RentalManager rentalManager;
    private ChatListener chatListener;
    private AnchorListener anchorListener;
    private GemFactory gemFactory;
    private GemRegistry gemRegistry;
    private GemEffectTask gemEffectTask;
    private RentalExpirationTask rentalExpirationTask;
    private AutoDeleteTask autoDeleteTask;
    private GlobalTickManager globalTickManager;
    private ProximityBorderTask proximityBorderTask;

    @Override
    public void onEnable() {
        // Apply the bundled style update before any module reads its documents.
        try {
            Class.forName("dev.lovelace.lovecore.gui.GuiStyles")
                    .getMethod("migrate", org.bukkit.plugin.java.JavaPlugin.class).invoke(null, this);
        } catch (java.lang.reflect.InvocationTargetException error) {
            throw new IllegalStateException("Обновление оформления остановлено; проверьте конфиги и резервные копии", error.getCause());
        } catch (ReflectiveOperationException error) {
            getLogger().warning("Оформление не обновлено: обновите LoveCore. Причина: " + error);
        }

        instance = this;
        getLogger().info("LoveClaims loading...");

        try {
            // 1. Загрузка конфигурации
            this.configManager = new ConfigManager(this);
            this.configManager.loadAll();
            getLogger().info("Config loaded successfully!");

            // 2. Инициализация БД и фабрики гемов
            this.effects = new me.lovelace.loveclaims.effects.ClaimEffects(this);
            this.gemFactory = new GemFactory(this);
            this.storage = new SQLiteStorage(this);
            this.storage.initDatabase().join();
            getLogger().info("SQLite initialized successfully!");

            // 3. Создание менеджеров
            this.chatListener = new ChatListener(this);
            this.anchorManager = new AnchorManager(this);
            this.gemRegistry = new GemRegistry(this, this.gemFactory);
            this.gemRegistry.refreshWhenItemsAdderIsReady();
            this.claimManager = new ClaimManager(this);
            this.userManager = new UserManager(this);
            this.rentalManager = new RentalManager(this);
            this.claimMerger = new me.lovelace.loveclaims.manager.ClaimMerger(this);
            this.claimTeleport = new me.lovelace.loveclaims.manager.ClaimTeleport(this);
            getServer().getPluginManager().registerEvents(claimTeleport, this);

            // 4. Инициализация API
            me.lovelace.loveclaims.api.LoveClaimsAPI.init(this);
            getLogger().info("API initialized!");

            // 5. Загрузка клеймов из базы данных
            this.storage.loadAllClaims()
                    .thenAccept(claims -> {
                        claimManager.loadClaims(claims);
                        rentalManager.loadPlots(claims);
                        getLogger().info("Loaded " + claims.size() + " claims from database.");
                    })
                    .join();

            // 6. Регистрация команд
            MainCommand mainCommand = new MainCommand(this);
            getCommand("ac").setExecutor(mainCommand);
            getCommand("ac").setTabCompleter(mainCommand);
            RentalCommand rentalCommand = new RentalCommand(this);
            getCommand("rental").setExecutor(rentalCommand);
            getCommand("rental").setTabCompleter(rentalCommand);
            LoveClaimsAdminCommand adminCommand = new LoveClaimsAdminCommand(this);
            getCommand("loveclaimsadmin").setExecutor(adminCommand);
            getCommand("loveclaimsadmin").setTabCompleter(adminCommand);
            getLogger().info("Commands registered!");

            // 6. Регистрация слушателей
            PluginManager pm = getServer().getPluginManager();
            getServer().getPluginManager().registerEvents(new me.lovelace.loveclaims.listener.LandlordListener(this), this);
            this.anchorListener = new AnchorListener(this);

            pm.registerEvents(this.anchorListener, this);
            pm.registerEvents(this.chatListener, this);
            pm.registerEvents(new ProtectionListener(this), this);
            pm.registerEvents(new me.lovelace.loveclaims.listener.PropertyProtectionListener(this), this);
            pm.registerEvents(new me.lovelace.loveclaims.listener.AutomationProtectionListener(this), this);
            pm.registerEvents(new me.lovelace.loveclaims.listener.ContainerProtectionListener(this), this);
            pm.registerEvents(new me.lovelace.loveclaims.listener.WorldClaimsListener(this), this);
            pm.registerEvents(new GuiListener(this), this);
            pm.registerEvents(new PlayerDataListener(this), this);
            pm.registerEvents(new PlayerMoveListener(this), this);
            pm.registerEvents(new RentalInteractListener(this), this);
            pm.registerEvents(new SpawnFoodListener(this), this);
            pm.registerEvents(new EntitySpawnListener(this), this);
            pm.registerEvents(new DupeGuardListener(this), this);
            pm.registerEvents(new BedrockGuardListener(this), this);
            pm.registerEvents(new GemCraftListener(this, this.gemRegistry, this.gemFactory), this);
            pm.registerEvents(new GemEffectListener(this), this);
            getLogger().info("Listeners registered!");

            // 7. Проверка и регистрация PAPI
            if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
                new PAPIExpansion(this).register();
                getLogger().info("PlaceholderAPI integration enabled!");
            }

            // 7.1 Регистрация TerritoryOracle в LoveCore, если ядро установлено
            if (getServer().getPluginManager().getPlugin("LoveCore") != null) {
                try {
                    getServer().getServicesManager().register(
                            dev.lovelace.lovecore.api.territory.TerritoryOracle.class,
                            new me.lovelace.loveclaims.integration.LoveClaimsTerritoryOracle(this),
                            this,
                            org.bukkit.plugin.ServicePriority.High);
                    getLogger().info("LoveCore integration: TerritoryOracle registered.");
                } catch (Throwable t) {
                    getLogger().warning("Failed to register TerritoryOracle with LoveCore: " + t.getMessage());
                }
            }

            // 8. Старт фоновых задач
            this.globalTickManager = new GlobalTickManager(this);
            this.globalTickManager.start();

            autoDeleteTask = new AutoDeleteTask(this);
            autoDeleteTask.start();

            this.rentalExpirationTask = new RentalExpirationTask(this);
            this.rentalExpirationTask.start();

            this.proximityBorderTask = new ProximityBorderTask(this);
            this.proximityBorderTask.start();

            this.gemEffectTask = new GemEffectTask(this);
            this.gemEffectTask.start();

            getLogger().info("LoveClaims enabled successfully!");

        } catch (Exception e) {
            getLogger().severe("Failed to enable LoveClaims! " + e.getMessage());
            getLogger().log(java.util.logging.Level.SEVERE, "Полная трассировка ошибки:", e);
            this.setEnabled(false);
        }
    }

    @Override
    public void onDisable() {
        if (claimTeleport != null) claimTeleport.shutdown();
        HandlerList.unregisterAll(this);
        getLogger().info("Disabling LoveClaims...");

        try {
            // 1. Остановка всех задач
            if (globalTickManager != null) globalTickManager.cancel();
            if (rentalExpirationTask != null) rentalExpirationTask.cancel();
            if (autoDeleteTask != null && !autoDeleteTask.isCancelled()) autoDeleteTask.cancel();
            if (proximityBorderTask != null) proximityBorderTask.cancel();
            if (gemEffectTask != null) gemEffectTask.cancel();
            if (gemRegistry != null) gemRegistry.unregisterRecipes();
            me.lovelace.loveclaims.task.ParticleBorder.cancelAll();
            // Revert pending anchor previews and hide borders so no display entity or fake block outlives the plugin.
            if (anchorListener != null) {
                for (var pending : new java.util.ArrayList<>(anchorListener.getPendingClaims().values())) {
                    try { pending.previewTask().revert(); } catch (Exception ex) { getLogger().warning("Preview revert failed: " + ex.getMessage()); }
                }
            }
            me.lovelace.loveclaims.task.BorderDisplayTask.hideAll();
            getLogger().info("Tasks stopped!");

            // 2. СИНХРОННОЕ Сохранение всех данных пользователей
            if (userManager != null) {
                userManager.saveUsersSync();
                getLogger().info("User data saved synchronously!");
            }

            // 3. Очистка кэшей Caffeine
            if (claimManager != null) {
                claimManager.invalidateAllCaches();
                getLogger().info("Caffeine caches cleared!");
            }

            // 4. Сохранение всех изменённых клеймов синхронно
            if (claimManager != null && storage != null) {
                storage.saveAllClaimsSync(claimManager.getAllClaims());
                getLogger().info("Claims saved synchronously!");
            }

            // 5. Только потом закрываем хранилище и убиваем пулы потоков
            if (storage != null) {
                storage.close();
                getLogger().info("Database closed safely!");
            }

        } catch (Exception e) {
            getLogger().severe("Error during disable: " + e.getMessage());
            getLogger().log(java.util.logging.Level.SEVERE, "Полная трассировка ошибки:", e);
        } finally {
            getLogger().info("LoveClaims disabled successfully!");
            instance = null;
        }
    }

    public static LoveClaims getInstance() { return instance; }

    // Getters
    public SQLiteStorage getStorage() { return storage; }
    public ConfigManager getConfigManager() { return configManager; }
    public me.lovelace.loveclaims.effects.ClaimEffects getEffects() { return effects; }
    public me.lovelace.loveclaims.manager.ClaimMerger getClaimMerger() { return claimMerger; }
    public me.lovelace.loveclaims.manager.ClaimTeleport getClaimTeleport() { return claimTeleport; }
    public ClaimManager getClaimManager() { return claimManager; }
    public AnchorManager getAnchorManager() { return anchorManager; }
    public UserManager getUserManager() { return userManager; }
    public RentalManager getRentalManager() { return rentalManager; }
    public ChatListener getChatListener() { return chatListener; }
    public AnchorListener getAnchorListener() { return anchorListener; }
    public ProximityBorderTask getProximityBorderTask() { return proximityBorderTask; }
    public GemFactory getGemFactory() { return gemFactory; }
    public GemRegistry getGemRegistry() { return gemRegistry; }
    public GemEffectTask getGemEffectTask() { return gemEffectTask; }
}
