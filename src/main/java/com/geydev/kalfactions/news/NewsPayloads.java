package com.geydev.kalfactions.news;

import com.geydev.kalfactions.KalFactions;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class NewsPayloads {
    public static final int MAX_FACTION_ENTRIES = 256;
    public static final int MAX_ARTICLE_ENTRIES = 200;
    public static final int MAX_NAME_LENGTH = 32;
    public static final int MAX_EMBLEM_PIXELS = 1024;

    public record C2SPublishNews(String title, String body) implements CustomPacketPayload {
        public static final Type<C2SPublishNews> TYPE = payloadType("publish");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SPublishNews> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeUtf(payload.title, NewsManager.MAX_TITLE_LENGTH);
                    buffer.writeUtf(payload.body, NewsManager.MAX_BODY_LENGTH);
                },
                buffer -> new C2SPublishNews(
                        buffer.readUtf(NewsManager.MAX_TITLE_LENGTH),
                        buffer.readUtf(NewsManager.MAX_BODY_LENGTH)
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SRequestNews() implements CustomPacketPayload {
        public static final C2SRequestNews INSTANCE = new C2SRequestNews();
        public static final Type<C2SRequestNews> TYPE = payloadType("request_news");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SRequestNews> STREAM_CODEC =
                StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SRequestFactionNews(UUID factionId) implements CustomPacketPayload {
        public static final Type<C2SRequestFactionNews> TYPE = payloadType("request_faction_news");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SRequestFactionNews> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeUUID(payload.factionId),
                buffer -> new C2SRequestFactionNews(buffer.readUUID())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record NewsFactionEntry(
            UUID factionId,
            String name,
            int color,
            List<Integer> emblem,
            String emblemUrl,
            int articleCount,
            long latestMillis
    ) {
        public NewsFactionEntry {
            emblem = emblem == null ? List.of() : List.copyOf(emblem);
            emblemUrl = emblemUrl == null ? "" : emblemUrl;
        }

        private static void encode(RegistryFriendlyByteBuf buffer, NewsFactionEntry entry) {
            buffer.writeUUID(entry.factionId);
            buffer.writeUtf(entry.name, MAX_NAME_LENGTH);
            buffer.writeInt(entry.color);
            int pixels = Math.min(entry.emblem.size(), MAX_EMBLEM_PIXELS);
            buffer.writeVarInt(pixels);
            for (int i = 0; i < pixels; i++) {
                buffer.writeInt(entry.emblem.get(i));
            }
            buffer.writeUtf(entry.emblemUrl, 256);
            buffer.writeVarInt(entry.articleCount);
            buffer.writeLong(entry.latestMillis);
        }

        private static NewsFactionEntry decode(RegistryFriendlyByteBuf buffer) {
            UUID factionId = buffer.readUUID();
            String name = buffer.readUtf(MAX_NAME_LENGTH);
            int color = buffer.readInt();
            int pixels = buffer.readVarInt();
            if (pixels < 0 || pixels > MAX_EMBLEM_PIXELS) {
                throw new DecoderException("News emblem pixel count " + pixels + " exceeds " + MAX_EMBLEM_PIXELS);
            }
            List<Integer> emblem = new ArrayList<>(pixels);
            for (int i = 0; i < pixels; i++) {
                emblem.add(buffer.readInt());
            }
            return new NewsFactionEntry(
                    factionId,
                    name,
                    color,
                    emblem,
                    buffer.readUtf(256),
                    buffer.readVarInt(),
                    buffer.readLong()
            );
        }
    }

    public record S2CNewsFactions(List<NewsFactionEntry> factions) implements CustomPacketPayload {
        public static final Type<S2CNewsFactions> TYPE = payloadType("factions");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CNewsFactions> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    int count = Math.min(payload.factions.size(), MAX_FACTION_ENTRIES);
                    buffer.writeVarInt(count);
                    for (int i = 0; i < count; i++) {
                        NewsFactionEntry.encode(buffer, payload.factions.get(i));
                    }
                },
                buffer -> {
                    int count = buffer.readVarInt();
                    if (count < 0 || count > MAX_FACTION_ENTRIES) {
                        throw new DecoderException("News faction count " + count + " exceeds " + MAX_FACTION_ENTRIES);
                    }
                    List<NewsFactionEntry> factions = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        factions.add(NewsFactionEntry.decode(buffer));
                    }
                    return new S2CNewsFactions(List.copyOf(factions));
                }
        );

        public S2CNewsFactions {
            factions = List.copyOf(factions);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ArticleEntry(UUID id, String title, String body, String author, long publishedAtMillis) {
        private static void encode(RegistryFriendlyByteBuf buffer, ArticleEntry entry) {
            buffer.writeUUID(entry.id);
            buffer.writeUtf(entry.title, NewsManager.MAX_TITLE_LENGTH);
            buffer.writeUtf(entry.body, NewsManager.MAX_BODY_LENGTH);
            buffer.writeUtf(entry.author, NewsManager.MAX_AUTHOR_LENGTH);
            buffer.writeLong(entry.publishedAtMillis);
        }

        private static ArticleEntry decode(RegistryFriendlyByteBuf buffer) {
            return new ArticleEntry(
                    buffer.readUUID(),
                    buffer.readUtf(NewsManager.MAX_TITLE_LENGTH),
                    buffer.readUtf(NewsManager.MAX_BODY_LENGTH),
                    buffer.readUtf(NewsManager.MAX_AUTHOR_LENGTH),
                    buffer.readLong()
            );
        }
    }

    public record S2CNewsArticles(UUID factionId, String factionName, List<ArticleEntry> articles)
            implements CustomPacketPayload {
        public static final Type<S2CNewsArticles> TYPE = payloadType("articles");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CNewsArticles> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeUUID(payload.factionId);
                    buffer.writeUtf(payload.factionName, MAX_NAME_LENGTH);
                    int count = Math.min(payload.articles.size(), MAX_ARTICLE_ENTRIES);
                    buffer.writeVarInt(count);
                    for (int i = 0; i < count; i++) {
                        ArticleEntry.encode(buffer, payload.articles.get(i));
                    }
                },
                buffer -> {
                    UUID factionId = buffer.readUUID();
                    String factionName = buffer.readUtf(MAX_NAME_LENGTH);
                    int count = buffer.readVarInt();
                    if (count < 0 || count > MAX_ARTICLE_ENTRIES) {
                        throw new DecoderException("News article count " + count + " exceeds " + MAX_ARTICLE_ENTRIES);
                    }
                    List<ArticleEntry> articles = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        articles.add(ArticleEntry.decode(buffer));
                    }
                    return new S2CNewsArticles(factionId, factionName, List.copyOf(articles));
                }
        );

        public S2CNewsArticles {
            articles = List.copyOf(articles);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CNewsBadge(int count) implements CustomPacketPayload {
        public static final Type<S2CNewsBadge> TYPE = payloadType("badge");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CNewsBadge> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeVarInt(payload.count),
                buffer -> new S2CNewsBadge(buffer.readVarInt())
        );

        public S2CNewsBadge {
            count = Math.max(0, count);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "news_" + path)
        );
    }

    private NewsPayloads() {
    }
}
