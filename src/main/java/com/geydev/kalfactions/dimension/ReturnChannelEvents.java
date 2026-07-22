package com.geydev.kalfactions.dimension;

import com.geydev.kalfactions.KalFactions;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class ReturnChannelEvents {
    private static final double MAX_MOVEMENT_SQUARED = 0.04D;
    private static final Map<UUID, Channel> CHANNELS = new HashMap<>();

    public static void begin(ServerPlayer player, ReturnBinding binding) {
        CHANNELS.put(player.getUUID(), new Channel(binding, player.position()));
    }

    public static boolean complete(ServerPlayer player, ReturnBinding binding) {
        Channel channel = CHANNELS.remove(player.getUUID());
        return channel != null
                && channel.binding.equals(binding)
                && player.position().distanceToSqr(channel.start) <= MAX_MOVEMENT_SQUARED
                && player.hurtTime == 0
                && DimensionControlManager.get(player.serverLevel().getServer()).consumeReturn(binding, Instant.now());
    }

    public static void cancel(ServerPlayer player) {
        CHANNELS.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Channel channel = CHANNELS.get(player.getUUID());
        if (channel == null) {
            return;
        }
        if (player.position().distanceToSqr(channel.start) > MAX_MOVEMENT_SQUARED || player.hurtTime > 0) {
            CHANNELS.remove(player.getUUID());
            player.stopUsingItem();
            player.displayClientMessage(Component.translatable("kingdoms.nether.return.cancelled"), true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CHANNELS.remove(event.getEntity().getUUID());
    }

    private record Channel(ReturnBinding binding, Vec3 start) {
    }

    private ReturnChannelEvents() {
    }
}
