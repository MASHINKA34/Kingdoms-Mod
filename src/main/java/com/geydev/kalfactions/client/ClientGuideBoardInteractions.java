package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.registry.ModBlocks;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID, value = Dist.CLIENT)
public final class ClientGuideBoardInteractions {
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isGuideBoard(event)) {
            openRightClick(event);
        }
    }

    private static boolean isGuideBoard(PlayerInteractEvent event) {
        return event.getLevel().isClientSide()
                && event.getLevel().getBlockState(event.getPos()).is(ModBlocks.GUIDE_BOARD.get());
    }

    private static void openRightClick(PlayerInteractEvent.RightClickBlock event) {
        ClientFactionPayloadHandler.handleOpenGuide();
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private ClientGuideBoardInteractions() {
    }
}
