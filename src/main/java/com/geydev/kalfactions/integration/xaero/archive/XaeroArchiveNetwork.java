package com.geydev.kalfactions.integration.xaero.archive;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.integration.xaero.archive.client.XaeroArchiveClient;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class XaeroArchiveNetwork {
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(XaeroArchiveLimits.PROTOCOL_VERSION);
        registrar.playToServer(XaeroArchivePayloads.C2SBeginUpload.TYPE,
                XaeroArchivePayloads.C2SBeginUpload.STREAM_CODEC, XaeroArchiveNetwork::beginUpload);
        registrar.playToServer(XaeroArchivePayloads.C2SUploadPart.TYPE,
                XaeroArchivePayloads.C2SUploadPart.STREAM_CODEC, XaeroArchiveNetwork::uploadPart);
        registrar.playToServer(XaeroArchivePayloads.C2SFinishUpload.TYPE,
                XaeroArchivePayloads.C2SFinishUpload.STREAM_CODEC, XaeroArchiveNetwork::finishUpload);
        registrar.playToServer(XaeroArchivePayloads.C2SRequestDownload.TYPE,
                XaeroArchivePayloads.C2SRequestDownload.STREAM_CODEC, XaeroArchiveNetwork::requestDownload);
        registrar.playToServer(XaeroArchivePayloads.C2SCancel.TYPE,
                XaeroArchivePayloads.C2SCancel.STREAM_CODEC, XaeroArchiveNetwork::cancel);
        registrar.playToServer(XaeroArchivePayloads.C2SRequestStats.TYPE,
                XaeroArchivePayloads.C2SRequestStats.STREAM_CODEC, XaeroArchiveNetwork::requestStats);
        registrar.playToClient(XaeroArchivePayloads.S2CBeginDownload.TYPE,
                XaeroArchivePayloads.S2CBeginDownload.STREAM_CODEC, XaeroArchiveNetwork::beginDownload);
        registrar.playToClient(XaeroArchivePayloads.S2CDownloadPart.TYPE,
                XaeroArchivePayloads.S2CDownloadPart.STREAM_CODEC, XaeroArchiveNetwork::downloadPart);
        registrar.playToClient(XaeroArchivePayloads.S2CStatus.TYPE,
                XaeroArchivePayloads.S2CStatus.STREAM_CODEC, XaeroArchiveNetwork::status);
        registrar.playToClient(XaeroArchivePayloads.S2CStats.TYPE,
                XaeroArchivePayloads.S2CStats.STREAM_CODEC, XaeroArchiveNetwork::stats);
    }

    private static void beginUpload(XaeroArchivePayloads.C2SBeginUpload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            XaeroArchiveSessions.beginUpload(player, payload);
        }
    }

    private static void uploadPart(XaeroArchivePayloads.C2SUploadPart payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            XaeroArchiveSessions.uploadPart(player, payload);
        }
    }

    private static void finishUpload(XaeroArchivePayloads.C2SFinishUpload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            XaeroArchiveSessions.finishUpload(player, payload);
        }
    }

    private static void requestDownload(XaeroArchivePayloads.C2SRequestDownload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            XaeroArchiveSessions.requestDownload(player, payload);
        }
    }

    private static void cancel(XaeroArchivePayloads.C2SCancel payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            XaeroArchiveSessions.cancel(player, payload.sessionId());
        }
    }

    private static void requestStats(XaeroArchivePayloads.C2SRequestStats payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            XaeroArchiveSessions.requestStats(player, payload);
        }
    }

    private static void beginDownload(XaeroArchivePayloads.S2CBeginDownload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            XaeroArchiveClient.handleBeginDownload(payload);
        }
    }

    private static void downloadPart(XaeroArchivePayloads.S2CDownloadPart payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            XaeroArchiveClient.handleDownloadPart(payload);
        }
    }

    private static void status(XaeroArchivePayloads.S2CStatus payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            XaeroArchiveClient.handleStatus(payload);
        }
    }

    private static void stats(XaeroArchivePayloads.S2CStats payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            XaeroArchiveClient.handleStats(payload);
        }
    }

    private XaeroArchiveNetwork() {
    }
}
