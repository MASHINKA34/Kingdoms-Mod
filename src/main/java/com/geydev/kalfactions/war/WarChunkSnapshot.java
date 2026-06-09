package com.geydev.kalfactions.war;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;

/**
 * Copy-on-write capture of a single chunk's block states and block-entity NBT, taken before the
 * first war-time modification to that chunk. The snapshot is restored verbatim when the war ends,
 * with one deliberate exception: container inventories are never written back from the snapshot
 * (a destroyed container is restored empty, a surviving one keeps whatever it currently holds).
 * This closes the "empty the chest before the war, get it back full" dupe.
 */
public final class WarChunkSnapshot {
    /** Same codec the vanilla chunk serializer uses for a section's block states. */
    private static final Codec<PalettedContainer<BlockState>> BLOCK_STATE_CODEC = PalettedContainer.codecRW(
        Block.BLOCK_STATE_REGISTRY,
        BlockState.CODEC,
        PalettedContainer.Strategy.SECTION_STATES,
        Blocks.AIR.defaultBlockState()
    );

    /**
     * Client update + skip neighbour-shape updates + suppress drops. We restore exact states, so we
     * do not want shape recalculation, physics, or item drops while mass-reverting a chunk.
     */
    private static final int RESTORE_FLAGS =
        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private static final String TAG_MIN_SECTION = "minSection";
    private static final String TAG_SECTIONS = "sections";
    private static final String TAG_BLOCK_ENTITIES = "blockEntities";
    private static final String TAG_BE_POS = "pos";
    private static final String TAG_BE_NBT = "nbt";

    private final int minSection;
    private final List<PalettedContainer<BlockState>> sections;
    private final Map<BlockPos, CompoundTag> blockEntities;

    private WarChunkSnapshot(
        int minSection,
        List<PalettedContainer<BlockState>> sections,
        Map<BlockPos, CompoundTag> blockEntities
    ) {
        this.minSection = minSection;
        this.sections = sections;
        this.blockEntities = blockEntities;
    }

    /** Captures the current state of the loaded chunk. Runs on the server thread, before any change. */
    public static WarChunkSnapshot capture(ServerLevel level, ChunkPos chunkPos, HolderLookup.Provider registries) {
        LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
        LevelChunkSection[] source = chunk.getSections();
        List<PalettedContainer<BlockState>> copies = new ArrayList<>(source.length);
        for (LevelChunkSection section : source) {
            copies.add(section.getStates().copy());
        }
        Map<BlockPos, CompoundTag> capturedBlockEntities = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
            capturedBlockEntities.put(entry.getKey().immutable(), entry.getValue().saveWithFullMetadata(registries));
        }
        return new WarChunkSnapshot(chunk.getMinSection(), copies, capturedBlockEntities);
    }

    /**
     * Patches a single position in the snapshot to {@code state}. Used to record the pre-placement
     * block when a placement is the first modification of an otherwise-untouched chunk (the place
     * event fires after the new block is already in the world).
     */
    public void setBlockState(BlockPos pos, BlockState state) {
        int index = SectionPos.blockToSectionCoord(pos.getY()) - minSection;
        if (index < 0 || index >= sections.size()) {
            return;
        }
        sections.get(index).set(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, state);
    }

    /** Drops a captured block-entity tag (paired with {@link #setBlockState} when reverting a placement). */
    public void removeBlockEntity(BlockPos pos) {
        blockEntities.remove(pos.immutable());
    }

    /**
     * Reverts the chunk to the captured state. Only positions whose current state differs are touched,
     * keeping {@code setBlock} calls to a minimum. Block-entity NBT is reapplied only where the block
     * actually changed (a freshly created, empty block entity); container inventories are then wiped so
     * the snapshot never restores items.
     */
    public void restore(ServerLevel level, ChunkPos chunkPos, HolderLookup.Provider registries) {
        LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
        int baseX = chunkPos.getMinBlockX();
        int baseZ = chunkPos.getMinBlockZ();
        Set<BlockPos> changed = new HashSet<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int index = 0; index < sections.size(); index++) {
            PalettedContainer<BlockState> container = sections.get(index);
            int baseY = (minSection + index) << 4;
            for (int localY = 0; localY < 16; localY++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localX = 0; localX < 16; localX++) {
                        BlockState snapshotState = container.get(localX, localY, localZ);
                        cursor.set(baseX + localX, baseY + localY, baseZ + localZ);
                        if (chunk.getBlockState(cursor) == snapshotState) {
                            continue;
                        }
                        BlockPos pos = cursor.immutable();
                        level.setBlock(pos, snapshotState, RESTORE_FLAGS);
                        changed.add(pos);
                    }
                }
            }
        }

        for (Map.Entry<BlockPos, CompoundTag> entry : blockEntities.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!changed.contains(pos)) {
                continue; // surviving block entity: keep its current contents untouched
            }
            BlockEntity blockEntity = BlockEntity.loadStatic(pos, level.getBlockState(pos), entry.getValue(), registries);
            if (blockEntity == null) {
                continue;
            }
            if (blockEntity instanceof Container container) {
                container.clearContent(); // destroyed container is restored empty, never refilled from the snapshot
            }
            level.setBlockEntity(blockEntity);
            blockEntity.setChanged();
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_MIN_SECTION, minSection);

        ListTag sectionsTag = new ListTag();
        for (PalettedContainer<BlockState> container : sections) {
            sectionsTag.add(BLOCK_STATE_CODEC.encodeStart(NbtOps.INSTANCE, container).getOrThrow());
        }
        tag.put(TAG_SECTIONS, sectionsTag);

        ListTag blockEntitiesTag = new ListTag();
        for (Map.Entry<BlockPos, CompoundTag> entry : blockEntities.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putLong(TAG_BE_POS, entry.getKey().asLong());
            entryTag.put(TAG_BE_NBT, entry.getValue());
            blockEntitiesTag.add(entryTag);
        }
        tag.put(TAG_BLOCK_ENTITIES, blockEntitiesTag);
        return tag;
    }

    public static WarChunkSnapshot load(CompoundTag tag) {
        int minSection = tag.getInt(TAG_MIN_SECTION);

        ListTag sectionsTag = tag.getList(TAG_SECTIONS, Tag.TAG_COMPOUND);
        List<PalettedContainer<BlockState>> sections = new ArrayList<>(sectionsTag.size());
        for (int index = 0; index < sectionsTag.size(); index++) {
            sections.add(BLOCK_STATE_CODEC
                .parse(NbtOps.INSTANCE, sectionsTag.getCompound(index))
                .getOrThrow(message -> new IllegalStateException("Invalid war chunk section: " + message)));
        }

        Map<BlockPos, CompoundTag> blockEntities = new LinkedHashMap<>();
        ListTag blockEntitiesTag = tag.getList(TAG_BLOCK_ENTITIES, Tag.TAG_COMPOUND);
        for (int index = 0; index < blockEntitiesTag.size(); index++) {
            CompoundTag entryTag = blockEntitiesTag.getCompound(index);
            blockEntities.put(BlockPos.of(entryTag.getLong(TAG_BE_POS)), entryTag.getCompound(TAG_BE_NBT));
        }
        return new WarChunkSnapshot(minSection, sections, blockEntities);
    }
}
