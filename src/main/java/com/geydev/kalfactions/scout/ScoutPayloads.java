package com.geydev.kalfactions.scout;

import com.geydev.kalfactions.KalFactions;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class ScoutPayloads {
    public static final int MAX_PACKAGES = 8;
    public static final int MAX_CHUNK_COORDINATE = 1_875_000;

    public record PackageEntry(int ordinal, int sizeChunks, int durationMinutes, long price) {
        private static void encode(RegistryFriendlyByteBuf buffer, PackageEntry entry) {
            buffer.writeVarInt(entry.ordinal);
            buffer.writeVarInt(entry.sizeChunks);
            buffer.writeVarInt(entry.durationMinutes);
            buffer.writeLong(entry.price);
        }

        private static PackageEntry decode(RegistryFriendlyByteBuf buffer) {
            return new PackageEntry(
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readLong()
            );
        }
    }

    public record S2COpenScout(
            List<PackageEntry> packages,
            long treasuryBalance,
            boolean canOrder,
            BlockPos scoutPos
    ) implements CustomPacketPayload {
        public static final Type<S2COpenScout> TYPE = payloadType("open");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2COpenScout> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    int count = Math.min(payload.packages.size(), MAX_PACKAGES);
                    buffer.writeVarInt(count);
                    for (int index = 0; index < count; index++) {
                        PackageEntry.encode(buffer, payload.packages.get(index));
                    }
                    buffer.writeLong(payload.treasuryBalance);
                    buffer.writeBoolean(payload.canOrder);
                    buffer.writeBlockPos(payload.scoutPos);
                },
                buffer -> {
                    int count = buffer.readVarInt();
                    if (count < 0 || count > MAX_PACKAGES) {
                        throw new DecoderException("Scout package count " + count + " exceeds " + MAX_PACKAGES);
                    }
                    List<PackageEntry> packages = new ArrayList<>(count);
                    for (int index = 0; index < count; index++) {
                        packages.add(PackageEntry.decode(buffer));
                    }
                    return new S2COpenScout(
                            packages,
                            buffer.readLong(),
                            buffer.readBoolean(),
                            buffer.readBlockPos()
                    );
                }
        );

        public S2COpenScout {
            packages = packages == null ? List.of() : List.copyOf(packages);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CScoutState(
            boolean active,
            long endsAtMillis,
            int progressPercent,
            int sizeChunks,
            int centerChunkX,
            int centerChunkZ
    ) implements CustomPacketPayload {
        public static final Type<S2CScoutState> TYPE = payloadType("state");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CScoutState> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBoolean(payload.active);
                    buffer.writeLong(payload.endsAtMillis);
                    buffer.writeVarInt(payload.progressPercent);
                    buffer.writeVarInt(payload.sizeChunks);
                    buffer.writeInt(payload.centerChunkX);
                    buffer.writeInt(payload.centerChunkZ);
                },
                buffer -> new S2CScoutState(
                        buffer.readBoolean(),
                        buffer.readLong(),
                        buffer.readVarInt(),
                        buffer.readVarInt(),
                        buffer.readInt(),
                        buffer.readInt()
                )
        );

        public static S2CScoutState idle() {
            return new S2CScoutState(false, 0L, 0, 0, 0, 0);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SScoutOrder(
            int packageOrdinal,
            ResourceLocation dimension,
            int centerChunkX,
            int centerChunkZ
    ) implements CustomPacketPayload {
        public static final Type<C2SScoutOrder> TYPE = payloadType("order");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SScoutOrder> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeVarInt(payload.packageOrdinal);
                    buffer.writeResourceLocation(payload.dimension);
                    buffer.writeInt(payload.centerChunkX);
                    buffer.writeInt(payload.centerChunkZ);
                },
                buffer -> {
                    int ordinal = buffer.readVarInt();
                    if (ordinal < 0 || ordinal >= MAX_PACKAGES) {
                        throw new DecoderException("Invalid scout package ordinal " + ordinal);
                    }
                    ResourceLocation dimension = buffer.readResourceLocation();
                    int centerChunkX = buffer.readInt();
                    int centerChunkZ = buffer.readInt();
                    if (Math.abs(centerChunkX) > MAX_CHUNK_COORDINATE || Math.abs(centerChunkZ) > MAX_CHUNK_COORDINATE) {
                        throw new DecoderException("Scout centre chunk is out of bounds");
                    }
                    return new C2SScoutOrder(ordinal, dimension, centerChunkX, centerChunkZ);
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SRequestScoutState() implements CustomPacketPayload {
        public static final C2SRequestScoutState INSTANCE = new C2SRequestScoutState();
        public static final Type<C2SRequestScoutState> TYPE = payloadType("request_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SRequestScoutState> STREAM_CODEC =
                StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "scout_" + path)
        );
    }

    private ScoutPayloads() {
    }
}
