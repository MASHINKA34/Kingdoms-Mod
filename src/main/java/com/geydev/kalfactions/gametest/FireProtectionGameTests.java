package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.faction.FactionManager;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FireProtectionGameTests {
    @GameTest(template = "empty", batch = "claim_fire", timeoutTicks = 400)
    public static void burningRespectsClaimsAndTheConfiguration(GameTestHelper helper) throws ReflectiveOperationException {
        ServerLevel level = helper.getLevel();
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos wild = new BlockPos(((spawn.getX() + 224) & ~15) - 1, level.getSeaLevel() + 8, spawn.getZ() + 224);
        BlockPos claimed = wild.east();
        FactionManager original = FactionManager.get(level);
        FactionManager manager = new FactionManager();
        helper.assertTrue(manager.createFaction(UUID.randomUUID(), "Fireguard", ClaimKey.of(level, claimed), 1)
                .successful(), "the claim exists");
        level.getServer().overworld().getDataStorage().set(FactionManager.DATA_NAME, manager);
        boolean protectFire = ModConfigSpec.PROTECT_FIRE.get();
        var burn = FireBlock.class.getDeclaredMethod("checkBurnOut", Level.class, BlockPos.class,
                int.class, RandomSource.class, int.class, Direction.class);
        burn.setAccessible(true);
        try {
            ModConfigSpec.PROTECT_FIRE.set(true);
            level.setBlockAndUpdate(wild, Blocks.OAK_PLANKS.defaultBlockState());
            level.setBlockAndUpdate(claimed, Blocks.OAK_PLANKS.defaultBlockState());
            var fire = Blocks.FIRE.defaultBlockState();
            helper.assertTrue(!fire.canSurvive(level, claimed.above()), "claimed wood cannot support fire");
            helper.assertTrue(fire.canSurvive(level, wild.above()), "wild wood still supports fire");
            burn.invoke(Blocks.FIRE, level, claimed, 1, RandomSource.create(1L), 0, Direction.WEST);
            helper.assertTrue(level.getBlockState(claimed).is(Blocks.OAK_PLANKS),
                    "the vanilla burnout method cannot destroy claimed wood");
            burn.invoke(Blocks.FIRE, level, wild, 1, RandomSource.create(1L), 0, Direction.EAST);
            helper.assertTrue(!level.getBlockState(wild).is(Blocks.OAK_PLANKS), "wild wood still burns normally");
            level.setBlockAndUpdate(claimed.above(), fire);
            helper.assertTrue(level.getBlockState(claimed.above()).isAir(), "fire is removed inside a claim");
            ModConfigSpec.PROTECT_FIRE.set(false);
            burn.invoke(Blocks.FIRE, level, claimed, 1, RandomSource.create(1L), 0, Direction.WEST);
            helper.assertTrue(!level.getBlockState(claimed).is(Blocks.OAK_PLANKS),
                    "disabling claim fire protection restores vanilla burning");
        } finally {
            ModConfigSpec.PROTECT_FIRE.set(protectFire);
            level.removeBlock(wild, false);
            level.removeBlock(claimed, false);
            level.removeBlock(claimed.above(), false);
            level.getServer().overworld().getDataStorage().set(FactionManager.DATA_NAME, original);
        }
        helper.succeed();
    }

    private FireProtectionGameTests() {
    }
}
