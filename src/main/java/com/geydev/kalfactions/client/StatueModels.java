package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID, value = Dist.CLIENT)
public final class StatueModels {
    public static final ModelResourceLocation CRYSTAL_SCIENCE = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "block/statue_crystal_science")
    );

    @SubscribeEvent
    public static void onRegisterAdditional(ModelEvent.RegisterAdditional event) {
        event.register(CRYSTAL_SCIENCE);
    }

    private StatueModels() {
    }
}
