package me.lovelace.loveclaims.integration;

import dev.lovelace.lovecore.api.territory.TerritoryOracle;
import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.model.TrustLevel;
import me.lovelace.loveclans.api.LoveClansAPI;
import me.lovelace.loveclans.model.Clan;
import org.bukkit.Location;

import java.util.Optional;
import java.util.UUID;

/**
 * Реализация {@link TerritoryOracle} поверх реестра приватов LoveClaims.
 *
 * <p>Регистрируется в {@code ServicesManager} с приоритетом выше {@code Normal} и вытесняет
 * рефлексивную реализацию из {@code lovecore-plugin}: LoveClaims знает о своих приватах
 * напрямую, рефлексии ядру для этого больше не нужно.</p>
 */
public final class LoveClaimsTerritoryOracle implements TerritoryOracle {

    private final LoveClaims plugin;

    public LoveClaimsTerritoryOracle(LoveClaims plugin) {
        this.plugin = plugin;
    }

    @Override
    public Optional<Owner> ownerAt(Location location) {
        return plugin.getClaimManager().getClaimAt(location).map(this::toOwner);
    }

    private Owner toOwner(Claim claim) {
        if (claim.isClanTerritory()) {
            String displayName = claim.getOwnerDisplayName() != null ? claim.getOwnerDisplayName() : "Клан";
            return new Owner(OwnerKind.CLAN, claim.getOwnerUuid(), displayName);
        }
        String displayName = claim.getName() != null ? claim.getName() : "Приват";
        return new Owner(OwnerKind.PLAYER_CLAIM, claim.getOwnerUuid(), displayName);
    }

    @Override
    public boolean hostileFor(UUID playerId, Location location) {
        Optional<Claim> claimOpt = plugin.getClaimManager().getClaimAt(location);
        if (claimOpt.isEmpty()) {
            return false;
        }
        Claim claim = claimOpt.get();

        if (claim.isClanTerritory()) {
            return hostileClanTerritory(playerId, claim);
        }

        // Приват без владельца (ещё не сданный в аренду участок) — ничейная земля, не враждебна.
        if (claim.getOwnerUuid() == null || playerId.equals(claim.getOwnerUuid())) {
            return false;
        }
        return claim.getTrust(playerId) == TrustLevel.NONE;
    }

    private boolean hostileClanTerritory(UUID playerId, Claim claim) {
        LoveClansAPI clansApi = LoveClansAPI.getInstance();
        if (clansApi == null) {
            return false;
        }
        Optional<Clan> ownerClan = clansApi.getPlayerClan(claim.getOwnerUuid());
        if (ownerClan.isEmpty() || ownerClan.get().isMember(playerId)) {
            return false;
        }
        if (claim.isUnderSiege()) {
            return true;
        }
        Optional<Clan> playerClan = clansApi.getPlayerClan(playerId);
        return playerClan.isPresent() && clansApi.isAtWar(playerClan.get(), ownerClan.get());
    }
}
