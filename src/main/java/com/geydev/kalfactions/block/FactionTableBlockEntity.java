package com.geydev.kalfactions.block;

import com.geydev.kalfactions.registry.ModBlockEntities;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class FactionTableBlockEntity extends BlockEntity {
    private static final String FACTION_ID_TAG = "FactionId";
    private static final String FACTION_COLOR_TAG = "FactionColor";
    private static final String PLACED_BY_TAG = "PlacedBy";
    @Nullable
    private UUID factionId;
    @Nullable
    private UUID placedBy;
    private int factionColor = 0x4E7A42;

    public FactionTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FACTION_TABLE.get(), pos, state);
    }

    @Nullable
    public UUID getFactionId() {
        return factionId;
    }

    public void setFactionId(@Nullable UUID factionId) {
        if (java.util.Objects.equals(this.factionId, factionId)) {
            return;
        }
        this.factionId = factionId;
        setChangedAndSync();
    }

    public int getFactionColor() {
        return factionColor;
    }

    public void setFactionColor(int factionColor) {
        factionColor &= 0xFFFFFF;
        if (this.factionColor == factionColor) {
            return;
        }
        this.factionColor = factionColor;
        setChangedAndSync();
    }

    @Nullable
    public UUID getPlacedBy() {
        return placedBy;
    }

    public void setPlacedBy(@Nullable UUID placedBy) {
        if (java.util.Objects.equals(this.placedBy, placedBy)) {
            return;
        }
        this.placedBy = placedBy;
        setChangedAndSync();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        factionId = tag.hasUUID(FACTION_ID_TAG) ? tag.getUUID(FACTION_ID_TAG) : null;
        factionColor = tag.contains(FACTION_COLOR_TAG) ? tag.getInt(FACTION_COLOR_TAG) & 0xFFFFFF : 0x4E7A42;
        placedBy = tag.hasUUID(PLACED_BY_TAG) ? tag.getUUID(PLACED_BY_TAG) : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (factionId != null) {
            tag.putUUID(FACTION_ID_TAG, factionId);
        }
        tag.putInt(FACTION_COLOR_TAG, factionColor);
        if (placedBy != null) {
            tag.putUUID(PLACED_BY_TAG, placedBy);
        }
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
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            loadAdditional(tag, registries);
        }
    }

    private void setChangedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }
}
