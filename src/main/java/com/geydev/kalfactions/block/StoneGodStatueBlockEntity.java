package com.geydev.kalfactions.block;

import com.geydev.kalfactions.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class StoneGodStatueBlockEntity extends BlockEntity {
    public StoneGodStatueBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STONE_GOD_STATUE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null || level.isClientSide() || level.getServer() == null) {
            return;
        }
        Level loadedLevel = level;
        loadedLevel.getServer().execute(() -> {
            if (isRemoved() || this.level != loadedLevel) {
                return;
            }
            BlockState state = loadedLevel.getBlockState(worldPosition);
            if (state.getBlock() instanceof StoneGodStatueBlock statue
                    && state.getValue(StoneGodStatueBlock.SEGMENT) == 0) {
                statue.ensureCollisionField(loadedLevel, worldPosition, state);
            }
        });
    }
}
