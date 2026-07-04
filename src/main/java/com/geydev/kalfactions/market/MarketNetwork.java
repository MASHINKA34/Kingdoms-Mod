package com.geydev.kalfactions.market;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.client.ClientPlotStore;
import com.geydev.kalfactions.client.screen.PlotScreen;
import com.geydev.kalfactions.client.screen.PlotTrustScreen;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class MarketNetwork {
    private static final String PROTOCOL_VERSION = "1";

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(
                MarketPayloads.S2CSyncPlots.TYPE,
                MarketPayloads.S2CSyncPlots.STREAM_CODEC,
                MarketNetwork::handleSyncPlots
        );
        registrar.playToClient(
                MarketPayloads.S2COpenPlotScreen.TYPE,
                MarketPayloads.S2COpenPlotScreen.STREAM_CODEC,
                MarketNetwork::handleOpenPlotScreen
        );
        registrar.playToServer(
                MarketPayloads.C2SBuyPlot.TYPE,
                MarketPayloads.C2SBuyPlot.STREAM_CODEC,
                MarketNetwork::handleBuyPlot
        );
        registrar.playToServer(
                MarketPayloads.C2SManagePlot.TYPE,
                MarketPayloads.C2SManagePlot.STREAM_CODEC,
                MarketNetwork::handleManagePlot
        );
        registrar.playToClient(
                MarketPayloads.S2CPlotTrustState.TYPE,
                MarketPayloads.S2CPlotTrustState.STREAM_CODEC,
                MarketNetwork::handlePlotTrustState
        );
        registrar.playToServer(
                MarketPayloads.C2SRequestPlotTrust.TYPE,
                MarketPayloads.C2SRequestPlotTrust.STREAM_CODEC,
                MarketNetwork::handleRequestPlotTrust
        );
        registrar.playToServer(
                MarketPayloads.C2SEditPlotTrust.TYPE,
                MarketPayloads.C2SEditPlotTrust.STREAM_CODEC,
                MarketNetwork::handleEditPlotTrust
        );
        registrar.playToServer(
                MarketPayloads.C2SAdjustPlotSelection.TYPE,
                MarketPayloads.C2SAdjustPlotSelection.STREAM_CODEC,
                MarketNetwork::handleAdjustPlotSelection
        );
    }

    private static void handlePlotTrustState(MarketPayloads.S2CPlotTrustState payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            PlotTrustScreen.handle(payload);
        }
    }

    private static void handleRequestPlotTrust(MarketPayloads.C2SRequestPlotTrust payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            MarketPlotService.requestTrust(player, payload.plotId());
        }
    }

    private static void handleEditPlotTrust(MarketPayloads.C2SEditPlotTrust payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            MarketPlotService.editTrust(
                    player, payload.plotId(), payload.add(), payload.faction(), payload.targetId());
        }
    }

    private static void handleAdjustPlotSelection(
            MarketPayloads.C2SAdjustPlotSelection payload,
            IPayloadContext context
    ) {
        if (context.player() instanceof ServerPlayer player) {
            MarketPlotService.adjustSelection(player, payload.face(), payload.delta());
        }
    }

    private static void handleSyncPlots(MarketPayloads.S2CSyncPlots payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientPlotStore.handle(payload);
        }
    }

    private static void handleOpenPlotScreen(MarketPayloads.S2COpenPlotScreen payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            PlotScreen.handle(payload);
        }
    }

    private static void handleBuyPlot(MarketPayloads.C2SBuyPlot payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            MarketPlotService.buy(player, payload.plotId());
        }
    }

    private static void handleManagePlot(MarketPayloads.C2SManagePlot payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            MarketPlotService.manage(player, payload.plotId(), payload.action(), payload.price());
        }
    }

    private MarketNetwork() {
    }
}
