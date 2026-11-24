package me.lovelace.loveclaims.effects;

import me.lovelace.loveclaims.LoveClaims;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single entry point for sounds and particles of claim, gem and anchor events.
 * Every effect is described in config.yml under {@code effects.<key>}; a missing or
 * "NONE" part is silently skipped. Main thread only.
 */
public final class ClaimEffects {
    private static final int DEFAULT_VIEW_RADIUS = 32;
    private static final int DEFAULT_MAX_PARTICLES = 60;

    // Optional values so that misses are cached too (ConcurrentHashMap rejects null values).
    private static final Map<String, Optional<Sound>> SOUND_INDEX = new ConcurrentHashMap<>();

    private final LoveClaims plugin;

    public ClaimEffects(LoveClaims plugin) {
        this.plugin = plugin;
    }

    /** Plays the effect only for the given player, centred on them (GUI feedback). */
    public void playFor(Player player, String key) {
        ConfigurationSection sec = section(key);
        if (sec == null || player == null) return;
        playSoundTo(player, sec);
    }

    /**
     * Sound goes to the viewer (the menu can be opened from anywhere, far from the anchor),
     * particles are shown at the anchor to everyone within the view radius.
     */
    public void playFor(Player viewer, Location at, String key) {
        ConfigurationSection sec = section(key);
        if (sec == null) return;
        if (viewer != null) playSoundTo(viewer, sec);
        spawnParticles(at, sec);
    }

    /** Plays the effect in the world at {@code at}; particles go only to players within the view radius. */
    public void playAt(Location at, String key) {
        ConfigurationSection sec = section(key);
        if (sec == null || at == null || at.getWorld() == null) return;
        Sound sound = readSound(sec);
        if (sound != null) {
            at.getWorld().playSound(at, sound, (float) sec.getDouble("volume", 1.0),
                    (float) sec.getDouble("pitch", 1.0));
        }
        spawnParticles(at, sec);
    }

    private void playSoundTo(Player player, ConfigurationSection sec) {
        Sound sound = readSound(sec);
        if (sound != null) {
            player.playSound(player.getLocation(), sound, (float) sec.getDouble("volume", 1.0),
                    (float) sec.getDouble("pitch", 1.0));
        }
    }

    private void spawnParticles(Location at, ConfigurationSection sec) {
        if (at == null || at.getWorld() == null) return;
        Particle particle = readParticle(sec);
        if (particle == null) return;
        if (particle.getDataType() != Void.class) {
            plugin.getLogger().warning("Effect particle " + sec.getName() + " needs extra data and is skipped: " + particle);
            return;
        }
        int maxParticles = plugin.getConfig().getInt("effects-settings.max-particles-per-event");
        int count = Math.min(Math.max(0, sec.getInt("count", 10)), maxParticles > 0 ? maxParticles : DEFAULT_MAX_PARTICLES);
        if (count == 0) return;
        double spread = sec.getDouble("spread", 0.5);
        int viewRadius = plugin.getConfig().getInt("effects-settings.view-radius");
        double radius = viewRadius > 0 ? viewRadius : DEFAULT_VIEW_RADIUS;
        double radiusSq = radius * radius;
        Location centre = at.clone().add(0.5, 0.5, 0.5);
        for (Player p : at.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(centre) <= radiusSq) {
                p.spawnParticle(particle, centre, count, spread, spread, spread, sec.getDouble("speed", 0.02));
            }
        }
    }

    private ConfigurationSection section(String key) {
        return plugin.getConfig().getConfigurationSection("effects." + key);
    }

    private static Sound readSound(ConfigurationSection sec) {
        String name = sec.getString("sound", "NONE");
        if (name == null || name.isBlank() || name.equalsIgnoreCase("NONE")) return null;
        return resolveSound(name);
    }

    /**
     * Resolves enum-style names (BLOCK_ANVIL_USE) as well as namespaced keys (block.anvil.use).
     * A plain replace of '_' with '.' is wrong for keys like entity.experience_orb.pickup, so the
     * registry is scanned once and indexed by the normalised enum-style name.
     */
    public static Sound resolveSound(String name) {
        if (name == null) return null;
        String norm = name.trim().toUpperCase().replace('.', '_');
        return SOUND_INDEX.computeIfAbsent(norm, n -> {
            for (Sound s : Registry.SOUNDS) {
                if (Registry.SOUNDS.getKey(s).getKey().toUpperCase().replace('.', '_').equals(n)) {
                    return Optional.of(s);
                }
            }
            return Optional.empty();
        }).orElse(null);
    }

    private static Particle readParticle(ConfigurationSection sec) {
        String name = sec.getString("particle", "NONE");
        if (name == null || name.isBlank() || name.equalsIgnoreCase("NONE")) return null;
        return Registry.PARTICLE_TYPE.get(NamespacedKey.minecraft(name.toLowerCase()));
    }
}
