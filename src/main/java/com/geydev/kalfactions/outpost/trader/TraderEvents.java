package com.geydev.kalfactions.outpost.trader;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class TraderEvents {
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof WanderingTrader trader)
                || !TraderService.isMarked(trader)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (player.getItemInHand(InteractionHand.MAIN_HAND).is(ModItems.TRADER_REMOVER.get())) {
            trader.discard();
            return;
        }
        TraderService.open(player, trader);
    }

    private TraderEvents() {
    }
}
