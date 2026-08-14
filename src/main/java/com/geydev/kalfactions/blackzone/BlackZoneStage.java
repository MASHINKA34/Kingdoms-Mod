package com.geydev.kalfactions.blackzone;

import com.geydev.kalfactions.config.ModConfigSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

public enum BlackZoneStage {
    ENTRY(
            "entry",
            ModConfigSpec.BLACK_ZONE_STAGE_ENTRY_MINUTES::getAsInt,
            ModConfigSpec.BLACK_ZONE_HEALTH_PENALTY_ENTRY::getAsInt,
            null,
            0
    ),
    HUNGER_1(
            "hunger_1",
            ModConfigSpec.BLACK_ZONE_STAGE_HUNGER_1_MINUTES::getAsInt,
            null,
            MobEffects.HUNGER,
            0
    ),
    HEALTH_2(
            "health_2",
            ModConfigSpec.BLACK_ZONE_STAGE_HEALTH_2_MINUTES::getAsInt,
            ModConfigSpec.BLACK_ZONE_HEALTH_PENALTY_SECOND::getAsInt,
            null,
            0
    ),
    HEALTH_4(
            "health_4",
            ModConfigSpec.BLACK_ZONE_STAGE_HEALTH_4_MINUTES::getAsInt,
            ModConfigSpec.BLACK_ZONE_HEALTH_PENALTY_THIRD::getAsInt,
            null,
            0
    ),
    WEAKNESS(
            "weakness",
            ModConfigSpec.BLACK_ZONE_STAGE_WEAKNESS_MINUTES::getAsInt,
            null,
            MobEffects.WEAKNESS,
            0
    ),
    HEALTH_6(
            "health_6",
            ModConfigSpec.BLACK_ZONE_STAGE_HEALTH_6_MINUTES::getAsInt,
            ModConfigSpec.BLACK_ZONE_HEALTH_PENALTY_FOURTH::getAsInt,
            null,
            0
    ),
    SLOWNESS(
            "slowness",
            ModConfigSpec.BLACK_ZONE_STAGE_SLOWNESS_MINUTES::getAsInt,
            null,
            MobEffects.MOVEMENT_SLOWDOWN,
            0
    ),
    HUNGER_2(
            "hunger_2",
            ModConfigSpec.BLACK_ZONE_STAGE_HUNGER_2_MINUTES::getAsInt,
            null,
            MobEffects.HUNGER,
            1
    ),
    MINING_FATIGUE(
            "mining_fatigue",
            ModConfigSpec.BLACK_ZONE_STAGE_MINING_FATIGUE_MINUTES::getAsInt,
            null,
            MobEffects.DIG_SLOWDOWN,
            0
    ),
    KOLYVAN(
            "kolyvan",
            ModConfigSpec.BLACK_ZONE_STAGE_KOLYVAN_MINUTES::getAsInt,
            null,
            null,
            0
    ),
    POISON(
            "poison",
            ModConfigSpec.BLACK_ZONE_STAGE_POISON_MINUTES::getAsInt,
            null,
            MobEffects.POISON,
            0
    ),
    WITHER(
            "wither",
            ModConfigSpec.BLACK_ZONE_STAGE_WITHER_MINUTES::getAsInt,
            null,
            MobEffects.WITHER,
            0
    ),
    POISON_2(
            "poison_2",
            ModConfigSpec.BLACK_ZONE_STAGE_POISON_2_MINUTES::getAsInt,
            null,
            MobEffects.POISON,
            1
    ),
    DEATH(
            "death",
            ModConfigSpec.BLACK_ZONE_STAGE_DEATH_MINUTES::getAsInt,
            null,
            null,
            0
    );

    public static final long MINUTE_MILLIS = 60_000L;

    private final String id;
    private final IntSupplier thresholdMinutes;
    private final IntSupplier healthPenalty;
    private final Holder<MobEffect> effect;
    private final int amplifier;

    BlackZoneStage(
            String id,
            IntSupplier thresholdMinutes,
            IntSupplier healthPenalty,
            Holder<MobEffect> effect,
            int amplifier
    ) {
        this.id = id;
        this.thresholdMinutes = thresholdMinutes;
        this.healthPenalty = healthPenalty;
        this.effect = effect;
        this.amplifier = amplifier;
    }

    public String id() {
        return id;
    }

    public long thresholdMillis() {
        return Math.max(0, thresholdMinutes.getAsInt()) * MINUTE_MILLIS;
    }

    public int healthPenalty() {
        return healthPenalty == null ? 0 : Math.max(0, healthPenalty.getAsInt());
    }

    public Holder<MobEffect> effect() {
        return effect;
    }

    public int amplifier() {
        return amplifier;
    }

    public boolean clearedOnLeaving() {
        return this == WITHER;
    }

    public String noticeKey() {
        return "kingdoms.blackzone.stage." + id;
    }

    public static BlackZoneStage current(long accumulatedMillis) {
        BlackZoneStage current = null;
        for (BlackZoneStage stage : values()) {
            if (accumulatedMillis < stage.thresholdMillis()) {
                continue;
            }
            if (current == null || stage.thresholdMillis() >= current.thresholdMillis()) {
                current = stage;
            }
        }
        return current;
    }

    public static List<BlackZoneStage> reached(long accumulatedMillis) {
        List<BlackZoneStage> reached = new ArrayList<>();
        for (BlackZoneStage stage : values()) {
            if (accumulatedMillis >= stage.thresholdMillis()) {
                reached.add(stage);
            }
        }
        return reached;
    }

    public static int healthPenaltyAt(long accumulatedMillis) {
        int penalty = 0;
        for (BlackZoneStage stage : reached(accumulatedMillis)) {
            penalty = Math.max(penalty, stage.healthPenalty());
        }
        return penalty;
    }

    public static Map<Holder<MobEffect>, Integer> effectsAt(long accumulatedMillis, boolean inZone) {
        Map<Holder<MobEffect>, Integer> effects = new LinkedHashMap<>();
        for (BlackZoneStage stage : reached(accumulatedMillis)) {
            if (stage.effect == null || (!inZone && stage.clearedOnLeaving())) {
                continue;
            }
            effects.merge(stage.effect, stage.amplifier, Math::max);
        }
        return effects;
    }

    public static boolean kolyvanActive(long accumulatedMillis) {
        return accumulatedMillis >= KOLYVAN.thresholdMillis();
    }

    public static boolean lethal(long accumulatedMillis) {
        return accumulatedMillis >= DEATH.thresholdMillis();
    }
}
