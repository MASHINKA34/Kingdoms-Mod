package com.geydev.kalfactions.registry;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.block.DrillBlockEntity;
import com.geydev.kalfactions.block.FactionTableBlockEntity;
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
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FactionTableBlockEntity>> FACTION_TABLE =
            DeferredHolder.create(Registries.BLOCK_ENTITY_TYPE, FACTION_TABLE_ID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DrillBlockEntity>> DRILL =
            DeferredHolder.create(Registries.BLOCK_ENTITY_TYPE, DRILL_ID);

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
    }

    private ModBlockEntities() {
    }
}
