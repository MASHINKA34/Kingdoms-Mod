package com.geydev.kalfactions.registry;

import com.geydev.kalfactions.KalFactions;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.ModifyRegistriesEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class ModRemovedBlocks {
    private static final List<String> IDS = List.of("news_board", "world_map");
    private static final ResourceLocation AIR = ResourceLocation.withDefaultNamespace("air");

    @SubscribeEvent
    public static void onModifyRegistries(ModifyRegistriesEvent event) {
        Registry<Block> blocks = event.getRegistry(Registries.BLOCK);
        for (String id : IDS) {
            blocks.addAlias(ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, id), AIR);
        }
    }

    private ModRemovedBlocks() {
    }
}
