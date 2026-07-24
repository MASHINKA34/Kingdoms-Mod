package com.geydev.kalfactions.client;

import com.geydev.kalfactions.client.screen.QuarryScreen;
import com.geydev.kalfactions.quarry.QuarryPayloads;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;

public final class ClientQuarryState {
    private static final Map<Integer, QuarryPayloads.S2CState> STATES = new ConcurrentHashMap<>();

    public static void accept(QuarryPayloads.S2CState state) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            STATES.put(state.containerId(), state);
            if (minecraft.screen instanceof QuarryScreen screen
                    && screen.getMenu().containerId == state.containerId()) {
                screen.acceptState(state);
            }
        });
    }

    public static QuarryPayloads.S2CState get(int containerId) {
        return STATES.get(containerId);
    }

    public static void clear(int containerId) {
        STATES.remove(containerId);
    }

    private ClientQuarryState() {
    }
}
