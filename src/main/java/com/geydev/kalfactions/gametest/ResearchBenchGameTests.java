package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.block.ResearchBenchBlockEntity;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.faction.InfluenceType;
import com.geydev.kalfactions.faction.ScienceLedger;
import com.geydev.kalfactions.registry.ModBlocks;
import com.geydev.kalfactions.registry.ModItems;
import com.geydev.kalfactions.sanctuary.SanctuaryManager;
import com.geydev.kalfactions.science.ResearchBenchStatus;
import com.geydev.kalfactions.science.ScienceInputs;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ResearchBenchGameTests {
    private static final String TAG_LAST = "LastProduceMillis";

    @GameTest(template = "empty", batch = "research_bench", timeoutTicks = 200)
    public static void oneMaterialPaysExactlyItsValue(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        try {
            ScienceInputs.Entry paper = requireEntry(helper);
            fixture.bench.setItem(0, new ItemStack(Items.PAPER, 4));
            fixture.bench.runCheck(fixture.level);
            helper.assertTrue(
                    fixture.bench.status() == ResearchBenchStatus.WORKING,
                    "A loaded bench on faction land did not start working"
            );

            ResearchBenchBlockEntity bench = fixture.rewind(paper.intervalMillis());
            bench.runCheck(fixture.level);

            helper.assertValueEqual(fixture.science(), paper.science(), "science granted for one material");
            helper.assertValueEqual(bench.getItem(0).getCount(), 3, "materials left after one cycle");
            helper.assertValueEqual(
                    fixture.ledger().grantedToday(fixture.factionId, System.currentTimeMillis()),
                    paper.science(),
                    "daily science counter after one cycle"
            );
        } finally {
            fixture.cleanUp();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "research_bench", timeoutTicks = 200)
    public static void aCappedDayKeepsTheMaterials(GameTestHelper helper) {
        long cap = ModConfigSpec.SCIENCE_DAILY_CAP.getAsLong();
        helper.assertTrue(cap > 0L, "The daily science cap is disabled");
        Fixture fixture = fixture(helper);
        try {
            ScienceInputs.Entry paper = requireEntry(helper);
            fixture.ledger().recordScience(fixture.factionId, System.currentTimeMillis(), cap);
            fixture.bench.setItem(0, new ItemStack(Items.PAPER, 2));
            fixture.bench.runCheck(fixture.level);

            ResearchBenchBlockEntity bench = fixture.rewind(5L * paper.intervalMillis());
            bench.runCheck(fixture.level);

            helper.assertValueEqual(fixture.science(), 0L, "science granted on a capped day");
            helper.assertValueEqual(bench.getItem(0).getCount(), 2, "materials left on a capped day");
            helper.assertTrue(
                    bench.status() == ResearchBenchStatus.DAILY_CAP,
                    "A capped bench does not report the daily limit"
            );

            fixture.ledger().resetDaily(fixture.factionId);
            bench.runCheck(fixture.level);

            helper.assertValueEqual(fixture.science(), paper.science(), "science granted after the day rolled over");
            helper.assertValueEqual(bench.getItem(0).getCount(), 1, "materials left after the day rolled over");
        } finally {
            fixture.cleanUp();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "research_bench", timeoutTicks = 200)
    public static void catchUpAfterAReloadDoesNotDoubleCount(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        try {
            ScienceInputs.Entry paper = requireEntry(helper);
            fixture.bench.setItem(0, new ItemStack(Items.PAPER, 8));
            fixture.bench.runCheck(fixture.level);

            ResearchBenchBlockEntity bench = fixture.rewind(3L * paper.intervalMillis());
            bench.runCheck(fixture.level);

            helper.assertValueEqual(fixture.science(), 3L * paper.science(), "science after the catch-up");
            helper.assertValueEqual(bench.getItem(0).getCount(), 5, "materials left after the catch-up");

            bench.runCheck(fixture.level);

            helper.assertValueEqual(fixture.science(), 3L * paper.science(), "science after a repeated check");
            helper.assertValueEqual(bench.getItem(0).getCount(), 5, "materials left after a repeated check");
            helper.assertTrue(
                    bench.status() == ResearchBenchStatus.WORKING,
                    "The bench stopped working after the catch-up"
            );
        } finally {
            fixture.cleanUp();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "research_bench", timeoutTicks = 200)
    public static void anOutsiderCannotOpenTheBench(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        try {
            helper.assertTrue(fixture.bench.canOpen(fixture.owner), "A member cannot open their own bench");
            helper.assertTrue(
                    !fixture.bench.canOpen(UUID.randomUUID()),
                    "A player from outside the faction can open the bench"
            );
            helper.assertTrue(
                    fixture.bench.canPlaceItem(0, new ItemStack(Items.PAPER)),
                    "The bench refuses a listed research material"
            );
            helper.assertTrue(
                    !fixture.bench.canPlaceItem(0, new ItemStack(Items.STONE)),
                    "The bench accepts an item that is not a research material"
            );
        } finally {
            fixture.cleanUp();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "research_bench", timeoutTicks = 200)
    public static void unclaimedLandStopsTheBench(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FactionManager factions = FactionManager.get(level);
        ChunkPos chunk = freeChunk(level, factions);
        BlockPos pos = benchPosition(level, chunk);
        try {
            ResearchBenchBlockEntity bench = placeBench(level, pos);
            bench.setItem(0, new ItemStack(Items.PAPER, 2));
            bench.runCheck(level);

            helper.assertTrue(
                    bench.status() == ResearchBenchStatus.OFF_TERRITORY,
                    "A bench outside any claim does not report lost territory"
            );
            helper.assertValueEqual(bench.getItem(0).getCount(), 2, "materials left outside a claim");
            helper.assertValueEqual(bench.progressTicks(), 0, "progress outside a claim");
        } finally {
            clearBench(level, pos);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "research_bench", timeoutTicks = 200)
    public static void onlyOneBenchRecipeLoads(GameTestHelper helper) {
        boolean create = ModList.get().isLoaded("create");
        RecipeHolder<?> withCreate = recipe(helper, "research_bench");
        RecipeHolder<?> withoutCreate = recipe(helper, "research_bench_vanilla");

        helper.assertTrue(create == (withCreate != null), "The Create bench recipe ignores whether Create is loaded");
        helper.assertTrue(create != (withoutCreate != null), "Both bench recipes loaded at once");
        RecipeHolder<?> loaded = withCreate == null ? withoutCreate : withCreate;
        helper.assertTrue(
                loaded.value().getResultItem(helper.getLevel().registryAccess()).is(ModItems.RESEARCH_BENCH.get()),
                "The bench recipe does not craft a research bench"
        );
        helper.succeed();
    }

    private static RecipeHolder<?> recipe(GameTestHelper helper, String id) {
        return helper.getLevel()
                .getRecipeManager()
                .byKey(ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, id))
                .orElse(null);
    }

    private static ScienceInputs.Entry requireEntry(GameTestHelper helper) {
        ScienceInputs.Entry entry = ScienceInputs.entry(new ItemStack(Items.PAPER));
        helper.assertTrue(entry != null, "science_inputs.json does not list minecraft:paper");
        return entry;
    }

    private static Fixture fixture(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FactionManager factions = FactionManager.get(level);
        ChunkPos chunk = freeChunk(level, factions);
        UUID owner = UUID.randomUUID();
        FactionManager.OperationResult created = factions.createFaction(
                owner,
                "Bench" + Integer.toUnsignedString(owner.hashCode(), 36),
                new ClaimKey(level.dimension(), chunk),
                1
        );
        helper.assertTrue(created.successful(), "bench test faction created");
        BlockPos pos = benchPosition(level, chunk);
        return new Fixture(level, factions, created.factionId(), owner, pos, placeBench(level, pos));
    }

    private static ChunkPos freeChunk(ServerLevel level, FactionManager factions) {
        BlockPos spawn = level.getSharedSpawnPos();
        SanctuaryManager sanctuary = SanctuaryManager.get(level);
        for (int distance = 6_000; distance <= 9_000; distance += 32) {
            ChunkPos chunk = new ChunkPos(new BlockPos(spawn.getX() + distance, 0, spawn.getZ() + 512));
            ClaimKey key = new ClaimKey(level.dimension(), chunk);
            if (factions.getFactionAt(key).isEmpty() && !sanctuary.isSanctuary(key)) {
                return chunk;
            }
        }
        throw new IllegalStateException("No free chunk for the research bench test");
    }

    private static BlockPos benchPosition(ServerLevel level, ChunkPos chunk) {
        return new BlockPos(chunk.getMinBlockX() + 4, level.getMinBuildHeight() + 12, chunk.getMinBlockZ() + 4);
    }

    private static ResearchBenchBlockEntity placeBench(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, ModBlocks.RESEARCH_BENCH.get().defaultBlockState(), 3);
        return (ResearchBenchBlockEntity) level.getBlockEntity(pos);
    }

    private static void clearBench(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ResearchBenchBlockEntity bench) {
            bench.clearContent();
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    private record Fixture(
            ServerLevel level,
            FactionManager factions,
            UUID factionId,
            UUID owner,
            BlockPos pos,
            ResearchBenchBlockEntity bench
    ) {
        private ScienceLedger ledger() {
            return ScienceLedger.get(level);
        }

        private long science() {
            return factions.getFactionById(factionId)
                    .map(faction -> faction.influence(InfluenceType.SCIENCE))
                    .orElse(0L);
        }

        private ResearchBenchBlockEntity rewind(long millis) {
            ResearchBenchBlockEntity current = (ResearchBenchBlockEntity) level.getBlockEntity(pos);
            CompoundTag tag = current.saveWithoutMetadata(level.registryAccess());
            tag.putLong(TAG_LAST, tag.getLong(TAG_LAST) - millis);
            current.clearContent();
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            ResearchBenchBlockEntity reloaded = placeBench(level, pos);
            reloaded.loadCustomOnly(tag, level.registryAccess());
            return reloaded;
        }

        private void cleanUp() {
            clearBench(level, pos);
            ledger().removeFaction(factionId);
            factions.disbandFaction(factionId);
        }
    }

    private ResearchBenchGameTests() {
    }
}
