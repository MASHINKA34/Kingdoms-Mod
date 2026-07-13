package com.geydev.kalfactions.tax;

import com.geydev.kalfactions.KalFactions;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class LagTaxPayloads {
    public static final int MAX_CHUNK_ENTRIES = 32;
    public static final int MAX_FACTION_ENTRIES = 32;
    public static final int MAX_DETAIL_ENTRIES = 32;
    public static final int MAX_METER_CHUNKS = 64;
    public static final int MAX_NAME_LENGTH = 64;

    public record C2SOpenAnalyzer() implements CustomPacketPayload {
        public static final C2SOpenAnalyzer INSTANCE = new C2SOpenAnalyzer();
        public static final Type<C2SOpenAnalyzer> TYPE = payloadType("open_analyzer");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SOpenAnalyzer> STREAM_CODEC =
                StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SAnalyzerTeleport(ResourceLocation dimension, int chunkX, int chunkZ)
            implements CustomPacketPayload {
        public static final Type<C2SAnalyzerTeleport> TYPE = payloadType("analyzer_teleport");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SAnalyzerTeleport> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeResourceLocation(payload.dimension);
                    buffer.writeVarInt(payload.chunkX);
                    buffer.writeVarInt(payload.chunkZ);
                },
                buffer -> new C2SAnalyzerTeleport(
                        buffer.readResourceLocation(),
                        buffer.readVarInt(),
                        buffer.readVarInt()
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SAnalyzerDetail(ResourceLocation dimension, int chunkX, int chunkZ)
            implements CustomPacketPayload {
        public static final Type<C2SAnalyzerDetail> TYPE = payloadType("analyzer_detail");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SAnalyzerDetail> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeResourceLocation(payload.dimension);
                    buffer.writeVarInt(payload.chunkX);
                    buffer.writeVarInt(payload.chunkZ);
                },
                buffer -> new C2SAnalyzerDetail(
                        buffer.readResourceLocation(),
                        buffer.readVarInt(),
                        buffer.readVarInt()
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SOpenMeter() implements CustomPacketPayload {
        public static final C2SOpenMeter INSTANCE = new C2SOpenMeter();
        public static final Type<C2SOpenMeter> TYPE = payloadType("open_meter");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SOpenMeter> STREAM_CODEC =
                StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SSetAutoRenew(boolean enabled) implements CustomPacketPayload {
        public static final Type<C2SSetAutoRenew> TYPE = payloadType("set_auto_renew");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SSetAutoRenew> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeBoolean(payload.enabled),
                buffer -> new C2SSetAutoRenew(buffer.readBoolean())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SBuyChunkLoad(ResourceLocation dimension, long packedChunk, int hours)
            implements CustomPacketPayload {
        public static final Type<C2SBuyChunkLoad> TYPE = payloadType("buy_chunk_load");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SBuyChunkLoad> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeResourceLocation(payload.dimension);
                    buffer.writeLong(payload.packedChunk);
                    buffer.writeVarInt(payload.hours);
                },
                buffer -> new C2SBuyChunkLoad(
                        buffer.readResourceLocation(),
                        buffer.readLong(),
                        buffer.readVarInt()
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ChunkEntry(
            ResourceLocation dimension,
            int chunkX,
            int chunkZ,
            long nanosPerTick,
            String factionName,
            boolean frozen
    ) {
        private static void encode(RegistryFriendlyByteBuf buffer, ChunkEntry entry) {
            buffer.writeResourceLocation(entry.dimension);
            buffer.writeVarInt(entry.chunkX);
            buffer.writeVarInt(entry.chunkZ);
            buffer.writeLong(entry.nanosPerTick);
            buffer.writeUtf(entry.factionName, MAX_NAME_LENGTH);
            buffer.writeBoolean(entry.frozen);
        }

        private static ChunkEntry decode(RegistryFriendlyByteBuf buffer) {
            return new ChunkEntry(
                    buffer.readResourceLocation(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readLong(),
                    buffer.readUtf(MAX_NAME_LENGTH),
                    buffer.readBoolean()
            );
        }
    }

    public record FactionEntry(String name, long nanosPerTick, long unpaidBill, boolean frozen) {
        private static void encode(RegistryFriendlyByteBuf buffer, FactionEntry entry) {
            buffer.writeUtf(entry.name, MAX_NAME_LENGTH);
            buffer.writeLong(entry.nanosPerTick);
            buffer.writeLong(entry.unpaidBill);
            buffer.writeBoolean(entry.frozen);
        }

        private static FactionEntry decode(RegistryFriendlyByteBuf buffer) {
            return new FactionEntry(
                    buffer.readUtf(MAX_NAME_LENGTH),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readBoolean()
            );
        }
    }

    public record S2CAnalyzerData(
            List<ChunkEntry> chunks,
            List<FactionEntry> factions,
            long quotaNanos
    ) implements CustomPacketPayload {
        public static final Type<S2CAnalyzerData> TYPE = payloadType("analyzer_data");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CAnalyzerData> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    int chunkCount = Math.min(payload.chunks.size(), MAX_CHUNK_ENTRIES);
                    buffer.writeVarInt(chunkCount);
                    for (int i = 0; i < chunkCount; i++) {
                        ChunkEntry.encode(buffer, payload.chunks.get(i));
                    }
                    int factionCount = Math.min(payload.factions.size(), MAX_FACTION_ENTRIES);
                    buffer.writeVarInt(factionCount);
                    for (int i = 0; i < factionCount; i++) {
                        FactionEntry.encode(buffer, payload.factions.get(i));
                    }
                    buffer.writeLong(payload.quotaNanos);
                },
                buffer -> {
                    int chunkCount = buffer.readVarInt();
                    if (chunkCount < 0 || chunkCount > MAX_CHUNK_ENTRIES) {
                        throw new DecoderException("Analyzer chunk count " + chunkCount + " exceeds " + MAX_CHUNK_ENTRIES);
                    }
                    List<ChunkEntry> chunks = new ArrayList<>(chunkCount);
                    for (int i = 0; i < chunkCount; i++) {
                        chunks.add(ChunkEntry.decode(buffer));
                    }
                    int factionCount = buffer.readVarInt();
                    if (factionCount < 0 || factionCount > MAX_FACTION_ENTRIES) {
                        throw new DecoderException("Analyzer faction count " + factionCount + " exceeds " + MAX_FACTION_ENTRIES);
                    }
                    List<FactionEntry> factions = new ArrayList<>(factionCount);
                    for (int i = 0; i < factionCount; i++) {
                        factions.add(FactionEntry.decode(buffer));
                    }
                    return new S2CAnalyzerData(List.copyOf(chunks), List.copyOf(factions), buffer.readLong());
                }
        );

        public S2CAnalyzerData {
            chunks = List.copyOf(chunks);
            factions = List.copyOf(factions);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record DetailEntry(BlockPos pos, String blockId, long nanosPerTick) {
        private static void encode(RegistryFriendlyByteBuf buffer, DetailEntry entry) {
            buffer.writeBlockPos(entry.pos);
            buffer.writeUtf(entry.blockId, MAX_NAME_LENGTH);
            buffer.writeLong(entry.nanosPerTick);
        }

        private static DetailEntry decode(RegistryFriendlyByteBuf buffer) {
            return new DetailEntry(
                    buffer.readBlockPos(),
                    buffer.readUtf(MAX_NAME_LENGTH),
                    buffer.readLong()
            );
        }
    }

    public record S2CChunkDetail(
            ResourceLocation dimension,
            int chunkX,
            int chunkZ,
            List<DetailEntry> entries
    ) implements CustomPacketPayload {
        public static final Type<S2CChunkDetail> TYPE = payloadType("chunk_detail");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CChunkDetail> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeResourceLocation(payload.dimension);
                    buffer.writeVarInt(payload.chunkX);
                    buffer.writeVarInt(payload.chunkZ);
                    int count = Math.min(payload.entries.size(), MAX_DETAIL_ENTRIES);
                    buffer.writeVarInt(count);
                    for (int i = 0; i < count; i++) {
                        DetailEntry.encode(buffer, payload.entries.get(i));
                    }
                },
                buffer -> {
                    ResourceLocation dimension = buffer.readResourceLocation();
                    int chunkX = buffer.readVarInt();
                    int chunkZ = buffer.readVarInt();
                    int count = buffer.readVarInt();
                    if (count < 0 || count > MAX_DETAIL_ENTRIES) {
                        throw new DecoderException("Chunk detail count " + count + " exceeds " + MAX_DETAIL_ENTRIES);
                    }
                    List<DetailEntry> entries = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        entries.add(DetailEntry.decode(buffer));
                    }
                    return new S2CChunkDetail(dimension, chunkX, chunkZ, List.copyOf(entries));
                }
        );

        public S2CChunkDetail {
            entries = List.copyOf(entries);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record MeterChunk(
            ResourceLocation dimension,
            int chunkX,
            int chunkZ,
            long nanosPerTick,
            boolean loaded,
            long expiresAtMillis
    ) {
        private static void encode(RegistryFriendlyByteBuf buffer, MeterChunk entry) {
            buffer.writeResourceLocation(entry.dimension);
            buffer.writeVarInt(entry.chunkX);
            buffer.writeVarInt(entry.chunkZ);
            buffer.writeLong(entry.nanosPerTick);
            buffer.writeBoolean(entry.loaded);
            buffer.writeLong(entry.expiresAtMillis);
        }

        private static MeterChunk decode(RegistryFriendlyByteBuf buffer) {
            return new MeterChunk(
                    buffer.readResourceLocation(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readLong(),
                    buffer.readBoolean(),
                    buffer.readLong()
            );
        }
    }

    public record S2CMeterData(
            long totalNanos,
            long quotaNanos,
            long forecastPerDay,
            long accruedBill,
            long unpaidBill,
            boolean frozen,
            boolean autoRenew,
            long loadPricePerHour,
            int loadMaxDays,
            List<MeterChunk> chunks
    ) implements CustomPacketPayload {
        public static final Type<S2CMeterData> TYPE = payloadType("meter_data");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CMeterData> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeLong(payload.totalNanos);
                    buffer.writeLong(payload.quotaNanos);
                    buffer.writeLong(payload.forecastPerDay);
                    buffer.writeLong(payload.accruedBill);
                    buffer.writeLong(payload.unpaidBill);
                    buffer.writeBoolean(payload.frozen);
                    buffer.writeBoolean(payload.autoRenew);
                    buffer.writeLong(payload.loadPricePerHour);
                    buffer.writeVarInt(payload.loadMaxDays);
                    int count = Math.min(payload.chunks.size(), MAX_METER_CHUNKS);
                    buffer.writeVarInt(count);
                    for (int i = 0; i < count; i++) {
                        MeterChunk.encode(buffer, payload.chunks.get(i));
                    }
                },
                buffer -> {
                    long totalNanos = buffer.readLong();
                    long quotaNanos = buffer.readLong();
                    long forecastPerDay = buffer.readLong();
                    long accruedBill = buffer.readLong();
                    long unpaidBill = buffer.readLong();
                    boolean frozen = buffer.readBoolean();
                    boolean autoRenew = buffer.readBoolean();
                    long loadPricePerHour = buffer.readLong();
                    int loadMaxDays = buffer.readVarInt();
                    int count = buffer.readVarInt();
                    if (count < 0 || count > MAX_METER_CHUNKS) {
                        throw new DecoderException("Meter chunk count " + count + " exceeds " + MAX_METER_CHUNKS);
                    }
                    List<MeterChunk> chunks = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        chunks.add(MeterChunk.decode(buffer));
                    }
                    return new S2CMeterData(
                            totalNanos,
                            quotaNanos,
                            forecastPerDay,
                            accruedBill,
                            unpaidBill,
                            frozen,
                            autoRenew,
                            loadPricePerHour,
                            loadMaxDays,
                            List.copyOf(chunks)
                    );
                }
        );

        public S2CMeterData {
            chunks = List.copyOf(chunks);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "lagtax_" + path)
        );
    }

    private LagTaxPayloads() {
    }
}
