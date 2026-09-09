package me.lovelace.loveclaims.task;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.lovelace.loveclaims.LoveClaims;

import java.util.concurrent.TimeUnit;

/**
 * Глобальный менеджер тиков для периодического автосохранения.
 * Использует Paper Regionized Scheduler для лучшей производительности.
 */
public class GlobalTickManager {
    private final LoveClaims plugin;
    private long tickCounter = 0;
    private ScheduledTask task;

    public GlobalTickManager(LoveClaims plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin, scheduler -> {
            tickCounter++;

            // АВТОСОХРАНЕНИЕ В БАЗУ ДАННЫХ: Каждые 5 минут (6000 тиков = 300 секунд)
            if (tickCounter % 6000 == 0) {
                plugin.getStorage().batchSaveAllAsync(plugin.getClaimManager().getAllClaims());
            }

        }, 50, 50, TimeUnit.MILLISECONDS);
    }

    public void cancel() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }
}