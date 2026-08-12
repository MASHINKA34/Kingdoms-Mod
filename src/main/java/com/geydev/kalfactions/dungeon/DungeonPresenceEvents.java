package com.geydev.kalfactions.dungeon;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class DungeonPresenceEvents {
    private static final int CHECK_INTERVAL_TICKS = 10;
    private static final Map<UUID, Integer> LAST_DUNGEON = new HashMap<>();
    private static int ticksUntilCheck;

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_DUNGEON.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (--ticksUntilCheck > 0) {
            return;
        }
        ticksUntilCheck = CHECK_INTERVAL_TICKS;
        DungeonManager manager = DungeonManager.get(event.getServer());
        if (manager.isEmpty()) {
            if (!LAST_DUNGEON.isEmpty()) {
                LAST_DUNGEON.clear();
            }
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            DungeonManager.DungeonView dungeon = manager
                    .dungeonAt(ClaimKey.of(player.level(), player.blockPosition()))
                    .orElse(null);
            int id = dungeon == null ? 0 : dungeon.id();
            Integer previous = LAST_DUNGEON.put(player.getUUID(), id);
            if (id != 0 && (previous == null || previous != id)) {
                player.displayClientMessage(
                        Component.translatable("kingdoms.dungeon.entered", dungeon.name()),
                        true
                );
            }
        }
    }

    private DungeonPresenceEvents() {
    }
}
