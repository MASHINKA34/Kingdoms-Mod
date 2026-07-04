package com.geydev.kalfactions.market;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

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
        return template.save(new CompoundTag());
    }

    public static boolean restore(ServerLevel level, BoundingBox box, CompoundTag snapshot) {
        if (snapshot.isEmpty()) {
            return false;
        }
        StructureTemplate template = new StructureTemplate();
        template.load(level.holderLookup(Registries.BLOCK), snapshot);

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

    /**
     * Returns the owner's investment before the snapshot overwrites it: every block
     * that differs from the original build drops as loot, and the contents of
     * player-placed containers spill out. Containers that were part of the original
     * build keep snapshot semantics (silently reset) so buy-sell cycles cannot farm
     * the original stock.
     */
    private static void dropModifiedBlocks(ServerLevel level, BoundingBox box, CompoundTag snapshot) {
        BlockPos origin = new BlockPos(box.minX(), box.minY(), box.minZ());
        Map<BlockPos, BlockState> expected = new HashMap<>();
        Set<BlockPos> originalNbtPositions = new HashSet<>();
        readSnapshotStates(level, snapshot, origin, expected, originalNbtPositions);

        for (BlockPos pos : BlockPos.betweenClosed(
                box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (level.getBlockEntity(pos) instanceof Container container
                    && !originalNbtPositions.contains(pos)) {
                Containers.dropContents(level, pos, container);
                container.clearContent();
            }
            BlockState current = level.getBlockState(pos);
            if (current.isAir()) {
                continue;
            }
            BlockState want = expected.get(pos);
            if (want == null || current != want) {
                Block.dropResources(current, level, pos.immutable());
            }
        }
    }

    private static void readSnapshotStates(
            ServerLevel level,
            CompoundTag snapshot,
            BlockPos origin,
            Map<BlockPos, BlockState> expected,
            Set<BlockPos> withNbt
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
            if (entryTag.contains("nbt", Tag.TAG_COMPOUND)) {
                withNbt.add(pos);
            }
        }
    }

    private PlotSnapshots() {
    }
}
