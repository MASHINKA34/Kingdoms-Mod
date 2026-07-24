package com.geydev.kalfactions.client;

import com.geydev.kalfactions.outpost.cluster.DrillPayloads;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientDrillTargets {
    private static final Map<Integer, DrillPayloads.S2CTargets> TARGETS = new ConcurrentHashMap<>();

    public static void accept(DrillPayloads.S2CTargets payload) {
        TARGETS.put(payload.containerId(), payload);
    }

    public static DrillPayloads.S2CTargets get(int containerId) {
        return TARGETS.get(containerId);
    }

    public static void clear(int containerId) {
        TARGETS.remove(containerId);
    }

    private ClientDrillTargets() {
    }
}
