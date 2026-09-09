package com.geydev.kalfactions.charon;

import com.geydev.kalfactions.KalFactions;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class CharonPayloads {
    public record S2CGhostState(UUID playerId, boolean active) implements CustomPacketPayload {
        public static final Type<S2CGhostState> TYPE = payloadType("ghost_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CGhostState> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeUUID(payload.playerId);
                    buffer.writeBoolean(payload.active);
                },
                buffer -> new S2CGhostState(buffer.readUUID(), buffer.readBoolean())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "charon_" + path)
        );
    }

    private CharonPayloads() {
    }
}
