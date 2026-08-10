package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID, value = Dist.CLIENT)
public final class StoneGodStatueHighlightHandler {
    @SubscribeEvent
    public static void onRenderBlockHighlight(RenderHighlightEvent.Block event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        BlockPos pos = event.getTarget().getBlockPos();
        BlockState state = minecraft.level.getBlockState(pos);
        if (state.is(ModBlocks.RESEARCH_GOD_STONE_8BLOCKS.get())
                || state.is(ModBlocks.WAR_GOD_STONE_8BLOCKS.get())
                || state.is(ModBlocks.ECONOMY_GOD_STONE_8BLOCKS.get())
                || state.is(ModBlocks.STONE_GOD_STATUE_COLLISION.get())) {
            event.setCanceled(true);
        }
    }

    private StoneGodStatueHighlightHandler() {
    }
}
