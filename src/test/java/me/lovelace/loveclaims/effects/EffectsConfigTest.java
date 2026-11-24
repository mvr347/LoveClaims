package me.lovelace.loveclaims.effects;

import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.gems.GemOverload;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.*;

/** The shipped config must name only sounds and particles that exist: a typo would silently play nothing. */
class EffectsConfigTest {
    ServerMock server;
    LoveClaims plugin;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        MockBukkit.createMockPlugin("LoveCore");
        plugin = MockBukkit.load(LoveClaims.class);
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    @Test
    void everyConfiguredEffectSoundAndParticleResolves() {
        ConfigurationSection effects = plugin.getConfig().getConfigurationSection("effects");
        assertNotNull(effects);
        for (String key : effects.getKeys(false)) {
            ConfigurationSection sec = effects.getConfigurationSection(key);
            String sound = sec.getString("sound", "NONE");
            if (!sound.equalsIgnoreCase("NONE")) {
                assertNotNull(ClaimEffects.resolveSound(sound), "effects." + key + ".sound = " + sound);
            }
            String particle = sec.getString("particle", "NONE");
            if (!particle.equalsIgnoreCase("NONE")) {
                Particle p = Registry.PARTICLE_TYPE.get(NamespacedKey.minecraft(particle.toLowerCase()));
                assertNotNull(p, "effects." + key + ".particle = " + particle);
                assertEquals(Void.class, p.getDataType(), "particle needs data and would be skipped: " + particle);
            }
        }
    }

    @Test
    void everyLegacySoundKeyResolves() {
        ConfigurationSection sounds = plugin.getConfig().getConfigurationSection("sounds");
        assertNotNull(sounds);
        for (String key : sounds.getKeys(false)) {
            assertNotNull(ClaimEffects.resolveSound(sounds.getString(key)), "sounds." + key);
        }
    }

    @Test
    void overloadDefaultsComeFromTheJarConfig() {
        assertEquals(2, GemOverload.explodeThreshold());
        assertEquals(0.5, GemOverload.multiplierPerGap(), 1e-9);
    }
}
