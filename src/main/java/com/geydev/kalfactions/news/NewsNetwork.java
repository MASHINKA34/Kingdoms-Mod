package com.geydev.kalfactions.news;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.client.ClientNewsState;
import com.geydev.kalfactions.client.screen.NewsScreen;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class NewsNetwork {
    private static final String PROTOCOL_VERSION = "1";

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(
                NewsPayloads.C2SPublishNews.TYPE,
                NewsPayloads.C2SPublishNews.STREAM_CODEC,
                NewsNetwork::handlePublish
        );
        registrar.playToServer(
                NewsPayloads.C2SRequestNews.TYPE,
                NewsPayloads.C2SRequestNews.STREAM_CODEC,
                NewsNetwork::handleRequestNews
        );
        registrar.playToServer(
                NewsPayloads.C2SRequestFactionNews.TYPE,
                NewsPayloads.C2SRequestFactionNews.STREAM_CODEC,
                NewsNetwork::handleRequestFactionNews
        );
        registrar.playToClient(
                NewsPayloads.S2CNewsFactions.TYPE,
                NewsPayloads.S2CNewsFactions.STREAM_CODEC,
                NewsNetwork::handleNewsFactions
        );
        registrar.playToClient(
                NewsPayloads.S2CNewsArticles.TYPE,
                NewsPayloads.S2CNewsArticles.STREAM_CODEC,
                NewsNetwork::handleNewsArticles
        );
        registrar.playToClient(
                NewsPayloads.S2CNewsBadge.TYPE,
                NewsPayloads.S2CNewsBadge.STREAM_CODEC,
                NewsNetwork::handleNewsBadge
        );
    }

    private static void handlePublish(NewsPayloads.C2SPublishNews payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            NewsService.publish(player, payload.title(), payload.body());
        }
    }

    private static void handleRequestNews(NewsPayloads.C2SRequestNews payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            NewsService.sendNewsFactions(player);
        }
    }

    private static void handleRequestFactionNews(NewsPayloads.C2SRequestFactionNews payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            NewsService.sendFactionArticles(player, payload.factionId());
        }
    }

    private static void handleNewsFactions(NewsPayloads.S2CNewsFactions payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NewsScreen.handleFactions(payload);
        }
    }

    private static void handleNewsArticles(NewsPayloads.S2CNewsArticles payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NewsScreen.handleArticles(payload);
        }
    }

    private static void handleNewsBadge(NewsPayloads.S2CNewsBadge payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientNewsState.accept(payload.count());
        }
    }

    private NewsNetwork() {
    }
}
