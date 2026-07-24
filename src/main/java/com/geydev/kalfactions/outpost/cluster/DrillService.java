package com.geydev.kalfactions.outpost.cluster;

import com.geydev.kalfactions.block.DrillBlockEntity;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.menu.DrillMenu;
import com.geydev.kalfactions.net.FactionServerHooks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DrillService {
    public static void sendTargets(ServerPlayer player, DrillBlockEntity drill) {
        if (!(player.containerMenu instanceof DrillMenu menu)
                || !drill.stillValid(player)
                || menu.serverDrill() != drill) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Faction faction = FactionManager.get(level).getFactionForMember(player.getUUID()).orElse(null);
        List<DrillPayloads.TargetInfo> targets = new ArrayList<>();
        if (faction != null && faction.hasClaim(ClaimKey.of(level, drill.getBlockPos()))) {
            long drillPos = drill.getBlockPos().asLong();
            Long selected = drill.targetClusterChunk();
            for (ResourceClusterManager.SurfaceClusterView cluster : ResourceClusterManager.get(level)
                    .clustersIn(faction.claims(), level.dimension().location())) {
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
        }
        PacketDistributor.sendToPlayer(player, new DrillPayloads.S2CTargets(
                menu.containerId,
                drill.getBlockPos(),
                targets
        ));
    }

    public static void selectTarget(ServerPlayer player, DrillPayloads.C2SSelectTarget payload) {
        if (!(player.containerMenu instanceof DrillMenu menu)
                || menu.containerId != payload.containerId()
                || menu.serverDrill() == null
                || !menu.serverDrill().stillValid(player)) {
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
                && clusters.clusterAt(new ChunkPos(payload.targetChunk())).isPresent();
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

    private DrillService() {
    }
}
