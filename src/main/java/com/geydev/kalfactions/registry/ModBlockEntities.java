package com.geydev.kalfactions.registry;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.block.DrillBlockEntity;
import com.geydev.kalfactions.block.DungeonChestBlockEntity;
import com.geydev.kalfactions.block.DungeonKeyPedestalBlockEntity;
import com.geydev.kalfactions.block.EconomyGodStatueBlockEntity;
import com.geydev.kalfactions.block.FactionTableBlockEntity;
import com.geydev.kalfactions.block.GuideBoardBlockEntity;
import com.geydev.kalfactions.block.KeyForgeBlockEntity;
import com.geydev.kalfactions.block.KeyForgeType;
import com.geydev.kalfactions.block.KeyHolderBlockEntity;
import com.geydev.kalfactions.block.MusicBlockEntity;
import com.geydev.kalfactions.block.ResearchBenchBlockEntity;
import com.geydev.kalfactions.block.StatueScienceBlockEntity;
import com.geydev.kalfactions.block.StoneGodStatueBlockEntity;
import com.geydev.kalfactions.block.WarGodStatueBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class ModBlockEntities {
    public static final ResourceLocation FACTION_TABLE_ID =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "faction_table");
    public static final ResourceLocation DRILL_ID =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "drill");
    public static final ResourceLocation GUIDE_BOARD_ID =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "guide_board");
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FactionTableBlockEntity>> FACTION_TABLE =
            DeferredHolder.create(Registries.BLOCK_ENTITY_TYPE, FACTION_TABLE_ID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DrillBlockEntity>> DRILL =
            DeferredHolder.create(Registries.BLOCK_ENTITY_TYPE, DRILL_ID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GuideBoardBlockEntity>> GUIDE_BOARD =
            DeferredHolder.create(Registries.BLOCK_ENTITY_TYPE, GUIDE_BOARD_ID);
    public static final ResourceLocation STATUE_SCIENCE_ID =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "statue_science");
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StatueScienceBlockEntity>> STATUE_SCIENCE =
            DeferredHolder.create(Registries.BLOCK_ENTITY_TYPE, STATUE_SCIENCE_ID);
    public static final ResourceLocation WAR_GOD_STATUE_ID =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "war_god_statue");
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WarGodStatueBlockEntity>> WAR_GOD_STATUE =
            DeferredHolder.create(Registries.BLOCK_ENTITY_TYPE, WAR_GOD_STATUE_ID);
    public static final ResourceLocation ECONOMY_GOD_STATUE_ID =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "economy_god_statue");
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EconomyGodStatueBlockEntity>> ECONOMY_GOD_STATUE =
            DeferredHolder.create(Registries.BLOCK_ENTITY_TYPE, ECONOMY_GOD_STATUE_ID);
    public static final ResourceLocation STONE_GOD_STATUE_ID =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "stone_god_statue");
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StoneGodStatueBlockEntity>> STONE_GOD_STATUE =
            DeferredHolder.create(Registries.BLOCK_ENTITY_TYPE, STONE_GOD_STATUE_ID);
    public static final ResourceLocation DUNGEON_CHEST_ID =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "dungeon_chest");
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DungeonChestBlockEntity>> DUNGEON_CHEST =
            DeferredHolder.create(Registries.BLOCK_ENTITY_TYPE, DUNGEON_CHEST_ID);
    public static final ResourceLocation MUSIC_BLOCK_ID =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "music_block");
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MusicBlockEntity>> MUSIC_BLOCK =
            DeferredHolder.create(Registries.BLOCK_ENTITY_TYPE, MUSIC_BLOCK_ID);
    public static final ResourceLocation RESEARCH_BENCH_ID =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "research_bench");
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ResearchBenchBlockEntity>> RESEARCH_BENCH =
            DeferredHolder.create(Registries.BLOCK_ENTITY_TYPE, RESEARCH_BENCH_ID);
    public static final ResourceLocation GHOST_KEY_FORGE_ID =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "ghost_key_forge");
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<KeyForgeBlockEntity>> GHOST_KEY_FORGE =
            DeferredHolder.create(Registries.BLOCK_ENTITY_TYPE, GHOST_KEY_FORGE_ID);
    public static final ResourceLocation SCULK_KEY_FORGE_ID =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "sculk_key_forge");
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<KeyForgeBlockEntity>> SCULK_KEY_FORGE =
            DeferredHolder.create(Registries.BLOCK_ENTITY_TYPE, SCULK_KEY_FORGE_ID);
    public static final ResourceLocation INFERNAL_KEY_FORGE_ID =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "infernal_key_forge");
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<KeyForgeBlockEntity>> INFERNAL_KEY_FORGE =
            DeferredHolder.create(Registries.BLOCK_ENTITY_TYPE, INFERNAL_KEY_FORGE_ID);
    public static final ResourceLocation MOSSY_KEY_FORGE_ID =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "mossy_key_forge");
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<KeyForgeBlockEntity>> MOSSY_KEY_FORGE =
            DeferredHolder.create(Registries.BLOCK_ENTITY_TYPE, MOSSY_KEY_FORGE_ID);
    public static final ResourceLocation DUNGEON_KEY_PEDESTAL_ID =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "dungeon_key_pedestal");
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DungeonKeyPedestalBlockEntity>>
            DUNGEON_KEY_PEDESTAL = DeferredHolder.create(
                    Registries.BLOCK_ENTITY_TYPE,
                    DUNGEON_KEY_PEDESTAL_ID
            );

    public static final ResourceLocation KEY_HOLDER_ID =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "key_holder");
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<KeyHolderBlockEntity>> KEY_HOLDER =
            DeferredHolder.create(Registries.BLOCK_ENTITY_TYPE, KEY_HOLDER_ID);

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK_ENTITY_TYPE, FACTION_TABLE_ID, () -> {
            Block block = BuiltInRegistries.BLOCK.get(FACTION_TABLE_ID);
            if (block == Blocks.AIR) {
                throw new IllegalStateException(
                        "kingdoms:faction_table must be registered before its block entity type"
                );
            }
            return BlockEntityType.Builder.of(FactionTableBlockEntity::new, block).build(null);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, DRILL_ID, () -> {
            Block block = BuiltInRegistries.BLOCK.get(DRILL_ID);
            if (block == Blocks.AIR) {
                throw new IllegalStateException(
                        "kingdoms:drill must be registered before its block entity type"
                );
            }
            return BlockEntityType.Builder.of(DrillBlockEntity::new, block).build(null);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, GUIDE_BOARD_ID, () -> {
            Block block = BuiltInRegistries.BLOCK.get(GUIDE_BOARD_ID);
            if (block == Blocks.AIR) {
                throw new IllegalStateException(
                        "kingdoms:guide_board must be registered before its block entity type"
                );
            }
            return BlockEntityType.Builder.of(GuideBoardBlockEntity::new, block).build(null);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, STATUE_SCIENCE_ID, () -> {
            Block block = BuiltInRegistries.BLOCK.get(STATUE_SCIENCE_ID);
            if (block == Blocks.AIR) {
                throw new IllegalStateException(
                        "kingdoms:statue_science must be registered before its block entity type"
                );
            }
            return BlockEntityType.Builder.of(StatueScienceBlockEntity::new, block).build(null);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, WAR_GOD_STATUE_ID, () -> {
            Block block = BuiltInRegistries.BLOCK.get(WAR_GOD_STATUE_ID);
            if (block == Blocks.AIR) {
                throw new IllegalStateException(
                        "kingdoms:war_god_statue must be registered before its block entity type"
                );
            }
            return BlockEntityType.Builder.of(WarGodStatueBlockEntity::new, block).build(null);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, ECONOMY_GOD_STATUE_ID, () -> {
            Block block = BuiltInRegistries.BLOCK.get(ECONOMY_GOD_STATUE_ID);
            if (block == Blocks.AIR) {
                throw new IllegalStateException(
                        "kingdoms:economy_god_statue must be registered before its block entity type"
                );
            }
            return BlockEntityType.Builder.of(EconomyGodStatueBlockEntity::new, block).build(null);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, STONE_GOD_STATUE_ID, () -> {
            Block research = BuiltInRegistries.BLOCK.get(
                    ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "research_god_stone_8blocks")
            );
            Block war = BuiltInRegistries.BLOCK.get(
                    ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "war_god_stone_8blocks")
            );
            Block economy = BuiltInRegistries.BLOCK.get(
                    ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "economy_god_stone_8blocks")
            );
            if (research == Blocks.AIR || war == Blocks.AIR || economy == Blocks.AIR) {
                throw new IllegalStateException("Stone god statues must be registered before their block entity type");
            }
            return BlockEntityType.Builder.of(StoneGodStatueBlockEntity::new, research, war, economy).build(null);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, DUNGEON_CHEST_ID, () -> {
            Block block = BuiltInRegistries.BLOCK.get(DUNGEON_CHEST_ID);
            if (block == Blocks.AIR) {
                throw new IllegalStateException(
                        "kingdoms:dungeon_chest must be registered before its block entity type"
                );
            }
            return BlockEntityType.Builder.of(DungeonChestBlockEntity::new, block).build(null);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, RESEARCH_BENCH_ID, () -> {
            Block block = BuiltInRegistries.BLOCK.get(RESEARCH_BENCH_ID);
            if (block == Blocks.AIR) {
                throw new IllegalStateException(
                        "kingdoms:research_bench must be registered before its block entity type"
                );
            }
            return BlockEntityType.Builder.of(ResearchBenchBlockEntity::new, block).build(null);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, MUSIC_BLOCK_ID, () -> {
            Block block = BuiltInRegistries.BLOCK.get(MUSIC_BLOCK_ID);
            if (block == Blocks.AIR) {
                throw new IllegalStateException(
                        "kingdoms:music_block must be registered before its block entity type"
                );
            }
            return BlockEntityType.Builder.of(MusicBlockEntity::new, block).build(null);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, GHOST_KEY_FORGE_ID, () -> {
            Block block = BuiltInRegistries.BLOCK.get(GHOST_KEY_FORGE_ID);
            if (block == Blocks.AIR) {
                throw new IllegalStateException(
                        "kingdoms:ghost_key_forge must be registered before its block entity type"
                );
            }
            return BlockEntityType.Builder.of(
                    (pos, state) -> new KeyForgeBlockEntity(KeyForgeType.GHOST, pos, state),
                    block
            ).build(null);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, SCULK_KEY_FORGE_ID, () -> {
            Block block = BuiltInRegistries.BLOCK.get(SCULK_KEY_FORGE_ID);
            if (block == Blocks.AIR) {
                throw new IllegalStateException(
                        "kingdoms:sculk_key_forge must be registered before its block entity type"
                );
            }
            return BlockEntityType.Builder.of(
                    (pos, state) -> new KeyForgeBlockEntity(KeyForgeType.SCULK, pos, state),
                    block
            ).build(null);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, INFERNAL_KEY_FORGE_ID, () -> {
            Block block = BuiltInRegistries.BLOCK.get(INFERNAL_KEY_FORGE_ID);
            if (block == Blocks.AIR) {
                throw new IllegalStateException(
                        "kingdoms:infernal_key_forge must be registered before its block entity type"
                );
            }
            return BlockEntityType.Builder.of(
                    (pos, state) -> new KeyForgeBlockEntity(KeyForgeType.INFERNAL, pos, state),
                    block
            ).build(null);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, MOSSY_KEY_FORGE_ID, () -> {
            Block block = BuiltInRegistries.BLOCK.get(MOSSY_KEY_FORGE_ID);
            if (block == Blocks.AIR) {
                throw new IllegalStateException(
                        "kingdoms:mossy_key_forge must be registered before its block entity type"
                );
            }
            return BlockEntityType.Builder.of(
                    (pos, state) -> new KeyForgeBlockEntity(KeyForgeType.MOSSY, pos, state),
                    block
            ).build(null);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, DUNGEON_KEY_PEDESTAL_ID, () -> {
            Block block = BuiltInRegistries.BLOCK.get(DUNGEON_KEY_PEDESTAL_ID);
            if (block == Blocks.AIR) {
                throw new IllegalStateException(
                        "kingdoms:dungeon_key_pedestal must be registered before its block entity type"
                );
            }
            return BlockEntityType.Builder.of(DungeonKeyPedestalBlockEntity::new, block).build(null);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, KEY_HOLDER_ID, () -> {
            Block block = BuiltInRegistries.BLOCK.get(KEY_HOLDER_ID);
            if (block == Blocks.AIR) {
                throw new IllegalStateException(
                        "kingdoms:key_holder must be registered before its block entity type"
                );
            }
            return BlockEntityType.Builder.of(KeyHolderBlockEntity::new, block).build(null);
        });
    }

    private ModBlockEntities() {
    }
}
