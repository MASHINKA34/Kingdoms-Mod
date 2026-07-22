package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.entity.SellerTraderEntity;
import com.geydev.kalfactions.outpost.trader.SellerTraderRole;
import com.geydev.kalfactions.outpost.trader.TraderAccessPolicy;
import com.geydev.kalfactions.outpost.trader.TraderSpawnSafety;
import com.geydev.kalfactions.outpost.trader.TraderWorldData;
import com.geydev.kalfactions.outpost.trader.TraderLifecycle;
import com.geydev.kalfactions.registry.ModEntities;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TraderLifecycleGameTests {
    @GameTest(template = "empty")
    public static void sellerRoleNbtRoundTrip(GameTestHelper helper) {
        SellerTraderEntity original = ModEntities.SELLER_TRADER.get().create(helper.getLevel());
        helper.assertTrue(original != null, "Seller entity could not be created");
        UUID eventId = UUID.randomUUID();
        UUID factionId = UUID.randomUUID();
        original.setTraderRole(SellerTraderRole.WANDERING);
        original.setEventId(eventId);
        original.setTargetFactionId(factionId);
        original.setExpiresAtMillis(123_456L);
        CompoundTag tag = new CompoundTag();
        original.addAdditionalSaveData(tag);
        SellerTraderEntity loaded = ModEntities.SELLER_TRADER.get().create(helper.getLevel());
        helper.assertTrue(loaded != null, "Second seller entity could not be created");
        loaded.readAdditionalSaveData(tag);

        helper.assertTrue(loaded.traderRole() == SellerTraderRole.WANDERING, "Role was not persisted");
        helper.assertTrue(loaded.eventId().filter(eventId::equals).isPresent(), "Event ID was not persisted");
        helper.assertTrue(loaded.targetFactionId().filter(factionId::equals).isPresent(), "Faction was not persisted");
        helper.assertTrue(loaded.expiresAtMillis() == 123_456L, "Expiry was not persisted");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void spawnSafetyRejectsDangerousFloor(GameTestHelper helper) {
        BlockPos floorRelative = new BlockPos(1, 1, 1);
        helper.setBlock(floorRelative, Blocks.MAGMA_BLOCK);
        BlockPos spawn = helper.absolutePos(floorRelative.above());
        helper.assertTrue(!TraderSpawnSafety.isSafe(helper.getLevel(), spawn), "Dangerous floor was accepted");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void spawnSafetyAcceptsClearedStonePlatform(GameTestHelper helper) {
        BlockPos floor = new BlockPos(2, 1, 2);
        helper.setBlock(floor, Blocks.STONE);
        for (int x = 1; x <= 3; x++) {
            for (int y = 2; y <= 4; y++) {
                for (int z = 1; z <= 3; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }

        helper.assertTrue(
                TraderSpawnSafety.isSafe(helper.getLevel(), helper.absolutePos(floor.above())),
                "Cleared stone platform was rejected"
        );
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void reservedContrabandSurvivesJoinAndRejectsSecondEvent(GameTestHelper helper) {
        TraderWorldData data = TraderWorldData.get(helper.getLevel().getServer());
        data.contraband().ifPresent(active -> data.cancelContraband(active.eventId()));
        SellerTraderEntity trader = ModEntities.SELLER_TRADER.get().create(helper.getLevel());
        helper.assertTrue(trader != null, "Seller entity could not be created");
        UUID eventId = UUID.randomUUID();
        UUID pointId = UUID.randomUUID();
        trader.setTraderRole(SellerTraderRole.CONTRABAND);
        trader.setEventId(eventId);
        trader.setExpiresAtMillis(System.currentTimeMillis() + 60_000L);
        TraderWorldData.ActiveContraband active = new TraderWorldData.ActiveContraband(
                eventId,
                trader.getUUID(),
                pointId,
                helper.getLevel().dimension(),
                helper.absolutePos(new BlockPos(1, 2, 1)),
                trader.expiresAtMillis()
        );
        helper.assertTrue(data.beginContraband(active), "First event was not reserved");

        TraderLifecycle.onJoin(trader, helper.getLevel());
        boolean second = data.beginContraband(new TraderWorldData.ActiveContraband(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), helper.getLevel().dimension(),
                active.pos(), active.expiresAt()
        ));

        helper.assertTrue(!trader.isRemoved(), "Reserved entity was discarded on join");
        helper.assertTrue(!second, "Second contraband event was accepted");
        data.cancelContraband(eventId);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void removedContrabandClearsEventAndStartsCooldown(GameTestHelper helper) {
        TraderWorldData data = TraderWorldData.get(helper.getLevel().getServer());
        data.contraband().ifPresent(active -> data.cancelContraband(active.eventId()));
        SellerTraderEntity trader = contrabandTrader(helper, System.currentTimeMillis() + 60_000L);
        UUID eventId = trader.eventId().orElseThrow();
        TraderWorldData.ActiveContraband active = activeContraband(helper, trader, eventId);
        helper.assertTrue(data.beginContraband(active), "Event was not reserved before entity join");
        helper.assertTrue(helper.getLevel().addFreshEntity(trader), "Trader could not be spawned");

        long beforeRemoval = System.currentTimeMillis();
        trader.discard();

        helper.assertTrue(data.contraband().isEmpty(), "Removed trader left an active event");
        helper.assertTrue(data.contrabandCooldownUntil() > beforeRemoval, "Removal did not start cooldown");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void expiredContrabandIsRemovedAndCleared(GameTestHelper helper) {
        TraderWorldData data = TraderWorldData.get(helper.getLevel().getServer());
        data.contraband().ifPresent(active -> data.cancelContraband(active.eventId()));
        long now = System.currentTimeMillis();
        SellerTraderEntity trader = contrabandTrader(helper, now - 1L);
        UUID eventId = trader.eventId().orElseThrow();
        helper.assertTrue(
                data.beginContraband(activeContraband(helper, trader, eventId)),
                "Expired event was not reserved for reconciliation"
        );
        helper.assertTrue(helper.getLevel().addFreshEntity(trader), "Expired trader could not be spawned");

        TraderLifecycle.tick(helper.getLevel().getServer(), now);

        helper.assertTrue(trader.isRemoved(), "Expired trader was not removed");
        helper.assertTrue(data.contraband().isEmpty(), "Expired event remained active");
        helper.assertTrue(data.contrabandCooldownUntil() > now, "Expiry did not start cooldown");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wanderingAccessRejectsAnotherFaction(GameTestHelper helper) {
        UUID targetFaction = UUID.randomUUID();
        UUID otherFaction = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        long now = System.currentTimeMillis();
        TraderWorldData.WanderingEvent event = new TraderWorldData.WanderingEvent(
                targetFaction,
                eventId,
                entityId,
                new ClaimKey(helper.getLevel().dimension(), 0, 0),
                helper.absolutePos(new BlockPos(1, 2, 1)),
                List.of(new TraderWorldData.RolledOffer("coal", 5L)),
                now + 60_000L,
                0L
        );

        helper.assertTrue(!TraderAccessPolicy.canUseWandering(
                otherFaction,
                targetFaction,
                targetFaction,
                entityId,
                eventId,
                now + 60_000L,
                event,
                now
        ), "Another faction gained access to wandering offers");
        helper.succeed();
    }

    private static SellerTraderEntity contrabandTrader(GameTestHelper helper, long expiresAt) {
        SellerTraderEntity trader = ModEntities.SELLER_TRADER.get().create(helper.getLevel());
        if (trader == null) {
            throw new IllegalStateException("Seller entity could not be created");
        }
        trader.setTraderRole(SellerTraderRole.CONTRABAND);
        trader.setEventId(UUID.randomUUID());
        trader.setExpiresAtMillis(expiresAt);
        return trader;
    }

    private static TraderWorldData.ActiveContraband activeContraband(
            GameTestHelper helper,
            SellerTraderEntity trader,
            UUID eventId
    ) {
        return new TraderWorldData.ActiveContraband(
                eventId,
                trader.getUUID(),
                UUID.randomUUID(),
                helper.getLevel().dimension(),
                helper.absolutePos(new BlockPos(1, 2, 1)),
                trader.expiresAtMillis()
        );
    }

    private TraderLifecycleGameTests() {
    }
}
