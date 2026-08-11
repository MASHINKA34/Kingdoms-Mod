package com.geydev.kalfactions.integration.xaero;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.blackzone.BlackZoneFormat;
import com.geydev.kalfactions.client.ClientBlackZoneState;
import com.geydev.kalfactions.client.ClientZoneInfo;
import com.geydev.kalfactions.outpost.cluster.distribution.ResourceZone;
import net.minecraft.network.chat.Component;
import xaero.common.HudMod;
import xaero.hud.minimap.Minimap;
import xaero.hud.minimap.info.InfoDisplay;
import xaero.hud.minimap.info.InfoDisplayManager;
import xaero.hud.minimap.info.widget.InfoDisplayCommonWidgetFactories;
import xaero.lib.common.config.option.value.io.serialization.BuiltInConfigValueIOCodecs;

final class ZoneInfoDisplay {
    private static final String ID = KalFactions.MOD_ID + ":zone";

    private static boolean installed;
    private static boolean failureLogged;

    static void ensureInstalled() {
        if (installed) {
            return;
        }
        try {
            HudMod hud = HudMod.INSTANCE;
            if (hud == null) {
                return;
            }
            Minimap minimap = hud.getMinimap();
            if (minimap == null || minimap.getInfoDisplays() == null) {
                return;
            }
            InfoDisplayManager manager = minimap.getInfoDisplays().getManager();
            if (manager == null) {
                return;
            }
            if (manager.get(ID) != null) {
                installed = true;
                return;
            }
            manager.add(InfoDisplay.Builder.<Boolean>begin()
                    .setId(ID)
                    .setName(Component.translatable("kingdoms.xaero.zone_display"))
                    .setDefaultState(Boolean.TRUE)
                    .setCodec(BuiltInConfigValueIOCodecs.BOOLEAN)
                    .setWidgetFactory(InfoDisplayCommonWidgetFactories.OFF_ON)
                    .setCompiler((display, compiler, session, height, position) -> {
                        if (!Boolean.TRUE.equals(display.getEffectiveState())) {
                            return;
                        }
                        ResourceZone zone = ClientZoneInfo.zoneAt(position);
                        if (zone != null) {
                            compiler.addLine(Component.translatable(zoneKey(zone)));
                        }
                        if (ClientBlackZoneState.active()) {
                            compiler.addLine(Component.translatable(
                                    "kingdoms.blackzone.display.timer",
                                    BlackZoneFormat.clock(ClientBlackZoneState.accumulatedMillis())
                            ));
                        }
                    })
                    .build());
            manager.applyLocalConfig();
            installed = true;
        } catch (RuntimeException | LinkageError exception) {
            installed = true;
            if (!failureLogged) {
                failureLogged = true;
                KalFactions.LOGGER.warn("Could not add the Kingdoms zone line to the Xaero minimap", exception);
            }
        }
    }

    private static String zoneKey(ResourceZone zone) {
        return switch (zone) {
            case BLUE -> "kingdoms.xaero.zone.blue";
            case YELLOW -> "kingdoms.xaero.zone.yellow";
            case RED -> "kingdoms.xaero.zone.red";
            case BLACK -> "kingdoms.xaero.zone.black";
        };
    }

    private ZoneInfoDisplay() {
    }
}
