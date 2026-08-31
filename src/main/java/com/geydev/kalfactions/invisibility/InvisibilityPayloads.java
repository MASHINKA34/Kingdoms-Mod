package com.geydev.kalfactions.invisibility;

import com.geydev.kalfactions.KalFactions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class InvisibilityPayloads {
    public record S2COpenChaliceSettings(BlockPos pos, int durationSeconds) implements CustomPacketPayload {
        public static final Type<S2COpenChaliceSettings> TYPE = payloadType("chalice_open_settings");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2COpenChaliceSettings> STREAM_CODEC =
                StreamCodec.of(
                        (buffer, payload) -> {
                            buffer.writeBlockPos(payload.pos);
                            buffer.writeVarInt(payload.durationSeconds);
                        },
                        buffer -> new S2COpenChaliceSettings(buffer.readBlockPos(), buffer.readVarInt())
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SUpdateChaliceSettings(BlockPos pos, int durationSeconds) implements CustomPacketPayload {
        public static final Type<C2SUpdateChaliceSettings> TYPE = payloadType("chalice_update_settings");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SUpdateChaliceSettings> STREAM_CODEC =
                StreamCodec.of(
                        (buffer, payload) -> {
                            buffer.writeBlockPos(payload.pos);
                            buffer.writeVarInt(payload.durationSeconds);
                        },
                        buffer -> new C2SUpdateChaliceSettings(buffer.readBlockPos(), buffer.readVarInt())
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CTrueInvisibility(int entityId, boolean active) implements CustomPacketPayload {
        public static final Type<S2CTrueInvisibility> TYPE = payloadType("true_invisibility");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CTrueInvisibility> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeVarInt(payload.entityId);
                    buffer.writeBoolean(payload.active);
                },
                buffer -> new S2CTrueInvisibility(buffer.readVarInt(), buffer.readBoolean())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "invisibility_" + path)
        );
    }

    private InvisibilityPayloads() {
    }
}
