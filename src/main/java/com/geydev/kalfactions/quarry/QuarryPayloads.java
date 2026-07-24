package com.geydev.kalfactions.quarry;

import com.geydev.kalfactions.KalFactions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class QuarryPayloads {
    public static final int ACTION_NONE = 0;
    public static final int ACTION_ACTIVATE = 1;
    public static final int ACTION_UPGRADE = 2;
    public static final int ACTION_CAPTURE = 3;
    public static final int STATUS_NEUTRAL = 0;
    public static final int STATUS_OWNED = 1;
    public static final int STATUS_UNDER_ATTACK = 2;
    public static final int REASON_NONE = 0;
    public static final int REASON_NOT_IN_FACTION = 1;
    public static final int REASON_REQUIRES_ACTIVATOR = 2;
    public static final int REASON_NO_PERMISSION = 3;
    public static final int REASON_INSUFFICIENT_FUNDS = 4;
    public static final int REASON_MAX_LEVEL = 5;
    public static final int REASON_CAPTURE_ACTIVE = 6;
    public static final int REASON_CAPTURE_BUSY = 7;
    private static final int MAX_NAME_LENGTH = 48;

    public record C2SRequestState(int containerId, BlockPos core) implements CustomPacketPayload {
        public static final Type<C2SRequestState> TYPE = payloadType("quarry_request_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SRequestState> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeInt(payload.containerId);
                    buffer.writeBlockPos(payload.core);
                },
                buffer -> new C2SRequestState(buffer.readInt(), buffer.readBlockPos())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SAction(
            int containerId,
            BlockPos core,
            long stateVersion,
            int action
    ) implements CustomPacketPayload {
        public static final Type<C2SAction> TYPE = payloadType("quarry_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SAction> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeInt(payload.containerId);
                    buffer.writeBlockPos(payload.core);
                    buffer.writeLong(payload.stateVersion);
                    buffer.writeInt(payload.action);
                },
                buffer -> new C2SAction(
                        buffer.readInt(),
                        buffer.readBlockPos(),
                        buffer.readLong(),
                        buffer.readInt()
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CState(
            int containerId,
            BlockPos core,
            long stateVersion,
            int status,
            String ownerName,
            int ownerColor,
            int level,
            int nextLevel,
            long nextCost,
            String viewerFactionName,
            int viewerFactionColor,
            long treasury,
            String attackerName,
            int attackerColor,
            int captureTicksRemaining,
            boolean capturePaused,
            int action,
            boolean actionEnabled,
            int reason
    ) implements CustomPacketPayload {
        public static final Type<S2CState> TYPE = payloadType("quarry_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CState> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeInt(payload.containerId);
                    buffer.writeBlockPos(payload.core);
                    buffer.writeLong(payload.stateVersion);
                    buffer.writeByte(payload.status);
                    buffer.writeUtf(payload.ownerName, MAX_NAME_LENGTH);
                    buffer.writeInt(payload.ownerColor);
                    buffer.writeByte(payload.level);
                    buffer.writeByte(payload.nextLevel);
                    buffer.writeLong(payload.nextCost);
                    buffer.writeUtf(payload.viewerFactionName, MAX_NAME_LENGTH);
                    buffer.writeInt(payload.viewerFactionColor);
                    buffer.writeLong(payload.treasury);
                    buffer.writeUtf(payload.attackerName, MAX_NAME_LENGTH);
                    buffer.writeInt(payload.attackerColor);
                    buffer.writeInt(payload.captureTicksRemaining);
                    buffer.writeBoolean(payload.capturePaused);
                    buffer.writeByte(payload.action);
                    buffer.writeBoolean(payload.actionEnabled);
                    buffer.writeByte(payload.reason);
                },
                buffer -> new S2CState(
                        buffer.readInt(),
                        buffer.readBlockPos(),
                        Math.max(0L, buffer.readLong()),
                        Math.clamp(buffer.readByte(), STATUS_NEUTRAL, STATUS_UNDER_ATTACK),
                        buffer.readUtf(MAX_NAME_LENGTH),
                        buffer.readInt() & 0xFFFFFF,
                        Math.clamp(buffer.readByte(), 0, QuarryManager.MAX_LEVEL),
                        Math.clamp(buffer.readByte(), 0, QuarryManager.MAX_LEVEL),
                        Math.max(0L, buffer.readLong()),
                        buffer.readUtf(MAX_NAME_LENGTH),
                        buffer.readInt() & 0xFFFFFF,
                        Math.max(0L, buffer.readLong()),
                        buffer.readUtf(MAX_NAME_LENGTH),
                        buffer.readInt() & 0xFFFFFF,
                        Math.clamp(buffer.readInt(), 0, QuarryManager.CAPTURE_TICKS),
                        buffer.readBoolean(),
                        Math.clamp(buffer.readByte(), ACTION_NONE, ACTION_CAPTURE),
                        buffer.readBoolean(),
                        Math.clamp(buffer.readByte(), REASON_NONE, REASON_CAPTURE_BUSY)
                )
        );

        public S2CState {
            ownerName = bounded(ownerName);
            viewerFactionName = bounded(viewerFactionName);
            attackerName = bounded(attackerName);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static String bounded(String value) {
        String result = value == null ? "" : value.strip();
        return result.length() <= MAX_NAME_LENGTH ? result : result.substring(0, MAX_NAME_LENGTH);
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, path));
    }

    private QuarryPayloads() {
    }
}
