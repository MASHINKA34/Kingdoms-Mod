package com.geydev.kalfactions.client;

import com.geydev.kalfactions.outpost.cluster.DrillPayloads;
import com.geydev.kalfactions.client.screen.DrillScreen;
import com.geydev.kalfactions.client.screen.DrillTargetScreen;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;

public final class ClientDrillTargets {
    private static final Map<Integer, DrillPayloads.S2CTargets> TARGETS = new ConcurrentHashMap<>();

    public static void accept(DrillPayloads.S2CTargets payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            TARGETS.put(payload.containerId(), payload);
            if (minecraft.screen instanceof DrillTargetScreen selector) {
                selector.acceptState(payload);
            } else if (minecraft.screen instanceof DrillScreen screen
                    && screen.getMenu().containerId == payload.containerId()) {
                screen.acceptState(payload);
            }
        });
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
