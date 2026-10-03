package me.lovelace.loveclaims.config;

import dev.lovelace.lovecore.api.economy.MoneyParser;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** Money keys of config.yml and anchors.yml must parse: a typo must fail the build, not silently make plots free. */
class MoneyKeysTest {

    private static YamlConfiguration load(String name) throws Exception {
        try (Reader r = new InputStreamReader(MoneyKeysTest.class.getResourceAsStream("/" + name), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(r);
        }
    }

    private static long money(Object raw) {
        if (raw instanceof Number n) return n.longValue();
        return MoneyParser.parse(String.valueOf(raw), MoneyParser.STANDARD);
    }

    @Test
    void rentalLimitsAreSane() throws Exception {
        YamlConfiguration cfg = load("config.yml");
        long min = money(cfg.get("rental.min-price"));
        long max = money(cfg.get("rental.max-price"));
        assertEquals(100L, min);
        assertTrue(max > min);
        assertTrue(cfg.getDouble("economy.migration.factor") > 0);
    }

    @Test
    void anchorCostsGrowWithTier() throws Exception {
        YamlConfiguration anchors = load("anchors.yml");
        long previous = -1;
        for (String tier : anchors.getConfigurationSection("tiers").getKeys(false)) {
            long cost = money(anchors.get("tiers." + tier + ".create-cost"));
            assertTrue(cost >= previous, tier + " must not be cheaper than the tier below");
            previous = cost;
        }
        assertEquals(60_000L, previous);
    }
}
