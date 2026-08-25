package com.geydev.kalfactions.dungeon;

import com.geydev.kalfactions.KalFactions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class DungeonPlantGrowth {
    public static final TagKey<Block> RANDOM_TICKING_PLANTS = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "dungeon_random_ticking_plants")
    );

    public static boolean blocksRandomTick(ServerLevel level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        return (block instanceof BonemealableBlock || state.is(RANDOM_TICKING_PLANTS))
                && DungeonProtection.isDungeon(level, pos);
    }

    private DungeonPlantGrowth() {
    }
}
