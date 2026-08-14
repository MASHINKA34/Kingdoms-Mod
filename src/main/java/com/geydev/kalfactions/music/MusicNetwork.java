package com.geydev.kalfactions.music;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.client.ClientMusicPlayer;
import com.geydev.kalfactions.client.screen.MusicBlockScreen;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class MusicNetwork {
    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MusicLimits.PROTOCOL_VERSION);
        registrar.playToServer(
                MusicPayloads.C2SBeginUpload.TYPE,
                MusicPayloads.C2SBeginUpload.STREAM_CODEC,
                (payload, context) -> onServer(context, player -> MusicService.beginUpload(player, payload))
        );
        registrar.playToServer(
                MusicPayloads.C2SUploadChunk.TYPE,
                MusicPayloads.C2SUploadChunk.STREAM_CODEC,
                (payload, context) -> onServer(context, player -> MusicService.uploadChunk(player, payload))
        );
        registrar.playToServer(
                MusicPayloads.C2SCancelUpload.TYPE,
                MusicPayloads.C2SCancelUpload.STREAM_CODEC,
                (payload, context) -> onServer(context, player ->
                        MusicService.cancelUpload(player, payload.sessionId()))
        );
        registrar.playToServer(
                MusicPayloads.C2SRequestTrack.TYPE,
                MusicPayloads.C2SRequestTrack.STREAM_CODEC,
                (payload, context) -> onServer(context, player -> MusicService.requestTrack(player, payload.hash()))
        );
        registrar.playToServer(
                MusicPayloads.C2SRequestSpeaker.TYPE,
                MusicPayloads.C2SRequestSpeaker.STREAM_CODEC,
                (payload, context) -> onServer(context, player -> MusicService.requestScreen(player, payload.pos()))
        );
        registrar.playToServer(
                MusicPayloads.C2SUpdateSpeaker.TYPE,
                MusicPayloads.C2SUpdateSpeaker.STREAM_CODEC,
                (payload, context) -> onServer(context, player -> MusicService.updateSpeaker(player, payload))
        );
        registrar.playToServer(
                MusicPayloads.C2SDeleteTrack.TYPE,
                MusicPayloads.C2SDeleteTrack.STREAM_CODEC,
                (payload, context) -> onServer(context, player -> MusicService.deleteTrack(player, payload))
        );
        registrar.playToClient(
                MusicPayloads.S2CUploadStatus.TYPE,
                MusicPayloads.S2CUploadStatus.STREAM_CODEC,
                (payload, context) -> onClient(() -> MusicBlockScreen.handleUploadStatus(payload))
        );
        registrar.playToClient(
                MusicPayloads.S2CTrackBegin.TYPE,
                MusicPayloads.S2CTrackBegin.STREAM_CODEC,
                (payload, context) -> onClient(() -> ClientMusicPlayer.handleTrackBegin(payload))
        );
        registrar.playToClient(
                MusicPayloads.S2CTrackChunk.TYPE,
                MusicPayloads.S2CTrackChunk.STREAM_CODEC,
                (payload, context) -> onClient(() -> ClientMusicPlayer.handleTrackChunk(payload))
        );
        registrar.playToClient(
                MusicPayloads.S2CTrackFailed.TYPE,
                MusicPayloads.S2CTrackFailed.STREAM_CODEC,
                (payload, context) -> onClient(() -> ClientMusicPlayer.handleTrackFailed(payload))
        );
        registrar.playToClient(
                MusicPayloads.S2CSpeakerStart.TYPE,
                MusicPayloads.S2CSpeakerStart.STREAM_CODEC,
                (payload, context) -> onClient(() -> ClientMusicPlayer.handleStart(payload))
        );
        registrar.playToClient(
                MusicPayloads.S2CSpeakerStop.TYPE,
                MusicPayloads.S2CSpeakerStop.STREAM_CODEC,
                (payload, context) -> onClient(() -> ClientMusicPlayer.handleStop(payload))
        );
        registrar.playToClient(
                MusicPayloads.S2CSpeakerStopAll.TYPE,
                MusicPayloads.S2CSpeakerStopAll.STREAM_CODEC,
                (payload, context) -> onClient(ClientMusicPlayer::handleStopAll)
        );
        registrar.playToClient(
                MusicPayloads.S2CMusicMute.TYPE,
                MusicPayloads.S2CMusicMute.STREAM_CODEC,
                (payload, context) -> onClient(() -> ClientMusicPlayer.handleMute(payload.muted()))
        );
        registrar.playToClient(
                MusicPayloads.S2COpenSpeaker.TYPE,
                MusicPayloads.S2COpenSpeaker.STREAM_CODEC,
                (payload, context) -> onClient(() -> MusicBlockScreen.handleOpen(payload))
        );
    }

    private static void onServer(IPayloadContext context, java.util.function.Consumer<ServerPlayer> action) {
        if (context.player() instanceof ServerPlayer player) {
            action.accept(player);
        }
    }

    private static void onClient(Runnable action) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            action.run();
        }
    }

    private MusicNetwork() {
    }
}
