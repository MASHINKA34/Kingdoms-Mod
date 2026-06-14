package com.geydev.kalfactions.registry;

import com.geydev.kalfactions.KalFactions;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
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
                        output.accept(ModItems.FACTION_TABLE.get());
                        output.accept(ModItems.ACCESS_TOOL.get());
                        output.accept(ModItems.OUTPOST_CHARTER.get());
                        output.accept(ModItems.DRILL.get());
                        output.accept(ModItems.TRADER_SPAWN_EGG.get());
                        output.accept(ModItems.TRADER_REMOVER.get());
                    })
                    .build()
    );

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }

    private ModCreativeTabs() {
    }
}
