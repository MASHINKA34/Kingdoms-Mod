package com.geydev.kalfactions.client;

import com.geydev.kalfactions.net.FactionPayloads;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class ClientWorldMapTracks {
    private static final float[] EMPTY = new float[0];

    private static volatile float[] segments = EMPTY;
    private static volatile ResourceLocation dimension;

    private ClientWorldMapTracks() {
    }

    public static void handle(FactionPayloads.S2CWorldMapTracks payload) {
        segments = payload.segments();
        dimension = payload.dimension();
    }

    public static float[] segments(ResourceKey<Level> dim) {
        ResourceLocation current = dimension;
        return current != null && current.equals(dim.location()) ? segments : EMPTY;
    }
}
