package com.geydev.kalfactions.music;

import com.geydev.kalfactions.block.MusicBlockEntity;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.integration.xaero.archive.ArchiveHashing;
import com.geydev.kalfactions.registry.ModBlocks;
import com.geydev.kalfactions.sanctuary.SanctuaryManager;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class MusicService {
    private static final double MAX_INTERACT_DISTANCE_SQR = 64.0D;
    private static final long UPLOAD_BURST_BYTES = 512L * 1024L;
    private static final long UPLOAD_BYTES_PER_SECOND = 2L * 1024L * 1024L;

    private static final Map<UUID, UploadSession> UPLOADS = new ConcurrentHashMap<>();
    private static final Map<UUID, DownloadQueue> DOWNLOADS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_ACTION_TICK = new ConcurrentHashMap<>();

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
        deleteTrack(player.server, payload.hash());
        notice(player, "kingdoms.music.track_deleted");
        sendScreen(player, pos);
    }

    public static Optional<MusicTrack> deleteTrack(MinecraftServer server, String hash) {
        MusicManager manager = MusicManager.get(server);
        Optional<MusicTrack> removed = manager.removeTrack(hash);
        if (removed.isPresent()) {
            MusicStorage.deleteAsync(server, hash);
            MusicRadius.refreshAll(server);
        }
        return removed;
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
        if (!ArchiveHashing.isSha256(payload.checksum())) {
            uploadFailed(player, sessionId, "kingdoms.music.error.invalid_file");
            return;
        }
        String name = MusicLimits.sanitizeName(payload.name());
        if (name.isEmpty()) {
            uploadFailed(player, sessionId, "kingdoms.music.error.invalid_name");
            return;
        }
        int total = payload.totalBytes();
        if (total <= 0 || total > MusicLimits.maxTrackBytes()) {
            uploadFailed(player, sessionId, "kingdoms.music.error.too_large");
            return;
        }
        MusicManager manager = MusicManager.get(player.serverLevel());
        if (manager.track(payload.checksum()).isPresent()
                && MusicStorage.exists(player.server, payload.checksum())) {
            assignTrack(player, pos, payload.checksum());
            PacketDistributor.sendToPlayer(player, new MusicPayloads.S2CUploadStatus(
                    sessionId, total, total, true, false, "kingdoms.music.upload.deduplicated"));
            sendScreen(player, pos);
            return;
        }
        if (manager.trackCount() >= MusicLimits.maxTracks()) {
            uploadFailed(player, sessionId, "kingdoms.music.error.too_many_tracks");
            return;
        }
        if (manager.totalBytes() + total > MusicLimits.maxStorageBytes()) {
            uploadFailed(player, sessionId, "kingdoms.music.error.storage_full");
            return;
        }
        UploadSession session = new UploadSession(sessionId, pos, name, total, payload.checksum());
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
        if (!session.accept(payload.index(), payload.data())) {
            UPLOADS.remove(player.getUUID(), session);
            uploadFailed(player, session.id, "kingdoms.music.error.invalid_chunk");
            return;
        }
        if (session.received < session.total) {
            PacketDistributor.sendToPlayer(player, new MusicPayloads.S2CUploadStatus(
                    session.id, session.received, session.total, false, false, "kingdoms.music.upload.running"));
            return;
        }
        UPLOADS.remove(player.getUUID(), session);
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
        if (manager.track(hash).isEmpty()) {
            PacketDistributor.sendToPlayer(player,
                    new MusicPayloads.S2CTrackFailed(hash, "kingdoms.music.error.unknown_track"));
            return;
        }
        DownloadQueue queue = DOWNLOADS.computeIfAbsent(player.getUUID(), key -> new DownloadQueue());
        if (!queue.reserve(hash)) {
            return;
        }
        MinecraftServer server = player.server;
        MusicStorage.read(server, hash)
                .whenComplete((data, error) -> server.execute(() -> {
                    ServerPlayer target = server.getPlayerList().getPlayer(player.getUUID());
                    if (target == null) {
                        queue.release(hash);
                        return;
                    }
                    if (error != null || data == null || !ArchiveHashing.sha256(data).equals(hash)) {
                        queue.release(hash);
                        PacketDistributor.sendToPlayer(target,
                                new MusicPayloads.S2CTrackFailed(hash, "kingdoms.music.error.read_failed"));
                        return;
                    }
                    queue.enqueue(new PendingDownload(hash, data));
                    PacketDistributor.sendToPlayer(target, new MusicPayloads.S2CTrackBegin(hash, data.length));
                }));
    }

    public static void pumpDownloads(MinecraftServer server) {
        if (DOWNLOADS.isEmpty()) {
            return;
        }
        long budgetPerTick = Math.max(MusicLimits.CHUNK_SIZE, MusicLimits.downloadBytesPerSecond() / 20L);
        for (Map.Entry<UUID, DownloadQueue> entry : DOWNLOADS.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            DownloadQueue queue = entry.getValue();
            if (player == null) {
                queue.clear();
                continue;
            }
            queue.pump(player, budgetPerTick);
        }
        DOWNLOADS.entrySet().removeIf(entry -> entry.getValue().isIdle()
                && server.getPlayerList().getPlayer(entry.getKey()) == null);
    }

    public static void forget(UUID playerId) {
        UPLOADS.remove(playerId);
        DOWNLOADS.remove(playerId);
        LAST_ACTION_TICK.remove(playerId);
    }

    public static void clear() {
        UPLOADS.clear();
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
        byte[] data = session.buffer;
        if (!MusicLimits.hasOggSignature(data)) {
            uploadFailed(player, session.id, "kingdoms.music.error.not_ogg");
            return;
        }
        String hash = ArchiveHashing.sha256(data);
        if (!hash.equals(session.checksum)) {
            uploadFailed(player, session.id, "kingdoms.music.error.checksum");
            return;
        }
        MinecraftServer server = player.server;
        MusicManager manager = MusicManager.get(server);
        if (manager.track(hash).isPresent() && MusicStorage.exists(server, hash)) {
            assignTrack(player, session.speakerPos, hash);
            PacketDistributor.sendToPlayer(player, new MusicPayloads.S2CUploadStatus(
                    session.id, session.total, session.total, true, false, "kingdoms.music.upload.deduplicated"));
            sendScreen(player, session.speakerPos);
            return;
        }
        MusicStorage.write(server, hash, data).whenComplete((ignored, error) -> server.execute(() -> {
            ServerPlayer target = server.getPlayerList().getPlayer(player.getUUID());
            if (error != null) {
                if (target != null) {
                    uploadFailed(target, session.id, "kingdoms.music.error.write_failed");
                }
                return;
            }
            manager.addTrack(new MusicTrack(
                    hash,
                    session.name,
                    data.length,
                    player.getUUID(),
                    player.getGameProfile().getName(),
                    System.currentTimeMillis()
            ));
            if (target == null) {
                return;
            }
            assignTrack(target, session.speakerPos, hash);
            PacketDistributor.sendToPlayer(target, new MusicPayloads.S2CUploadStatus(
                    session.id, session.total, session.total, true, false, "kingdoms.music.upload.done"));
            sendScreen(target, session.speakerPos);
        }));
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
                        <= MAX_INTERACT_DISTANCE_SQR;
    }

    private static boolean rateLimited(ServerPlayer player) {
        long now = player.level().getGameTime();
        Long previous = LAST_ACTION_TICK.put(player.getUUID(), now);
        return previous != null && now - previous < MusicLimits.ACTION_COOLDOWN_TICKS;
    }

    private record PendingDownload(String hash, byte[] data) {
    }

    private static final class UploadSession {
        private final UUID id;
        private final BlockPos speakerPos;
        private final String name;
        private final int total;
        private final String checksum;
        private final byte[] buffer;
        private final long startedAt = System.currentTimeMillis();
        private int expectedIndex;
        private int received;
        private long lastActivity = System.currentTimeMillis();

        private UploadSession(UUID id, BlockPos speakerPos, String name, int total, String checksum) {
            this.id = id;
            this.speakerPos = speakerPos;
            this.name = name;
            this.total = total;
            this.checksum = checksum;
            this.buffer = new byte[total];
        }

        private boolean accept(int index, byte[] data) {
            lastActivity = System.currentTimeMillis();
            if (index != expectedIndex || data.length == 0 || data.length > MusicLimits.CHUNK_SIZE) {
                return false;
            }
            if (received + data.length > total) {
                return false;
            }
            int expectedLength = Math.min(MusicLimits.CHUNK_SIZE, total - received);
            if (data.length != expectedLength) {
                return false;
            }
            long elapsed = Math.max(0L, lastActivity - startedAt);
            long allowed = UPLOAD_BURST_BYTES + elapsed * UPLOAD_BYTES_PER_SECOND / 1000L;
            if ((long) received + data.length > allowed) {
                return false;
            }
            System.arraycopy(data, 0, buffer, received, data.length);
            received += data.length;
            expectedIndex++;
            return true;
        }
    }

    private static final class DownloadQueue {
        private final Deque<PendingDownload> pending = new ArrayDeque<>();
        private final java.util.Set<String> reserved = java.util.concurrent.ConcurrentHashMap.newKeySet();
        private int offset;
        private int chunkIndex;

        private synchronized boolean reserve(String hash) {
            return reserved.add(hash);
        }

        private synchronized void release(String hash) {
            reserved.remove(hash);
        }

        private synchronized void enqueue(PendingDownload download) {
            pending.addLast(download);
        }

        private synchronized void clear() {
            pending.clear();
            reserved.clear();
            offset = 0;
            chunkIndex = 0;
        }

        private synchronized boolean isIdle() {
            return pending.isEmpty();
        }

        private synchronized void pump(ServerPlayer player, long budget) {
            long spent = 0L;
            while (spent < budget) {
                PendingDownload current = pending.peekFirst();
                if (current == null) {
                    return;
                }
                int length = Math.min(MusicLimits.CHUNK_SIZE, current.data().length - offset);
                byte[] chunk = new byte[length];
                System.arraycopy(current.data(), offset, chunk, 0, length);
                PacketDistributor.sendToPlayer(player,
                        new MusicPayloads.S2CTrackChunk(current.hash(), chunkIndex, chunk));
                offset += length;
                chunkIndex++;
                spent += length;
                if (offset >= current.data().length) {
                    pending.pollFirst();
                    reserved.remove(current.hash());
                    offset = 0;
                    chunkIndex = 0;
                }
            }
        }
    }

    private MusicService() {
    }
}
