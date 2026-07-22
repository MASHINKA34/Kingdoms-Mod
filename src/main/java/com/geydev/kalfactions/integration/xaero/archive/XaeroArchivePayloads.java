package com.geydev.kalfactions.integration.xaero.archive;

import com.geydev.kalfactions.KalFactions;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class XaeroArchivePayloads {
    public record C2SBeginUpload(
            UUID sessionId,
            BlockPos anchor,
            ResourceLocation dimension,
            String xaeroVersion,
            long compressedSize,
            long uncompressedSize,
            int totalParts,
            String checksum,
            List<ArchiveRegionDescriptor> regions
    ) implements CustomPacketPayload {
        public static final Type<C2SBeginUpload> TYPE = payloadType("xaero_archive_upload_begin");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SBeginUpload> STREAM_CODEC = StreamCodec.of(
                (buffer, value) -> {
                    buffer.writeUUID(value.sessionId);
                    buffer.writeBlockPos(value.anchor);
                    buffer.writeResourceLocation(value.dimension);
                    buffer.writeUtf(value.xaeroVersion, 32);
                    buffer.writeVarLong(value.compressedSize);
                    buffer.writeVarLong(value.uncompressedSize);
                    buffer.writeVarInt(value.totalParts);
                    buffer.writeUtf(value.checksum, 64);
                    writeDescriptors(buffer, value.regions);
                },
                buffer -> new C2SBeginUpload(
                        buffer.readUUID(),
                        buffer.readBlockPos(),
                        buffer.readResourceLocation(),
                        buffer.readUtf(32),
                        buffer.readVarLong(),
                        buffer.readVarLong(),
                        checkedParts(buffer.readVarInt()),
                        buffer.readUtf(64),
                        readDescriptors(buffer)
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SUploadPart(
            UUID sessionId,
            int sequence,
            int totalParts,
            int regionIndex,
            long offset,
            String checksum,
            byte[] data
    ) implements CustomPacketPayload {
        public static final Type<C2SUploadPart> TYPE = payloadType("xaero_archive_upload_part");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SUploadPart> STREAM_CODEC = StreamCodec.of(
                (buffer, value) -> {
                    buffer.writeUUID(value.sessionId);
                    buffer.writeVarInt(value.sequence);
                    buffer.writeVarInt(value.totalParts);
                    buffer.writeVarInt(value.regionIndex);
                    buffer.writeVarLong(value.offset);
                    buffer.writeUtf(value.checksum, 64);
                    buffer.writeByteArray(value.data);
                },
                buffer -> new C2SUploadPart(
                        buffer.readUUID(),
                        buffer.readVarInt(),
                        checkedParts(buffer.readVarInt()),
                        buffer.readVarInt(),
                        buffer.readVarLong(),
                        buffer.readUtf(64),
                        buffer.readByteArray(XaeroArchiveLimits.PART_SIZE)
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SFinishUpload(UUID sessionId, String checksum) implements CustomPacketPayload {
        public static final Type<C2SFinishUpload> TYPE = payloadType("xaero_archive_upload_finish");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SFinishUpload> STREAM_CODEC = StreamCodec.of(
                (buffer, value) -> {
                    buffer.writeUUID(value.sessionId);
                    buffer.writeUtf(value.checksum, 64);
                },
                buffer -> new C2SFinishUpload(buffer.readUUID(), buffer.readUtf(64))
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SRequestDownload(UUID sessionId, BlockPos anchor, ResourceLocation dimension) implements CustomPacketPayload {
        public static final Type<C2SRequestDownload> TYPE = payloadType("xaero_archive_download_request");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SRequestDownload> STREAM_CODEC = StreamCodec.of(
                (buffer, value) -> {
                    buffer.writeUUID(value.sessionId);
                    buffer.writeBlockPos(value.anchor);
                    buffer.writeResourceLocation(value.dimension);
                },
                buffer -> new C2SRequestDownload(buffer.readUUID(), buffer.readBlockPos(), buffer.readResourceLocation())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SCancel(UUID sessionId) implements CustomPacketPayload {
        public static final Type<C2SCancel> TYPE = payloadType("xaero_archive_cancel");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SCancel> STREAM_CODEC = StreamCodec.of(
                (buffer, value) -> buffer.writeUUID(value.sessionId),
                buffer -> new C2SCancel(buffer.readUUID())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SRequestStats(UUID requestId, BlockPos anchor, ResourceLocation dimension) implements CustomPacketPayload {
        public static final Type<C2SRequestStats> TYPE = payloadType("xaero_archive_stats_request");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SRequestStats> STREAM_CODEC = StreamCodec.of(
                (buffer, value) -> {
                    buffer.writeUUID(value.requestId);
                    buffer.writeBlockPos(value.anchor);
                    buffer.writeResourceLocation(value.dimension);
                },
                buffer -> new C2SRequestStats(buffer.readUUID(), buffer.readBlockPos(), buffer.readResourceLocation())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CBeginDownload(
            UUID sessionId,
            String serverIdentity,
            ResourceLocation dimension,
            UUID factionId,
            long compressedSize,
            long uncompressedSize,
            int totalParts,
            String checksum,
            List<ArchiveRegionDescriptor> regions
    ) implements CustomPacketPayload {
        public static final Type<S2CBeginDownload> TYPE = payloadType("xaero_archive_download_begin");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CBeginDownload> STREAM_CODEC = StreamCodec.of(
                (buffer, value) -> {
                    buffer.writeUUID(value.sessionId);
                    buffer.writeUtf(value.serverIdentity, 64);
                    buffer.writeResourceLocation(value.dimension);
                    buffer.writeUUID(value.factionId);
                    buffer.writeVarLong(value.compressedSize);
                    buffer.writeVarLong(value.uncompressedSize);
                    buffer.writeVarInt(value.totalParts);
                    buffer.writeUtf(value.checksum, 64);
                    writeDescriptors(buffer, value.regions);
                },
                buffer -> new S2CBeginDownload(
                        buffer.readUUID(),
                        buffer.readUtf(64),
                        buffer.readResourceLocation(),
                        buffer.readUUID(),
                        buffer.readVarLong(),
                        buffer.readVarLong(),
                        checkedParts(buffer.readVarInt()),
                        buffer.readUtf(64),
                        readDescriptors(buffer)
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CDownloadPart(
            UUID sessionId,
            int sequence,
            int totalParts,
            int regionIndex,
            long offset,
            String checksum,
            byte[] data
    ) implements CustomPacketPayload {
        public static final Type<S2CDownloadPart> TYPE = payloadType("xaero_archive_download_part");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CDownloadPart> STREAM_CODEC = StreamCodec.of(
                (buffer, value) -> {
                    buffer.writeUUID(value.sessionId);
                    buffer.writeVarInt(value.sequence);
                    buffer.writeVarInt(value.totalParts);
                    buffer.writeVarInt(value.regionIndex);
                    buffer.writeVarLong(value.offset);
                    buffer.writeUtf(value.checksum, 64);
                    buffer.writeByteArray(value.data);
                },
                buffer -> new S2CDownloadPart(
                        buffer.readUUID(),
                        buffer.readVarInt(),
                        checkedParts(buffer.readVarInt()),
                        buffer.readVarInt(),
                        buffer.readVarLong(),
                        buffer.readUtf(64),
                        buffer.readByteArray(XaeroArchiveLimits.PART_SIZE)
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CStatus(
            UUID sessionId,
            String phase,
            long completed,
            long total,
            boolean terminal,
            boolean successful,
            String messageKey
    ) implements CustomPacketPayload {
        public static final Type<S2CStatus> TYPE = payloadType("xaero_archive_status");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CStatus> STREAM_CODEC = StreamCodec.of(
                (buffer, value) -> {
                    buffer.writeUUID(value.sessionId);
                    buffer.writeUtf(value.phase, 24);
                    buffer.writeVarLong(value.completed);
                    buffer.writeVarLong(value.total);
                    buffer.writeBoolean(value.terminal);
                    buffer.writeBoolean(value.successful);
                    buffer.writeUtf(value.messageKey, 128);
                },
                buffer -> new S2CStatus(
                        buffer.readUUID(),
                        buffer.readUtf(24),
                        buffer.readVarLong(),
                        buffer.readVarLong(),
                        buffer.readBoolean(),
                        buffer.readBoolean(),
                        buffer.readUtf(128)
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CStats(
            UUID requestId,
            ResourceLocation dimension,
            long compressedSize,
            long uncompressedSize,
            int regionCount,
            int tileCount,
            boolean successful,
            String messageKey
    ) implements CustomPacketPayload {
        public static final Type<S2CStats> TYPE = payloadType("xaero_archive_stats");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CStats> STREAM_CODEC = StreamCodec.of(
                (buffer, value) -> {
                    buffer.writeUUID(value.requestId);
                    buffer.writeResourceLocation(value.dimension);
                    buffer.writeVarLong(value.compressedSize);
                    buffer.writeVarLong(value.uncompressedSize);
                    buffer.writeVarInt(value.regionCount);
                    buffer.writeVarInt(value.tileCount);
                    buffer.writeBoolean(value.successful);
                    buffer.writeUtf(value.messageKey, 128);
                },
                buffer -> new S2CStats(
                        buffer.readUUID(),
                        buffer.readResourceLocation(),
                        buffer.readVarLong(),
                        buffer.readVarLong(),
                        buffer.readVarInt(),
                        buffer.readVarInt(),
                        buffer.readBoolean(),
                        buffer.readUtf(128)
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void writeDescriptors(RegistryFriendlyByteBuf buffer, List<ArchiveRegionDescriptor> descriptors) {
        buffer.writeVarInt(descriptors.size());
        for (ArchiveRegionDescriptor descriptor : descriptors) {
            buffer.writeUtf(descriptor.name(), 48);
            buffer.writeVarLong(descriptor.compressedSize());
            buffer.writeVarLong(descriptor.uncompressedSize());
            buffer.writeVarInt(descriptor.tileCount());
            buffer.writeUtf(descriptor.checksum(), 64);
        }
    }

    private static List<ArchiveRegionDescriptor> readDescriptors(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > XaeroArchiveLimits.MAX_REGIONS) {
            throw new DecoderException("Invalid Xaero region descriptor count");
        }
        ArrayList<ArchiveRegionDescriptor> descriptors = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            try {
                descriptors.add(new ArchiveRegionDescriptor(
                        buffer.readUtf(48),
                        buffer.readVarLong(),
                        buffer.readVarLong(),
                        buffer.readVarInt(),
                        buffer.readUtf(64)
                ));
            } catch (IllegalArgumentException exception) {
                throw new DecoderException("Invalid Xaero region descriptor", exception);
            }
        }
        return List.copyOf(descriptors);
    }

    private static int checkedParts(int parts) {
        if (parts < 0 || parts > XaeroArchiveLimits.MAX_PARTS) {
            throw new DecoderException("Invalid Xaero archive part count");
        }
        return parts;
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, path));
    }

    private XaeroArchivePayloads() {
    }
}
