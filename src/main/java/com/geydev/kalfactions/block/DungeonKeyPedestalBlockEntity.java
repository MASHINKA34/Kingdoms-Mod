package com.geydev.kalfactions.block;

import com.geydev.kalfactions.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class DungeonKeyPedestalBlockEntity extends BlockEntity {
    public static final int DEFAULT_SIGNAL_TICKS = 400;
    public static final int MIN_SIGNAL_TICKS = 20;
    public static final int MAX_SIGNAL_TICKS = 20 * 60 * 60 * 24;

    private static final String TAG_REQUIRED_KEY = "RequiredKey";
    private static final String TAG_SIGNAL_TICKS = "SignalTicks";

    private DungeonKeyPedestalActivation requiredKey = DungeonKeyPedestalActivation.NONE;
    private int signalTicks = DEFAULT_SIGNAL_TICKS;

    public DungeonKeyPedestalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DUNGEON_KEY_PEDESTAL.get(), pos, state);
    }

    public DungeonKeyPedestalActivation requiredKey() {
        return requiredKey;
    }

    public int signalTicks() {
        return signalTicks;
    }

    public void configure(DungeonKeyPedestalActivation requiredKey, int signalTicks) {
        this.requiredKey = requiredKey == null ? DungeonKeyPedestalActivation.NONE : requiredKey;
        this.signalTicks = clampSignalTicks(signalTicks);
        setChanged();
    }

    public static int clampSignalTicks(int ticks) {
        return Math.clamp(ticks, MIN_SIGNAL_TICKS, MAX_SIGNAL_TICKS);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        requiredKey = DungeonKeyPedestalActivation.fromSerializedName(tag.getString(TAG_REQUIRED_KEY));
        signalTicks = tag.contains(TAG_SIGNAL_TICKS)
                ? clampSignalTicks(tag.getInt(TAG_SIGNAL_TICKS))
                : DEFAULT_SIGNAL_TICKS;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(TAG_REQUIRED_KEY, requiredKey.getSerializedName());
        tag.putInt(TAG_SIGNAL_TICKS, signalTicks);
    }
}
