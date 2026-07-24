package com.geydev.kalfactions.outpost.cluster;

import com.geydev.kalfactions.block.DrillBlockEntity;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.menu.DrillMenu;
import com.geydev.kalfactions.net.FactionServerHooks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DrillService {
    private static final Map<UUID, Long> LAST_REQUEST_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_SELECT_TICK = new ConcurrentHashMap<>();

    public static void sendTargets(ServerPlayer player, DrillBlockEntity drill) {
        if (!(player.containerMenu instanceof DrillMenu menu)
                || !drill.stillValid(player)
                || menu.serverDrill() != drill) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Faction faction = FactionManager.get(level).getFactionForMember(player.getUUID()).orElse(null);
        List<DrillPayloads.TargetInfo> targets = targetsFor(level, faction, drill);
        PacketDistributor.sendToPlayer(player, new DrillPayloads.S2CTargets(
                menu.containerId,
                drill.getBlockPos(),
                drill.targetClusterChunk() != null,
                targets
        ));
    }

    public static List<DrillPayloads.TargetInfo> targetsFor(
            ServerLevel level,
            Faction faction,
            DrillBlockEntity drill
    ) {
        if (faction == null || !faction.hasClaim(ClaimKey.of(level, drill.getBlockPos()))) {
            if (drill.targetClusterChunk() != null) {
                drill.releaseTarget(level);
            }
            return List.of();
        }
        ResourceClusterManager clusters = ResourceClusterManager.get(level);
        long drillPos = drill.getBlockPos().asLong();
        Long selected = drill.targetClusterChunk();
        if (selected != null) {
            ChunkPos selectedChunk = new ChunkPos(selected);
            boolean valid = faction.hasClaim(ClaimKey.of(level, selectedChunk))
                    && clusters.clusterAt(selectedChunk).isPresent()
                    && (clusters.isBoundDrill(selectedChunk, drill.getBlockPos())
                            || clusters.bindDrill(selectedChunk, drill.getBlockPos()));
            if (!valid) {
                drill.releaseTarget(level);
                selected = null;
            }
        }
        List<DrillPayloads.TargetInfo> targets = new ArrayList<>();
        for (ResourceClusterManager.SurfaceClusterView cluster
                : clusters.clustersIn(faction.claims(), level.dimension().location())) {
            if (targets.size() >= DrillPayloads.MAX_TARGETS) {
                break;
            }
            boolean isSelected = selected != null && selected == cluster.chunk();
            boolean available = cluster.boundDrill() == null
                    || cluster.boundDrill() == drillPos
                    || isSelected;
            targets.add(new DrillPayloads.TargetInfo(
                    cluster.chunk(),
                    cluster.pos(),
                    cluster.type().id(),
                    cluster.richness(),
                    isSelected,
                    available
            ));
        }
        return List.copyOf(targets);
    }

    public static void requestTargets(ServerPlayer player, DrillPayloads.C2SRequestTargets payload) {
        if (payload.containerId() < 0
                || !(player.containerMenu instanceof DrillMenu menu)
                || menu.containerId != payload.containerId()
                || menu.serverDrill() == null
                || !menu.serverDrill().stillValid(player)
                || !allow(LAST_REQUEST_TICK, player, 2L)) {
            return;
        }
        sendTargets(player, menu.serverDrill());
    }

    public static void selectTarget(ServerPlayer player, DrillPayloads.C2SSelectTarget payload) {
        if (payload.containerId() < 0
                || !(player.containerMenu instanceof DrillMenu menu)
                || menu.containerId != payload.containerId()
                || menu.serverDrill() == null
                || !menu.serverDrill().stillValid(player)
                || !allow(LAST_SELECT_TICK, player, 4L)) {
            return;
        }
        DrillBlockEntity drill = menu.serverDrill();
        ServerLevel level = player.serverLevel();
        Faction faction = FactionManager.get(level).getFactionForMember(player.getUUID()).orElse(null);
        ClaimKey drillClaim = ClaimKey.of(level, drill.getBlockPos());
        ClaimKey targetClaim = ClaimKey.of(level, new ChunkPos(payload.targetChunk()));
        ResourceClusterManager clusters = ResourceClusterManager.get(level);
        boolean valid = faction != null
                && faction.hasClaim(drillClaim)
                && faction.hasClaim(targetClaim)
                && clusters.clusterAt(new ChunkPos(payload.targetChunk())).isPresent()
                && level.getWorldBorder().isWithinBounds(new BlockPos(
                        targetClaim.chunk().getMiddleBlockX(),
                        level.getSeaLevel(),
                        targetClaim.chunk().getMiddleBlockZ()
                ));
        if (!valid) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.drill.target_invalid"), false);
            sendTargets(player, drill);
            return;
        }
        if (!drill.selectTarget(level, payload.targetChunk())) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.drill.cluster_taken"), false);
            sendTargets(player, drill);
            return;
        }
        FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.drill.target_selected"), true);
        sendTargets(player, drill);
    }

    public static void clearRateLimit(UUID playerId) {
        LAST_REQUEST_TICK.remove(playerId);
        LAST_SELECT_TICK.remove(playerId);
    }

    private static boolean allow(Map<UUID, Long> limits, ServerPlayer player, long intervalTicks) {
        long now = player.serverLevel().getGameTime();
        Long previous = limits.get(player.getUUID());
        if (previous != null && now - previous < intervalTicks) {
            return false;
        }
        limits.put(player.getUUID(), now);
        return true;
    }

    private DrillService() {
    }
}
