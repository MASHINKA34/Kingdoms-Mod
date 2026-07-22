package com.geydev.kalfactions.outpost.trader;

import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.entity.SellerTraderEntity;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;

public final class TraderLifecycle {
    private static final long MINUTE_MILLIS = 60_000L;
    private static final int MAX_FACTIONS_PER_ROLL = 512;

    public static void tick(MinecraftServer server, long now) {
        TraderWorldData data = TraderWorldData.get(server);
        reconcileContraband(server, data, now);
        if (data.contraband().isEmpty() && now >= data.contrabandCooldownUntil()) {
            spawnContraband(server, data, now);
        }
        reconcileWandering(server, data, now);
        if (now >= data.wanderingNextRollAt()) {
            rollWandering(server, data, now);
            data.setWanderingNextRollAt(now + minutes(ModConfigSpec.WANDERING_ROLL_INTERVAL_MINUTES.getAsInt()));
        }
    }

    public static void onJoin(SellerTraderEntity trader, ServerLevel level) {
        if (trader.traderRole() == SellerTraderRole.PERMANENT) {
            SellerOfferRotation.get(level.getServer()).ensureShop(level.getServer(), trader.getUUID());
            return;
        }
        SellerOfferRotation.get(level.getServer()).setCatalogVisible(level.getServer(), trader.getUUID(), false);
        TraderWorldData data = TraderWorldData.get(level.getServer());
        UUID eventId = trader.eventId().orElse(null);
        boolean valid = trader.traderRole() == SellerTraderRole.CONTRABAND
                ? data.contraband().filter(active -> active.entityId().equals(trader.getUUID()))
                        .filter(active -> active.eventId().equals(eventId)).isPresent()
                : trader.targetFactionId().flatMap(data::wandering)
                        .filter(TraderWorldData.WanderingEvent::active)
                        .filter(active -> active.entityId().equals(trader.getUUID()))
                        .filter(active -> active.eventId().equals(eventId)).isPresent();
        if (!valid) {
            trader.discard();
        }
    }

    public static void onRemoved(SellerTraderEntity trader, ServerLevel level) {
        if (trader.traderRole() == SellerTraderRole.CONTRABAND) {
            TraderWorldData data = TraderWorldData.get(level.getServer());
            if (data.contraband().filter(active -> active.entityId().equals(trader.getUUID())).isPresent()) {
                data.clearContraband(System.currentTimeMillis() + minutes(
                        ModConfigSpec.CONTRABAND_COOLDOWN_MINUTES.getAsInt()
                ));
            }
        } else if (trader.traderRole() == SellerTraderRole.WANDERING) {
            trader.targetFactionId().ifPresent(factionId -> finishWandering(
                    level.getServer(), TraderWorldData.get(level.getServer()), factionId, trader, System.currentTimeMillis()
            ));
        }
    }

    private static void reconcileContraband(MinecraftServer server, TraderWorldData data, long now) {
        TraderWorldData.ActiveContraband active = data.contraband().orElse(null);
        if (active == null) {
            return;
        }
        ServerLevel level = server.getLevel(active.dimension());
        if (level == null) {
            data.clearContraband(now + minutes(ModConfigSpec.CONTRABAND_COOLDOWN_MINUTES.getAsInt()));
            return;
        }
        Entity entity = level.getEntity(active.entityId());
        if (now >= active.expiresAt()) {
            if (entity != null) {
                entity.discard();
            }
            if (data.contraband().isPresent()) {
                data.clearContraband(now + minutes(ModConfigSpec.CONTRABAND_COOLDOWN_MINUTES.getAsInt()));
            }
            return;
        }
        if (entity == null && level.isLoaded(active.pos())) {
            data.clearContraband(now + minutes(ModConfigSpec.CONTRABAND_COOLDOWN_MINUTES.getAsInt()));
        }
    }

