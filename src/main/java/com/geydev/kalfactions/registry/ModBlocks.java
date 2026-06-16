package com.geydev.kalfactions.registry;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.block.DrillBlock;
import com.geydev.kalfactions.block.FactionTableBlock;
import com.geydev.kalfactions.block.OutpostCoreBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(KalFactions.MOD_ID);

    public static final DeferredBlock<FactionTableBlock> FACTION_TABLE = BLOCKS.register(
            "faction_table",
            () -> new FactionTableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .noOcclusion())
    );

    public static final DeferredBlock<Block> RESOURCE_CLUSTER_SCIENCE = BLOCKS.register(
            "resource_cluster_science",
            () -> new Block(resourceClusterProperties())
    );

    public static final DeferredBlock<Block> RESOURCE_CLUSTER_ECONOMIC = BLOCKS.register(
            "resource_cluster_economic",
            () -> new Block(resourceClusterProperties())
    );

    public static final DeferredBlock<Block> RESOURCE_CLUSTER_MILITARY = BLOCKS.register(
            "resource_cluster_military",
            () -> new Block(resourceClusterProperties())
    );

    public static final DeferredBlock<Block> RESOURCE_CLUSTER_DIAMOND = BLOCKS.register(
            "resource_cluster_diamond",
            () -> new Block(resourceClusterProperties())
    );

    public static final DeferredBlock<DrillBlock> DRILL = BLOCKS.register(
            "drill",
            () -> new DrillBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops())
    );

    public static final DeferredBlock<OutpostCoreBlock> OUTPOST_CORE = BLOCKS.register(
            "outpost_core",
            () -> new OutpostCoreBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(50.0F, 1200.0F)
                    .sound(SoundType.STONE))
    );

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }

    private static BlockBehaviour.Properties resourceClusterProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(-1.0F, 3_600_000.0F)
                .sound(SoundType.STONE)
                .noLootTable();
    }

    private ModBlocks() {
    }
}
