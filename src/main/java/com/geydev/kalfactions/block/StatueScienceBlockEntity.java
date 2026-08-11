package com.geydev.kalfactions.block;

import com.geydev.kalfactions.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class StatueScienceBlockEntity extends SmallStatueBlockEntity {
    public StatueScienceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STATUE_SCIENCE.get(), pos, state);
    }
}
