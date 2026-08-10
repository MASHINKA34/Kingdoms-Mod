package com.geydev.kalfactions.block;

import com.geydev.kalfactions.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class EconomyGodStatueBlockEntity extends BlockEntity {
    public EconomyGodStatueBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ECONOMY_GOD_STATUE.get(), pos, state);
    }
}
