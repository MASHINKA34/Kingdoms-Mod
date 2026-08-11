package com.geydev.kalfactions.registry;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.faith.FaithGod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    private static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, KalFactions.MOD_ID);

    public static final DeferredHolder<MobEffect, MobEffect> FAITH_SCIENCE =
            EFFECTS.register("faith_science", () -> new FaithBlessingEffect(0x5AC8F0));

    public static final DeferredHolder<MobEffect, MobEffect> FAITH_WAR =
            EFFECTS.register("faith_war", () -> new FaithBlessingEffect(0xC8463C));

    public static final DeferredHolder<MobEffect, MobEffect> FAITH_ECONOMY =
            EFFECTS.register("faith_economy", () -> new FaithBlessingEffect(0xF0C24A));

    public static Holder<MobEffect> forGod(FaithGod god) {
        return switch (god) {
            case SCIENCE -> FAITH_SCIENCE;
            case WAR -> FAITH_WAR;
            case ECONOMY -> FAITH_ECONOMY;
        };
    }

    public static void register(IEventBus bus) {
        EFFECTS.register(bus);
    }

    private static final class FaithBlessingEffect extends MobEffect {
        private FaithBlessingEffect(int color) {
            super(MobEffectCategory.BENEFICIAL, color);
        }

        @Override
        public boolean applyEffectTick(net.minecraft.world.entity.LivingEntity entity, int amplifier) {
            return false;
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return false;
        }
    }

    private ModEffects() {
    }
}