    private static void spawnContraband(MinecraftServer server, TraderWorldData data, long now) {
        List<TraderWorldData.SpawnPoint> points = new ArrayList<>(data.points());
        Collections.shuffle(points, new Random(server.overworld().getSeed() ^ now));
        int attempts = Math.min(ModConfigSpec.CONTRABAND_SPAWN_ATTEMPTS.getAsInt(), points.size());
        for (int index = 0; index < attempts; index++) {
            TraderWorldData.SpawnPoint point = points.get(index);
            ServerLevel level = server.getLevel(point.dimension());
            if (level == null || !level.isLoaded(point.pos())) {
                continue;
            }
            BlockPos spawn = level.getSharedSpawnPos();
            int maximumDistance = ModConfigSpec.CONTRABAND_MAX_SPAWN_DISTANCE.getAsInt();
            if (point.pos().distSqr(spawn) > (double) maximumDistance * maximumDistance
                    || !TraderSpawnSafety.isSafe(level, point.pos())) {
                continue;
            }
            UUID eventId = UUID.randomUUID();
            long expiresAt = now + minutes(ModConfigSpec.CONTRABAND_LIFETIME_MINUTES.getAsInt());
            SellerTraderEntity trader = TraderService.createSellerEntity(
                    level,
                    point.pos().getX() + 0.5D,
                    point.pos().getY(),
                    point.pos().getZ() + 0.5D,
                    point.yaw(),
                    null,
                    SellerTraderRole.CONTRABAND,
                    eventId,
                    null,
                    expiresAt
            );
            if (trader == null) {
                continue;
            }
            if (!data.beginContraband(new TraderWorldData.ActiveContraband(
                    eventId, trader.getUUID(), point.id(), point.dimension(), point.pos(), expiresAt
            ))) {
                return;
            }
            if (!level.addFreshEntity(trader)) {
                data.cancelContraband(eventId);
                continue;
            }
            return;
        }
    }

    private static void reconcileWandering(MinecraftServer server, TraderWorldData data, long now) {
        FactionManager manager = FactionManager.get(server);
        for (TraderWorldData.WanderingEvent event : data.wanderingEvents()) {
            if (!event.active()) {
                continue;
            }
            Faction faction = manager.getFaction(event.factionId()).orElse(null);
            ServerLevel level = server.getLevel(event.claim().dimension());
            boolean invalid = faction == null
                    || !event.factionId().equals(manager.getFactionIdAt(event.claim()).orElse(null))
                    || now >= event.expiresAt()
                    || level == null;
            Entity entity = level == null ? null : level.getEntity(event.entityId());
            if (!invalid && entity == null && level.isLoaded(event.pos())) {
                invalid = true;
            }
            if (invalid) {
                if (entity != null) {
                    entity.discard();
                }
                if (faction == null) {
                    data.removeWandering(event.factionId());
                } else if (data.wandering(event.factionId()).filter(TraderWorldData.WanderingEvent::active).isPresent()) {
                    finishWandering(server, data, event.factionId(), null, now);
                }
            }
        }
    }

    private static void rollWandering(MinecraftServer server, TraderWorldData data, long now) {
        List<Faction> factions = FactionManager.get(server).factions().stream()
                .sorted(java.util.Comparator.comparing(Faction::id))
                .limit(MAX_FACTIONS_PER_ROLL)
                .toList();
        Random random = new Random(server.overworld().getSeed() ^ now);
        for (Faction faction : factions) {
            TraderWorldData.WanderingEvent current = data.wandering(faction.id()).orElse(null);
            if (current != null && (current.active() || now < current.cooldownUntil())) {
                continue;
            }
            if (random.nextInt(100) >= ModConfigSpec.WANDERING_CHANCE_PERCENT.getAsInt()) {
                continue;
            }
            spawnWandering(server, data, faction, now, random);
        }
    }

