package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.quarry.QuarryDistribution;
import com.geydev.kalfactions.quarry.QuarryManager;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class QuarryMapGameTests {
    @GameTest(template = "empty", batch = "quarry_map")
    public static void neutralQuarryPublishesThreeByThreeTerritory(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        QuarryManager manager = QuarryManager.get(level);
        ChunkPos candidate = findDistributionCandidate(level, manager);
        level.getChunk(candidate.x, candidate.z);
        manager.all().stream()
                .filter(view -> new ChunkPos(view.core()).equals(candidate))
                .findFirst()
                .ifPresent(view -> {
                    manager.removeByCore(level, view.core());
                    level.removeBlock(view.core(), false);
                });
        int surfaceY = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                candidate.getMiddleBlockX(),
                candidate.getMiddleBlockZ()
        );
        BlockPos surface = new BlockPos(candidate.getMiddleBlockX(), surfaceY, candidate.getMiddleBlockZ());
        level.setBlock(surface, Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(surface.above(), Blocks.AIR.defaultBlockState(), 2);
        long initialRevision = manager.mapRevision();
        QuarryManager.CreateResult result = manager.createAtChunk(level, candidate);
        if (result != QuarryManager.CreateResult.CREATED && result != QuarryManager.CreateResult.OVERLAP) {
            throw new IllegalStateException("Quarry candidate rejected: " + result);
        }
        QuarryManager.QuarryView quarry = manager.all().stream()
                .filter(view -> new ChunkPos(view.core()).equals(candidate))
                .findFirst()
                .orElseThrow();

        helper.assertValueEqual(quarry.chunks().size(), 9, "quarry territory size");
        int minX = quarry.chunks().stream().min(Comparator.comparingInt(ClaimKey::x)).orElseThrow().x();
        int maxX = quarry.chunks().stream().max(Comparator.comparingInt(ClaimKey::x)).orElseThrow().x();
        int minZ = quarry.chunks().stream().min(Comparator.comparingInt(ClaimKey::z)).orElseThrow().z();
        int maxZ = quarry.chunks().stream().max(Comparator.comparingInt(ClaimKey::z)).orElseThrow().z();
        helper.assertValueEqual(maxX - minX, 2, "quarry territory width");
        helper.assertValueEqual(maxZ - minZ, 2, "quarry territory height");
        helper.assertValueEqual(manager.mapRevision(), initialRevision + 1L, "quarry map revision after creation");

        helper.assertTrue(manager.removeByCore(level, quarry.core()), "quarry removed");
        level.removeBlock(quarry.core(), false);
        helper.assertValueEqual(manager.mapRevision(), initialRevision + 2L, "quarry map revision after removal");
        helper.succeed();
    }

    private static ChunkPos findDistributionCandidate(ServerLevel level, QuarryManager manager) {
        BlockPos spawn = level.getSharedSpawnPos();
        int red = ModConfigSpec.RESOURCE_RED_RADIUS.getAsInt();
        for (int distance = red + 64; distance <= red + 1_800; distance += 16) {
            for (int offset = -6_000; offset <= 6_000; offset += 16) {
                ChunkPos chunk = new ChunkPos(new BlockPos(spawn.getX() + distance, 0, spawn.getZ() + offset));
                if (!level.getWorldBorder().isWithinBounds(new BlockPos(
                        chunk.getMiddleBlockX(),
                        level.getSeaLevel(),
                        chunk.getMiddleBlockZ()
                ))) {
                    continue;
                }
                if (!QuarryDistribution.isCandidate(
                        level.getSeed() ^ 0x5155415252594C31L,
                        chunk.x,
                        chunk.z,
                        QuarryManager.MINIMUM_SPACING_CHUNKS
                )) {
                    continue;
                }
                boolean blocked = manager.all().stream().anyMatch(view -> {
                    ChunkPos existing = new ChunkPos(view.core());
                    return !existing.equals(chunk)
                            && Math.max(Math.abs(existing.x - chunk.x), Math.abs(existing.z - chunk.z))
                            <= QuarryManager.MINIMUM_SPACING_CHUNKS;
                });
                if (!blocked) {
                    return chunk;
                }
            }
        }
        throw new IllegalStateException("No valid black-zone quarry position found");
    }

    private QuarryMapGameTests() {
    }
}
