package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.invisibility.InvisibilityPayloads;
import com.geydev.kalfactions.invisibility.TrueInvisibility;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID, value = Dist.CLIENT)
public final class ClientTrueInvisibility {
    private static final Set<Integer> HIDDEN = ConcurrentHashMap.newKeySet();

    public static void handle(InvisibilityPayloads.S2CTrueInvisibility payload) {
        if (payload.active()) {
            HIDDEN.add(payload.entityId());
        } else {
            HIDDEN.remove(payload.entityId());
        }
    }

    public static boolean isHidden(LivingEntity entity) {
        return HIDDEN.contains(entity.getId()) || TrueInvisibility.isActive(entity);
    }

    public static void clear() {
        HIDDEN.clear();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    private ClientTrueInvisibility() {
    }
}
