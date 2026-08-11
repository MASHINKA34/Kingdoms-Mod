package com.geydev.kalfactions.registry;

import com.geydev.kalfactions.KalFactions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, KalFactions.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> KOLYVAN =
            SOUNDS.register("kolyvan", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "kolyvan")
            ));

    public static final DeferredHolder<SoundEvent, SoundEvent> KOLYVAN_SEIZE =
            SOUNDS.register("kolyvan_seize", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "kolyvan_seize")
            ));

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }

    private ModSounds() {
    }
}
