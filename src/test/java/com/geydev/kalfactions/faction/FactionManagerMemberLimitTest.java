package com.geydev.kalfactions.faction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.geydev.kalfactions.claim.ClaimKey;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class FactionManagerMemberLimitTest {
    @Test
    void ownerCountsAsFirstMember() {
        Fixture fixture = fixture();

        assertEquals(1, fixture.faction().memberCount());
        assertTrue(fixture.faction().isMember(fixture.ownerId()));
    }

    @Test
    void acceptsMembersThroughFour() {
        Fixture fixture = fixture();

        for (int expected = 2; expected <= FactionManager.MAX_FACTION_MEMBERS; expected++) {
            FactionManager.OperationResult result = fixture.manager().addMember(
                fixture.faction().id(),
                UUID.randomUUID()
            );
            assertEquals(FactionManager.Status.SUCCESS, result.status());
            assertEquals(expected, fixture.faction().memberCount());
        }
    }

    @Test
    void rejectsFifthMember() {
        Fixture fixture = fullFixture();

        FactionManager.OperationResult result = fixture.manager().addMember(
            fixture.faction().id(),
            UUID.randomUUID()
        );

        assertEquals(FactionManager.Status.FACTION_FULL, result.status());
        assertEquals(FactionManager.MAX_FACTION_MEMBERS, fixture.faction().memberCount());
    }

    @Test
    void rejectsDuplicateMemberWithoutChangingCount() {
        Fixture fixture = fixture();
        UUID memberId = UUID.randomUUID();
        assertTrue(fixture.manager().addMember(fixture.faction().id(), memberId).successful());

        FactionManager.OperationResult result = fixture.manager().addMember(fixture.faction().id(), memberId);

        assertEquals(FactionManager.Status.PLAYER_ALREADY_MEMBER, result.status());
        assertEquals(2, fixture.faction().memberCount());
    }

    @Test
    @Timeout(5)
    void concurrentAcceptsOnlyFillAvailableSlot() throws Exception {
        Fixture fixture = fixture();
        assertTrue(fixture.manager().addMember(fixture.faction().id(), UUID.randomUUID()).successful());
        assertTrue(fixture.manager().addMember(fixture.faction().id(), UUID.randomUUID()).successful());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<FactionManager.OperationResult> first = executor.submit(
                () -> addAfterSignal(fixture, UUID.randomUUID(), ready, start)
            );
            Future<FactionManager.OperationResult> second = executor.submit(
                () -> addAfterSignal(fixture, UUID.randomUUID(), ready, start)
            );
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            start.countDown();
            List<FactionManager.Status> statuses = List.of(first.get().status(), second.get().status());

            assertEquals(1, statuses.stream().filter(FactionManager.Status.SUCCESS::equals).count());
            assertEquals(1, statuses.stream().filter(FactionManager.Status.FACTION_FULL::equals).count());
            assertEquals(FactionManager.MAX_FACTION_MEMBERS, fixture.faction().memberCount());
        } finally {
            executor.shutdownNow();
        }
    }

    private static FactionManager.OperationResult addAfterSignal(
        Fixture fixture,
        UUID playerId,
        CountDownLatch ready,
        CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        return fixture.manager().addMember(fixture.faction().id(), playerId);
    }

    private static Fixture fullFixture() {
        Fixture fixture = fixture();
        while (fixture.faction().memberCount() < FactionManager.MAX_FACTION_MEMBERS) {
            assertTrue(fixture.manager().addMember(fixture.faction().id(), UUID.randomUUID()).successful());
        }
        return fixture;
    }

    private static Fixture fixture() {
        FactionManager manager = new FactionManager();
        UUID ownerId = UUID.randomUUID();
        FactionManager.OperationResult created = manager.createFaction(
            ownerId,
            "Test Faction",
            new ClaimKey(Level.OVERWORLD, new ChunkPos(0, 0)),
            1
        );
        assertTrue(created.successful());
        Faction faction = manager.getFactionById(created.factionId()).orElseThrow();
        return new Fixture(manager, faction, ownerId);
    }

    private record Fixture(FactionManager manager, Faction faction, UUID ownerId) {
    }
}
