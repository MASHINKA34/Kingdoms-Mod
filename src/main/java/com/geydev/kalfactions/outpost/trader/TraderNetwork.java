package com.geydev.kalfactions.outpost.trader;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.client.screen.SellerCatalogScreen;
import com.geydev.kalfactions.client.screen.TraderShopScreen;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class TraderNetwork {
    private static final String PROTOCOL_VERSION = "5";

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(
                TraderPayloads.C2SBuy.TYPE,
                TraderPayloads.C2SBuy.STREAM_CODEC,
                TraderNetwork::handleBuy
        );
        registrar.playToServer(
                TraderPayloads.C2SSell.TYPE,
                TraderPayloads.C2SSell.STREAM_CODEC,
                TraderNetwork::handleSell
        );
        registrar.playToServer(
                TraderPayloads.C2SRefreshSeller.TYPE,
                TraderPayloads.C2SRefreshSeller.STREAM_CODEC,
                TraderNetwork::handleRefreshSeller
        );
        registrar.playToClient(
                TraderPayloads.S2CShopState.TYPE,
                TraderPayloads.S2CShopState.STREAM_CODEC,
                TraderNetwork::handleShopState
        );
        registrar.playToClient(
                TraderPayloads.S2CSellerCatalog.TYPE,
                TraderPayloads.S2CSellerCatalog.STREAM_CODEC,
                TraderNetwork::handleSellerCatalog
        );
    }

    private static void handleBuy(TraderPayloads.C2SBuy payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            TraderService.buy(player, payload.traderId(), payload.offerId());
        }
    }

    private static void handleSell(TraderPayloads.C2SSell payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            TraderService.sell(player, payload.traderId(), payload.offerId(), payload.amount());
        }
    }

    private static void handleRefreshSeller(TraderPayloads.C2SRefreshSeller payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            TraderService.refreshSeller(player, payload.traderId());
        }
    }

    private static void handleShopState(TraderPayloads.S2CShopState payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            TraderShopScreen.handle(payload);
        }
    }

    private static void handleSellerCatalog(TraderPayloads.S2CSellerCatalog payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            SellerCatalogScreen.handle(payload);
        }
    }

    private TraderNetwork() {
    }
}
