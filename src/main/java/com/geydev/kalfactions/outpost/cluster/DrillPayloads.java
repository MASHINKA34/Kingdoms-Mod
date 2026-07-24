package com.geydev.kalfactions.outpost.cluster;

import com.geydev.kalfactions.KalFactions;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class DrillPayloads {
    public static final int MAX_TARGETS = 2048;
    private static final int MAX_TYPE_LENGTH = 16;

    public record C2SSelectTarget(int containerId, long targetChunk) implements CustomPacketPayload {
        public static final Type<C2SSelectTarget> TYPE = payloadType("drill_select_target");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SSelectTarget> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeVarInt(payload.containerId);
                    buffer.writeLong(payload.targetChunk);
                },
                buffer -> new C2SSelectTarget(buffer.readVarInt(), buffer.readLong())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CTargets(
            int containerId,
            BlockPos drillPos,
            List<TargetInfo> targets
    ) implements CustomPacketPayload {
        public static final Type<S2CTargets> TYPE = payloadType("drill_targets");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CTargets> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    if (payload.targets.size() > MAX_TARGETS) {
                        throw new EncoderException("Drill target count exceeds " + MAX_TARGETS);
                    }
                    buffer.writeVarInt(payload.containerId);
                    buffer.writeBlockPos(payload.drillPos);
                    buffer.writeVarInt(payload.targets.size());
                    for (TargetInfo target : payload.targets) {
                        target.encode(buffer);
                    }
                },
                buffer -> {
                    int containerId = buffer.readVarInt();
                    BlockPos drillPos = buffer.readBlockPos();
                    int size = buffer.readVarInt();
                    if (size < 0 || size > MAX_TARGETS) {
                        throw new DecoderException("Drill target count " + size + " exceeds " + MAX_TARGETS);
                    }
                    List<TargetInfo> targets = new ArrayList<>(size);
                    for (int index = 0; index < size; index++) {
                        targets.add(TargetInfo.decode(buffer));
                    }
                    return new S2CTargets(containerId, drillPos, List.copyOf(targets));
                }
        );

        public S2CTargets {
            targets = List.copyOf(targets);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record TargetInfo(
            long chunk,
            BlockPos pos,
            String type,
            int richness,
            boolean selected,
            boolean available
    ) {
        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeLong(chunk);
            buffer.writeBlockPos(pos);
            buffer.writeUtf(type, MAX_TYPE_LENGTH);
            buffer.writeVarInt(richness);
            buffer.writeBoolean(selected);
            buffer.writeBoolean(available);
        }

        private static TargetInfo decode(RegistryFriendlyByteBuf buffer) {
            return new TargetInfo(
                    buffer.readLong(),
                    buffer.readBlockPos(),
                    buffer.readUtf(MAX_TYPE_LENGTH),
                    Math.clamp(buffer.readVarInt(), 0, 16),
                    buffer.readBoolean(),
                    buffer.readBoolean()
            );
        }
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, path));
    }

    private DrillPayloads() {
    }
}
