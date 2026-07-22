package com.geydev.kalfactions.outpost.trader;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.entity.SellerTraderEntity;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class SellerShopEvents {
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof SellerTraderEntity trader
                && event.getLevel() instanceof ServerLevel level) {
            TraderLifecycle.onJoin(trader, level);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        TradeSessionManager.clear(event.getServer());
    }

    private SellerShopEvents() {
    }
}
