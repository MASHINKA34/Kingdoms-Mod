package com.geydev.kalfactions.registry;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.market.PlotSelection;
import com.geydev.kalfactions.outpost.trader.TraderPointToolMode;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class ModDataComponents {
    public static final ResourceLocation FACTION_ID_KEY =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "faction_id");
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> FACTION_ID =
            DeferredHolder.create(Registries.DATA_COMPONENT_TYPE, FACTION_ID_KEY);

    public static final ResourceLocation PLOT_SELECTION_KEY =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "plot_selection");
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PlotSelection>> PLOT_SELECTION =
            DeferredHolder.create(Registries.DATA_COMPONENT_TYPE, PLOT_SELECTION_KEY);
    public static final ResourceLocation TRADER_POINT_TOOL_MODE_KEY =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "trader_point_tool_mode");
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TraderPointToolMode>> TRADER_POINT_TOOL_MODE =
            DeferredHolder.create(Registries.DATA_COMPONENT_TYPE, TRADER_POINT_TOOL_MODE_KEY);

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(
                Registries.DATA_COMPONENT_TYPE,
                FACTION_ID_KEY,
                () -> DataComponentType.<UUID>builder()
                        .persistent(UUIDUtil.CODEC)
                        .networkSynchronized(UUIDUtil.STREAM_CODEC)
                        .cacheEncoding()
                        .build()
        );
        event.register(
                Registries.DATA_COMPONENT_TYPE,
                PLOT_SELECTION_KEY,
                () -> DataComponentType.<PlotSelection>builder()
                        .persistent(PlotSelection.CODEC)
                        .networkSynchronized(PlotSelection.STREAM_CODEC)
                        .cacheEncoding()
                        .build()
        );
        event.register(
                Registries.DATA_COMPONENT_TYPE,
                TRADER_POINT_TOOL_MODE_KEY,
                () -> DataComponentType.<TraderPointToolMode>builder()
                        .persistent(TraderPointToolMode.CODEC)
                        .networkSynchronized(TraderPointToolMode.STREAM_CODEC)
                        .cacheEncoding()
                        .build()
        );
    }

    private ModDataComponents() {
    }
}
