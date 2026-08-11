package com.geydev.kalfactions.blackzone;

import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.net.FactionPayloads;
import com.geydev.kalfactions.net.FactionServerHooks;
import com.geydev.kalfactions.territory.WorldZonePolicy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

public final class BlackZoneService {
    private static final long MAX_STEP_MILLIS = 10_000L;
    private static final long SYNC_INTERVAL_MILLIS = 5_000L;

    private static final Map<UUID, Long> LAST_SAMPLE = new HashMap<>();
    private static final Map<UUID, Long> LAST_SYNC = new HashMap<>();
    private static final Set<UUID> WARNED = new HashSet<>();

    public record Sample(
            BlackZoneStage stage,
            long accumulatedMillis,
            boolean inZone,
            boolean entered,
            boolean released
    ) {
    }

    public static boolean enabled() {
        return ModConfigSpec.BLACK_ZONE_ENABLED.get();
    }

    public static boolean exempt(Player player) {
        return ModConfigSpec.BLACK_ZONE_EXEMPT_OPERATORS.get() && player.hasPermissions(2);
    }

    public static long releaseMillis() {
        return ModConfigSpec.BLACK_ZONE_RELEASE_MINUTES.getAsInt() * BlackZoneStage.MINUTE_MILLIS;
    }

    public static boolean inBlackZone(Player player) {
        return player.level() instanceof ServerLevel level
                && WorldZonePolicy.isBlack(level, player.blockPosition());
    }

