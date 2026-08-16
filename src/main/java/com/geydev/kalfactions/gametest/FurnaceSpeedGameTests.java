package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.faction.ResearchNode;
import com.geydev.kalfactions.sanctuary.SanctuaryManager;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FurnaceSpeedGameTests {
    private static final int WARMUP_TICKS = 160;
    private static final int SAMPLE_TICKS = 200;
    private static final int VANILLA_COOK_TICKS = 200;

    @GameTest(template = "empty", batch = "furnace_speed", timeoutTicks = 600)
    public static void claimedFurnacesKeepTheirDoubledSmeltRate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FactionManager factions = FactionManager.get(level);
        ChunkPos boostedChunk = findFreeChunk(level, factions, 6_000);
        ChunkPos plainChunk = findFreeChunk(level, factions, 7_000);
        UUID owner = UUID.randomUUID();
        FactionManager.OperationResult created = factions.createFaction(
                owner,
                "Smelt" + Integer.toUnsignedString(owner.hashCode(), 36),
                new ClaimKey(level.dimension(), boostedChunk),
                1
        );
        helper.assertTrue(created.successful(), "smelting test faction created");
        Faction faction = factions.getFactionById(created.factionId()).orElseThrow();
        helper.assertTrue(
                factions.grantResearch(faction.id(), ResearchNode.SCI_SMELT),
                "smelting research granted"
        );
        helper.assertTrue(faction.researchBonusCount("SMELT_SPEED") > 0, "smelting bonus active");

        AbstractFurnaceBlockEntity boosted = lightFurnace(level, boostedChunk);
        AbstractFurnaceBlockEntity plain = lightFurnace(level, plainChunk);
        long[] start = new long[2];

        helper.runAtTickTime(WARMUP_TICKS, () -> {
            start[0] = totalProgress(boosted);
            start[1] = totalProgress(plain);
        });
        helper.runAtTickTime(WARMUP_TICKS + SAMPLE_TICKS, () -> {
            long boostedGain = totalProgress(boosted) - start[0];
            long plainGain = totalProgress(plain) - start[1];
            cleanUp(level, factions, faction.id(), boostedChunk, plainChunk);

            helper.assertTrue(
                    plainGain >= SAMPLE_TICKS * 95L / 100L && plainGain <= SAMPLE_TICKS,
                    "an unclaimed furnace advances one tick of progress per tick, saw " + plainGain
            );
            helper.assertTrue(
                    boostedGain >= SAMPLE_TICKS * 195L / 100L,
                    "a claimed furnace must still advance about two per tick, saw " + boostedGain
            );
            helper.assertTrue(
                    boostedGain <= SAMPLE_TICKS * 2L,
                    "the bonus must not exceed one extra tick of progress per tick, saw " + boostedGain
            );
            helper.succeed();
        });
    }

    private static long totalProgress(AbstractFurnaceBlockEntity furnace) {
        return (long) VANILLA_COOK_TICKS * furnace.getItem(2).getCount() + furnace.cookingProgress;
    }

    private static AbstractFurnaceBlockEntity lightFurnace(ServerLevel level, ChunkPos chunk) {
        level.setChunkForced(chunk.x, chunk.z, true);
        BlockPos pos = level.getHeightmapPos(
                Heightmap.Types.WORLD_SURFACE,
                new BlockPos(chunk.getMiddleBlockX(), 0, chunk.getMiddleBlockZ())
        );
        level.setBlock(pos, Blocks.FURNACE.defaultBlockState(), 3);
        AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) level.getBlockEntity(pos);
        furnace.setItem(0, new ItemStack(Items.RAW_IRON, 64));
        furnace.setItem(1, new ItemStack(Items.COAL, 64));
        furnace.setChanged();
        return furnace;
    }

    private static void cleanUp(
            ServerLevel level,
            FactionManager factions,
            UUID factionId,
            ChunkPos boostedChunk,
            ChunkPos plainChunk
    ) {
        factions.disbandFaction(factionId);
        for (ChunkPos chunk : new ChunkPos[]{boostedChunk, plainChunk}) {
            BlockPos pos = level.getHeightmapPos(
                    Heightmap.Types.WORLD_SURFACE,
                    new BlockPos(chunk.getMiddleBlockX(), 0, chunk.getMiddleBlockZ())
            );
            level.removeBlockEntity(pos);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            level.setChunkForced(chunk.x, chunk.z, false);
        }
    }

    private static ChunkPos findFreeChunk(ServerLevel level, FactionManager factions, int startDistance) {
        BlockPos spawn = level.getSharedSpawnPos();
        SanctuaryManager sanctuary = SanctuaryManager.get(level);
        for (int distance = startDistance; distance <= startDistance + 512; distance += 16) {
            ChunkPos chunk = new ChunkPos(new BlockPos(spawn.getX() + distance, 0, spawn.getZ()));
            ClaimKey key = new ClaimKey(level.dimension(), chunk);
            if (factions.getFactionAt(key).isEmpty() && !sanctuary.isSanctuary(key)) {
                return chunk;
            }
        }
        throw new IllegalStateException("No free chunk for the smelting test");
    }

    private FurnaceSpeedGameTests() {
    }
}
