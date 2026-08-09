package com.geydev.kalfactions.registry;

import com.geydev.kalfactions.KalFactions;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
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
                ModItems.OUTPOST_CORE.get(),
                ModItems.RESOURCE_CLUSTER_SCIENCE.get(),
                ModItems.RESOURCE_CLUSTER_ECONOMIC.get(),
                ModItems.RESOURCE_CLUSTER_MILITARY.get(),
                ModItems.RESOURCE_CLUSTER_DIAMOND.get(),
                ModItems.QUARRY_CORE.get(),
                ModItems.DRILL.get(),
                ModItems.WORLD_MAP.get(),
                ModItems.XAERO_MAP_ARCHIVE.get(),
                ModItems.ACCESS_TOOL.get(),
                ModItems.OUTPOST_CHARTER.get(),
                ModItems.QUARRY_ACTIVATOR.get(),
                ModItems.TRADER_SPAWN_EGG.get(),
                ModItems.SELLER_SPAWN_EGG.get(),
                ModItems.BANKER_SPAWN_EGG.get(),
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
                ModItems.CRYSTAL_MILITARY.get()
        );
    }

    private ModCreativeTabs() {
    }
}
