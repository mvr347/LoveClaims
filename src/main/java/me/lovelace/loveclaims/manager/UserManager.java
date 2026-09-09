package me.lovelace.loveclaims.manager;

import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.model.UserData;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер пользовательских данных (лимиты участников, слотов приватов).
 */
public class UserManager {
    private final LoveClaims plugin;
    private final Map<UUID, UserData> users = new ConcurrentHashMap<>();

    public UserManager(LoveClaims plugin) {
        this.plugin = plugin;
    }

    /**
     * Получить данные пользователя.
     * Сначала проверяет кэш Caffeine, затем карту памяти.
     */
    public UserData getUserData(UUID uuid) {
        UserData cached = plugin.getClaimManager().getCachedUserData(uuid);
        if (cached != null) {
            return cached;
        }

        UserData data = users.computeIfAbsent(uuid, UserData::new);
        plugin.getClaimManager().cacheUserData(uuid, data);
        return data;
    }

    /**
     * Загрузить данные всех подключенных пользователей.
     */
    public void loadUsers() {
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            plugin.getStorage().loadUserData(p.getUniqueId()).thenAccept(data -> {
                getUserData(p.getUniqueId()).loadFrom(data);
            });
        }
    }

    /**
     * Сохранить данные всех пользователей (асинхронно).
     */
    public void saveUsers() {
        for (UserData data : users.values()) {
            plugin.getStorage().saveUserDataAsync(data);
        }
    }

    /**
     * Сохранить данные всех пользователей (синхронно при выключении).
     */
    public void saveUsersSync() {
        plugin.getStorage().saveAllUserDataSync(users.values());
    }

    /**
     * Выгрузить пользователя из памяти.
     */
    public void unloadUser(UUID uuid) {
        users.remove(uuid);
    }
}