    private static void spawnWandering(
            MinecraftServer server,
            TraderWorldData data,
            Faction faction,
            long now,
            Random random
    ) {
        List<com.geydev.kalfactions.claim.ClaimKey> claims = new ArrayList<>(faction.claims());
        Collections.shuffle(claims, random);
        int attempts = Math.min(ModConfigSpec.WANDERING_SPAWN_ATTEMPTS.getAsInt(), claims.size());
        for (int index = 0; index < attempts; index++) {
            com.geydev.kalfactions.claim.ClaimKey claim = claims.get(index);
            ServerLevel level = server.getLevel(claim.dimension());
            if (level == null || !level.hasChunk(claim.x(), claim.z())) {
                continue;
            }
            int x = claim.x() * 16 + random.nextInt(16);
            int z = claim.z() * 16 + random.nextInt(16);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (!TraderSpawnSafety.isSafe(level, pos)) {
                continue;
            }
            UUID eventId = UUID.randomUUID();
            List<TraderWorldData.RolledOffer> offers = rollOffers(random);
            if (offers.isEmpty()) {
                return;
            }
            long expiresAt = now + minutes(ModConfigSpec.WANDERING_LIFETIME_MINUTES.getAsInt());
            SellerTraderEntity trader = TraderService.createSellerEntity(
                    level, x + 0.5D, y, z + 0.5D, random.nextFloat() * 360.0F, null,
                    SellerTraderRole.WANDERING, eventId, faction.id(), expiresAt
            );
            if (trader == null) {
                continue;
            }
            TraderWorldData.WanderingEvent event = new TraderWorldData.WanderingEvent(
                    faction.id(), eventId, trader.getUUID(), claim, pos, offers, expiresAt, 0L
            );
            if (!data.putWandering(event)) {
                return;
            }
            if (!level.addFreshEntity(trader)) {
                data.removeWandering(faction.id());
                continue;
            }
            notifyFaction(server, faction, "kingdoms.trader.wandering.spawned");
            return;
        }
    }

    private static List<TraderWorldData.RolledOffer> rollOffers(Random random) {
        List<TraderCatalogOffer> pool = new ArrayList<>(TraderCatalogManager.offers(TraderCatalogRole.WANDERING));
        Collections.shuffle(pool, random);
        int minimum = Math.min(ModConfigSpec.WANDERING_OFFER_COUNT_MIN.getAsInt(), pool.size());
        int maximum = Math.min(Math.max(minimum, ModConfigSpec.WANDERING_OFFER_COUNT_MAX.getAsInt()), pool.size());
        if (maximum <= 0) {
            return List.of();
        }
        int count = minimum + random.nextInt(maximum - minimum + 1);
        return pool.stream().limit(count)
                .map(offer -> new TraderWorldData.RolledOffer(offer.id(), offer.price(random.nextLong())))
                .toList();
    }

    private static void finishWandering(
            MinecraftServer server,
            TraderWorldData data,
            UUID factionId,
            SellerTraderEntity trader,
            long now
    ) {
        TraderWorldData.WanderingEvent current = data.wandering(factionId).orElse(null);
        if (current == null || (trader != null && current.entityId() != null
                && !current.entityId().equals(trader.getUUID()))) {
            return;
        }
        data.finishWandering(factionId, now + minutes(ModConfigSpec.WANDERING_COOLDOWN_MINUTES.getAsInt()));
        FactionManager.get(server).getFaction(factionId)
                .ifPresent(faction -> notifyFaction(server, faction, "kingdoms.trader.wandering.departed"));
    }

    private static void notifyFaction(MinecraftServer server, Faction faction, String key) {
        Component message = Component.translatable(key);
        for (UUID memberId : faction.members().keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberId);
            if (player != null) {
                player.sendSystemMessage(message);
            }
        }
    }

    private static long minutes(int value) {
        return Math.multiplyExact((long) value, MINUTE_MILLIS);
    }

    private TraderLifecycle() {
    }
}
