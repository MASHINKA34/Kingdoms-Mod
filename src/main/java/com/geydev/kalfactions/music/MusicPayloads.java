package com.geydev.kalfactions.music;

import com.geydev.kalfactions.KalFactions;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class MusicPayloads {
    private static final int HASH_LENGTH = 64;
    private static final int MESSAGE_LENGTH = 128;

    public record TrackEntry(String hash, String name, long size, String uploaderName, long uploadedAt) {
        private static void encode(RegistryFriendlyByteBuf buffer, TrackEntry entry) {
            buffer.writeUtf(entry.hash, HASH_LENGTH);
            buffer.writeUtf(entry.name, MusicLimits.MAX_NAME_LENGTH);
            buffer.writeLong(entry.size);
            buffer.writeUtf(entry.uploaderName, 32);
            buffer.writeLong(entry.uploadedAt);
        }

        private static TrackEntry decode(RegistryFriendlyByteBuf buffer) {
            return new TrackEntry(
                    buffer.readUtf(HASH_LENGTH),
                    buffer.readUtf(MusicLimits.MAX_NAME_LENGTH),
                    buffer.readLong(),
                    buffer.readUtf(32),
                    buffer.readLong()
            );
        }
    }

    public record C2SBeginUpload(UUID sessionId, BlockPos speakerPos, String name, int totalBytes, String checksum)
            implements CustomPacketPayload {
        public static final Type<C2SBeginUpload> TYPE = payloadType("begin_upload");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SBeginUpload> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeUUID(payload.sessionId);
                    buffer.writeBlockPos(payload.speakerPos);
                    buffer.writeUtf(payload.name, MusicLimits.MAX_NAME_LENGTH);
                    buffer.writeVarInt(payload.totalBytes);
                    buffer.writeUtf(payload.checksum, HASH_LENGTH);
                },
                buffer -> new C2SBeginUpload(
                        buffer.readUUID(),
                        buffer.readBlockPos(),
                        buffer.readUtf(MusicLimits.MAX_NAME_LENGTH),
                        buffer.readVarInt(),
                        buffer.readUtf(HASH_LENGTH)
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SUploadChunk(UUID sessionId, int index, byte[] data) implements CustomPacketPayload {
        public static final Type<C2SUploadChunk> TYPE = payloadType("upload_chunk");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SUploadChunk> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeUUID(payload.sessionId);
                    buffer.writeVarInt(payload.index);
                    buffer.writeByteArray(payload.data);
                },
                buffer -> new C2SUploadChunk(
                        buffer.readUUID(),
                        buffer.readVarInt(),
                        buffer.readByteArray(MusicLimits.CHUNK_SIZE)
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SCancelUpload(UUID sessionId) implements CustomPacketPayload {
        public static final Type<C2SCancelUpload> TYPE = payloadType("cancel_upload");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SCancelUpload> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeUUID(payload.sessionId),
                buffer -> new C2SCancelUpload(buffer.readUUID())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CUploadStatus(
            UUID sessionId,
            long received,
            long total,
            boolean done,
            boolean failed,
            String messageKey
    ) implements CustomPacketPayload {
        public static final Type<S2CUploadStatus> TYPE = payloadType("upload_status");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CUploadStatus> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeUUID(payload.sessionId);
                    buffer.writeLong(payload.received);
                    buffer.writeLong(payload.total);
                    buffer.writeBoolean(payload.done);
                    buffer.writeBoolean(payload.failed);
                    buffer.writeUtf(payload.messageKey, MESSAGE_LENGTH);
                },
                buffer -> new S2CUploadStatus(
                        buffer.readUUID(),
                        buffer.readLong(),
                        buffer.readLong(),
                        buffer.readBoolean(),
                        buffer.readBoolean(),
                        buffer.readUtf(MESSAGE_LENGTH)
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SRequestTrack(String hash) implements CustomPacketPayload {
        public static final Type<C2SRequestTrack> TYPE = payloadType("request_track");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SRequestTrack> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeUtf(payload.hash, HASH_LENGTH),
                buffer -> new C2SRequestTrack(buffer.readUtf(HASH_LENGTH))
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CTrackBegin(String hash, int totalBytes) implements CustomPacketPayload {
        public static final Type<S2CTrackBegin> TYPE = payloadType("track_begin");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CTrackBegin> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeUtf(payload.hash, HASH_LENGTH);
                    buffer.writeVarInt(payload.totalBytes);
                },
                buffer -> new S2CTrackBegin(buffer.readUtf(HASH_LENGTH), buffer.readVarInt())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CTrackChunk(String hash, int index, byte[] data) implements CustomPacketPayload {
        public static final Type<S2CTrackChunk> TYPE = payloadType("track_chunk");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CTrackChunk> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeUtf(payload.hash, HASH_LENGTH);
                    buffer.writeVarInt(payload.index);
                    buffer.writeByteArray(payload.data);
                },
                buffer -> new S2CTrackChunk(
                        buffer.readUtf(HASH_LENGTH),
                        buffer.readVarInt(),
                        buffer.readByteArray(MusicLimits.CHUNK_SIZE)
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CTrackFailed(String hash, String messageKey) implements CustomPacketPayload {
        public static final Type<S2CTrackFailed> TYPE = payloadType("track_failed");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CTrackFailed> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeUtf(payload.hash, HASH_LENGTH);
                    buffer.writeUtf(payload.messageKey, MESSAGE_LENGTH);
                },
                buffer -> new S2CTrackFailed(buffer.readUtf(HASH_LENGTH), buffer.readUtf(MESSAGE_LENGTH))
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CSpeakerStart(BlockPos pos, String hash, float volume, int radius, boolean loop)
            implements CustomPacketPayload {
        public static final Type<S2CSpeakerStart> TYPE = payloadType("speaker_start");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CSpeakerStart> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBlockPos(payload.pos);
                    buffer.writeUtf(payload.hash, HASH_LENGTH);
                    buffer.writeFloat(payload.volume);
                    buffer.writeVarInt(payload.radius);
                    buffer.writeBoolean(payload.loop);
                },
                buffer -> new S2CSpeakerStart(
                        buffer.readBlockPos(),
                        buffer.readUtf(HASH_LENGTH),
                        buffer.readFloat(),
                        buffer.readVarInt(),
                        buffer.readBoolean()
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CSpeakerStop(BlockPos pos) implements CustomPacketPayload {
        public static final Type<S2CSpeakerStop> TYPE = payloadType("speaker_stop");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CSpeakerStop> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeBlockPos(payload.pos),
                buffer -> new S2CSpeakerStop(buffer.readBlockPos())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CSpeakerStopAll() implements CustomPacketPayload {
        public static final S2CSpeakerStopAll INSTANCE = new S2CSpeakerStopAll();
        public static final Type<S2CSpeakerStopAll> TYPE = payloadType("speaker_stop_all");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CSpeakerStopAll> STREAM_CODEC =
                StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SSpeakerStatus(List<Long> handled) implements CustomPacketPayload {
        public static final int MAX_ENTRIES = 16;
        public static final Type<C2SSpeakerStatus> TYPE = payloadType("speaker_status");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SSpeakerStatus> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    int count = Math.min(payload.handled.size(), MAX_ENTRIES);
                    buffer.writeVarInt(count);
                    for (int index = 0; index < count; index++) {
                        buffer.writeLong(payload.handled.get(index));
                    }
                },
                buffer -> {
                    int count = buffer.readVarInt();
                    if (count < 0 || count > MAX_ENTRIES) {
                        throw new DecoderException("Music speaker status count " + count + " exceeds " + MAX_ENTRIES);
                    }
                    List<Long> handled = new ArrayList<>(count);
                    for (int index = 0; index < count; index++) {
                        handled.add(buffer.readLong());
                    }
                    return new C2SSpeakerStatus(handled);
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CMusicMute(boolean muted) implements CustomPacketPayload {
        public static final Type<S2CMusicMute> TYPE = payloadType("mute");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CMusicMute> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeBoolean(payload.muted),
                buffer -> new S2CMusicMute(buffer.readBoolean())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SRequestSpeaker(BlockPos pos) implements CustomPacketPayload {
        public static final Type<C2SRequestSpeaker> TYPE = payloadType("request_speaker");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SRequestSpeaker> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeBlockPos(payload.pos),
                buffer -> new C2SRequestSpeaker(buffer.readBlockPos())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SUpdateSpeaker(
            BlockPos pos,
            String hash,
            float volume,
            int radius,
            boolean loop,
            boolean playing,
            boolean redstone
    ) implements CustomPacketPayload {
        public static final Type<C2SUpdateSpeaker> TYPE = payloadType("update_speaker");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SUpdateSpeaker> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBlockPos(payload.pos);
                    buffer.writeUtf(payload.hash, HASH_LENGTH);
                    buffer.writeFloat(payload.volume);
                    buffer.writeVarInt(payload.radius);
                    buffer.writeBoolean(payload.loop);
                    buffer.writeBoolean(payload.playing);
                    buffer.writeBoolean(payload.redstone);
                },
                buffer -> new C2SUpdateSpeaker(
                        buffer.readBlockPos(),
                        buffer.readUtf(HASH_LENGTH),
                        buffer.readFloat(),
                        buffer.readVarInt(),
                        buffer.readBoolean(),
                        buffer.readBoolean(),
                        buffer.readBoolean()
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SDeleteTrack(BlockPos pos, String hash) implements CustomPacketPayload {
        public static final Type<C2SDeleteTrack> TYPE = payloadType("delete_track");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SDeleteTrack> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBlockPos(payload.pos);
                    buffer.writeUtf(payload.hash, HASH_LENGTH);
                },
                buffer -> new C2SDeleteTrack(buffer.readBlockPos(), buffer.readUtf(HASH_LENGTH))
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2COpenSpeaker(
            BlockPos pos,
            String hash,
            String trackName,
            float volume,
            int radius,
            boolean loop,
            boolean playing,
            boolean redstone,
            boolean canEdit,
            int maxRadius,
            int maxTrackBytes,
            List<TrackEntry> tracks
    ) implements CustomPacketPayload {
        public static final Type<S2COpenSpeaker> TYPE = payloadType("open_speaker");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2COpenSpeaker> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBlockPos(payload.pos);
                    buffer.writeUtf(payload.hash, HASH_LENGTH);
                    buffer.writeUtf(payload.trackName, MusicLimits.MAX_NAME_LENGTH);
                    buffer.writeFloat(payload.volume);
                    buffer.writeVarInt(payload.radius);
                    buffer.writeBoolean(payload.loop);
                    buffer.writeBoolean(payload.playing);
                    buffer.writeBoolean(payload.redstone);
                    buffer.writeBoolean(payload.canEdit);
                    buffer.writeVarInt(payload.maxRadius);
                    buffer.writeVarInt(payload.maxTrackBytes);
                    int count = Math.min(payload.tracks.size(), MusicLimits.MAX_TRACK_ENTRIES);
                    buffer.writeVarInt(count);
                    for (int index = 0; index < count; index++) {
                        TrackEntry.encode(buffer, payload.tracks.get(index));
                    }
                },
                buffer -> {
                    BlockPos pos = buffer.readBlockPos();
                    String hash = buffer.readUtf(HASH_LENGTH);
                    String trackName = buffer.readUtf(MusicLimits.MAX_NAME_LENGTH);
                    float volume = buffer.readFloat();
                    int radius = buffer.readVarInt();
                    boolean loop = buffer.readBoolean();
                    boolean playing = buffer.readBoolean();
                    boolean redstone = buffer.readBoolean();
                    boolean canEdit = buffer.readBoolean();
                    int maxRadius = buffer.readVarInt();
                    int maxTrackBytes = buffer.readVarInt();
                    int count = buffer.readVarInt();
                    if (count < 0 || count > MusicLimits.MAX_TRACK_ENTRIES) {
                        throw new DecoderException("Music track count " + count + " exceeds "
                                + MusicLimits.MAX_TRACK_ENTRIES);
                    }
                    List<TrackEntry> tracks = new ArrayList<>(count);
                    for (int index = 0; index < count; index++) {
                        tracks.add(TrackEntry.decode(buffer));
                    }
                    return new S2COpenSpeaker(pos, hash, trackName, volume, radius, loop, playing, redstone,
                            canEdit, maxRadius, maxTrackBytes, tracks);
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "music_" + path)
        );
    }

    private MusicPayloads() {
    }
}
