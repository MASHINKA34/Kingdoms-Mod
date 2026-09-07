package com.geydev.kalfactions.war;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

final class WarRollbackQueue {
    private static final int MAX_IN_FLIGHT = 16;
    private final Map<Task, Pending> tasks = new LinkedHashMap<>();

    void add(UUID warId, ClaimKey key) {
        tasks.putIfAbsent(new Task(warId, key), new Pending());
    }

    void tick(
            long now, int budget,
            BiFunction<UUID, ClaimKey, CompletableFuture<Void>> restore,
            BiConsumer<UUID, ClaimKey> committed
    ) {
        int inFlight = (int) tasks.values().stream().filter(pending -> pending.write != null).count();
        var iterator = tasks.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Task task = entry.getKey();
            Pending pending = entry.getValue();
            if (pending.write != null) {
                if (!pending.write.isDone()) {
                    continue;
                }
                inFlight--;
                try {
                    pending.write.join();
                    committed.accept(task.warId(), task.key());
                    iterator.remove();
                } catch (RuntimeException exception) {
                    failed(task, pending, now, exception);
                }
                continue;
            }
            if (budget <= 0 || inFlight >= MAX_IN_FLIGHT || now < pending.retryAt) {
                continue;
            }
            budget--;
            try {
                pending.write = restore.apply(task.warId(), task.key());
                inFlight++;
            } catch (RuntimeException exception) {
                failed(task, pending, now, exception);
            }
        }
    }

    private static void failed(Task task, Pending pending, long now, RuntimeException exception) {
        pending.write = null;
        pending.failures++;
        pending.retryAt = now + Math.min(1200L, 100L * pending.failures);
        KalFactions.LOGGER.error("War {} rollback for {} failed; snapshot retained for retry",
                task.warId(), task.key(), exception);
    }

    int size() {
        return tasks.size();
    }

    private record Task(UUID warId, ClaimKey key) {
    }

    private static final class Pending {
        private CompletableFuture<Void> write;
        private long retryAt;
        private int failures;
    }
}
