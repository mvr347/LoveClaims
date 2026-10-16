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
    private ScheduledTask task;

    public GlobalTickManager(LoveClaims plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // One 5-minute timer instead of a 50 ms no-op tick counter that only ever fired every 6000th run.
        task = plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin,
                scheduler -> plugin.getStorage().batchSaveAllAsync(plugin.getClaimManager().getAllClaims()),
                5, 5, TimeUnit.MINUTES);
    }

    public void cancel() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }
}