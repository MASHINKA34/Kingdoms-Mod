package com.geydev.kalfactions.market;

import com.geydev.kalfactions.data.SnapshotBlockEntities;
import com.geydev.kalfactions.integration.BlockInventories;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;

public final class PlotSnapshots {
    public static CompoundTag capture(ServerLevel level, BoundingBox box) {
        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(
                level,
                new BlockPos(box.minX(), box.minY(), box.minZ()),
                new Vec3i(box.getXSpan(), box.getYSpan(), box.getZSpan()),
                false,
                null
        );
        return withoutContainerContents(level, box, template.save(new CompoundTag()));
    }

    public static boolean restore(ServerLevel level, BoundingBox box, CompoundTag snapshot) {
        if (snapshot.isEmpty()) {
            return false;
        }
        StructureTemplate template = new StructureTemplate();
        template.load(level.holderLookup(Registries.BLOCK), withoutContainerContents(level, box, snapshot));

        AABB bounds = AABB.of(box);
        for (Entity entity : level.getEntities((Entity) null, bounds, entity -> !(entity instanceof Player))) {
            entity.discard();
        }

        dropModifiedBlocks(level, box, snapshot);

        BlockPos origin = new BlockPos(box.minX(), box.minY(), box.minZ());
        return template.placeInWorld(
                level,
                origin,
                origin,
                new StructurePlaceSettings().setIgnoreEntities(true),
                level.getRandom(),
                Block.UPDATE_ALL
        );
    }

    private static CompoundTag withoutContainerContents(ServerLevel level, BoundingBox box, CompoundTag snapshot) {
        CompoundTag sanitized = snapshot.copy();
        BlockPos origin = new BlockPos(box.minX(), box.minY(), box.minZ());
        Map<BlockPos, BlockState> states = new HashMap<>();
        readSnapshotStates(level, sanitized, origin, states);
        ListTag blocks = sanitized.getList("blocks", Tag.TAG_COMPOUND);
        for (int index = 0; index < blocks.size(); index++) {
            CompoundTag entry = blocks.getCompound(index);
            ListTag coordinates = entry.getList("pos", Tag.TAG_INT);
            if (!entry.contains("nbt", Tag.TAG_COMPOUND) || coordinates.size() != 3) {
                continue;
            }
            BlockPos pos = origin.offset(coordinates.getInt(0), coordinates.getInt(1), coordinates.getInt(2));
            BlockState state = states.get(pos);
            if (state == null) {
                continue;
            }
            BlockEntity blockEntity = SnapshotBlockEntities.loadEmpty(
                    pos, state, entry.getCompound("nbt"), level.registryAccess());
            if (blockEntity != null) {
                entry.put("nbt", blockEntity.saveWithFullMetadata(level.registryAccess()));
            }
        }
        return sanitized;
    }

    private static void dropModifiedBlocks(ServerLevel level, BoundingBox box, CompoundTag snapshot) {
        BlockPos origin = new BlockPos(box.minX(), box.minY(), box.minZ());
        Map<BlockPos, BlockState> expected = new HashMap<>();
        readSnapshotStates(level, snapshot, origin, expected);

        for (BlockPos pos : BlockPos.betweenClosed(
                box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity instanceof Container container) {
                Containers.dropContents(level, pos, container);
                container.clearContent();
            } else if (entity != null) {
                IItemHandler handler = BlockInventories.localHandler(level, entity);
                if (handler != null) {
                    for (int slot = 0; slot < handler.getSlots(); slot++) {
                        ItemStack stack = handler.extractItem(slot, Integer.MAX_VALUE, false);
                        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                    }
                }
            }
            BlockState current = level.getBlockState(pos);
            if (current.isAir()) {
                continue;
            }
            BlockState want = expected.get(pos);
            if (want == null || !current.is(want.getBlock())) {
                Block.dropResources(current, level, pos.immutable());
            }
        }
    }

    private static void readSnapshotStates(
            ServerLevel level,
            CompoundTag snapshot,
            BlockPos origin,
            Map<BlockPos, BlockState> expected
    ) {
        HolderGetter<Block> blocks = level.holderLookup(Registries.BLOCK);
        ListTag paletteTag = snapshot.getList("palette", Tag.TAG_COMPOUND);
        List<BlockState> palette = new ArrayList<>(paletteTag.size());
        for (int index = 0; index < paletteTag.size(); index++) {
            palette.add(NbtUtils.readBlockState(blocks, paletteTag.getCompound(index)));
        }
        ListTag blocksTag = snapshot.getList("blocks", Tag.TAG_COMPOUND);
        for (int index = 0; index < blocksTag.size(); index++) {
            CompoundTag entryTag = blocksTag.getCompound(index);
            ListTag posTag = entryTag.getList("pos", Tag.TAG_INT);
            int stateIndex = entryTag.getInt("state");
            if (posTag.size() != 3 || stateIndex < 0 || stateIndex >= palette.size()) {
                continue;
            }
            BlockPos pos = origin.offset(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2));
            expected.put(pos, palette.get(stateIndex));
        }
    }

    private PlotSnapshots() {
    }
}
