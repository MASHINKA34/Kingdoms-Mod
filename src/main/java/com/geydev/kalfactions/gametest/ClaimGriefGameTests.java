package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.protection.ClaimBoundary;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClaimGriefGameTests {
    @GameTest(template = "empty", batch = "claim_grief", timeoutTicks = 400)
    public static void pistonsCannotReachAcrossAClaimBoundary(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos edge = boundaryEdge(level);
        BlockPos target = edge.east();
        helper.assertTrue(
                new ChunkPos(edge).x != new ChunkPos(target).x,
                "the fixture straddles a chunk boundary");

        FactionManager original = FactionManager.get(level);
        try {
            level.setBlockAndUpdate(
                    edge,
                    Blocks.PISTON.defaultBlockState()
                            .setValue(BlockStateProperties.FACING, Direction.EAST)
                            .setValue(PistonBaseBlock.EXTENDED, false));
            level.setBlockAndUpdate(target, Blocks.STONE.defaultBlockState());

            install(level, ClaimKey.of(level, target), 1);
            helper.assertTrue(
                    pistonBlocked(level, edge),
                    "the piston is refused when it reaches into a foreign claim");

            install(level, ClaimKey.of(level, edge), 3);
            helper.assertTrue(
                    !pistonBlocked(level, edge),
                    "the piston runs once both chunks belong to the same faction");
        } finally {
            level.removeBlock(edge, false);
            level.removeBlock(target, false);
            level.getServer().overworld().getDataStorage().set(FactionManager.DATA_NAME, original);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "claim_grief", timeoutTicks = 400)
    public static void fluidsStayInsideTheirOwnClaim(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos edge = boundaryEdge(level);
        BlockPos across = edge.east();

        FactionManager original = FactionManager.get(level);
        try {
            install(level, ClaimKey.of(level, across), 1);
            helper.assertTrue(
                    ClaimBoundary.crossesClaimBoundary(level, edge, across),
                    "flow from unclaimed land into a claim is refused");
            helper.assertTrue(
                    !ClaimBoundary.crossesClaimBoundary(level, across, across.east()),
                    "flow inside one chunk is untouched");

            install(level, ClaimKey.of(level, edge), 3);
            helper.assertTrue(
                    !ClaimBoundary.crossesClaimBoundary(level, edge, across),
                    "flow between two chunks of the same faction is allowed");
        } finally {
            level.getServer().overworld().getDataStorage().set(FactionManager.DATA_NAME, original);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "claim_grief", timeoutTicks = 400)
    public static void mobsCannotGriefInsideAClaim(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));

        FactionManager original = FactionManager.get(level);
        EnderMan enderman = EntityType.ENDERMAN.spawn(level, pos, MobSpawnType.COMMAND);
        helper.assertTrue(enderman != null, "the enderman spawned");
        install(level, ClaimKey.of(level, pos), 1);
        try {
            EntityMobGriefingEvent inside = new EntityMobGriefingEvent(level, enderman);
            NeoForge.EVENT_BUS.post(inside);
            helper.assertTrue(!inside.canGrief(), "mob griefing is refused inside a claim");
        } finally {
            enderman.discard();
            level.getServer().overworld().getDataStorage().set(FactionManager.DATA_NAME, original);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "claim_grief", timeoutTicks = 400)
    public static void ownersMayBlastTheirOwnClaim(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        ServerPlayer owner = RegressionPlayers.create(level, pos, 0).player();
        ServerPlayer outsider = RegressionPlayers.create(level, pos, 0).player();

        FactionManager original = FactionManager.get(level);
        FactionManager manager = new FactionManager();
        FactionManager.OperationResult created = manager.createFaction(
                owner.getUUID(), "Sappers", ClaimKey.of(level, pos), 1);
        helper.assertTrue(created.successful(), "the test faction was created: " + created.status());
        level.getServer().overworld().getDataStorage().set(FactionManager.DATA_NAME, manager);
        try {
            helper.assertTrue(
                    detonate(level, owner, pos).contains(pos),
                    "the owning faction may blast its own claim");
            helper.assertTrue(
                    !detonate(level, outsider, pos).contains(pos),
                    "an outsider still cannot blast the claim");
        } finally {
            level.getServer().overworld().getDataStorage().set(FactionManager.DATA_NAME, original);
        }
        helper.succeed();
    }

    private static List<BlockPos> detonate(ServerLevel level, ServerPlayer source, BlockPos pos) {
        List<BlockPos> affected = new ArrayList<>(List.of(pos));
        Explosion explosion = new Explosion(
                level,
                source,
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D,
                3.0F,
                false,
                Explosion.BlockInteraction.DESTROY,
                affected);
        ExplosionEvent.Detonate event = new ExplosionEvent.Detonate(level, explosion, List.of());
        NeoForge.EVENT_BUS.post(event);
        return event.getAffectedBlocks();
    }

    private static boolean pistonBlocked(ServerLevel level, BlockPos piston) {
        PistonEvent.Pre event = new PistonEvent.Pre(
                level, piston, Direction.EAST, PistonEvent.PistonMoveType.EXTEND);
        NeoForge.EVENT_BUS.post(event);
        return event.isCanceled();
    }

    private static BlockPos boundaryEdge(ServerLevel level) {
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos edge = new BlockPos(
                ((spawn.getX() + 96) & ~15) - 1,
                level.getSeaLevel() + 8,
                spawn.getZ() + 96);
        level.getChunk(new ChunkPos(edge).x, new ChunkPos(edge).z);
        level.getChunk(new ChunkPos(edge.east()).x, new ChunkPos(edge.east()).z);
        return edge;
    }

    private static void install(ServerLevel level, ClaimKey center, int size) {
        FactionManager manager = new FactionManager();
        FactionManager.OperationResult created = manager.createFaction(
                UUID.randomUUID(), "Borderline", center, size);
        if (!created.successful()) {
            throw new IllegalStateException("Cannot create test faction: " + created.status());
        }
        level.getServer().overworld().getDataStorage().set(FactionManager.DATA_NAME, manager);
    }

    private ClaimGriefGameTests() {
    }
}
