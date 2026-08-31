package com.geydev.kalfactions.block;

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

public final class KeyHolderBlockEntity extends BlockEntity {
    public static final int DEFAULT_PULSE_TICKS = 40;
    public static final int MIN_PULSE_TICKS = 2;
    public static final int MAX_PULSE_TICKS = 20 * 60 * 60 * 24;

    private static final String TAG_MODE = "Mode";
    private static final String TAG_PULSE_TICKS = "PulseTicks";
    private static final String TAG_CONSUME_KEY = "ConsumeKey";

    private KeyHolderMode mode = KeyHolderMode.PULSE;
    private int pulseTicks = DEFAULT_PULSE_TICKS;
    private boolean consumeKey = true;

    public KeyHolderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.KEY_HOLDER.get(), pos, state);
    }

    public KeyHolderMode mode() {
        return mode;
    }

    public int pulseTicks() {
        return pulseTicks;
    }

    public boolean consumeKey() {
        return consumeKey;
    }

    public void configure(KeyHolderMode mode, int pulseTicks, boolean consumeKey) {
        this.mode = mode == null ? KeyHolderMode.PULSE : mode;
        this.pulseTicks = clampPulseTicks(pulseTicks);
        this.consumeKey = consumeKey;
        setChangedAndSync();
    }

    public static int clampPulseTicks(int ticks) {
        return Math.clamp(ticks, MIN_PULSE_TICKS, MAX_PULSE_TICKS);
    }

    public static boolean isValidPulseTicks(int ticks) {
        return ticks >= MIN_PULSE_TICKS && ticks <= MAX_PULSE_TICKS;
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
        mode = KeyHolderMode.fromSerializedName(tag.getString(TAG_MODE));
        pulseTicks = tag.contains(TAG_PULSE_TICKS)
                ? clampPulseTicks(tag.getInt(TAG_PULSE_TICKS))
                : DEFAULT_PULSE_TICKS;
        consumeKey = !tag.contains(TAG_CONSUME_KEY) || tag.getBoolean(TAG_CONSUME_KEY);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(TAG_MODE, mode.getSerializedName());
        tag.putInt(TAG_PULSE_TICKS, pulseTicks);
        tag.putBoolean(TAG_CONSUME_KEY, consumeKey);
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
