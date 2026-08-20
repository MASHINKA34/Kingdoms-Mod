package com.geydev.kalfactions.registry;

import com.geydev.kalfactions.KalFactions;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import java.util.List;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, KalFactions.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> KINGDOMS = TABS.register(
            "kingdoms",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.kingdoms"))
                    .withTabsBefore(CreativeModeTabs.FUNCTIONAL_BLOCKS)
                    .icon(() -> ModItems.ACCESS_TOOL.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        creativeItems().forEach(output::accept);
                        vanillaTools().forEach(output::accept);
                    })
                    .build()
    );

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }

    public static List<Item> creativeItems() {
        return List.of(
                ModItems.FACTION_TABLE.get(),
                ModItems.WAR_ARCHIVE.get(),
                ModItems.GUIDE_BOARD.get(),
                ModItems.NEWS_BOARD.get(),
                ModItems.SANCTUARY_CORE.get(),
                ModItems.STATUE_SCIENCE.get(),
                ModItems.WAR_GOD_STATUE.get(),
                ModItems.ECONOMY_GOD_STATUE.get(),
                ModItems.RESEARCH_GOD_STONE_8BLOCKS.get(),
                ModItems.WAR_GOD_STONE_8BLOCKS.get(),
                ModItems.ECONOMY_GOD_STONE_8BLOCKS.get(),
                ModItems.OUTPOST_CORE.get(),
                ModItems.RESOURCE_CLUSTER_SCIENCE.get(),
                ModItems.RESOURCE_CLUSTER_ECONOMIC.get(),
                ModItems.RESOURCE_CLUSTER_MILITARY.get(),
                ModItems.RESOURCE_CLUSTER_DIAMOND.get(),
                ModItems.QUARRY_CORE.get(),
                ModItems.DRILL.get(),
                ModItems.RESEARCH_BENCH.get(),
                ModItems.WORLD_MAP.get(),
                ModItems.XAERO_MAP_ARCHIVE.get(),
                ModItems.DUNGEON_CORE.get(),
                ModItems.DUNGEON_CHEST.get(),
                ModItems.GHOST_KEY_FORGE.get(),
                ModItems.INFERNAL_KEY_FORGE.get(),
                ModItems.ACCESS_TOOL.get(),
                ModItems.OUTPOST_CHARTER.get(),
                ModItems.QUARRY_ACTIVATOR.get(),
                ModItems.TRADER_SPAWN_EGG.get(),
                ModItems.SELLER_SPAWN_EGG.get(),
                ModItems.BANKER_SPAWN_EGG.get(),
                ModItems.MAP_SCOUT_SPAWN_EGG.get(),
                ModItems.TRADER_REMOVER.get(),
                ModItems.TRADER_POINT_TOOL.get(),
                ModItems.SELLER_CATALOG.get(),
                ModItems.DIMENSION_KEY.get(),
                ModItems.NETHER_RETURN.get(),
                ModItems.PLOT_WAND.get(),
                ModItems.ADMIN_ANALYZER.get(),
                ModItems.FACTION_METER.get(),
                ModItems.WAR_TROPHY.get(),
                ModItems.CRYSTAL_SCIENCE.get(),
                ModItems.CRYSTAL_ECONOMIC.get(),
                ModItems.CRYSTAL_MILITARY.get(),
                ModItems.MINIBOSS_TOKEN_GHOST.get(),
                ModItems.MINIBOSS_TOKEN_SCULK.get(),
                ModItems.MINIBOSS_TOKEN_NETHER.get(),
                ModItems.MINIBOSS_TOKEN_LUSH_CAVES.get(),
                ModItems.MINIBOSS_TOKEN_END.get(),
                ModItems.BOSS_TROPHY_LESSER.get(),
                ModItems.BOSS_TROPHY_GREATER.get(),
                ModItems.BOSS_TROPHY_LEGENDARY.get(),
                ModItems.BLACKZONE_ANTIDOTE.get(),
                ModItems.MUSIC_BLOCK.get(),
                ModItems.SCULK_KEY_FORGE.get(),
                ModItems.MOSSY_KEY_FORGE.get(),
                ModItems.GHOST_KEY_BOW_FRAGMENT.get(),
                ModItems.GHOST_KEY_SHAFT_FRAGMENT.get(),
                ModItems.GHOST_KEY_BIT_FRAGMENT.get(),
                ModItems.GHOST_KEY.get(),
                ModItems.INFERNAL_KEY_BOW_FRAGMENT.get(),
                ModItems.INFERNAL_KEY_SHAFT_FRAGMENT.get(),
                ModItems.INFERNAL_KEY_BIT_FRAGMENT.get(),
                ModItems.INFERNAL_KEY.get(),
                ModItems.SCULK_KEY_BOW_FRAGMENT.get(),
                ModItems.SCULK_KEY_SHAFT_FRAGMENT.get(),
                ModItems.SCULK_KEY_BIT_FRAGMENT.get(),
                ModItems.SCULK_KEY.get(),
                ModItems.MOSSY_KEY_BOW_FRAGMENT.get(),
                ModItems.MOSSY_KEY_SHAFT_FRAGMENT.get(),
                ModItems.MOSSY_KEY_BIT_FRAGMENT.get(),
                ModItems.MOSSY_KEY.get(),
                ModItems.DUNGEON_KEY_PEDESTAL.get()
        );
    }

    public static List<Item> vanillaTools() {
        return List.of(
                Items.COMMAND_BLOCK,
                Items.CHAIN_COMMAND_BLOCK,
                Items.REPEATING_COMMAND_BLOCK
        );
    }

    private ModCreativeTabs() {
    }
}
