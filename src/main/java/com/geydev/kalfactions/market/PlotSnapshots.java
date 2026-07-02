package com.geydev.kalfactions.market;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
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

    private PlotSnapshots() {
    }
}
