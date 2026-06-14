package com.geydev.kalfactions.registry;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.entity.OutpostTraderEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, KalFactions.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<OutpostTraderEntity>> OUTPOST_TRADER =
            ENTITIES.register("outpost_trader", () -> EntityType.Builder
                    .<OutpostTraderEntity>of(OutpostTraderEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.74F)
                    .clientTrackingRange(10)
                    .build("outpost_trader"));

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
    }

    @SubscribeEvent
    public static void onCreateAttributes(EntityAttributeCreationEvent event) {
        event.put(OUTPOST_TRADER.get(), OutpostTraderEntity.createAttributes().build());
    }

    private ModEntities() {
    }
}
