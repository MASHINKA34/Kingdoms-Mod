package com.geydev.kalfactions.outpost;

import com.geydev.kalfactions.KalFactions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class OutpostPayloads {
    public record S2COutpostState(BlockPos core, String factionName, boolean canManage) implements CustomPacketPayload {
        public static final Type<S2COutpostState> TYPE = payloadType("outpost_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2COutpostState> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBlockPos(payload.core);
                    buffer.writeUtf(payload.factionName, 32);
                    buffer.writeBoolean(payload.canManage);
                },
                buffer -> new S2COutpostState(buffer.readBlockPos(), buffer.readUtf(32), buffer.readBoolean())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SDismantleOutpost(BlockPos core) implements CustomPacketPayload {
        public static final Type<C2SDismantleOutpost> TYPE = payloadType("dismantle_outpost");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SDismantleOutpost> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeBlockPos(payload.core),
                buffer -> new C2SDismantleOutpost(buffer.readBlockPos())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, path));
    }

    private OutpostPayloads() {
    }
}
