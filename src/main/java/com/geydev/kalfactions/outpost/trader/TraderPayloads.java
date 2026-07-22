package com.geydev.kalfactions.outpost.trader;

import com.geydev.kalfactions.KalFactions;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class TraderPayloads {
    public static final int MAX_OFFERS = 8;
    public static final int MAX_SELL_OFFERS = 9;
    public static final int MAX_SELLERS = 32;
    public static final int MAX_OFFER_ID_LENGTH = 64;
    public static final int MAX_TITLE_KEY_LENGTH = 128;
    public static final int MAX_ITEM_ID_LENGTH = 128;

    public record C2SBuy(UUID traderId, UUID sessionId, long sequence, String offerId) implements CustomPacketPayload {
        public static final Type<C2SBuy> TYPE = payloadType("trader_buy");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SBuy> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeUUID(payload.traderId);
                    buffer.writeUUID(payload.sessionId);
                    buffer.writeVarLong(payload.sequence);
                    buffer.writeUtf(payload.offerId, MAX_OFFER_ID_LENGTH);
                },
                buffer -> new C2SBuy(
                        buffer.readUUID(),
                        buffer.readUUID(),
                        buffer.readVarLong(),
                        buffer.readUtf(MAX_OFFER_ID_LENGTH)
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SSell(UUID traderId, UUID sessionId, long sequence, String offerId, int amount) implements CustomPacketPayload {
        public static final Type<C2SSell> TYPE = payloadType("trader_sell");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SSell> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeUUID(payload.traderId);
                    buffer.writeUUID(payload.sessionId);
                    buffer.writeVarLong(payload.sequence);
                    buffer.writeUtf(payload.offerId, MAX_OFFER_ID_LENGTH);
                    buffer.writeVarInt(payload.amount);
                },
                buffer -> new C2SSell(
                        buffer.readUUID(),
                        buffer.readUUID(),
                        buffer.readVarLong(),
                        buffer.readUtf(MAX_OFFER_ID_LENGTH),
                        buffer.readVarInt()
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SRefreshSeller(UUID traderId, UUID sessionId) implements CustomPacketPayload {
        public static final Type<C2SRefreshSeller> TYPE = payloadType("trader_seller_refresh");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SRefreshSeller> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeUUID(payload.traderId);
                    buffer.writeUUID(payload.sessionId);
                },
                buffer -> new C2SRefreshSeller(buffer.readUUID(), buffer.readUUID())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SCloseTrader(UUID traderId, UUID sessionId) implements CustomPacketPayload {
        public static final Type<C2SCloseTrader> TYPE = payloadType("trader_close");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SCloseTrader> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeUUID(payload.traderId);
                    buffer.writeUUID(payload.sessionId);
                },
                buffer -> new C2SCloseTrader(buffer.readUUID(), buffer.readUUID())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CShopState(
            UUID traderId,
            UUID sessionId,
            long acknowledgedSequence,
            String titleKey,
            List<OfferInfo> offers,
            List<OfferInfo> sellOffers,
            Component notice,
            boolean successful,
            long nextSellRefreshEpochMillis
    ) implements CustomPacketPayload {
        public static final Type<S2CShopState> TYPE = payloadType("trader_shop_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CShopState> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeUUID(payload.traderId);
                    buffer.writeUUID(payload.sessionId);
                    buffer.writeVarLong(payload.acknowledgedSequence);
                    buffer.writeUtf(payload.titleKey, MAX_TITLE_KEY_LENGTH);
                    int size = Math.min(payload.offers.size(), MAX_OFFERS);
                    buffer.writeVarInt(size);
                    for (int i = 0; i < size; i++) {
                        OfferInfo.encode(buffer, payload.offers.get(i));
                    }
                    int sellSize = Math.min(payload.sellOffers.size(), MAX_SELL_OFFERS);
                    buffer.writeVarInt(sellSize);
                    for (int i = 0; i < sellSize; i++) {
                        OfferInfo.encode(buffer, payload.sellOffers.get(i));
                    }
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, payload.notice);
                    buffer.writeBoolean(payload.successful);
                    buffer.writeLong(payload.nextSellRefreshEpochMillis);
                },
                buffer -> {
                    UUID traderId = buffer.readUUID();
                    UUID sessionId = buffer.readUUID();
                    long acknowledgedSequence = buffer.readVarLong();
                    String titleKey = buffer.readUtf(MAX_TITLE_KEY_LENGTH);
                    int size = buffer.readVarInt();
                    if (size < 0 || size > MAX_OFFERS) {
                        throw new DecoderException("Trader offer count " + size + " exceeds " + MAX_OFFERS);
                    }
                    List<OfferInfo> offers = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        offers.add(OfferInfo.decode(buffer));
                    }
                    int sellSize = buffer.readVarInt();
                    if (sellSize < 0 || sellSize > MAX_SELL_OFFERS) {
                        throw new DecoderException("Trader sell offer count " + sellSize + " exceeds " + MAX_SELL_OFFERS);
                    }
                    List<OfferInfo> sellOffers = new ArrayList<>(sellSize);
                    for (int i = 0; i < sellSize; i++) {
                        sellOffers.add(OfferInfo.decode(buffer));
                    }
                    return new S2CShopState(
                            traderId,
                            sessionId,
                            acknowledgedSequence,
                            titleKey,
                            List.copyOf(offers),
                            List.copyOf(sellOffers),
                            ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer),
                            buffer.readBoolean(),
                            buffer.readLong()
                    );
                }
        );

        public S2CShopState {
            titleKey = titleKey == null || titleKey.isBlank() ? "screen.kingdoms.trader.title" : titleKey;
            offers = List.copyOf(offers);
            sellOffers = List.copyOf(sellOffers);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CSellerCatalog(List<SellerInfo> sellers) implements CustomPacketPayload {
        public static final Type<S2CSellerCatalog> TYPE = payloadType("seller_catalog");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CSellerCatalog> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    int size = Math.min(payload.sellers.size(), MAX_SELLERS);
                    buffer.writeVarInt(size);
                    for (int i = 0; i < size; i++) {
                        SellerInfo.encode(buffer, payload.sellers.get(i));
                    }
                },
                buffer -> {
                    int size = buffer.readVarInt();
                    if (size < 0 || size > MAX_SELLERS) {
                        throw new DecoderException("Seller count " + size + " exceeds " + MAX_SELLERS);
                    }
                    List<SellerInfo> sellers = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        sellers.add(SellerInfo.decode(buffer));
                    }
                    return new S2CSellerCatalog(List.copyOf(sellers));
                }
        );

        public S2CSellerCatalog {
            sellers = List.copyOf(sellers);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SellerInfo(
            UUID sellerId,
            int index,
            List<OfferInfo> offers,
            long nextRefreshEpochMillis
    ) {
        private static void encode(RegistryFriendlyByteBuf buffer, SellerInfo seller) {
            buffer.writeUUID(seller.sellerId);
            buffer.writeVarInt(seller.index);
            int size = Math.min(seller.offers.size(), MAX_SELL_OFFERS);
            buffer.writeVarInt(size);
            for (int i = 0; i < size; i++) {
                OfferInfo.encode(buffer, seller.offers.get(i));
            }
            buffer.writeLong(seller.nextRefreshEpochMillis);
        }

        private static SellerInfo decode(RegistryFriendlyByteBuf buffer) {
            UUID sellerId = buffer.readUUID();
            int index = buffer.readVarInt();
            int size = buffer.readVarInt();
            if (size < 0 || size > MAX_SELL_OFFERS) {
                throw new DecoderException("Seller catalog offer count " + size + " exceeds " + MAX_SELL_OFFERS);
            }
            List<OfferInfo> offers = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                offers.add(OfferInfo.decode(buffer));
            }
            return new SellerInfo(sellerId, index, List.copyOf(offers), buffer.readLong());
        }

        public SellerInfo {
            index = Math.max(1, index);
            offers = List.copyOf(offers);
        }
    }

    public record OfferInfo(
            String id,
            String itemId,
            int itemCount,
            long price,
            int remainingLimit,
            boolean permanent
    ) {
        private static void encode(RegistryFriendlyByteBuf buffer, OfferInfo offer) {
            buffer.writeUtf(offer.id, MAX_OFFER_ID_LENGTH);
            buffer.writeUtf(offer.itemId, MAX_ITEM_ID_LENGTH);
            buffer.writeVarInt(offer.itemCount);
            buffer.writeLong(offer.price);
            buffer.writeVarInt(offer.remainingLimit);
            buffer.writeBoolean(offer.permanent);
        }

        private static OfferInfo decode(RegistryFriendlyByteBuf buffer) {
            return new OfferInfo(
                    buffer.readUtf(MAX_OFFER_ID_LENGTH),
                    buffer.readUtf(MAX_ITEM_ID_LENGTH),
                    Math.clamp(buffer.readVarInt(), 1, 64),
                    buffer.readLong(),
                    buffer.readVarInt(),
                    buffer.readBoolean()
            );
        }
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, path)
        );
    }

    private TraderPayloads() {
    }
}
