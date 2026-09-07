package com.geydev.kalfactions.war;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.geydev.kalfactions.claim.ClaimKey;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class WarRollbackQueueTest {
    private static final UUID WAR = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final ClaimKey KEY = new ClaimKey(Level.OVERWORLD, 1, 2);

    @Test
    void completionWaitsForTheChunkWriteAcknowledgement() {
        WarRollbackQueue queue = new WarRollbackQueue();
        queue.add(WAR, KEY);
        CompletableFuture<Void> write = new CompletableFuture<>();
        AtomicInteger commits = new AtomicInteger();
        queue.tick(0, 8, (war, key) -> write, (war, key) -> commits.incrementAndGet());
        queue.tick(1, 8, (war, key) -> write, (war, key) -> commits.incrementAndGet());
        assertEquals(0, commits.get());
        assertEquals(1, queue.size());
        write.complete(null);
        queue.tick(2, 8, (war, key) -> write, (war, key) -> commits.incrementAndGet());
        assertEquals(1, commits.get());
        assertEquals(0, queue.size());
    }

    @Test
    void diskFailureKeepsTheTaskAndRetriesWithBackoff() {
        WarRollbackQueue queue = new WarRollbackQueue();
        queue.add(WAR, KEY);
        AtomicInteger commits = new AtomicInteger();
        AtomicInteger attempts = new AtomicInteger();
        queue.tick(0, 1, (war, key) -> {
            attempts.incrementAndGet();
            return CompletableFuture.failedFuture(new IOException("disk unavailable"));
        }, (war, key) -> commits.incrementAndGet());
        queue.tick(1, 1, (war, key) -> CompletableFuture.completedFuture(null), (war, key) -> commits.incrementAndGet());
        queue.tick(100, 1, (war, key) -> {
            attempts.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }, (war, key) -> commits.incrementAndGet());
        assertEquals(1, attempts.get());
        assertEquals(0, commits.get());
        assertEquals(1, queue.size());
        queue.tick(101, 1, (war, key) -> CompletableFuture.completedFuture(null), (war, key) -> commits.incrementAndGet());
        queue.tick(102, 1, (war, key) -> CompletableFuture.completedFuture(null), (war, key) -> commits.incrementAndGet());
        assertEquals(1, commits.get());
    }

    @Test
    void unavailableDimensionDoesNotBlockOtherChunks() {
        WarRollbackQueue queue = new WarRollbackQueue();
        queue.add(WAR, KEY);
        ClaimKey other = new ClaimKey(Level.NETHER, 1, 2);
        queue.add(WAR, other);
        AtomicInteger commits = new AtomicInteger();
        queue.tick(0, 2, (war, key) -> {
            if (key.equals(KEY)) {
                throw new IllegalStateException("dimension unavailable");
            }
            return CompletableFuture.completedFuture(null);
        }, (war, key) -> commits.incrementAndGet());
        queue.tick(1, 2, (war, key) -> CompletableFuture.completedFuture(null), (war, key) -> commits.incrementAndGet());
        assertEquals(1, commits.get());
        assertEquals(1, queue.size());
    }

    @Test
    void duplicateRequestsDoNotRestoreTwiceAndWritesAreBounded() {
        WarRollbackQueue queue = new WarRollbackQueue();
        for (int index = 0; index < 50; index++) {
            ClaimKey key = new ClaimKey(Level.OVERWORLD, index, 0);
            queue.add(WAR, key);
            queue.add(WAR, key);
        }
        AtomicInteger starts = new AtomicInteger();
        for (int tick = 0; tick < 20; tick++) {
            queue.tick(tick, 8, (war, key) -> {
                starts.incrementAndGet();
                return new CompletableFuture<>();
            }, (war, key) -> {});
        }
        assertEquals(50, queue.size());
        assertEquals(16, starts.get());
    }
}
