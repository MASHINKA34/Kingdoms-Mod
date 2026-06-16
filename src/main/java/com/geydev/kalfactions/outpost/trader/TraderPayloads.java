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
    public static final int MAX_SELL_OFFERS = 16;
    public static final int MAX_OFFER_ID_LENGTH = 32;

    public record C2SBuy(UUID traderId, String offerId) implements CustomPacketPayload {
        public static final Type<C2SBuy> TYPE = payloadType("trader_buy");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SBuy> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeUUID(payload.traderId);
                    buffer.writeUtf(payload.offerId, MAX_OFFER_ID_LENGTH);
                },
                buffer -> new C2SBuy(
                        buffer.readUUID(),
                        buffer.readUtf(MAX_OFFER_ID_LENGTH)
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SSell(UUID traderId, String offerId) implements CustomPacketPayload {
        public static final Type<C2SSell> TYPE = payloadType("trader_sell");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SSell> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeUUID(payload.traderId);
                    buffer.writeUtf(payload.offerId, MAX_OFFER_ID_LENGTH);
                },
                buffer -> new C2SSell(
                        buffer.readUUID(),
                        buffer.readUtf(MAX_OFFER_ID_LENGTH)
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CShopState(
            UUID traderId,
            List<OfferInfo> offers,
            List<OfferInfo> sellOffers,
            Component notice,
            boolean successful
    ) implements CustomPacketPayload {
        public static final Type<S2CShopState> TYPE = payloadType("trader_shop_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CShopState> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeUUID(payload.traderId);
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
                },
                buffer -> {
                    UUID traderId = buffer.readUUID();
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
                            List.copyOf(offers),
                            List.copyOf(sellOffers),
                            ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer),
                            buffer.readBoolean()
                    );
                }
        );

        public S2CShopState {
            offers = List.copyOf(offers);
            sellOffers = List.copyOf(sellOffers);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record OfferInfo(String id, long price) {
        private static void encode(RegistryFriendlyByteBuf buffer, OfferInfo offer) {
            buffer.writeUtf(offer.id, MAX_OFFER_ID_LENGTH);
            buffer.writeLong(offer.price);
        }

        private static OfferInfo decode(RegistryFriendlyByteBuf buffer) {
            return new OfferInfo(
                    buffer.readUtf(MAX_OFFER_ID_LENGTH),
                    buffer.readLong()
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