    public static Sample tick(ServerLevel level, Player player, boolean inBlack) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return new Sample(null, 0L, inBlack, false, false);
        }
        long now = BlackZoneClock.now();
        BlackZoneData data = BlackZoneData.get(server);
        UUID playerId = player.getUUID();
        BlackZoneToll toll = data.toll(playerId);
        boolean entered = false;
        boolean released = false;
        boolean announceRelease = false;

        if (!toll.equals(BlackZoneToll.EMPTY) && now - toll.lastInZoneAt() >= releaseMillis()) {
            BlackZonePenalties.adoptFor(playerId, toll.accumulatedMillis());
            toll = BlackZoneToll.EMPTY;
            released = true;
            announceRelease = WARNED.remove(playerId);
        }

        if (inBlack) {
            Long previous = LAST_SAMPLE.put(playerId, now);
            long delta = previous == null ? 0L : Math.max(0L, Math.min(MAX_STEP_MILLIS, now - previous));
            toll = toll.advance(delta, now);
            entered = WARNED.add(playerId);
        } else {
            LAST_SAMPLE.remove(playerId);
            if (toll.isEmpty()) {
                WARNED.remove(playerId);
            }
        }

        BlackZoneStage stage = inBlack || !toll.isEmpty()
                ? BlackZoneStage.current(toll.accumulatedMillis())
                : null;

        if (stage == null) {
            if (BlackZonePenalties.tracked(playerId)) {
                BlackZonePenalties.clear(player);
            }
        } else {
            BlackZonePenalties.apply(player, toll.accumulatedMillis(), inBlack);
        }

        if (player instanceof ServerPlayer serverPlayer) {
            if (entered) {
                FactionServerHooks.sendNotice(
                        serverPlayer,
                        Component.translatable("kingdoms.blackzone.warning"),
                        false
                );
            }
            if (announceRelease) {
                FactionServerHooks.sendNotice(
                        serverPlayer,
                        Component.translatable("kingdoms.blackzone.released"),
                        true
                );
            }
            int stageOrdinal = stage == null ? -1 : stage.ordinal();
            if (stageOrdinal > toll.notifiedStage()) {
                toll = toll.withNotifiedStage(stageOrdinal);
                FactionServerHooks.sendNotice(serverPlayer, Component.translatable(stage.noticeKey()), false);
            }
            if (inBlack && stage != null && BlackZoneStage.kolyvanActive(toll.accumulatedMillis())
                    && now - toll.lastKolyvanAt() >= kolyvanIntervalMillis()
                    && KolyvanSpawner.spawnFor(level, serverPlayer)) {
                toll = toll.withKolyvanAt(now);
            }
            sync(
                    serverPlayer,
                    stage,
                    toll.accumulatedMillis(),
                    inBlack,
                    releaseLeftMillis(toll, now),
                    released || entered
            );
        }

        data.put(playerId, toll);
        return new Sample(stage, toll.accumulatedMillis(), inBlack, entered, released);
    }

    public static void forget(UUID playerId) {
        LAST_SAMPLE.remove(playerId);
        LAST_SYNC.remove(playerId);
        WARNED.remove(playerId);
        BlackZonePenalties.forget(playerId);
    }

    public static void suspend(Player player) {
        UUID playerId = player.getUUID();
        LAST_SAMPLE.remove(playerId);
        WARNED.remove(playerId);
        if (BlackZonePenalties.tracked(playerId)) {
            BlackZonePenalties.clear(player);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            sync(serverPlayer, null, 0L, false, 0L, true);
        }
    }

    public static void reset(MinecraftServer server, Player player) {
        BlackZoneData.get(server).clear(player.getUUID());
        LAST_SAMPLE.remove(player.getUUID());
        WARNED.remove(player.getUUID());
        BlackZonePenalties.clear(player);
        if (player instanceof ServerPlayer serverPlayer) {
            sync(serverPlayer, null, 0L, false, 0L, true);
        }
    }

    public static void setAccumulatedMinutes(MinecraftServer server, ServerPlayer player, int minutes) {
        long now = BlackZoneClock.now();
        BlackZoneData data = BlackZoneData.get(server);
        long millis = Math.max(0, minutes) * BlackZoneStage.MINUTE_MILLIS;
        BlackZoneStage stage = BlackZoneStage.current(millis);
        BlackZoneToll toll = data.toll(player.getUUID())
                .withAccumulated(millis, now)
                .withNotifiedStage(stage == null ? -1 : stage.ordinal());
        data.put(player.getUUID(), toll);
        boolean inBlack = inBlackZone(player);
        if (millis <= 0L && !inBlack) {
            BlackZonePenalties.clear(player);
        } else {
            BlackZonePenalties.apply(player, millis, inBlack);
        }
        sync(player, stage, millis, inBlack, releaseLeftMillis(toll, now), true);
    }

    public static long accumulatedMillis(MinecraftServer server, UUID playerId) {
        return BlackZoneData.get(server).toll(playerId).accumulatedMillis();
    }

    public static long kolyvanIntervalMillis() {
        return ModConfigSpec.BLACK_ZONE_KOLYVAN_INTERVAL_MINUTES.getAsInt() * BlackZoneStage.MINUTE_MILLIS;
    }

    private static long releaseLeftMillis(BlackZoneToll toll, long now) {
        if (toll.isEmpty()) {
            return 0L;
        }
        return Math.max(0L, toll.lastInZoneAt() + releaseMillis() - now);
    }

    private static void sync(
            ServerPlayer player,
            BlackZoneStage stage,
            long accumulatedMillis,
            boolean inZone,
            long releaseLeftMillis,
            boolean force
    ) {
        long now = BlackZoneClock.now();
        Long last = LAST_SYNC.get(player.getUUID());
        if (stage == null) {
            if (last == null) {
                return;
            }
            LAST_SYNC.remove(player.getUUID());
        } else {
            if (!force && last != null && now - last < SYNC_INTERVAL_MILLIS) {
                return;
            }
            LAST_SYNC.put(player.getUUID(), now);
        }
        PacketDistributor.sendToPlayer(player, new FactionPayloads.S2CBlackZoneState(
                stage == null ? -1 : stage.ordinal(),
                (int) Math.min(Integer.MAX_VALUE, accumulatedMillis / 1000L),
                inZone,
                (int) Math.min(Integer.MAX_VALUE, releaseLeftMillis / 1000L)
        ));
    }

    private BlackZoneService() {
    }
}
