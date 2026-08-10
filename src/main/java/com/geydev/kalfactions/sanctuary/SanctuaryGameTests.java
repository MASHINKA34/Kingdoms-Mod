package com.geydev.kalfactions.sanctuary;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SanctuaryGameTests {
    @GameTest(template = "empty")
    public static void remoteControlPointDoesNotMoveAutomaticSquare(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SanctuaryManager manager = SanctuaryManager.get(level);
        BlockPos worldSpawn = level.getSharedSpawnPos();
        BlockPos remoteControl = worldSpawn.offset(1_024, 0, -1_024);
        manager.initializeAutomaticSpawn(level);
        Set<ClaimKey> spawnClaims = SanctuaryManager.calculateAutomaticClaims(
                Level.OVERWORLD,
                worldSpawn,
                200
        );

        manager.initializeAutomaticSpawn(Level.OVERWORLD, remoteControl, 200);
        helper.assertTrue(manager.claims().containsAll(spawnClaims), "spawn square stays in place");

        ClaimKey extension = ClaimKey.of(level, remoteControl);
        manager.setClaim(extension, true);
        helper.assertTrue(manager.isSanctuary(extension), "remote control can extend sanctuary");
        helper.assertTrue(manager.claims().containsAll(spawnClaims), "extension does not move spawn square");
        manager.setClaim(extension, false);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "sanctuary_fire", timeoutTicks = 600)
    public static void fireIsExtinguishedAndCannotSpreadIntoSanctuary(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SanctuaryManager manager = SanctuaryManager.get(level);
        BlockPos anchor = level.getSharedSpawnPos().offset(4_096, 0, 4_096);
        ChunkPos sanctuaryChunk = new ChunkPos(anchor);
        ChunkPos wildChunk = new ChunkPos(sanctuaryChunk.x + 1, sanctuaryChunk.z);
        level.getChunk(sanctuaryChunk.x, sanctuaryChunk.z);
        level.getChunk(wildChunk.x, wildChunk.z);

        int y = level.getSeaLevel() + 24;
        BlockPos insideFloor = new BlockPos(sanctuaryChunk.getMaxBlockX(), y, sanctuaryChunk.getMinBlockZ() + 8);
        BlockPos outsideFloor = insideFloor.east();
        BlockPos insideFuel = insideFloor.above();
        BlockPos outsideFire = outsideFloor.above();

        ClaimKey sanctuaryKey = ClaimKey.of(level, insideFloor);
        boolean fireTick = level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
        manager.setClaim(sanctuaryKey, true);
        level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(true, level.getServer());
        try {
            helper.assertTrue(manager.isSanctuary(sanctuaryKey), "test chunk is a sanctuary");
            helper.assertFalse(
                    manager.isSanctuary(ClaimKey.of(level, outsideFloor)),
                    "neighbour chunk stays wild"
            );

            level.setBlockAndUpdate(insideFloor, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(outsideFloor, Blocks.OAK_PLANKS.defaultBlockState());
            level.setBlockAndUpdate(insideFuel, Blocks.OAK_PLANKS.defaultBlockState());

            BlockState fire = Blocks.FIRE.defaultBlockState();
            helper.assertFalse(
                    fire.canSurvive(level, insideFuel.above()),
                    "fire cannot survive inside the sanctuary"
            );
            helper.assertTrue(
                    fire.canSurvive(level, outsideFire),
                    "fire still survives outside the sanctuary"
            );

            level.setBlockAndUpdate(insideFuel.above(), fire);
            helper.assertTrue(
                    level.getBlockState(insideFuel.above()).isAir(),
                    "fire placed inside the sanctuary is removed at once"
            );

            for (int attempt = 0; attempt < 400; attempt++) {
                level.setBlock(outsideFloor, Blocks.OAK_PLANKS.defaultBlockState(), 3);
                if (!level.getBlockState(outsideFire).is(Blocks.FIRE)) {
                    level.setBlock(outsideFire, fire, 3);
                }
                level.getBlockState(outsideFire).tick(level, outsideFire, level.getRandom());
            }

            helper.assertTrue(
                    level.getBlockState(insideFuel).is(Blocks.OAK_PLANKS),
                    "sanctuary fuel next to burning wild fire never burns out"
            );
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = -1; dy <= 4; dy++) {
                        BlockPos probe = outsideFire.offset(dx, dy, dz);
                        if (!manager.isSanctuary(ClaimKey.of(level, probe))) {
                            continue;
                        }
                        helper.assertFalse(
                                level.getBlockState(probe).is(Blocks.FIRE),
                                "fire never spreads into the sanctuary at " + probe
                        );
                    }
                }
            }
        } finally {
            level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(fireTick, level.getServer());
            manager.setClaim(sanctuaryKey, false);
            for (BlockPos pos : new BlockPos[] {insideFloor, outsideFloor, insideFuel, outsideFire}) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "sanctuary_lightning", timeoutTicks = 600)
    public static void lightningNeverStrikesInsideSanctuary(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SanctuaryManager manager = SanctuaryManager.get(level);
        BlockPos anchor = level.getSharedSpawnPos().offset(-4_096, 0, 4_096);
        ChunkPos sanctuaryChunk = new ChunkPos(anchor);
        ChunkPos wildChunk = new ChunkPos(sanctuaryChunk.x + 1, sanctuaryChunk.z);
        level.getChunk(sanctuaryChunk.x, sanctuaryChunk.z);
        level.getChunk(wildChunk.x, wildChunk.z);

        int y = level.getSeaLevel() + 24;
        BlockPos inside = new BlockPos(sanctuaryChunk.getMaxBlockX(), y, sanctuaryChunk.getMinBlockZ() + 8);
        BlockPos outside = inside.east();

        ClaimKey sanctuaryKey = ClaimKey.of(level, inside);
        manager.setClaim(sanctuaryKey, true);
        try {
            helper.assertTrue(manager.isSanctuary(sanctuaryKey), "test chunk is a sanctuary");
            helper.assertFalse(
                    manager.isSanctuary(ClaimKey.of(level, outside)),
                    "neighbour chunk stays wild"
            );

            LightningBolt blocked = spawnBolt(level, inside);
            helper.assertFalse(level.addFreshEntity(blocked), "sanctuary rejects the lightning bolt");
            helper.assertFalse(blocked.isAddedToLevel(), "rejected bolt never joins the level");

            LightningBolt allowed = spawnBolt(level, outside);
            helper.assertTrue(level.addFreshEntity(allowed), "lightning still strikes outside the sanctuary");
            allowed.discard();
        } finally {
            manager.setClaim(sanctuaryKey, false);
        }
        helper.succeed();
    }

    private static LightningBolt spawnBolt(ServerLevel level, BlockPos pos) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) {
            throw new IllegalStateException("Lightning bolt could not be created");
        }
        bolt.moveTo(Vec3.atBottomCenterOf(pos));
        bolt.setVisualOnly(true);
        return bolt;
    }

    private SanctuaryGameTests() {
    }
}
