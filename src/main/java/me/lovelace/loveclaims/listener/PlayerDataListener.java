package me.lovelace.loveclaims.listener;
import me.lovelace.loveclaims.LoveClaims;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class PlayerDataListener implements Listener {
    private final LoveClaims plugin;

    public PlayerDataListener(LoveClaims plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        java.util.UUID playerUuid = player.getUniqueId();
        plugin.getStorage().loadUserData(playerUuid)
                .thenAccept(userData -> {
                    plugin.getUserManager().getUserData(playerUuid).loadFrom(userData);
                });

        // Обновляем активность приватов игрока при входе на сервер
        plugin.getClaimManager().getClaimsByOwner(playerUuid).forEach(me.lovelace.loveclaims.model.Claim::updateLastActive);
        plugin.getClaimManager().getClaimsByPlayer(playerUuid).forEach(me.lovelace.loveclaims.model.Claim::updateLastActive);

        if (isAuthenticated(player)) {
            giveStarterAnchorIfFirstJoin(player);
        }
    }

    @EventHandler
    public void onAuthenticated(dev.lovelace.lovecore.api.auth.PlayerAuthenticatedEvent event) {
        giveStarterAnchorIfFirstJoin(event.player());
    }

    private void giveStarterAnchorIfFirstJoin(Player player) {
        if (player.hasPlayedBefore()) return;
        ItemStack starterAnchor = plugin.getAnchorManager().createAnchorItem("tier-1");
        if (starterAnchor != null) {
            player.getInventory().addItem(starterAnchor);
            player.sendMessage(plugin.getConfigManager().getMessage("anchor-received"));
        }
    }

    /**
     * Не кэшируем Optional<AuthOracle> — сосед может зарегистрировать реализацию позже,
     * см. LoveCore.service(...) javadoc в LoveCore. Если LoveAuth не установлен, стартовый
     * набор выдаётся сразу на join, как и раньше.
     */
    private boolean isAuthenticated(Player player) {
        return dev.lovelace.lovecore.api.LoveCore.service(dev.lovelace.lovecore.api.auth.AuthOracle.class)
                .map(oracle -> oracle.isAuthenticated(player.getUniqueId()))
                .orElse(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        java.util.UUID uuid = player.getUniqueId();

        // 1. Асинхронное сохранение данных при выходе
        me.lovelace.loveclaims.model.UserData data = plugin.getUserManager().getUserData(uuid);
        plugin.getStorage().saveUserDataAsync(data);
        plugin.getUserManager().unloadUser(uuid);

        // 2. Очистка кэшей, тасков и утечек памяти (Memory Leaks)
        plugin.getAnchorListener().cleanupPlayer(player);
        plugin.getChatListener().cleanupPlayer(player);
        me.lovelace.loveclaims.task.BorderDisplayTask.hideBorder(player);

        // Очистка новых найденных утечек
        me.lovelace.loveclaims.gui.MainClaimGUI.removeCooldown(uuid);
        plugin.getClaimManager().removeInvite(uuid);
    }
}
