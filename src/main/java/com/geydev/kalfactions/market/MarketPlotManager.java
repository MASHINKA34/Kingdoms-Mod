package com.geydev.kalfactions.market;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;

public final class MarketPlotManager extends SavedData {
    public static final String DATA_NAME = "kingdoms_market_plots";
    public static final Factory<MarketPlotManager> FACTORY =
            new Factory<>(MarketPlotManager::new, MarketPlotManager::load);

    private static final String TAG_PLOTS = "plots";
    private static final String TAG_NEXT_ID = "next_id";

    private final Map<Integer, MarketPlot> plots = new LinkedHashMap<>();
    private int nextId = 1;
    private long revision = 1L;

    public static MarketPlotManager get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static MarketPlotManager get(ServerLevel level) {
        return get(Objects.requireNonNull(level, "level").getServer());
    }

    public synchronized List<MarketPlot> all() {
        return List.copyOf(plots.values());
    }

    public synchronized Optional<MarketPlot> byId(int id) {
        return Optional.ofNullable(plots.get(id));
    }

    public synchronized Optional<MarketPlot> plotAt(ResourceKey<Level> dimension, BlockPos pos) {
        for (MarketPlot plot : plots.values()) {
            if (plot.contains(dimension, pos)) {
                return Optional.of(plot);
            }
        }
        return Optional.empty();
    }

    public synchronized List<MarketPlot> plotsIn(ResourceKey<Level> dimension) {
        List<MarketPlot> result = new ArrayList<>();
        for (MarketPlot plot : plots.values()) {
            if (plot.dimension().equals(dimension)) {
                result.add(plot);
            }
        }
        return result;
    }

    public synchronized boolean intersectsAny(ResourceKey<Level> dimension, BoundingBox box) {
        for (MarketPlot plot : plots.values()) {
            if (plot.dimension().equals(dimension) && plot.box().intersects(box)) {
                return true;
            }
        }
        return false;
    }

    public synchronized MarketPlot create(ResourceKey<Level> dimension, BoundingBox box, long basePrice) {
        MarketPlot plot = new MarketPlot(nextId++, dimension, box, basePrice);
        plots.put(plot.id(), plot);
        markChanged();
        return plot;
    }

    public synchronized boolean remove(int id) {
        if (plots.remove(id) == null) {
            return false;
        }
        markChanged();
        return true;
    }

    public synchronized long revision() {
        return revision;
    }

    public synchronized void markChanged() {
        revision++;
        setDirty();
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag plotsTag = new ListTag();
        plots.values().stream()
                .map(MarketPlot::save)
                .forEach(plotsTag::add);
        tag.put(TAG_PLOTS, plotsTag);
        tag.putInt(TAG_NEXT_ID, nextId);
        return tag;
    }

    private static MarketPlotManager load(CompoundTag tag, HolderLookup.Provider registries) {
        MarketPlotManager manager = new MarketPlotManager();
        ListTag plotsTag = tag.getList(TAG_PLOTS, Tag.TAG_COMPOUND);
        for (int index = 0; index < plotsTag.size(); index++) {
            MarketPlot.load(plotsTag.getCompound(index))
                    .ifPresent(plot -> manager.plots.put(plot.id(), plot));
        }
        manager.nextId = Math.max(tag.getInt(TAG_NEXT_ID), 1);
        for (MarketPlot plot : manager.plots.values()) {
            manager.nextId = Math.max(manager.nextId, plot.id() + 1);
        }
        return manager;
    }
}
