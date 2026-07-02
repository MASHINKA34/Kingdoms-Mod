package com.geydev.kalfactions.client;

import com.geydev.kalfactions.market.MarketPayloads;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

public final class ClientPlotStore {
    private static volatile ResourceLocation dimension;
    private static volatile List<MarketPayloads.PlotEntry> plots = List.of();

    public static void handle(MarketPayloads.S2CSyncPlots payload) {
        dimension = payload.dimension();
        plots = payload.plots();
    }

    public static List<MarketPayloads.PlotEntry> plotsIn(ResourceLocation levelDimension) {
        return levelDimension.equals(dimension) ? plots : List.of();
    }

    public static void clear() {
        dimension = null;
        plots = List.of();
    }

    private ClientPlotStore() {
    }
}
