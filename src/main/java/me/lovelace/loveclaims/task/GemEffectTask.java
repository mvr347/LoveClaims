package me.lovelace.loveclaims.task;

import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.gems.GemRegistry;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.model.TrustLevel;
import me.lovelace.loveclaims.util.ClaimBypass;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Periodically (every 40 ticks) applies and removes gem PotionEffects for players inside claims.
 */
public final class GemEffectTask {
    private static final long INTERVAL_TICKS = 40L;
    private static final int EFFECT_DURATION_TICKS = 100;

    private final LoveClaims plugin;
    private final Map<UUID, Map<PotionEffectType, Integer>> activeEffects = new ConcurrentHashMap<>();
    private BukkitTask task;

    public GemEffectTask(LoveClaims plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, INTERVAL_TICKS, INTERVAL_TICKS);
    }

    public void cancel() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        for (UUID uuid : new ArrayList<>(activeEffects.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                clearPlayerEffects(player);
            }
        }
        activeEffects.clear();
    }

    public void clearPlayerEffects(Player player) {
        if (player == null) return;
        Map<PotionEffectType, Integer> tracked = activeEffects.remove(player.getUniqueId());
        if (tracked == null) return;
        tracked.forEach((type, amp) -> removeIfFromGem(player, type, amp));
    }

    public void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            tickPlayer(player);
        }
    }

    public void tickPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        Map<PotionEffectType, Integer> desired = computeDesiredEffects(player);
        Map<PotionEffectType, Integer> previouslyApplied = activeEffects.getOrDefault(uuid, Map.of());

        // Remove effects no longer granted by current claim
        for (Map.Entry<PotionEffectType, Integer> entry : previouslyApplied.entrySet()) {
            PotionEffectType type = entry.getKey();
            if (!desired.containsKey(type) || !Objects.equals(desired.get(type), entry.getValue())) {
                removeIfFromGem(player, type, entry.getValue());
            }
        }

        if (desired.isEmpty()) {
            activeEffects.remove(uuid);
            return;
        }

        Map<PotionEffectType, Integer> appliedNow = new HashMap<>();
        for (Map.Entry<PotionEffectType, Integer> entry : desired.entrySet()) {
            PotionEffectType type = entry.getKey();
            int amp = Math.max(0, entry.getValue());
            PotionEffect existing = player.getPotionEffect(type);
            if (existing != null && (existing.getAmplifier() > amp || existing.getDuration() > EFFECT_DURATION_TICKS + 20)) {
                // Keep stronger or external long-duration effect untouched
                continue;
            }
            player.addPotionEffect(new PotionEffect(type, EFFECT_DURATION_TICKS, amp, true, false, true));
            appliedNow.put(type, amp);
        }

        if (appliedNow.isEmpty()) {
            activeEffects.remove(uuid);
        } else {
            activeEffects.put(uuid, appliedNow);
        }
    }

    private Map<PotionEffectType, Integer> computeDesiredEffects(Player player) {
        Optional<Claim> claimOpt = plugin.getClaimManager().getClaimAt(player.getLocation());
        if (claimOpt.isEmpty()) return Map.of();

        Claim claim = claimOpt.get();
        if (claim.isClanTerritory() || claim.isRentalPlot()) return Map.of();

        Map<PotionEffectType, Integer> effects = claim.getModifiers().potionEffects();
        if (effects.isEmpty()) return Map.of();

        UUID uuid = player.getUniqueId();
        boolean isParticipant = claim.isOwner(uuid) || claim.getTrust(uuid) != TrustLevel.NONE;
        boolean bypass = ClaimBypass.allowed(player);

        Map<PotionEffectType, Integer> result = new HashMap<>();
        for (Map.Entry<PotionEffectType, Integer> entry : effects.entrySet()) {
            PotionEffectType type = entry.getKey();
            boolean harmful = GemRegistry.isHarmful(type);
            if (harmful) {
                if (!bypass) {
                    result.put(type, entry.getValue());
                }
            } else if (isParticipant) {
                result.put(type, entry.getValue());
            }
        }
        return result;
    }

    private static void removeIfFromGem(Player player, PotionEffectType type, int trackedAmp) {
        PotionEffect active = player.getPotionEffect(type);
        if (active != null && active.getAmplifier() == trackedAmp && active.getDuration() <= EFFECT_DURATION_TICKS + 20) {
            player.removePotionEffect(type);
        }
    }
}
