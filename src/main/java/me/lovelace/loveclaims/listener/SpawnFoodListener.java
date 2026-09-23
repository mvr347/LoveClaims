package me.lovelace.loveclaims.listener;

import me.lovelace.loveclaims.LoveClaims;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Замедляет естественное истощение голода на территории спавна - см.
 * spawn-claim.food-depletion-reduction в config.yml.
 *
 * Не трогает события, где foodLevel растёт или остаётся прежним (еда, регенерация,
 * сатурация) - только настоящее уменьшение. foodLevel - целое число (0-20), поэтому
 * плавно "срезать" дельту на процент нельзя (шаг всегда 1); вместо этого часть событий
 * уменьшения гасится вероятностно, что на длинной дистанции даёт ровно нужное среднее
 * замедление, не ломая дискретность самого голода.
 */
public class SpawnFoodListener implements Listener {
    private final LoveClaims plugin;

    public SpawnFoodListener(LoveClaims plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // Интересует только реальное уменьшение голода - рост (еда) не трогаем.
        if (event.getFoodLevel() >= player.getFoodLevel()) return;

        if (!plugin.getConfigManager().isInsideSpawnClaim(player.getLocation())) return;

        double reduction = plugin.getConfigManager().getSpawnFoodDepletionReduction();
        if (reduction <= 0.0) return;
        if (reduction >= 1.0) {
            event.setCancelled(true);
            return;
        }

        if (ThreadLocalRandom.current().nextDouble() < reduction) {
            event.setCancelled(true);
        }
    }
}
