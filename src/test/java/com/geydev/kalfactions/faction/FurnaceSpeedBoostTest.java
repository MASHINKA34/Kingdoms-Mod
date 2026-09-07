package com.geydev.kalfactions.faction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class FurnaceSpeedBoostTest {
    private static final int VANILLA_COOK_TICKS = 200;
    private static final int STACK = 64;

    private static final IntBinaryOperator NO_BOOST = (progress, total) -> progress;
    private static final IntBinaryOperator LEGACY_BOOST = (progress, total) -> {
        if (progress <= 0 || progress >= total - 1) {
            return progress;
        }
        return progress + 1;
    };
    private static final IntBinaryOperator CURRENT_BOOST = FurnaceSpeedTicker::boostedProgress;

    @BeforeAll
    static void bootstrap() {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void onlyALitFurnaceIsBoostable() {
        assertTrue(FurnaceSpeedTicker.boostable(
                Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.LIT, true)));
        assertFalse(FurnaceSpeedTicker.boostable(
                Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.LIT, false)));
        assertFalse(FurnaceSpeedTicker.boostable(Blocks.STONE.defaultBlockState()));
    }

    @Test
    void oneItemTakesTheSameTicksAsBeforeTheIndexRewrite() {
        assertEquals(VANILLA_COOK_TICKS, ticksToSmelt(1, VANILLA_COOK_TICKS, NO_BOOST));
        assertEquals(101, ticksToSmelt(1, VANILLA_COOK_TICKS, LEGACY_BOOST));
        assertEquals(101, ticksToSmelt(1, VANILLA_COOK_TICKS, CURRENT_BOOST));
    }

    @Test
    void oneStackTakesTheSameTicksAsBeforeTheIndexRewrite() {
        assertEquals(12_800, ticksToSmelt(STACK, VANILLA_COOK_TICKS, NO_BOOST));
        assertEquals(6_464, ticksToSmelt(STACK, VANILLA_COOK_TICKS, LEGACY_BOOST));
        assertEquals(6_464, ticksToSmelt(STACK, VANILLA_COOK_TICKS, CURRENT_BOOST));
    }

    @Test
    void theBoostMatchesTheRemovedCodeForEveryCookTime() {
        for (int cookTicks = 1; cookTicks <= 400; cookTicks++) {
            for (int items = 1; items <= 3; items++) {
                assertEquals(
                        ticksToSmelt(items, cookTicks, LEGACY_BOOST),
                        ticksToSmelt(items, cookTicks, CURRENT_BOOST),
                        "cookTicks=" + cookTicks + " items=" + items
                );
            }
        }
        for (int progress = -2; progress <= 400; progress++) {
            for (int cookTicks = 1; cookTicks <= 400; cookTicks++) {
                assertEquals(
                        LEGACY_BOOST.applyAsInt(progress, cookTicks),
                        FurnaceSpeedTicker.boostedProgress(progress, cookTicks),
                        "progress=" + progress + " cookTicks=" + cookTicks
                );
            }
        }
    }

    @Test
    void theBoostNeverSkipsTheCompletionFrame() {
        for (int cookTicks = 1; cookTicks <= 400; cookTicks++) {
            assertEquals(
                    ticksToSmelt(4, cookTicks, CURRENT_BOOST) - ticksToSmelt(3, cookTicks, CURRENT_BOOST),
                    ticksToSmelt(2, cookTicks, CURRENT_BOOST) - ticksToSmelt(1, cookTicks, CURRENT_BOOST),
                    "cookTicks=" + cookTicks
            );
        }
    }

    private static int ticksToSmelt(int items, int cookingTotalTime, IntBinaryOperator boost) {
        int cookingProgress = 0;
        int smelted = 0;
        int ticks = 0;
        while (smelted < items) {
            ticks++;
            cookingProgress++;
            if (cookingProgress == cookingTotalTime) {
                cookingProgress = 0;
                smelted++;
            }
            cookingProgress = boost.applyAsInt(cookingProgress, cookingTotalTime);
        }
        return ticks;
    }
}
