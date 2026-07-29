package com.geydev.kalfactions.client;

import com.geydev.kalfactions.outpost.trader.TraderPayloads;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

public final class ClientTraderPointStore {
    private static volatile List<TraderPayloads.PointEntry> points = List.of();

    public static void accept(List<TraderPayloads.PointEntry> entries) {
        points = List.copyOf(entries);
    }

    public static List<TraderPayloads.PointEntry> pointsIn(ResourceLocation dimension) {
        String id = dimension.toString();
        return points.stream().filter(entry -> entry.dimension().equals(id)).toList();
    }

    public static void clear() {
        points = List.of();
    }

    private ClientTraderPointStore() {
    }
}
