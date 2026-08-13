package com.geydev.kalfactions.registry;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.block.DrillBlock;
import com.geydev.kalfactions.block.DungeonChestBlock;
import com.geydev.kalfactions.block.DungeonCoreBlock;
import com.geydev.kalfactions.block.EconomyGodStatueBlock;
import com.geydev.kalfactions.block.FactionTableBlock;
import com.geydev.kalfactions.block.GuideBoardBlock;
import com.geydev.kalfactions.block.GhostKeyForgeBlock;
import com.geydev.kalfactions.block.NewsBoardBlock;
import com.geydev.kalfactions.block.OutpostCoreBlock;
import com.geydev.kalfactions.block.QuarryCoreBlock;
import com.geydev.kalfactions.block.SanctuaryCoreBlock;
import com.geydev.kalfactions.block.StatueScienceBlock;
import com.geydev.kalfactions.block.StoneGodStatueBlock;
import com.geydev.kalfactions.block.StoneGodStatueCollisionBlock;
import com.geydev.kalfactions.block.WarArchiveBlock;
import com.geydev.kalfactions.block.WarGodStatueBlock;
import com.geydev.kalfactions.block.WorldMapBlock;
import com.geydev.kalfactions.block.XaeroMapArchiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
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

    public static final DeferredBlock<WarArchiveBlock> WAR_ARCHIVE = BLOCKS.register(
            "war_archive",
            () -> new WarArchiveBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(2.5F)
                    .sound(SoundType.STONE)
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
                    .sound(SoundType.METAL))
    );

    public static final DeferredBlock<GuideBoardBlock> GUIDE_BOARD = BLOCKS.register(
            "guide_board",
            () -> new GuideBoardBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .noOcclusion())
    );

    public static final DeferredBlock<NewsBoardBlock> NEWS_BOARD = BLOCKS.register(
            "news_board",
            () -> new NewsBoardBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .noOcclusion())
    );

    public static final DeferredBlock<SanctuaryCoreBlock> SANCTUARY_CORE = BLOCKS.register(
            "sanctuary_core",
            () -> new SanctuaryCoreBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .strength(4.0F, 1200.0F)
                    .sound(SoundType.STONE)
                    .noOcclusion())
    );

    public static final DeferredBlock<OutpostCoreBlock> OUTPOST_CORE = BLOCKS.register(
            "outpost_core",
            () -> new OutpostCoreBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(50.0F, 1200.0F)
                    .sound(SoundType.STONE))
    );

    public static final DeferredBlock<QuarryCoreBlock> QUARRY_CORE = BLOCKS.register(
            "quarry_core",
            () -> new QuarryCoreBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(-1.0F, 3_600_000.0F)
                    .sound(SoundType.METAL)
                    .pushReaction(PushReaction.BLOCK)
                    .noLootTable())
    );

    public static final DeferredBlock<WorldMapBlock> WORLD_MAP = BLOCKS.register(
            "world_map",
            () -> new WorldMapBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.STONE))
    );

    public static final DeferredBlock<XaeroMapArchiveBlock> XAERO_MAP_ARCHIVE = BLOCKS.register(
            "xaero_map_archive",
            () -> new XaeroMapArchiveBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion())
    );

    public static final DeferredBlock<StatueScienceBlock> STATUE_SCIENCE = BLOCKS.register(
            "statue_science",
            () -> new StatueScienceBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.STONE)
                    .noOcclusion()
                    .dynamicShape())
    );

    public static final DeferredBlock<WarGodStatueBlock> WAR_GOD_STATUE = BLOCKS.register(
            "war_god_statue",
            () -> new WarGodStatueBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(6.0F, 1200.0F)
                    .sound(SoundType.STONE)
                    .noOcclusion()
                    .dynamicShape())
    );

    public static final DeferredBlock<EconomyGodStatueBlock> ECONOMY_GOD_STATUE = BLOCKS.register(
            "economy_god_statue",
            () -> new EconomyGodStatueBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(6.0F, 1200.0F)
                    .sound(SoundType.STONE)
                    .noOcclusion()
                    .dynamicShape())
    );

    public static final DeferredBlock<StoneGodStatueBlock> RESEARCH_GOD_STONE_8BLOCKS = BLOCKS.register(
            "research_god_stone_8blocks",
            () -> new StoneGodStatueBlock(
                    stoneGodStatueProperties(),
                    Block.box(-24.0D, 0.0D, -24.0D, 40.0D, 16.0D, 40.0D),
                    Block.box(-24.0D, 0.0D, -24.0D, 40.0D, 16.0D, 40.0D)
            )
    );

    public static final DeferredBlock<StoneGodStatueBlock> WAR_GOD_STONE_8BLOCKS = BLOCKS.register(
            "war_god_stone_8blocks",
            () -> new StoneGodStatueBlock(
                    stoneGodStatueProperties(),
                    Block.box(-44.0D, 0.0D, -24.0D, 52.0D, 16.0D, 40.0D),
                    Block.box(-24.0D, 0.0D, -44.0D, 40.0D, 16.0D, 52.0D)
            )
    );

    public static final DeferredBlock<StoneGodStatueBlock> ECONOMY_GOD_STONE_8BLOCKS = BLOCKS.register(
            "economy_god_stone_8blocks",
            () -> new StoneGodStatueBlock(
                    stoneGodStatueProperties(),
                    Block.box(-24.0D, 0.0D, -24.0D, 40.0D, 16.0D, 40.0D),
                    Block.box(-24.0D, 0.0D, -24.0D, 40.0D, 16.0D, 40.0D)
            )
    );

    public static final DeferredBlock<StoneGodStatueCollisionBlock> STONE_GOD_STATUE_COLLISION = BLOCKS.register(
            "stone_god_statue_collision",
            () -> new StoneGodStatueCollisionBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(8.0F, 1200.0F)
                    .sound(SoundType.STONE)
                    .pushReaction(PushReaction.BLOCK)
                    .noOcclusion()
                    .dynamicShape()
                    .noLootTable())
    );

    public static final DeferredBlock<DungeonCoreBlock> DUNGEON_CORE = BLOCKS.register(
            "dungeon_core",
            () -> new DungeonCoreBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(4.0F, 1200.0F)
                    .sound(SoundType.DEEPSLATE_TILES)
                    .pushReaction(PushReaction.BLOCK))
    );

    public static final DeferredBlock<DungeonChestBlock> DUNGEON_CHEST = BLOCKS.register(
            "dungeon_chest",
            () -> new DungeonChestBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(4.0F, 1200.0F)
                    .sound(SoundType.WOOD)
                    .pushReaction(PushReaction.BLOCK))
    );

    public static final DeferredBlock<GhostKeyForgeBlock> GHOST_KEY_FORGE = BLOCKS.register(
            "ghost_key_forge",
            () -> new GhostKeyForgeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(4.0F, 12.0F)
                    .sound(SoundType.POLISHED_DEEPSLATE)
                    .lightLevel(state -> 12)
                    .noOcclusion()
                    .dynamicShape())
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

    private static BlockBehaviour.Properties stoneGodStatueProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .strength(8.0F, 1200.0F)
                .sound(SoundType.STONE)
                .pushReaction(PushReaction.BLOCK)
                .noOcclusion()
                .dynamicShape();
    }

    private ModBlocks() {
    }
}
