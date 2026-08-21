package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.dungeon.DungeonManager;
import com.geydev.kalfactions.protection.MachineProtection;
import com.geydev.kalfactions.quarry.QuarryDistribution;
import com.geydev.kalfactions.quarry.QuarryManager;
import com.geydev.kalfactions.sanctuary.SanctuaryManager;
import com.simibubi.create.AllContraptionTypes;
import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.Contraption;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProtectedZoneMachineGameTests {
    private static final int OUTSIDE_BLOCKS = 4;
    private static final int INSIDE_BLOCKS = 4;

    @GameTest(template = "empty", batch = "protected_zone_machines", timeoutTicks = 400)
    public static void machinesRunBesideDungeons(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DungeonManager manager = DungeonManager.get(level);
        BlockPos anchor = new BlockPos(
                level.getSharedSpawnPos().getX() + 120_000,
                level.getSeaLevel() + 24,
                level.getSharedSpawnPos().getZ() + 120_000
        );
        ChunkPos chunk = new ChunkPos(anchor);
        level.getChunk(chunk.x, chunk.z);
        DungeonManager.CreateResult created = manager.create(level, anchor, "Тест механизмов");
        helper.assertTrue(created.successful(), "the dungeon was created in the black zone");
        int dungeonId = created.dungeon().id();
        helper.assertTrue(
                manager.setClaims(level, dungeonId, List.of(ClaimKey.of(level, anchor)), true).changed() == 1,
                "the anchor chunk joined the dungeon"
        );
        try {
            assertMachinesRunBeside(helper, level, chunk, anchor.getY());
            assertMachinesRunInside(helper, level, chunk, anchor.getY());
        } finally {
            manager.remove(dungeonId);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "protected_zone_machines", timeoutTicks = 400)
    public static void machinesRunBesideQuarries(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        QuarryManager manager = QuarryManager.get(level);
        ChunkPos core = findQuarryCandidate(level, manager);
        level.getChunk(core.x, core.z);
        QuarryManager.CreateResult created = manager.createAtChunk(level, core);
        helper.assertTrue(
                created == QuarryManager.CreateResult.CREATED || created == QuarryManager.CreateResult.OVERLAP,
                "the quarry was created, got " + created
        );
        BlockPos quarryCore = manager.all().stream()
                .filter(view -> new ChunkPos(view.core()).equals(core))
                .findFirst()
                .orElseThrow()
                .core();
        try {
            ChunkPos edge = new ChunkPos(core.x - 1, core.z);
            assertMachinesRunBeside(helper, level, edge, level.getSeaLevel() + 24);
            assertMachinesRunInside(helper, level, edge, level.getSeaLevel() + 24);
        } finally {
            manager.removeByCore(level, quarryCore);
            level.removeBlock(quarryCore, false);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "protected_zone_machines", timeoutTicks = 400)
    public static void machinesRunBesideTheSpawnZone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SanctuaryManager manager = SanctuaryManager.get(level);
        ChunkPos chunk = new ChunkPos(new BlockPos(
                level.getSharedSpawnPos().getX() + 150_000,
                0,
                level.getSharedSpawnPos().getZ() + 150_000
        ));
        level.getChunk(chunk.x, chunk.z);
        ClaimKey key = new ClaimKey(level.dimension(), chunk);
        helper.assertTrue(manager.setClaim(key, true), "the chunk joined the spawn zone");
        try {
            assertMachinesRunBeside(helper, level, chunk, level.getSeaLevel() + 24);
            assertMachinesRunInside(helper, level, chunk, level.getSeaLevel() + 24);
        } finally {
            manager.setClaim(key, false);
        }
        helper.succeed();
    }

    private static void assertMachinesRunBeside(
            GameTestHelper helper,
            ServerLevel level,
            ChunkPos protectedChunk,
            int y
    ) {
        level.getChunk(protectedChunk.x - 1, protectedChunk.z);
        int z = protectedChunk.getMiddleBlockZ();
        int firstX = protectedChunk.getMinBlockX() - OUTSIDE_BLOCKS;
        int lastX = protectedChunk.getMinBlockX() + INSIDE_BLOCKS - 1;
        BlockPos start = new BlockPos(firstX, y, z);
        BlockPos inside = new BlockPos(protectedChunk.getMinBlockX(), y, z);
        try {
            clear(level, firstX - 1, lastX + 1, y, z);
            for (int x = firstX; x <= lastX; x++) {
                level.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState(), 3);
            }

            helper.assertTrue(MachineProtection.protectsBlocks(level, inside), "the zone keeps its blocks");
            helper.assertFalse(
                    MachineProtection.canContraptionBreak(level, inside),
                    "a machine still may not change a protected block"
            );
            helper.assertTrue(
                    MachineProtection.canContraptionBreak(level, start),
                    "a machine may change blocks right outside the zone"
            );

            TestContraption contraption = new TestContraption();
            boolean assembled;
            try {
                assembled = contraption.searchMovedStructure(level, start, Direction.EAST);
            } catch (AssemblyException exception) {
                helper.fail("assembly beside a protected zone threw " + exception.getMessage(), start);
                return;
            }
            helper.assertTrue(assembled, "a contraption beside a protected zone still assembles");

            int outside = 0;
            for (BlockPos local : contraption.getBlocks().keySet()) {
                BlockPos world = start.offset(local);
                helper.assertFalse(
                        MachineProtection.protectsBlocks(level, world),
                        "the contraption absorbed a protected block at " + world
                );
                if (world.getY() == y && world.getZ() == z) {
                    outside++;
                }
            }
            helper.assertValueEqual(outside, OUTSIDE_BLOCKS, "blocks taken from outside the zone");

            assertDisassemblyDropsInsteadOfOverwriting(helper, level, contraption, inside.above(2), start.above(2));
        } finally {
            clear(level, firstX - 1, lastX + 1, y, z);
        }
    }

    private static void assertMachinesRunInside(
            GameTestHelper helper,
            ServerLevel level,
            ChunkPos protectedChunk,
            int y
    ) {
        int z = protectedChunk.getMiddleBlockZ();
        int firstX = protectedChunk.getMinBlockX() + 2;
        int lastX = firstX + INSIDE_BLOCKS - 1;
        BlockPos start = new BlockPos(firstX, y, z);
        try {
            clear(level, firstX - 1, lastX + 1, y, z);
            for (int x = firstX; x <= lastX; x++) {
                level.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState(), 3);
            }

            TestContraption contraption = new TestContraption();
            boolean assembled;
            try {
                assembled = contraption.searchMovedStructure(level, start, Direction.EAST);
            } catch (AssemblyException exception) {
                helper.fail("assembly inside a protected zone threw " + exception.getMessage(), start);
                return;
            }
            helper.assertTrue(assembled, "a machine standing inside the zone assembles");
            helper.assertValueEqual(
                    contraption.getBlocks().size(),
                    INSIDE_BLOCKS,
                    "blocks a machine inside the zone may move"
            );
            helper.assertFalse(
                    contraption.placeBlock(level, start.above(2), Blocks.OAK_PLANKS.defaultBlockState()),
                    "a machine inside the zone puts its blocks back instead of dropping them"
            );
        } finally {
            clear(level, firstX - 1, lastX + 1, y, z);
        }
    }

    private static void assertDisassemblyDropsInsteadOfOverwriting(
            GameTestHelper helper,
            ServerLevel level,
            TestContraption contraption,
            BlockPos inside,
            BlockPos outside
    ) {
        BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
        discardItems(level, inside);

        helper.assertFalse(
                contraption.placeBlock(level, outside, planks),
                "outside the zone the contraption places its own blocks"
        );
        helper.assertTrue(
                contraption.placeBlock(level, inside, planks),
                "inside the zone the contraption skips its own block"
        );
        helper.assertTrue(
                level.getBlockState(inside).isAir(),
                "the protected block was not overwritten"
        );
        helper.assertTrue(
                level.getEntitiesOfClass(ItemEntity.class, new AABB(inside).inflate(4.0D)).stream()
                        .anyMatch(item -> item.getItem().is(Blocks.OAK_PLANKS.asItem())),
                "the skipped block dropped as an item instead of vanishing"
        );
        discardItems(level, inside);
    }

    private static void discardItems(ServerLevel level, BlockPos around) {
        level.getEntitiesOfClass(ItemEntity.class, new AABB(around).inflate(4.0D)).forEach(ItemEntity::discard);
    }

    private static void clear(ServerLevel level, int fromX, int toX, int y, int z) {
        for (int x = fromX; x <= toX; x++) {
            for (int offsetY = -1; offsetY <= 2; offsetY++) {
                for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                    level.setBlock(new BlockPos(x, y + offsetY, z + offsetZ), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static ChunkPos findQuarryCandidate(ServerLevel level, QuarryManager manager) {
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
        throw new IllegalStateException("No valid quarry position found");
    }

    private static final class TestContraption extends Contraption {
        private boolean placeBlock(LevelAccessor world, BlockPos pos, BlockState state) {
            return customBlockPlacement(world, pos, state);
        }

        @Override
        public boolean assemble(Level world, BlockPos pos) throws AssemblyException {
            return searchMovedStructure(world, pos, null);
        }

        @Override
        public boolean canBeStabilized(Direction facing, BlockPos localPos) {
            return false;
        }

        @Override
        public ContraptionType getType() {
            return AllContraptionTypes.BEARING.value();
        }

        @Override
        protected boolean isAnchoringBlockAt(BlockPos pos) {
            return false;
        }
    }

    private ProtectedZoneMachineGameTests() {
    }
}
