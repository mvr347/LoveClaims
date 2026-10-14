package me.lovelace.loveclaims.listener;

import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.model.ClaimFlag;
import me.lovelace.loveclaims.model.TrustLevel;
import me.lovelace.loveclaims.util.Notify;
import me.lovelace.loveclaims.api.event.ClaimBorderCrossEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerMoveListener implements Listener {
    private final LoveClaims plugin;
    private final Map<UUID, UUID> lastClaim = new ConcurrentHashMap<>();

    public PlayerMoveListener(LoveClaims plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();

        // Оптимизация: игнорируем движения головой, проверяем только смену блока
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        handleMovement(event.getPlayer(), from, to, event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        handleMovement(event.getPlayer(), event.getFrom(), event.getTo(), null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastClaim.remove(event.getPlayer().getUniqueId());
    }

    private void handleMovement(Player player, Location from, Location to, PlayerMoveEvent event) {
        UUID playerId = player.getUniqueId();
        Optional<Claim> claimOpt = plugin.getClaimManager().getClaimAt(to);
        UUID currentClaimId = claimOpt.map(Claim::getId).orElse(null);
        UUID previousClaimId = lastClaim.get(playerId);

        // Игрок зашел в новый приват
        if (currentClaimId != null && !currentClaimId.equals(previousClaimId)) {
            Claim claim = claimOpt.get();

            lastClaim.put(playerId, currentClaimId);
            if (!fireBorderEvent(player, claim, true)) {
                sendClaimMessage(player, claim, true);
            }

            if (playerId.equals(claim.getOwnerUuid()) || claim.getMembers().containsKey(playerId)) {
                claim.updateLastActive();
            }

        }
        else if (currentClaimId == null && previousClaimId != null) {
            lastClaim.remove(playerId);
            plugin.getClaimManager().getClaimById(previousClaimId).ifPresent(claim -> {
                if (!fireBorderEvent(player, claim, false)) {
                    sendClaimMessage(player, claim, false);
                }
            });
        }
    }

    /** @return true when a listener (e.g. LoveClans) handled the crossing and LoveClaims must stay silent. */
    private boolean fireBorderEvent(Player player, Claim claim, boolean enter) {
        ClaimBorderCrossEvent event = new ClaimBorderCrossEvent(player, claim.getId(), enter, claim.isClanTerritory());
        Bukkit.getPluginManager().callEvent(event);
        return event.isHandled();
    }

    private void sendClaimMessage(Player player, Claim claim, boolean enter) {
        String ownerName;
        String claimDisplayName;

        if (claim.isClanTerritory()) {
            // Для клановых территорий используем имя, установленное через API, или дефолтное из конфига
            claimDisplayName = claim.getName() != null ? claim.getName() : plugin.getConfigManager().getString("misc.clan-claim-display-name", "Клановая Территория");
            // Настоящее имя/тег клана-владельца передаётся из LoveClans при создании территории
            // (см. LoveClaimsAPI#createClanClaim). Если его почему-то нет (старая территория,
            // созданная до этого поля) - используем дефолтную подпись из конфига.
            String realOwnerName = claim.getOwnerDisplayName();
            ownerName = realOwnerName != null && !realOwnerName.isBlank()
                    ? realOwnerName
                    : plugin.getConfigManager().getString("misc.clan-owner-display-name", "Клан");
        } else {
            // Логика для обычных приватов
            ownerName = "Сервер";
            if (claim.getOwnerUuid() != null) {
                String fetchedName = Bukkit.getOfflinePlayer(claim.getOwnerUuid()).getName();
                if (fetchedName != null) ownerName = fetchedName;
            }
            claimDisplayName = claim.getName() != null ? claim.getName() : "Участок";
        }

        // One message through LoveNotify: the player's own settings choose title or action bar,
        // overflow goes to chat. On enter the owner line is appended to the claim name.
        Component message = enter
                ? plugin.getConfigManager().getComponent("title-enter", "name", claimDisplayName, "owner", ownerName)
                    .append(Component.space())
                    .append(plugin.getConfigManager().getComponent("actionbar-enter", "name", claimDisplayName, "owner", ownerName))
                : plugin.getConfigManager().getComponent("title-leave", "name", claimDisplayName, "owner", ownerName);
        Notify.title(player, message);
    }
}
