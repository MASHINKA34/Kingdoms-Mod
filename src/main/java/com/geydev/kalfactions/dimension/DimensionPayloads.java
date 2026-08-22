package com.geydev.kalfactions.dimension;

import com.geydev.kalfactions.KalFactions;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public final class DimensionPayloads {
    public static final int ACTION_OPEN = 0;
    public static final int ACTION_CLOSE = 1;
    public static final int ACTION_WIPE_SCHEDULE = 2;
    public static final int ACTION_WIPE_CANCEL = 3;

    public record C2SDimensionAction(UUID interactionId, boolean end, int action) implements CustomPacketPayload {
        public static final Type<C2SDimensionAction> TYPE = payloadType("dimension_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SDimensionAction> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeUUID(payload.interactionId);
                    buffer.writeBoolean(payload.end);
                    buffer.writeVarInt(payload.action);
                },
                buffer -> {
                    UUID interactionId = buffer.readUUID();
                    boolean end = buffer.readBoolean();
                    int action = buffer.readVarInt();
                    if (action < ACTION_OPEN || action > ACTION_WIPE_CANCEL) {
                        throw new DecoderException("Unknown dimension action " + action);
                    }
                    return new C2SDimensionAction(interactionId, end, action);
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CDimensionState(
            UUID interactionId,
            boolean netherClosed,
            boolean netherWipePending,
            int netherPlayers,
            boolean netherScheduleOpen,
            long netherSecondsUntilClose,
            int netherActiveSessions,
            boolean netherPortalRegistered,
            boolean endClosed,
            boolean endWipePending,
            int endPlayers,
            Component notice,
            boolean successful
    ) implements CustomPacketPayload {
        public static final Type<S2CDimensionState> TYPE = payloadType("dimension_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CDimensionState> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeUUID(payload.interactionId);
                    buffer.writeBoolean(payload.netherClosed);
                    buffer.writeBoolean(payload.netherWipePending);
                    buffer.writeVarInt(payload.netherPlayers);
                    buffer.writeBoolean(payload.netherScheduleOpen);
                    buffer.writeVarLong(payload.netherSecondsUntilClose);
                    buffer.writeVarInt(payload.netherActiveSessions);
                    buffer.writeBoolean(payload.netherPortalRegistered);
                    buffer.writeBoolean(payload.endClosed);
                    buffer.writeBoolean(payload.endWipePending);
                    buffer.writeVarInt(payload.endPlayers);
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, payload.notice);
                    buffer.writeBoolean(payload.successful);
                },
                buffer -> new S2CDimensionState(
                        buffer.readUUID(),
                        buffer.readBoolean(),
                        buffer.readBoolean(),
                        buffer.readVarInt(),
                        buffer.readBoolean(),
                        buffer.readVarLong(),
                        buffer.readVarInt(),
                        buffer.readBoolean(),
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

    public record C2SNetherStatusRequest() implements CustomPacketPayload {
        public static final C2SNetherStatusRequest INSTANCE = new C2SNetherStatusRequest();
        public static final Type<C2SNetherStatusRequest> TYPE = payloadType("nether_status_request");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SNetherStatusRequest> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                },
                buffer -> INSTANCE
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CNetherStatus(
            long serverNowEpochMillis,
            long phaseEndsAtEpochMillis,
            boolean openForPlayers,
            boolean administrativelyClosed,
            int remainingSessions,
            int totalSessions,
            long sessionEndsAtEpochMillis,
            long portalChargedUntilEpochMillis
    ) implements CustomPacketPayload {
        public static final Type<S2CNetherStatus> TYPE = payloadType("nether_status");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CNetherStatus> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeLong(payload.serverNowEpochMillis);
                    buffer.writeLong(payload.phaseEndsAtEpochMillis);
                    buffer.writeBoolean(payload.openForPlayers);
                    buffer.writeBoolean(payload.administrativelyClosed);
                    buffer.writeVarInt(payload.remainingSessions + 1);
                    buffer.writeVarInt(payload.totalSessions);
                    buffer.writeLong(payload.sessionEndsAtEpochMillis);
                    buffer.writeLong(payload.portalChargedUntilEpochMillis);
                },
                S2CNetherStatus::decodeStatus
        );

        public S2CNetherStatus {
            if (remainingSessions < -1 || remainingSessions > 16
                    || remainingSessions >= 0 && remainingSessions > totalSessions) {
                throw new IllegalArgumentException("remainingSessions");
            }
            if (totalSessions < 1 || totalSessions > 16) {
                throw new IllegalArgumentException("totalSessions");
            }
            if (phaseEndsAtEpochMillis < 0L || sessionEndsAtEpochMillis < 0L
                    || portalChargedUntilEpochMillis < 0L) {
                throw new IllegalArgumentException("end time");
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static S2CNetherStatus decodeStatus(RegistryFriendlyByteBuf buffer) {
            long serverNow = buffer.readLong();
            long phaseEnd = buffer.readLong();
            boolean open = buffer.readBoolean();
            boolean closed = buffer.readBoolean();
            int remaining = buffer.readVarInt() - 1;
            int total = buffer.readVarInt();
            long sessionEnd = buffer.readLong();
            long portalChargedUntil = buffer.readLong();
            if (remaining < -1 || remaining > 16 || total < 1 || total > 16
                    || remaining >= 0 && remaining > total
                    || serverNow < 0L || phaseEnd < 0L || sessionEnd < 0L || portalChargedUntil < 0L) {
                throw new DecoderException("Invalid Nether status payload");
            }
            return new S2CNetherStatus(
                    serverNow, phaseEnd, open, closed, remaining, total, sessionEnd, portalChargedUntil
            );
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
