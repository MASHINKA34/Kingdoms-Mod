package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.block.DrillBlockEntity;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.outpost.cluster.DrillPayloads;
import com.geydev.kalfactions.outpost.cluster.DrillService;
import com.geydev.kalfactions.outpost.cluster.ResourceClusterManager;
import com.geydev.kalfactions.outpost.cluster.distribution.ResourceZone;
import com.geydev.kalfactions.registry.ModBlocks;
import com.geydev.kalfactions.sanctuary.SanctuaryManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DrillRuntimeGameTests {
    private static final int STARTER_SIZE = 41;

    @GameTest(template = "empty", batch = "drill_runtime", timeoutTicks = 200)
    public static void idleSelectionChangeConflictAndMainTerritoryFilter(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FactionManager factions = FactionManager.get(level);
        ChunkPos factionCenter = findFactionCenter(level, factions);
        UUID owner = UUID.randomUUID();
        FactionManager.OperationResult created = factions.createFaction(
                owner,
                "Drill" + Integer.toUnsignedString(owner.hashCode(), 36),
                new ClaimKey(level.dimension(), factionCenter),
                STARTER_SIZE
        );
        helper.assertTrue(created.successful(), "drill test faction created");
        Faction faction = factions.getFactionById(created.factionId()).orElseThrow();
        List<BlockPos> drillPositions = new ArrayList<>();
        UUID outpostId = null;
        try {
            ResourceClusterManager clusters = ResourceClusterManager.get(level);
            List<ChunkPos> mainClusters = findAndGenerateClusters(level, clusters, faction.claims(), 2);
            helper.assertValueEqual(mainClusters.size(), 2, "two main territory clusters");

            BlockPos firstPos = drillPosition(level, factionCenter, 0);
            BlockPos secondPos = drillPosition(level, factionCenter, 2);
            BlockPos idlePos = drillPosition(level, factionCenter, 4);
            drillPositions.add(firstPos);
            drillPositions.add(secondPos);
            drillPositions.add(idlePos);
            DrillBlockEntity first = placeDrill(level, firstPos);
            DrillBlockEntity second = placeDrill(level, secondPos);
            DrillBlockEntity idle = placeDrill(level, idlePos);

            idle.runCheck(level, idlePos);
            helper.assertValueEqual(idle.progressTicks(), 0, "idle progress");
            helper.assertValueEqual(idle.lastProduceMillis(), 0L, "idle production clock");

            long firstCluster = mainClusters.get(0).toLong();
            long secondCluster = mainClusters.get(1).toLong();
            helper.assertTrue(first.selectTarget(level, firstCluster), "first drill selected first cluster");
            helper.assertValueEqual(first.progressTicks(), 0, "new target starts at zero");
            helper.assertTrue(first.lastProduceMillis() > 0L, "new target starts production clock");
            helper.assertTrue(!second.selectTarget(level, firstCluster), "second drill rejected occupied cluster");
            helper.assertTrue(first.selectTarget(level, secondCluster), "first drill changed target");
            helper.assertTrue(clusters.isBoundDrill(mainClusters.get(1), firstPos), "new cluster bound");
            helper.assertTrue(!clusters.isBoundDrill(mainClusters.get(0), firstPos), "old cluster released");
            helper.assertTrue(second.selectTarget(level, firstCluster), "released cluster selectable");

            ChunkPos outpostCluster = findAndGenerateOutpostCluster(level, clusters, faction.claims());
            FactionManager.OperationResult attached = factions.attachOutpost(
                    faction.id(),
                    new BlockPos(outpostCluster.getMiddleBlockX(), level.getSeaLevel(), outpostCluster.getMiddleBlockZ()),
                    Set.of(new ClaimKey(level.dimension(), outpostCluster))
            );
            helper.assertTrue(attached.successful(), "distant outpost attached");
            outpostId = faction.outpostAt(new ClaimKey(level.dimension(), outpostCluster)).orElseThrow().id();
            List<DrillPayloads.TargetInfo> targets = DrillService.targetsFor(level, faction, first);
            helper.assertTrue(
                    targets.stream().noneMatch(target -> target.chunk() == outpostCluster.toLong()),
                    "distant outpost cluster excluded"
            );
        } finally {
            if (outpostId != null) {
                factions.detachOutpost(faction.id(), outpostId);
            }
            for (BlockPos pos : drillPositions) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
            factions.disbandFaction(faction.id());
        }
        helper.succeed();
    }

    private static ChunkPos findFactionCenter(ServerLevel level, FactionManager factions) {
        BlockPos spawn = level.getSharedSpawnPos();
        SanctuaryManager sanctuary = SanctuaryManager.get(level);
        int half = STARTER_SIZE / 2;
        for (int distance = 2_000; distance <= 4_000; distance += 256) {
            ChunkPos center = new ChunkPos(new BlockPos(spawn.getX() + distance, 0, spawn.getZ()));
            boolean clear = true;
            for (int dx = -half; dx <= half && clear; dx++) {
                for (int dz = -half; dz <= half; dz++) {
                    ClaimKey key = new ClaimKey(level.dimension(), center.x + dx, center.z + dz);
                    if (factions.getFactionAt(key).isPresent() || sanctuary.isSanctuary(key)) {
                        clear = false;
                        break;
                    }
                }
            }
            if (clear) {
                return center;
            }
        }
        throw new IllegalStateException("No clear yellow-zone faction center found");
    }

    private static List<ChunkPos> findAndGenerateClusters(
            ServerLevel level,
            ResourceClusterManager manager,
            Set<ClaimKey> claims,
            int count
    ) {
        List<ChunkPos> result = new ArrayList<>();
        for (ClaimKey claim : claims.stream().sorted().toList()) {
            ChunkPos chunk = claim.chunk();
            ResourceClusterManager.ChunkDiagnostic diagnostic = manager.diagnoseChunk(level, chunk);
            if (diagnostic.zone() != ResourceZone.YELLOW
                    || diagnostic.surfaceCandidateChunk() == null
                    || !diagnostic.surfaceCandidateChunk().equals(chunk)) {
                continue;
            }
            generateCluster(level, manager, chunk);
            if (manager.clusterAt(chunk).isPresent()) {
                result.add(chunk);
                if (result.size() == count) {
                    return result;
                }
            }
        }
        return result;
    }

    private static ChunkPos findAndGenerateOutpostCluster(
            ServerLevel level,
            ResourceClusterManager manager,
            Set<ClaimKey> mainClaims
    ) {
        BlockPos spawn = level.getSharedSpawnPos();
        for (int distance = 4_200; distance <= 4_800; distance += 16) {
            for (int offset = -512; offset <= 512; offset += 16) {
                ChunkPos chunk = new ChunkPos(new BlockPos(spawn.getX() + distance, 0, spawn.getZ() + offset));
                ClaimKey key = new ClaimKey(level.dimension(), chunk);
                if (mainClaims.contains(key)) {
                    continue;
                }
                ResourceClusterManager.ChunkDiagnostic diagnostic = manager.diagnoseChunk(level, chunk);
                if (diagnostic.zone() == ResourceZone.YELLOW
                        && chunk.equals(diagnostic.surfaceCandidateChunk())) {
                    generateCluster(level, manager, chunk);
                    if (manager.clusterAt(chunk).isPresent()) {
                        return chunk;
                    }
                }
            }
        }
        throw new IllegalStateException("No distant yellow surface cluster found");
    }

    private static void generateCluster(ServerLevel level, ResourceClusterManager manager, ChunkPos chunk) {
        level.getChunk(chunk.x, chunk.z);
        manager.queue(chunk, level.getGameTime() - 1L);
        manager.tick(level);
    }

    private static BlockPos drillPosition(ServerLevel level, ChunkPos chunk, int offset) {
        return new BlockPos(
                chunk.getMinBlockX() + 4 + offset,
                level.getMinBuildHeight() + 12,
                chunk.getMinBlockZ() + 4
        );
    }

    private static DrillBlockEntity placeDrill(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, ModBlocks.DRILL.get().defaultBlockState(), 3);
        return (DrillBlockEntity) level.getBlockEntity(pos);
    }

    private DrillRuntimeGameTests() {
    }
}
