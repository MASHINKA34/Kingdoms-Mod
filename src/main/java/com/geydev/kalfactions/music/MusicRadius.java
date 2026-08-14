package com.geydev.kalfactions.music;

import com.geydev.kalfactions.registry.ModBlocks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class MusicRadius {
    private static final Map<UUID, Map<Long, MusicSpeaker>> ACTIVE = new ConcurrentHashMap<>();

    public static void refreshAll(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            refresh(player);
        }
    }

    public static void refresh(ServerPlayer player) {
        if (player == null || player.hasDisconnected()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Map<Long, MusicSpeaker> wanted = new HashMap<>();
        for (MusicSpeaker speaker : audibleAt(level, player.position())) {
            wanted.put(speaker.pos().asLong(), speaker);
        }
        Map<Long, MusicSpeaker> current = ACTIVE.computeIfAbsent(player.getUUID(), key -> new HashMap<>());
        for (Map.Entry<Long, MusicSpeaker> entry : List.copyOf(current.entrySet())) {
            MusicSpeaker target = wanted.get(entry.getKey());
            if (target == null) {
                current.remove(entry.getKey());
                PacketDistributor.sendToPlayer(player,
                        new MusicPayloads.S2CSpeakerStop(BlockPos.of(entry.getKey())));
            }
        }
        for (Map.Entry<Long, MusicSpeaker> entry : wanted.entrySet()) {
            MusicSpeaker previous = current.get(entry.getKey());
            MusicSpeaker speaker = entry.getValue();
            if (previous != null && sameSound(previous, speaker)) {
                continue;
            }
            current.put(entry.getKey(), speaker);
            PacketDistributor.sendToPlayer(player, new MusicPayloads.S2CSpeakerStart(
                    speaker.pos(), speaker.hash(), speaker.volume(), speaker.radius(), speaker.loop()));
        }
    }

    public static void stopFor(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ACTIVE.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, MusicPayloads.S2CSpeakerStopAll.INSTANCE);
    }

    public static void forget(UUID playerId) {
        ACTIVE.remove(playerId);
    }

    public static void clear() {
        ACTIVE.clear();
    }

    public static void stopEverywhere(MinecraftServer server) {
        ACTIVE.clear();
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, MusicPayloads.S2CSpeakerStopAll.INSTANCE);
        }
    }

    public static List<MusicSpeaker> audibleAt(ServerLevel level, Vec3 position) {
        MusicManager manager = MusicManager.get(level);
        List<MusicSpeaker> candidates = new ArrayList<>();
        for (MusicSpeaker speaker : manager.audibleSpeakers()) {
            if (!speaker.dimension().equals(level.dimension())) {
                continue;
            }
            if (manager.track(speaker.hash()).isEmpty()) {
                continue;
            }
            if (!isPresent(level, speaker.pos())) {
                manager.removeSpeaker(speaker.dimension(), speaker.pos());
                continue;
            }
            double radius = speaker.radius();
            if (distanceSqr(position, speaker.pos()) <= radius * radius) {
                candidates.add(speaker);
            }
        }
        candidates.sort(Comparator.comparingDouble(speaker -> distanceSqr(position, speaker.pos())));
        return candidates.size() <= MusicLimits.MAX_ACTIVE_SPEAKERS_PER_CLIENT
                ? List.copyOf(candidates)
                : List.copyOf(candidates.subList(0, MusicLimits.MAX_ACTIVE_SPEAKERS_PER_CLIENT));
    }

    private static boolean isPresent(ServerLevel level, BlockPos pos) {
        return !level.isLoaded(pos) || level.getBlockState(pos).is(ModBlocks.MUSIC_BLOCK.get());
    }

    private static double distanceSqr(Vec3 position, BlockPos pos) {
        return position.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }

    private static boolean sameSound(MusicSpeaker first, MusicSpeaker second) {
        return first.hash().equals(second.hash())
                && first.volume() == second.volume()
                && first.radius() == second.radius()
                && first.loop() == second.loop();
    }

    private MusicRadius() {
    }
}
