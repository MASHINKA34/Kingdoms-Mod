package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID, value = Dist.CLIENT)
public final class StatueScienceModels {
    public static final ModelResourceLocation FLOATING_CRYSTAL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(
                    KalFactions.MOD_ID,
                    "block/statue_science_floating_crystal"
            )
    );

    @SubscribeEvent
    public static void onRegisterAdditional(ModelEvent.RegisterAdditional event) {
        event.register(FLOATING_CRYSTAL);
    }

    private StatueScienceModels() {
    }
}
