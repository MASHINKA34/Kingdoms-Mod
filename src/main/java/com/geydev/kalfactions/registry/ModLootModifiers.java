package com.geydev.kalfactions.registry;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.scorched.RemoveRaidFlaresLootModifier;
import com.geydev.kalfactions.keyholder.BossKeyLootModifier;
import com.mojang.serialization.MapCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModLootModifiers {
    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, KalFactions.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<RemoveRaidFlaresLootModifier>>
            REMOVE_RAID_FLARES = TYPES.register("remove_raid_flares", () -> RemoveRaidFlaresLootModifier.CODEC);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<BossKeyLootModifier>>
            BOSS_KEY_DROP = TYPES.register("boss_key_drop", () -> BossKeyLootModifier.CODEC);

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    private ModLootModifiers() {
    }
}
