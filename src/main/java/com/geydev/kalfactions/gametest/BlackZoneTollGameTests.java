package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.blackzone.BlackZoneClock;
import com.geydev.kalfactions.blackzone.BlackZoneDamage;
import com.geydev.kalfactions.blackzone.BlackZoneData;
import com.geydev.kalfactions.blackzone.BlackZonePenalties;
import com.geydev.kalfactions.blackzone.BlackZoneService;
import com.geydev.kalfactions.blackzone.BlackZoneStage;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BlackZoneTollGameTests {
    private static final long START_MILLIS = 1_700_000_000_000L;
    private static final long STEP_MILLIS = 10_000L;

    @GameTest(template = "empty", batch = "black_zone", timeoutTicks = 600)
    public static void tollWalksThroughEveryStage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        AtomicLong clock = new AtomicLong(START_MILLIS);
        BlackZoneClock.override(clock::get);
        try {
            BlackZoneService.Sample entry = BlackZoneService.tick(level, player, true);
            helper.assertTrue(entry.stage() == BlackZoneStage.ENTRY, "entering the zone starts the toll");
            helper.assertTrue(entry.entered(), "the first sample counts as an entry");
            helper.assertValueEqual(BlackZonePenalties.healthPenaltyOn(player), 1, "entry costs one health point");
            helper.assertValueEqual((int) player.getMaxHealth(), 19, "maximum health follows the modifier");

            assertStageAt(helper, level, player, clock, 20, BlackZoneStage.HUNGER_1);
            assertEffect(helper, player, MobEffects.HUNGER, 0, "hunger I at 20 minutes");

            assertStageAt(helper, level, player, clock, 40, BlackZoneStage.HEALTH_2);
            helper.assertValueEqual(BlackZonePenalties.healthPenaltyOn(player), 2, "-2 health at 40 minutes");

            assertStageAt(helper, level, player, clock, 60, BlackZoneStage.HEALTH_4);
            helper.assertValueEqual(BlackZonePenalties.healthPenaltyOn(player), 4, "-4 health at one hour");

            assertStageAt(helper, level, player, clock, 80, BlackZoneStage.WEAKNESS);
            assertEffect(helper, player, MobEffects.WEAKNESS, 0, "weakness I at 1:20");

            assertStageAt(helper, level, player, clock, 100, BlackZoneStage.HEALTH_6);
            helper.assertValueEqual(BlackZonePenalties.healthPenaltyOn(player), 6, "-6 health at 1:40");
            helper.assertValueEqual((int) player.getMaxHealth(), 14, "maximum health dropped to 14");

            assertStageAt(helper, level, player, clock, 120, BlackZoneStage.SLOWNESS);
            assertEffect(helper, player, MobEffects.MOVEMENT_SLOWDOWN, 0, "slowness I at two hours");

            assertStageAt(helper, level, player, clock, 140, BlackZoneStage.HUNGER_2);
            assertEffect(helper, player, MobEffects.HUNGER, 1, "hunger II at 2:20");

            assertStageAt(helper, level, player, clock, 150, BlackZoneStage.MINING_FATIGUE);
            assertEffect(helper, player, MobEffects.DIG_SLOWDOWN, 0, "mining fatigue I at 2:30");

            assertStageAt(helper, level, player, clock, 160, BlackZoneStage.KOLYVAN);
            helper.assertTrue(
                    BlackZoneStage.kolyvanActive(BlackZoneStage.KOLYVAN.thresholdMillis()),
                    "kolyvan starts hunting at 2:40"
            );

            assertStageAt(helper, level, player, clock, 180, BlackZoneStage.POISON);
            assertEffect(helper, player, MobEffects.POISON, 0, "poison I at three hours");

            assertStageAt(helper, level, player, clock, 200, BlackZoneStage.WITHER);
            assertEffect(helper, player, MobEffects.WITHER, 0, "wither at 3:20");

            assertStageAt(helper, level, player, clock, 220, BlackZoneStage.POISON_2);
            assertEffect(helper, player, MobEffects.POISON, 1, "poison II at 3:40");

            BlackZoneService.Sample left = BlackZoneService.tick(level, player, false);
            helper.assertTrue(left.stage() == BlackZoneStage.POISON_2, "the toll keeps its stage after leaving");
            helper.assertTrue(player.getEffect(MobEffects.WITHER) == null, "wither is dropped on leaving");
            assertEffect(helper, player, MobEffects.POISON, 1, "poison survives leaving the zone");
            assertEffect(helper, player, MobEffects.HUNGER, 1, "hunger survives leaving the zone");
            helper.assertValueEqual(
                    BlackZonePenalties.healthPenaltyOn(player),
                    6,
                    "the health modifier survives leaving the zone"
            );
        } finally {
            cleanUp(level, player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "black_zone", timeoutTicks = 600)
    public static void tollIsDroppedAfterTenMinutesOutside(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        AtomicLong clock = new AtomicLong(START_MILLIS);
        BlackZoneClock.override(clock::get);
        try {
            BlackZoneService.tick(level, player, true);
            stay(level, player, clock, 45);
            helper.assertTrue(
                    BlackZoneService.tick(level, player, true).stage() == BlackZoneStage.HEALTH_2,
                    "45 minutes in the zone reach the -2 health stage"
            );

            clock.addAndGet(BlackZoneService.releaseMillis() - 60_000L);
            BlackZoneService.Sample waiting = BlackZoneService.tick(level, player, false);
            helper.assertFalse(waiting.released(), "the toll holds nine minutes after leaving");
            helper.assertTrue(waiting.stage() == BlackZoneStage.HEALTH_2, "the stage holds nine minutes after leaving");
            assertEffect(helper, player, MobEffects.HUNGER, 0, "hunger holds nine minutes after leaving");
            helper.assertValueEqual(BlackZonePenalties.healthPenaltyOn(player), 2, "health penalty holds");

            clock.addAndGet(60_000L);
            BlackZoneService.Sample released = BlackZoneService.tick(level, player, false);
            helper.assertTrue(released.released(), "ten minutes outside drop the toll");
            helper.assertTrue(released.stage() == null, "no stage is left after the release");
            helper.assertValueEqual((int) released.accumulatedMillis(), 0, "the toll is back to zero");
            helper.assertTrue(player.getEffect(MobEffects.HUNGER) == null, "hunger is removed on release");
            helper.assertValueEqual(BlackZonePenalties.healthPenaltyOn(player), 0, "the health modifier is removed");
            helper.assertValueEqual((int) player.getMaxHealth(), 20, "maximum health is restored");

            BlackZoneService.Sample back = BlackZoneService.tick(level, player, true);
            helper.assertTrue(back.stage() == BlackZoneStage.ENTRY, "coming back starts from the entry stage");
            helper.assertTrue(back.entered(), "coming back warns the player again");
        } finally {
            cleanUp(level, player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "black_zone", timeoutTicks = 600)
    public static void fourHoursInTheZoneKillThePlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        AtomicLong clock = new AtomicLong(START_MILLIS);
        BlackZoneClock.override(clock::get);
        try {
            helper.assertFalse(
                    BlackZoneStage.lethal(BlackZoneStage.POISON_2.thresholdMillis()),
                    "3:40 is not lethal yet"
            );
            helper.assertTrue(
                    BlackZoneStage.lethal(BlackZoneStage.DEATH.thresholdMillis()),
                    "four hours is lethal"
            );
            helper.assertTrue(
                    BlackZoneStage.current(BlackZoneStage.DEATH.thresholdMillis()) == BlackZoneStage.DEATH,
                    "four hours reaches the death stage"
            );

            DamageSource source = BlackZoneDamage.source(level);
            helper.assertTrue(
                    source.type().msgId().equals("kingdoms_black_zone"),
                    "the black zone damage type carries its own death message"
            );

            BlackZoneData data = BlackZoneData.get(level.getServer());
            data.put(
                    player.getUUID(),
                    data.toll(player.getUUID())
                            .withAccumulated(BlackZoneStage.POISON_2.thresholdMillis(), clock.get())
            );
            BlackZoneService.tick(level, player, true);
            helper.assertTrue(player.isAlive(), "3:40 in the zone leaves the player alive");

            data.put(
                    player.getUUID(),
                    data.toll(player.getUUID())
                            .withAccumulated(BlackZoneStage.DEATH.thresholdMillis(), clock.get())
            );
            BlackZoneService.tick(level, player, true);
            helper.assertFalse(player.isAlive(), "four hours in the zone kills the player");
        } finally {
            cleanUp(level, player);
        }
        helper.succeed();
    }

    private static void assertStageAt(
            GameTestHelper helper,
            ServerLevel level,
            Player player,
            AtomicLong clock,
            int minutes,
            BlackZoneStage expected
    ) {
        long target = minutes * BlackZoneStage.MINUTE_MILLIS;
        long accumulated = BlackZoneService.accumulatedMillis(level.getServer(), player.getUUID());
        BlackZoneService.Sample sample = null;
        while (accumulated < target) {
            clock.addAndGet(STEP_MILLIS);
            sample = BlackZoneService.tick(level, player, true);
            accumulated = sample.accumulatedMillis();
        }
        helper.assertTrue(
                sample != null && sample.stage() == expected,
                "stage " + expected.id() + " starts at " + minutes + " minutes"
        );
    }

    private static void stay(ServerLevel level, Player player, AtomicLong clock, int minutes) {
        long remaining = minutes * BlackZoneStage.MINUTE_MILLIS;
        while (remaining > 0L) {
            long step = Math.min(STEP_MILLIS, remaining);
            clock.addAndGet(step);
            BlackZoneService.tick(level, player, true);
            remaining -= step;
        }
    }

    private static void assertEffect(
            GameTestHelper helper,
            Player player,
            Holder<MobEffect> effect,
            int amplifier,
            String message
    ) {
        MobEffectInstance instance = player.getEffect(effect);
        helper.assertTrue(instance != null && instance.getAmplifier() == amplifier, message);
    }

    private static void cleanUp(ServerLevel level, Player player) {
        BlackZoneClock.reset();
        BlackZonePenalties.clear(player);
        BlackZoneService.forget(player.getUUID());
        if (level.getServer() != null) {
            BlackZoneData.get(level.getServer()).clear(player.getUUID());
        }
    }

    private BlackZoneTollGameTests() {
    }
}
