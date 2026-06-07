package com.geydev.kalfactions.pvp;

import com.geydev.kalfactions.KalFactions;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class DuelManager {
    private static final Map<DirectedPair, DuelRequest> REQUESTS = new HashMap<>();
    private static final Set<PlayerPair> ACTIVE_DUELS = new HashSet<>();
    private static Duration requestTimeout = Duration.ofSeconds(30);

    public static Result request(ServerPlayer challenger, ServerPlayer target) {
        if (challenger == target) {
            return Result.SELF;
        }
        PlayerPair pair = PlayerPair.of(challenger.getUUID(), target.getUUID());
        if (ACTIVE_DUELS.contains(pair)) {
            return Result.ALREADY_ACTIVE;
        }

        long expiresAt = System.nanoTime() + requestTimeout.toNanos();
        REQUESTS.put(
                new DirectedPair(challenger.getUUID(), target.getUUID()),
                new DuelRequest(challenger.getUUID(), target.getUUID(), expiresAt)
        );
        challenger.sendSystemMessage(Component.literal(
                "Duel request sent to " + target.getGameProfile().getName() + "."
        ));
        target.sendSystemMessage(Component.literal(
                challenger.getGameProfile().getName()
                        + " challenged you to a duel. Use /duel accept "
                        + challenger.getGameProfile().getName()
                        + "."
        ));
        return Result.SUCCESS;
    }

    public static Result accept(ServerPlayer target) {
        long now = System.nanoTime();
        Optional<DuelRequest> newest = REQUESTS.values().stream()
                .filter(request -> request.target().equals(target.getUUID()))
                .filter(request -> request.expiresAtNanos() > now)
                .max(java.util.Comparator.comparingLong(DuelRequest::expiresAtNanos));
        if (newest.isEmpty()) {
            return Result.NOT_FOUND;
        }
        ServerPlayer challenger = target.server.getPlayerList().getPlayer(newest.get().challenger());
        return challenger == null ? Result.PLAYER_OFFLINE : accept(target, challenger);
    }

    public static Result accept(ServerPlayer target, ServerPlayer challenger) {
        DirectedPair requestKey = new DirectedPair(challenger.getUUID(), target.getUUID());
        DuelRequest request = REQUESTS.remove(requestKey);
        if (request == null || request.expiresAtNanos() <= System.nanoTime()) {
            return Result.NOT_FOUND;
        }

        PlayerPair pair = PlayerPair.of(challenger.getUUID(), target.getUUID());
        ACTIVE_DUELS.add(pair);
        removeRequestsFor(challenger.getUUID());
        removeRequestsFor(target.getUUID());
        challenger.sendSystemMessage(Component.literal(
                target.getGameProfile().getName() + " accepted your duel request."
        ));
        target.sendSystemMessage(Component.literal(
                "Duel with " + challenger.getGameProfile().getName() + " started."
        ));
        return Result.SUCCESS;
    }

    public static Result decline(ServerPlayer target, ServerPlayer challenger) {
        DuelRequest removed = REQUESTS.remove(new DirectedPair(challenger.getUUID(), target.getUUID()));
        if (removed == null) {
            return Result.NOT_FOUND;
        }
        challenger.sendSystemMessage(Component.literal(
                target.getGameProfile().getName() + " declined your duel request."
        ));
        target.sendSystemMessage(Component.literal("Duel request declined."));
        return Result.SUCCESS;
    }

    public static boolean isActive(ServerPlayer first, ServerPlayer second) {
        return ACTIVE_DUELS.contains(PlayerPair.of(first.getUUID(), second.getUUID()));
    }

    public static void setRequestTimeout(Duration timeout) {
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Duel request timeout must be positive");
        }
        requestTimeout = timeout;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long now = System.nanoTime();
        REQUESTS.values().removeIf(request -> {
            if (request.expiresAtNanos() > now) {
                return false;
            }
            notifyTimeout(event.getServer(), request);
            return true;
        });
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer loser)) {
            return;
        }

        UUID loserId = loser.getUUID();
        Optional<PlayerPair> duel = ACTIVE_DUELS.stream()
                .filter(pair -> pair.contains(loserId))
                .findFirst();
        if (duel.isEmpty()) {
            return;
        }

        ACTIVE_DUELS.remove(duel.get());
        UUID winnerId = duel.get().other(loserId);
        ServerPlayer winner = loser.server.getPlayerList().getPlayer(winnerId);
        loser.sendSystemMessage(Component.literal("You lost the duel."));
        if (winner != null) {
            winner.sendSystemMessage(Component.literal(
                    "You won the duel against " + loser.getGameProfile().getName() + "."
            ));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId = event.getEntity().getUUID();
        removeRequestsFor(playerId);
        Optional<PlayerPair> duel = ACTIVE_DUELS.stream()
                .filter(pair -> pair.contains(playerId))
                .findFirst();
        if (duel.isEmpty()) {
            return;
        }
        ACTIVE_DUELS.remove(duel.get());
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerPlayer opponent = player.server.getPlayerList().getPlayer(duel.get().other(playerId));
            if (opponent != null) {
                opponent.sendSystemMessage(Component.literal("The duel ended because your opponent disconnected."));
            }
        }
    }

    private static void notifyTimeout(MinecraftServer server, DuelRequest request) {
        ServerPlayer challenger = server.getPlayerList().getPlayer(request.challenger());
        ServerPlayer target = server.getPlayerList().getPlayer(request.target());
        if (challenger != null) {
            challenger.sendSystemMessage(Component.literal("Your duel request expired."));
        }
        if (target != null) {
            target.sendSystemMessage(Component.literal("The duel request expired."));
        }
    }

    private static void removeRequestsFor(UUID playerId) {
        REQUESTS.keySet().removeIf(pair -> pair.first().equals(playerId) || pair.second().equals(playerId));
    }

    public enum Result {
        SUCCESS,
        SELF,
        ALREADY_ACTIVE,
        NOT_FOUND,
        PLAYER_OFFLINE
    }

    private record DuelRequest(UUID challenger, UUID target, long expiresAtNanos) {
    }

    private record DirectedPair(UUID first, UUID second) {
    }

    private record PlayerPair(UUID first, UUID second) {
        private static PlayerPair of(UUID first, UUID second) {
            return first.compareTo(second) <= 0
                    ? new PlayerPair(first, second)
                    : new PlayerPair(second, first);
        }

        private boolean contains(UUID player) {
            return first.equals(player) || second.equals(player);
        }

        private UUID other(UUID player) {
            if (first.equals(player)) {
                return second;
            }
            if (second.equals(player)) {
                return first;
            }
            throw new IllegalArgumentException("Player is not part of this duel");
        }
    }

    private DuelManager() {
    }
}
