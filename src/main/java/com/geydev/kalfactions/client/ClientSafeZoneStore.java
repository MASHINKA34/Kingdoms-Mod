package com.geydev.kalfactions.client;

import com.geydev.kalfactions.safezone.SafeZonePayloads;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

public final class ClientSafeZoneStore {
    private static volatile ResourceLocation dimension;
    private static volatile List<SafeZonePayloads.ZoneEntry> zones = List.of();

    public static void handle(SafeZonePayloads.S2CSyncSafeZones payload) {
        dimension = payload.dimension();
        zones = payload.zones();
    }

    public static List<SafeZonePayloads.ZoneEntry> zonesIn(ResourceLocation levelDimension) {
        return levelDimension.equals(dimension) ? zones : List.of();
    }

    public static void clear() {
        dimension = null;
        zones = List.of();
    }

    private ClientSafeZoneStore() {
    }
}
