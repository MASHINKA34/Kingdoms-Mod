package com.geydev.kalfactions.dimension;

import com.geydev.kalfactions.KalFactions;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class DimensionPayloads {
    public static final int ACTION_OPEN = 0;
    public static final int ACTION_CLOSE = 1;
    public static final int ACTION_WIPE_SCHEDULE = 2;
    public static final int ACTION_WIPE_CANCEL = 3;

    public record C2SDimensionAction(boolean end, int action) implements CustomPacketPayload {
        public static final Type<C2SDimensionAction> TYPE = payloadType("dimension_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SDimensionAction> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBoolean(payload.end);
                    buffer.writeVarInt(payload.action);
                },
                buffer -> {
                    boolean end = buffer.readBoolean();
                    int action = buffer.readVarInt();
                    if (action < ACTION_OPEN || action > ACTION_WIPE_CANCEL) {
                        throw new DecoderException("Unknown dimension action " + action);
                    }
                    return new C2SDimensionAction(end, action);
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CDimensionState(
            boolean netherClosed,
            boolean netherWipePending,
            int netherPlayers,
            boolean endClosed,
            boolean endWipePending,
            int endPlayers,
            Component notice,
            boolean successful
    ) implements CustomPacketPayload {
        public static final Type<S2CDimensionState> TYPE = payloadType("dimension_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CDimensionState> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBoolean(payload.netherClosed);
                    buffer.writeBoolean(payload.netherWipePending);
                    buffer.writeVarInt(payload.netherPlayers);
                    buffer.writeBoolean(payload.endClosed);
                    buffer.writeBoolean(payload.endWipePending);
                    buffer.writeVarInt(payload.endPlayers);
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, payload.notice);
                    buffer.writeBoolean(payload.successful);
                },
                buffer -> new S2CDimensionState(
                        buffer.readBoolean(),
                        buffer.readBoolean(),
                        buffer.readVarInt(),
                        buffer.readBoolean(),
                        buffer.readBoolean(),
                        buffer.readVarInt(),
                        ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer),
                        buffer.readBoolean()
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, path)
        );
    }

    private DimensionPayloads() {
    }
}
