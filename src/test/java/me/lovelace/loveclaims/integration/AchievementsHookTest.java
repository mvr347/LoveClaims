package me.lovelace.loveclaims.integration;

import dev.lovelace.lovecore.api.achievements.Achievements;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AchievementsHookTest {

    @Test
    void aCreatedClaimAddsOneToTheOwnersCounter() {
        List<String> calls = new ArrayList<>();
        Achievements recorder = (Achievements) Proxy.newProxyInstance(Achievements.class.getClassLoader(),
                new Class<?>[] {Achievements.class}, (proxy, method, args) -> {
                    calls.add(method.getName() + " " + args[1] + " " + args[2]);
                    return null;
                });
        AchievementsHook.Gateway.progress(recorder, UUID.randomUUID());
        assertEquals(List.of("progress claims.created 1.0"), calls);
    }
}
