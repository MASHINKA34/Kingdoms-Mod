package com.geydev.kalfactions.block;

import com.geydev.kalfactions.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class WarGodStatueBlockEntity extends SmallStatueBlockEntity {
    public WarGodStatueBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WAR_GOD_STATUE.get(), pos, state);
    }
}
