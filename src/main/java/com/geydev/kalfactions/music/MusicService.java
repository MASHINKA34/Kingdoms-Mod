package com.geydev.kalfactions.music;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.block.MusicBlockEntity;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.integration.xaero.archive.ArchiveHashing;
import com.geydev.kalfactions.net.ActionCooldown;
import com.geydev.kalfactions.registry.ModBlocks;
import com.geydev.kalfactions.sanctuary.SanctuaryManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public final class MusicService {
    private static final double MAX_INTERACT_DISTANCE_SQR = 64.0D;
    private static final long UPLOAD_BURST_BYTES = 512L * 1024L;
    private static final long UPLOAD_BYTES_PER_SECOND = 2L * 1024L * 1024L;
    private static final long MAX_PENDING_DOWNLOAD_BYTES = 256L * 1024L * 1024L;

    private static final Map<UUID, UploadSession> UPLOADS = new ConcurrentHashMap<>();
    private static final Map<UUID, MusicDownloadQueue> DOWNLOADS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_ACTION_TICK = new ConcurrentHashMap<>();

    public enum UploadCheck {
        OK(""),
        ALREADY_PRESENT("kingdoms.music.upload.deduplicated"),
        INVALID_CHECKSUM("kingdoms.music.error.invalid_file"),
        INVALID_NAME("kingdoms.music.error.invalid_name"),
        TOO_LARGE("kingdoms.music.error.too_large"),
        TOO_MANY_TRACKS("kingdoms.music.error.too_many_tracks"),
        STORAGE_FULL("kingdoms.music.error.storage_full");

        private final String messageKey;

        UploadCheck(String messageKey) {
            this.messageKey = messageKey;
        }

        public String messageKey() {
            return messageKey;
        }
    }

    public static UploadCheck checkUpload(MinecraftServer server, String checksum, String name, int totalBytes) {
        if (!ArchiveHashing.isSha256(checksum)) {
            return UploadCheck.INVALID_CHECKSUM;
        }
        if (MusicLimits.sanitizeName(name).isEmpty()) {
            return UploadCheck.INVALID_NAME;
        }
        if (totalBytes <= 0 || totalBytes > MusicLimits.maxTrackBytes()) {
            return UploadCheck.TOO_LARGE;
        }
        MusicManager manager = MusicManager.get(server);
        if (manager.track(checksum).isPresent() && MusicStorage.exists(server, checksum)) {
            return UploadCheck.ALREADY_PRESENT;
        }
        long reservedBytes = 0L;
        int reservedTracks = 0;
        for (UploadSession session : UPLOADS.values()) {
            reservedBytes += session.total;
            reservedTracks++;
        }
        if (manager.trackCount() + reservedTracks >= MusicLimits.maxTracks()) {
            return UploadCheck.TOO_MANY_TRACKS;
        }
        if (manager.totalBytes() + reservedBytes + totalBytes > MusicLimits.maxStorageBytes()) {
            return UploadCheck.STORAGE_FULL;
        }
        return UploadCheck.OK;
    }

    public static boolean canEdit(ServerPlayer player, BlockPos pos) {
        if (player.hasPermissions(2)) {
            return true;
        }
        if (!ModConfigSpec.MUSIC_ALLOW_FACTION_UPLOAD.get()) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        if (SanctuaryManager.get(level).isSanctuary(level, pos)) {
            return false;
        }
        FactionManager manager = FactionManager.get(level);
        UUID owner = manager.getFactionIdAt(ClaimKey.of(level, pos)).orElse(null);
        if (owner == null) {
            return false;
        }
        return owner.equals(manager.getFactionIdForMember(player.getUUID()).orElse(null));
    }

    public static boolean canPlace(ServerPlayer player, BlockPos pos) {
        return canEdit(player, pos);
    }

    public static void openScreen(ServerPlayer player, BlockPos pos) {
        if (!nearby(player, pos)) {
            return;
        }
        sendScreen(player, pos);
    }

    public static void requestScreen(ServerPlayer player, BlockPos pos) {
        if (!nearby(player, pos) || rateLimited(player)) {
            return;
        }
        sendScreen(player, pos);
    }

    public static void updateSpeaker(ServerPlayer player, MusicPayloads.C2SUpdateSpeaker payload) {
        BlockPos pos = payload.pos().immutable();
        if (!nearby(player, pos) || rateLimited(player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!level.getBlockState(pos).is(ModBlocks.MUSIC_BLOCK.get())) {
            return;
        }
        if (!canEdit(player, pos)) {
            notice(player, "kingdoms.music.error.no_permission");
            sendScreen(player, pos);
            return;
        }
        MusicManager manager = MusicManager.get(level);
        String hash = payload.hash();
        String trackName = "";
        if (!hash.isEmpty()) {
            Optional<MusicTrack> track = manager.track(hash);
            if (track.isEmpty()) {
                notice(player, "kingdoms.music.error.unknown_track");
                sendScreen(player, pos);
                return;
            }
            trackName = track.get().name();
        }
        MusicSpeaker speaker = new MusicSpeaker(
                level.dimension(),
                pos,
                hash,
                trackName,
                payload.volume(),
                payload.radius(),
                payload.loop(),
                payload.playing() && !hash.isEmpty(),
                payload.redstone()
        );
        store(level, speaker);
        sendScreen(player, pos);
    }

    public static void deleteTrack(ServerPlayer player, MusicPayloads.C2SDeleteTrack payload) {
        BlockPos pos = payload.pos().immutable();
        if (!nearby(player, pos) || rateLimited(player)) {
            return;
        }
        if (!canEdit(player, pos)) {
            notice(player, "kingdoms.music.error.no_permission");
            return;
        }
        MusicTrack track = MusicManager.get(player.server).track(payload.hash()).orElse(null);
        if (track == null) {
            notice(player, "kingdoms.music.error.unknown_track");
            return;
        }
        if (!canDelete(player, track)) {
            notice(player, "kingdoms.music.error.no_permission");
            return;
        }
        deleteTrack(player.server, payload.hash());
        notice(player, "kingdoms.music.track_deleted");
        sendScreen(player, pos);
    }

    public static Optional<MusicTrack> deleteTrack(MinecraftServer server, String hash) {
        MusicManager manager = MusicManager.get(server);
        Optional<MusicTrack> removed = manager.removeTrack(hash);
        if (removed.isPresent()) {
            MusicStorage.delete(server, hash);
            MusicRadius.refreshAll(server);
        }
        return removed;
    }

    static boolean canDelete(ServerPlayer player, MusicTrack track) {
        return player.hasPermissions(2) || player.getUUID().equals(track.uploader());
    }

    public static void beginUpload(ServerPlayer player, MusicPayloads.C2SBeginUpload payload) {
        BlockPos pos = payload.speakerPos().immutable();
        UUID sessionId = payload.sessionId();
        if (!nearby(player, pos) || rateLimited(player)) {
            uploadFailed(player, sessionId, "kingdoms.music.error.too_far");
            return;
        }
        if (!canEdit(player, pos)) {
            uploadFailed(player, sessionId, "kingdoms.music.error.no_permission");
            return;
        }
        if (UPLOADS.containsKey(player.getUUID())) {
            uploadFailed(player, sessionId, "kingdoms.music.error.session");
            return;
        }
        String name = MusicLimits.sanitizeName(payload.name());
        int total = payload.totalBytes();
        UploadCheck check = checkUpload(player.server, payload.checksum(), name, total);
        if (check == UploadCheck.ALREADY_PRESENT) {
            assignTrack(player, pos, payload.checksum());
            PacketDistributor.sendToPlayer(player, new MusicPayloads.S2CUploadStatus(
                    sessionId, total, total, true, false, "kingdoms.music.upload.deduplicated"));
            sendScreen(player, pos);
            return;
        }
        if (check != UploadCheck.OK) {
            uploadFailed(player, sessionId, check.messageKey());
            return;
        }
        UploadSession session = new UploadSession(player, sessionId, pos, name, total, payload.checksum());
        UPLOADS.put(player.getUUID(), session);
        PacketDistributor.sendToPlayer(player, new MusicPayloads.S2CUploadStatus(
                sessionId, 0L, total, false, false, "kingdoms.music.upload.running"));
    }

    public static void uploadChunk(ServerPlayer player, MusicPayloads.C2SUploadChunk payload) {
        UploadSession session = UPLOADS.get(player.getUUID());
        if (session == null || !session.id.equals(payload.sessionId())) {
            uploadFailed(player, payload.sessionId(), "kingdoms.music.error.session");
            return;
        }
        if (!session.validFor(player)) {
            UPLOADS.remove(player.getUUID(), session);
            uploadFailed(player, session.id, "kingdoms.music.error.no_permission");
            return;
        }
        if (session.finishing) {
            return;
        }
        if (!session.accept(payload.index(), payload.data())) {
            UPLOADS.remove(player.getUUID(), session);
            uploadFailed(player, session.id, "kingdoms.music.error.invalid_chunk");
            return;
        }
        if (session.received() < session.total) {
            PacketDistributor.sendToPlayer(player, new MusicPayloads.S2CUploadStatus(
                    session.id, session.received(), session.total, false, false, "kingdoms.music.upload.running"));
            return;
        }
        session.finishing = true;
        finishUpload(player, session);
    }

    public static void cancelUpload(ServerPlayer player, UUID sessionId) {
        UploadSession session = UPLOADS.get(player.getUUID());
        if (session != null && session.id.equals(sessionId)) {
            UPLOADS.remove(player.getUUID(), session);
        }
    }

    public static void requestTrack(ServerPlayer player, String hash) {
        if (!ArchiveHashing.isSha256(hash)) {
            PacketDistributor.sendToPlayer(player,
                    new MusicPayloads.S2CTrackFailed(hash, "kingdoms.music.error.unknown_track"));
            return;
        }
        MusicManager manager = MusicManager.get(player.serverLevel());
        MusicTrack track = manager.track(hash).orElse(null);
        if (track == null || track.size() <= 0L || track.size() > MusicLimits.HARD_MAX_TRACK_BYTES) {
            PacketDistributor.sendToPlayer(player,
                    new MusicPayloads.S2CTrackFailed(hash, "kingdoms.music.error.unknown_track"));
            return;
        }
        MusicDownloadQueue queue = DOWNLOADS.computeIfAbsent(player.getUUID(),
                key -> new MusicDownloadQueue(3, MusicLimits.HARD_MAX_TRACK_BYTES));
        if (queue.contains(hash)) {
            return;
        }
        long reservedBytes = DOWNLOADS.values().stream().mapToLong(MusicDownloadQueue::reservedBytes).sum();
        MusicDownloadQueue.Reservation reservation = reservedBytes + track.size() > MAX_PENDING_DOWNLOAD_BYTES
                ? null : queue.reserve(hash, track.size());
        if (reservation == null) {
            PacketDistributor.sendToPlayer(player,
                    new MusicPayloads.S2CTrackFailed(hash, "kingdoms.music.error.download_busy"));
            return;
        }
        MinecraftServer server = player.server;
        MusicStorage.read(server, hash, track.size(), () -> queue.contains(reservation))
                .whenComplete((data, error) -> {
                    if (!queue.contains(reservation)) {
                        return;
                    }
                    server.execute(() -> {
                        ServerPlayer target = server.getPlayerList().getPlayer(player.getUUID());
                        if (target != player || DOWNLOADS.get(player.getUUID()) != queue) {
                            queue.release(reservation);
                            return;
                        }
                        if (error != null || data == null || !queue.complete(reservation, data)) {
                            queue.release(reservation);
                            PacketDistributor.sendToPlayer(target,
                                    new MusicPayloads.S2CTrackFailed(hash, "kingdoms.music.error.read_failed"));
                            return;
                        }
                        KalFactions.LOGGER.info("Sending music track {} ({} bytes) to {}",
                                hash.substring(0, 8), data.length, target.getGameProfile().getName());
                    });
                });
    }

    public static void pumpDownloads(MinecraftServer server) {
        if (DOWNLOADS.isEmpty()) {
            return;
        }
        long budgetPerTick = Math.max(MusicLimits.CHUNK_SIZE, MusicLimits.downloadBytesPerSecond() / 20L);
        for (Map.Entry<UUID, MusicDownloadQueue> entry : DOWNLOADS.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            MusicDownloadQueue queue = entry.getValue();
            if (player == null) {
                queue.clear();
                DOWNLOADS.remove(entry.getKey(), queue);
                continue;
            }
            long spent = 0L;
            while (spent < budgetPerTick) {
                MusicDownloadQueue.Chunk chunk = queue.nextChunk();
                if (chunk == null) {
                    break;
                }
                if (chunk.index() == 0) {
                    PacketDistributor.sendToPlayer(player, new MusicPayloads.S2CTrackBegin(chunk.hash(), chunk.totalBytes()));
                }
                PacketDistributor.sendToPlayer(player,
                        new MusicPayloads.S2CTrackChunk(chunk.hash(), chunk.index(), chunk.data()));
                spent += chunk.data().length;
            }
        }
    }

    public static void forget(UUID playerId) {
        UPLOADS.remove(playerId);
        MusicDownloadQueue queue = DOWNLOADS.remove(playerId);
        if (queue != null) {
            queue.clear();
        }
        LAST_ACTION_TICK.remove(playerId);
    }

    public static void clear() {
        UPLOADS.clear();
        DOWNLOADS.values().forEach(MusicDownloadQueue::clear);
        DOWNLOADS.clear();
        LAST_ACTION_TICK.clear();
    }

    public static void expireSessions() {
        long now = System.currentTimeMillis();
        UPLOADS.values().removeIf(session -> now - session.lastActivity > MusicLimits.SESSION_TIMEOUT_MILLIS);
    }

    public static List<MusicPayloads.TrackEntry> trackEntries(MinecraftServer server) {
        List<MusicPayloads.TrackEntry> entries = new ArrayList<>();
        for (MusicTrack track : MusicManager.get(server).tracks()) {
            entries.add(new MusicPayloads.TrackEntry(
                    track.hash(), track.name(), track.size(), track.uploaderName(), track.uploadedAt()));
        }
        entries.sort(Comparator.comparing(MusicPayloads.TrackEntry::name, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    private static void finishUpload(ServerPlayer player, UploadSession session) {
        byte[] data = session.data();
        if (!MusicLimits.hasOggSignature(data)) {
            UPLOADS.remove(player.getUUID(), session);
            uploadFailed(player, session.id, "kingdoms.music.error.not_ogg");
            return;
        }
        String hash = ArchiveHashing.sha256(data);
        if (!hash.equals(session.checksum)) {
            UPLOADS.remove(player.getUUID(), session);
            uploadFailed(player, session.id, "kingdoms.music.error.checksum");
            return;
        }
        MinecraftServer server = player.server;
        MusicManager manager = MusicManager.get(server);
        if (manager.track(hash).isPresent() && MusicStorage.exists(server, hash)) {
            UPLOADS.remove(player.getUUID(), session);
            assignTrack(player, session.speakerPos, hash);
            PacketDistributor.sendToPlayer(player, new MusicPayloads.S2CUploadStatus(
                    session.id, session.total, session.total, true, false, "kingdoms.music.upload.deduplicated"));
            sendScreen(player, session.speakerPos);
            return;
        }
        MusicStorage.stage(server, hash, data).whenComplete((staged, error) -> {
            if (UPLOADS.get(player.getUUID()) != session) {
                MusicStorage.discard(staged);
                return;
            }
            server.execute(() -> {
                ServerPlayer target = server.getPlayerList().getPlayer(player.getUUID());
                if (target != player || UPLOADS.get(player.getUUID()) != session || !session.validFor(player)) {
                    UPLOADS.remove(player.getUUID(), session);
                    MusicStorage.discard(staged);
                    if (target == player) {
                        uploadFailed(player, session.id, "kingdoms.music.error.session");
                    }
                    return;
                }
                UPLOADS.remove(player.getUUID(), session);
                if (error != null) {
                    uploadFailed(target, session.id, "kingdoms.music.error.write_failed");
                    return;
                }
                UploadCheck check = checkUpload(server, hash, session.name, session.total);
                if (check != UploadCheck.OK && check != UploadCheck.ALREADY_PRESENT) {
                    MusicStorage.discard(staged);
                    uploadFailed(player, session.id, check.messageKey());
                    return;
                }
                if (check == UploadCheck.ALREADY_PRESENT) {
                    MusicStorage.discard(staged);
                } else {
                    try {
                        MusicStorage.commit(server, hash, staged);
                    } catch (RuntimeException exception) {
                        KalFactions.LOGGER.warn("Failed to commit music track {}", hash, exception);
                        uploadFailed(player, session.id, "kingdoms.music.error.write_failed");
                        return;
                    }
                    manager.addTrack(new MusicTrack(hash, session.name, session.total,
                            player.getUUID(), player.getGameProfile().getName(), System.currentTimeMillis()));
                }
                assignTrack(target, session.speakerPos, hash);
                PacketDistributor.sendToPlayer(target, new MusicPayloads.S2CUploadStatus(
                        session.id, session.total, session.total, true, false, "kingdoms.music.upload.done"));
                sendScreen(target, session.speakerPos);
            });
        });
    }

    private static void assignTrack(ServerPlayer player, BlockPos pos, String hash) {
        ServerLevel level = player.serverLevel();
        if (!level.getBlockState(pos).is(ModBlocks.MUSIC_BLOCK.get())) {
            return;
        }
        MusicManager manager = MusicManager.get(level);
        MusicTrack track = manager.track(hash).orElse(null);
        if (track == null) {
            return;
        }
        MusicSpeaker current = manager.speaker(level.dimension(), pos)
                .orElseGet(() -> defaultSpeaker(level, pos));
        store(level, current.withTrack(hash, track.name()));
    }

    private static void store(ServerLevel level, MusicSpeaker speaker) {
        MusicManager.get(level).putSpeaker(speaker);
        if (level.getBlockEntity(speaker.pos()) instanceof MusicBlockEntity music) {
            music.applySpeaker(speaker);
        }
        MusicRadius.refreshAll(level.getServer());
    }

    public static MusicSpeaker defaultSpeaker(ServerLevel level, BlockPos pos) {
        return new MusicSpeaker(
                level.dimension(),
                pos,
                "",
                "",
                1.0F,
                MusicLimits.defaultRadius(),
                true,
                false,
                true
        );
    }

    private static void sendScreen(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        MusicManager manager = MusicManager.get(level);
        MusicSpeaker speaker = manager.speaker(level.dimension(), pos)
                .orElseGet(() -> defaultSpeaker(level, pos));
        PacketDistributor.sendToPlayer(player, new MusicPayloads.S2COpenSpeaker(
                pos,
                speaker.hash(),
                speaker.trackName(),
                speaker.volume(),
                speaker.radius(),
                speaker.loop(),
                speaker.playing(),
                speaker.redstone(),
                canEdit(player, pos),
                MusicLimits.maxRadius(),
                MusicLimits.maxTrackBytes(),
                trackEntries(level.getServer())
        ));
    }

    private static void uploadFailed(ServerPlayer player, UUID sessionId, String messageKey) {
        PacketDistributor.sendToPlayer(player,
                new MusicPayloads.S2CUploadStatus(sessionId, 0L, 0L, false, true, messageKey));
    }

    private static void notice(ServerPlayer player, String messageKey) {
        player.sendSystemMessage(Component.translatable(messageKey));
    }

    private static boolean nearby(ServerPlayer player, BlockPos pos) {
        return player.isAlive()
                && !player.isSpectator()
                && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)
                        <= MAX_INTERACT_DISTANCE_SQR
                && player.serverLevel().hasChunkAt(pos)
                && player.serverLevel().getBlockEntity(pos) instanceof MusicBlockEntity;
    }

    private static boolean rateLimited(ServerPlayer player) {
        long now = player.level().getGameTime();
        Long previous = ActionCooldown.mark(
                LAST_ACTION_TICK, player.getUUID(), now, MusicLimits.ACTION_COOLDOWN_TICKS);
        return previous != null && now - previous < MusicLimits.ACTION_COOLDOWN_TICKS;
    }

    static final class UploadSession {
        private final UUID id;
        private final BlockPos speakerPos;
        private final String name;
        private final int total;
        private final String checksum;
        private final MusicChunkBuffer chunks;
        private final ResourceKey<Level> dimension;
        private final UUID faction;
        private final MusicBlockEntity speaker;
        private boolean finishing;
        private final long startedAt = System.currentTimeMillis();
        private long lastActivity = System.currentTimeMillis();

        UploadSession(ServerPlayer player, UUID id, BlockPos speakerPos, String name, int total, String checksum) {
            this.id = id;
            this.speakerPos = speakerPos;
            this.name = name;
            this.total = total;
            this.checksum = checksum;
            this.chunks = new MusicChunkBuffer(total);
            this.dimension = player.serverLevel().dimension();
            this.faction = FactionManager.get(player.serverLevel()).getFactionIdForMember(player.getUUID()).orElse(null);
            this.speaker = (MusicBlockEntity) player.serverLevel().getBlockEntity(speakerPos);
        }

        boolean validFor(ServerPlayer player) {
            return player.serverLevel().dimension().equals(dimension)
                    && nearby(player, speakerPos)
                    && player.serverLevel().getBlockEntity(speakerPos) == speaker
                    && Objects.equals(faction, FactionManager.get(player.serverLevel())
                            .getFactionIdForMember(player.getUUID()).orElse(null))
                    && canEdit(player, speakerPos);
        }

        private int received() {
            return chunks.received();
        }

        private byte[] data() {
            return chunks.data();
        }

        private boolean accept(int index, byte[] data) {
            lastActivity = System.currentTimeMillis();
            long elapsed = Math.max(0L, lastActivity - startedAt);
            long allowed = UPLOAD_BURST_BYTES + elapsed * UPLOAD_BYTES_PER_SECOND / 1000L;
            if ((long) chunks.received() + data.length > allowed) {
                return false;
            }
            return chunks.accept(index, data);
        }
    }

    private MusicService() {
    }
}
