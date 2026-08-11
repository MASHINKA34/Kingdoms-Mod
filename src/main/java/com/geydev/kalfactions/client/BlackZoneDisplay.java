package com.geydev.kalfactions.client;

import com.geydev.kalfactions.blackzone.BlackZoneFormat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

public final class BlackZoneDisplay {
    public static List<Component> lines() {
        List<Component> lines = new ArrayList<>(2);
        if (!ClientBlackZoneState.active()) {
            return lines;
        }
        lines.add(Component.translatable(
                "kingdoms.blackzone.display.timer",
                BlackZoneFormat.clock(ClientBlackZoneState.accumulatedMillis())
        ));
        if (!ClientBlackZoneState.inZone()) {
            lines.add(Component.translatable(
                    "kingdoms.blackzone.display.release",
                    BlackZoneFormat.clock(ClientBlackZoneState.releaseMillis())
            ));
        }
        return lines;
    }

    private BlackZoneDisplay() {
    }
}
