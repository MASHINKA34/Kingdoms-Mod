package com.geydev.kalfactions.client;

import com.geydev.kalfactions.net.FactionPayloads;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class ClientWorldMapTracks {
    private static final float[] EMPTY = new float[0];

    private static volatile float[] segments = EMPTY;
    private static volatile ResourceLocation dimension;
    private static volatile List<FactionPayloads.StationView> stations = List.of();
    private static volatile ResourceLocation stationDimension;

    private ClientWorldMapTracks() {
    }

    public static void handle(FactionPayloads.S2CWorldMapTracks payload) {
        segments = payload.segments();
        dimension = payload.dimension();
    }

    public static void handleStations(FactionPayloads.S2CWorldMapStations payload) {
        stations = payload.stations();
        stationDimension = payload.dimension();
    }

    public static float[] segments(ResourceKey<Level> dim) {
        ResourceLocation current = dimension;
        return current != null && current.equals(dim.location()) ? segments : EMPTY;
    }

    public static List<FactionPayloads.StationView> stations(ResourceKey<Level> dim) {
        ResourceLocation current = stationDimension;
        return current != null && current.equals(dim.location()) ? stations : List.of();
    }
}
