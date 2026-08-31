package com.geydev.kalfactions.keyholder;

import com.geydev.kalfactions.KalFactions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class KeyHolderPayloads {
    public static final int MODE_NAME_LENGTH = 16;

    public record S2COpenSettings(BlockPos pos, String mode, int pulseTicks, boolean consumeKey)
            implements CustomPacketPayload {
        public static final Type<S2COpenSettings> TYPE = payloadType("open_settings");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2COpenSettings> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBlockPos(payload.pos);
                    buffer.writeUtf(payload.mode, MODE_NAME_LENGTH);
                    buffer.writeVarInt(payload.pulseTicks);
                    buffer.writeBoolean(payload.consumeKey);
                },
                buffer -> new S2COpenSettings(
                        buffer.readBlockPos(),
                        buffer.readUtf(MODE_NAME_LENGTH),
                        buffer.readVarInt(),
                        buffer.readBoolean()
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SUpdateSettings(BlockPos pos, String mode, int pulseTicks, boolean consumeKey)
            implements CustomPacketPayload {
        public static final Type<C2SUpdateSettings> TYPE = payloadType("update_settings");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SUpdateSettings> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBlockPos(payload.pos);
                    buffer.writeUtf(payload.mode, MODE_NAME_LENGTH);
                    buffer.writeVarInt(payload.pulseTicks);
                    buffer.writeBoolean(payload.consumeKey);
                },
                buffer -> new C2SUpdateSettings(
                        buffer.readBlockPos(),
                        buffer.readUtf(MODE_NAME_LENGTH),
                        buffer.readVarInt(),
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
                ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "key_holder_" + path)
        );
    }

    private KeyHolderPayloads() {
    }
}
