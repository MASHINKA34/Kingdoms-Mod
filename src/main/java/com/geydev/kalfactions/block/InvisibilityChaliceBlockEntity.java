package com.geydev.kalfactions.block;

import com.geydev.kalfactions.invisibility.TrueInvisibility;
import com.geydev.kalfactions.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class InvisibilityChaliceBlockEntity extends BlockEntity {
    private static final String TAG_DURATION_SECONDS = "DurationSeconds";

    private int durationSeconds = TrueInvisibility.DEFAULT_SECONDS;

    public InvisibilityChaliceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INVISIBILITY_CHALICE.get(), pos, state);
    }

    public int durationSeconds() {
        return durationSeconds;
    }

    public void configure(int durationSeconds) {
        this.durationSeconds = TrueInvisibility.clampSeconds(durationSeconds);
        setChangedAndSync();
    }

    private void setChangedAndSync() {
        setChanged();
        Level level = getLevel();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        durationSeconds = tag.contains(TAG_DURATION_SECONDS)
                ? TrueInvisibility.clampSeconds(tag.getInt(TAG_DURATION_SECONDS))
                : TrueInvisibility.DEFAULT_SECONDS;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_DURATION_SECONDS, durationSeconds);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(
            Connection net,
            ClientboundBlockEntityDataPacket packet,
            HolderLookup.Provider registries
    ) {
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            loadAdditional(tag, registries);
        }
    }
}
