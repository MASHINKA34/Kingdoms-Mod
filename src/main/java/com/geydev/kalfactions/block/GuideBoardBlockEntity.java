package com.geydev.kalfactions.block;

import com.geydev.kalfactions.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class GuideBoardBlockEntity extends BlockEntity {
    public GuideBoardBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GUIDE_BOARD.get(), pos, state);
    }
}
