package me.lovelace.loveclaims.integration;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.achievements.Achievements;

import java.util.UUID;

/**
 * Feeds LoveCore's shared achievements with created claims. Looked up on every call; no LoveCore, an older one
 * without the contract ({@link LinkageError}) or no service means nothing is recorded. Main thread only.
 */
public final class AchievementsHook {

    /** Counter the catalogue's land achievements watch. */
    public static final String CLAIMS_CREATED = "claims.created";

    private AchievementsHook() {
    }

    public static void claimCreated(UUID owner) {
        try {
            LoveCore.service(Achievements.class).ifPresent(service -> Gateway.progress(service, owner));
        } catch (LinkageError missingContract) {
            // an older LoveCore: nothing to record
        }
    }

    /** Isolated so the contract class is only resolved inside the try/catch above. */
    static final class Gateway {
        private Gateway() {
        }

        static void progress(Achievements service, UUID owner) {
            service.progress(owner, CLAIMS_CREATED, 1);
        }
    }
}
