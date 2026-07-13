package com.geydev.kalfactions.tax;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.client.screen.AdminAnalyzerScreen;
import com.geydev.kalfactions.client.screen.FactionMeterScreen;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class LagTaxNetwork {
    private static final String PROTOCOL_VERSION = "1";

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(
                LagTaxPayloads.C2SOpenAnalyzer.TYPE,
                LagTaxPayloads.C2SOpenAnalyzer.STREAM_CODEC,
                LagTaxNetwork::handleOpenAnalyzer
        );
        registrar.playToServer(
                LagTaxPayloads.C2SAnalyzerTeleport.TYPE,
                LagTaxPayloads.C2SAnalyzerTeleport.STREAM_CODEC,
                LagTaxNetwork::handleAnalyzerTeleport
        );
        registrar.playToServer(
                LagTaxPayloads.C2SAnalyzerDetail.TYPE,
                LagTaxPayloads.C2SAnalyzerDetail.STREAM_CODEC,
                LagTaxNetwork::handleAnalyzerDetail
        );
        registrar.playToServer(
                LagTaxPayloads.C2SOpenMeter.TYPE,
                LagTaxPayloads.C2SOpenMeter.STREAM_CODEC,
                LagTaxNetwork::handleOpenMeter
        );
        registrar.playToServer(
                LagTaxPayloads.C2SSetAutoRenew.TYPE,
                LagTaxPayloads.C2SSetAutoRenew.STREAM_CODEC,
                LagTaxNetwork::handleSetAutoRenew
        );
        registrar.playToServer(
                LagTaxPayloads.C2SBuyChunkLoad.TYPE,
                LagTaxPayloads.C2SBuyChunkLoad.STREAM_CODEC,
                LagTaxNetwork::handleBuyChunkLoad
        );
        registrar.playToClient(
                LagTaxPayloads.S2CAnalyzerData.TYPE,
                LagTaxPayloads.S2CAnalyzerData.STREAM_CODEC,
                LagTaxNetwork::handleAnalyzerData
        );
        registrar.playToClient(
                LagTaxPayloads.S2CChunkDetail.TYPE,
                LagTaxPayloads.S2CChunkDetail.STREAM_CODEC,
                LagTaxNetwork::handleChunkDetail
        );
        registrar.playToClient(
                LagTaxPayloads.S2CMeterData.TYPE,
                LagTaxPayloads.S2CMeterData.STREAM_CODEC,
                LagTaxNetwork::handleMeterData
        );
    }

    private static void handleOpenAnalyzer(LagTaxPayloads.C2SOpenAnalyzer payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            LagTaxService.sendAnalyzerData(player);
        }
    }

    private static void handleAnalyzerTeleport(LagTaxPayloads.C2SAnalyzerTeleport payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            LagTaxService.analyzerTeleport(player, payload.dimension(), payload.chunkX(), payload.chunkZ());
        }
    }

    private static void handleAnalyzerDetail(LagTaxPayloads.C2SAnalyzerDetail payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            LagTaxService.startDetail(player, payload.dimension(), payload.chunkX(), payload.chunkZ());
        }
    }

    private static void handleOpenMeter(LagTaxPayloads.C2SOpenMeter payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            LagTaxService.sendMeterData(player);
        }
    }

    private static void handleSetAutoRenew(LagTaxPayloads.C2SSetAutoRenew payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            LagTaxService.setAutoRenew(player, payload.enabled());
        }
    }

    private static void handleBuyChunkLoad(LagTaxPayloads.C2SBuyChunkLoad payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            LagTaxService.buyChunkLoad(player, payload.dimension(), payload.packedChunk(), payload.hours());
        }
    }

    private static void handleAnalyzerData(LagTaxPayloads.S2CAnalyzerData payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            AdminAnalyzerScreen.handleData(payload);
        }
    }

    private static void handleChunkDetail(LagTaxPayloads.S2CChunkDetail payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            AdminAnalyzerScreen.handleDetail(payload);
        }
    }

    private static void handleMeterData(LagTaxPayloads.S2CMeterData payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            FactionMeterScreen.handle(payload);
        }
    }

    private LagTaxNetwork() {
    }
}
