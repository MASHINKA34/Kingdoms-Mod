package com.geydev.kalfactions.scout;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

final class ScoutDeliveryGateTest {
    @Test
    void cancellationPreventsArchiveCommit() {
        ScoutDeliveryGate gate = new ScoutDeliveryGate();
        assertTrue(gate.cancel());
        assertTrue(gate.cancel());
        assertFalse(gate.commit());
    }

    @Test
    void committedArchiveCannotBeCancelledOrCommittedTwice() {
        ScoutDeliveryGate gate = new ScoutDeliveryGate();
        assertTrue(gate.commit());
        assertFalse(gate.cancel());
        assertFalse(gate.commit());
    }

    @Test
    void cancellationRacingWithCommitHasExactlyOneWinner() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int attempt = 0; attempt < 100; attempt++) {
                ScoutDeliveryGate gate = new ScoutDeliveryGate();
                CountDownLatch start = new CountDownLatch(1);
                var cancel = executor.submit(() -> {
                    start.await();
                    return gate.cancel();
                });
                var commit = executor.submit(() -> {
                    start.await();
                    return gate.commit();
                });
                start.countDown();
                assertTrue(cancel.get() ^ commit.get());
            }
        }
    }
}
