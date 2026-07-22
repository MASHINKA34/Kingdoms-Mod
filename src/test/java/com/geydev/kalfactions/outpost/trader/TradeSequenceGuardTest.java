package com.geydev.kalfactions.outpost.trader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class TradeSequenceGuardTest {
    @Test
    void replayIsRejected() {
        TradeSequenceGuard guard = new TradeSequenceGuard();

        assertTrue(guard.accept(1L));
        assertFalse(guard.accept(1L));
        assertFalse(guard.accept(0L));
        assertTrue(guard.accept(2L));
    }

    @Test
    void concurrentDuplicateExecutesOnce() throws Exception {
        TradeSequenceGuard guard = new TradeSequenceGuard();
        AtomicInteger accepted = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 8; i++) {
                executor.submit(() -> {
                    start.await();
                    if (guard.accept(1L)) {
                        accepted.incrementAndGet();
                    }
                    return null;
                });
            }
            start.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }

        assertEquals(1, accepted.get());
    }
}
