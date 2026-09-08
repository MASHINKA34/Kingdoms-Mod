package com.geydev.kalfactions.scout;

import com.geydev.kalfactions.command.NumismaticsEconomy;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.entity.MapScoutEntity;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.faction.FactionMember;
import com.geydev.kalfactions.faction.FactionRole;
import com.geydev.kalfactions.net.ActionCooldown;
import com.geydev.kalfactions.net.FactionServerHooks;
import com.geydev.kalfactions.tax.OfflineNoticeQueue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ScoutService {
    public static final double MAX_NPC_DISTANCE_SQUARED = 100.0D;

    private static final long ACTION_COOLDOWN_TICKS = 4L;
    private static final int STATE_SYNC_INTERVAL_TICKS = 40;
    private static final ConcurrentHashMap<UUID, Long> LAST_ACTION_TICK = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, ScoutPayloads.S2CScoutState> LAST_STATE = new ConcurrentHashMap<>();
    private static ScoutRuntime runtime;
    private static int ticksUntilStateSync = STATE_SYNC_INTERVAL_TICKS;

    public static void openOrderScreen(ServerPlayer player, MapScoutEntity scout) {
        MinecraftServer server = player.getServer();
        if (server == null || !player.isAlive() || player.isSpectator()) {
            return;
        }
        FactionManager factions = FactionManager.get(player.serverLevel());
        Faction faction = factions.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.error.not_in_faction"), false);
            return;
        }
        ScoutManager manager = ScoutManager.get(server);
        ScoutOrder active = manager.activeOrder(faction.id()).orElse(null);
        if (active != null) {
            FactionServerHooks.sendNotice(player, busyNotice(active), false);
            pushState(player);
            return;
        }
        FactionMember member = faction.member(player.getUUID()).orElse(null);
        boolean officer = member != null && member.role().isAtLeast(FactionRole.OFFICER);
        List<ScoutPayloads.PackageEntry> packages = new ArrayList<>();
        for (ScoutPackage option : ScoutPackage.values()) {
            packages.add(new ScoutPayloads.PackageEntry(
                    option.ordinal(),
                    option.sizeChunks(),
                    option.durationMinutes(),
                    option.price()
            ));
        }
        PacketDistributor.sendToPlayer(player, new ScoutPayloads.S2COpenScout(
                packages,
                faction.treasuryBalance(),
                officer,
                scout.blockPosition()
        ));
    }

    public static void placeOrder(ServerPlayer player, ScoutPayloads.C2SScoutOrder payload) {
        MinecraftServer server = player.getServer();
        if (server == null || !player.isAlive() || player.isSpectator()) {
            return;
        }
        long now = player.level().getGameTime();
        Long previous = ActionCooldown.mark(
                LAST_ACTION_TICK, player.getUUID(), now, ACTION_COOLDOWN_TICKS);
        if (previous != null && now - previous < ACTION_COOLDOWN_TICKS) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.error.action_rate_limited"), false);
            return;
        }
        if (payload.packageOrdinal() < 0 || payload.packageOrdinal() >= ScoutPackage.values().length) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.scout.error.package"), false);
            return;
        }
        ScoutPackage option = ScoutPackage.byOrdinal(payload.packageOrdinal());
        ServerLevel level = player.serverLevel();
        ResourceLocation dimensionId = level.dimension().location();
        if (!dimensionId.equals(payload.dimension()) || !isAllowedDimension(dimensionId)) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.scout.error.dimension"), false);
            return;
        }
        if (nearbyScout(player) == null) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.scout.error.too_far"), false);
            return;
        }
        FactionManager factions = FactionManager.get(level);
        Faction faction = factions.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.error.not_in_faction"), false);
            return;
        }
        FactionMember member = faction.member(player.getUUID()).orElse(null);
        if (member == null || !member.role().isAtLeast(FactionRole.OFFICER)) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.scout.error.not_officer"), false);
            return;
        }
        int size = option.sizeChunks();
        if (size * size > ModConfigSpec.SCOUT_MAX_CHUNKS_PER_ORDER.getAsInt()) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.scout.error.too_large"), false);
            return;
        }
        if (!withinBorder(level, payload.centerChunkX(), payload.centerChunkZ(), size)) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.scout.error.border"), false);
            return;
        }
        ScoutManager manager = ScoutManager.get(server);
        long price = option.price();
        ScoutOrder order = new ScoutOrder(
                UUID.randomUUID(),
                option,
                level.dimension(),
                payload.centerChunkX(),
                payload.centerChunkZ(),
                size,
                System.currentTimeMillis(),
                option.durationMillis(),
                price,
                player.getUUID()
        );
        if (!manager.startOrder(faction.id(), order)) {
            ScoutOrder active = manager.activeOrder(faction.id()).orElse(null);
            FactionServerHooks.sendNotice(
                    player,
                    active == null
                            ? Component.translatable("kingdoms.scout.error.busy")
                            : busyNotice(active),
                    false
            );
            return;
        }
        if (!factions.withdraw(faction.id(), price).successful()) {
            manager.removeOrder(faction.id());
            FactionServerHooks.sendNotice(
                    player,
                    Component.translatable("kingdoms.scout.error.funds", NumismaticsEconomy.format(price)),
                    false
            );
            return;
        }
        Component notice = Component.translatable(
                "kingdoms.scout.ordered",
                size,
                size,
                option.durationMinutes(),
                NumismaticsEconomy.format(price)
        );
        broadcastToFaction(server, faction, notice, true);
        pushStateToFaction(server, faction.id());
    }

    public static void tick(MinecraftServer server) {
        if (runtime == null) {
            return;
        }
        if (--ticksUntilStateSync <= 0) {
            ticksUntilStateSync = STATE_SYNC_INTERVAL_TICKS;
            syncChangedStates(server);
        }
        ScoutManager manager = ScoutManager.get(server);
        for (ScoutRuntime.Result result : runtime.tick(manager, ModConfigSpec.SCOUT_CHUNKS_PER_TICK.getAsInt())) {
            finishDelivery(server, manager, result.factionId(), result.order(), result.successful());
        }
    }

    private static void finishDelivery(
            MinecraftServer server,
            ScoutManager manager,
            UUID factionId,
            ScoutOrder order,
            boolean successful
    ) {
        if (manager.activeOrder(factionId).orElse(null) != order) {
            return;
        }
        order.setDeliveryInFlight(false);
        if (!successful) {
            refundAndDrop(server, manager, factionId, order, "kingdoms.scout.failed");
            return;
        }
        order.markDelivered();
        manager.markDirty();
        Faction faction = FactionManager.get(server).getFactionById(factionId).orElse(null);
        Component notice = Component.translatable("kingdoms.scout.returned");
        if (faction != null) {
            broadcastToFaction(server, faction, notice, true);
        }
        pushStateToFaction(server, factionId);
    }

    private static void refundAndDrop(
            MinecraftServer server,
            ScoutManager manager,
            UUID factionId,
            ScoutOrder order,
            String messageKey
    ) {
        if (manager.activeOrder(factionId).orElse(null) != order) {
            return;
        }
        if (runtime != null) {
            runtime.cancel(factionId, order);
        }
        manager.removeOrder(factionId);
        FactionManager factions = FactionManager.get(server);
        if (order.paid() > 0L) {
            factions.deposit(factionId, order.paid());
        }
        Faction faction = factions.getFactionById(factionId).orElse(null);
        if (faction != null) {
            broadcastToFaction(
                    server,
                    faction,
                    Component.translatable(messageKey, NumismaticsEconomy.format(order.paid())),
                    false
            );
        }
        pushStateToFaction(server, factionId);
    }

    public static boolean cancel(MinecraftServer server, UUID factionId) {
        ScoutManager manager = ScoutManager.get(server);
        ScoutOrder order = manager.activeOrder(factionId).orElse(null);
        if (order == null) {
            return false;
        }
        if (runtime != null && !runtime.cancel(factionId, order)) {
            return false;
        }
        refundAndDrop(server, manager, factionId, order, "kingdoms.scout.cancelled");
        return true;
    }

    public static boolean completeNow(MinecraftServer server, UUID factionId) {
        ScoutManager manager = ScoutManager.get(server);
        ScoutOrder order = manager.activeOrder(factionId).orElse(null);
        if (order == null) {
            return false;
        }
        if (server.getLevel(order.dimension()) == null) {
            return false;
        }
        order.expedite();
        manager.markDirty();
        pushStateToFaction(server, factionId);
        return true;
    }

    public static void pushState(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        sendState(player, stateFor(player, server));
    }

    private static ScoutPayloads.S2CScoutState stateFor(ServerPlayer player, MinecraftServer server) {
        UUID factionId = FactionManager.get(server).getFactionIdForMember(player.getUUID()).orElse(null);
        ScoutOrder order = factionId == null
                ? null
                : ScoutManager.get(server).activeOrder(factionId).orElse(null);
        return order == null
                ? ScoutPayloads.S2CScoutState.idle()
                : new ScoutPayloads.S2CScoutState(
                        true,
                        order.endsAtMillis(),
                        order.progressPercent(),
                        order.sizeChunks(),
                        order.centerChunkX(),
                        order.centerChunkZ()
                );
    }

    private static void sendState(ServerPlayer player, ScoutPayloads.S2CScoutState state) {
        LAST_STATE.put(player.getUUID(), state);
        PacketDistributor.sendToPlayer(player, state);
    }

    private static void syncChangedStates(MinecraftServer server) {
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            syncStateIfChanged(player);
        }
    }

    static void syncStateIfChanged(ServerPlayer player) {
        ScoutPayloads.S2CScoutState state = stateFor(player, player.server);
        if (!state.equals(LAST_STATE.get(player.getUUID()))) {
            sendState(player, state);
        }
    }

    public static void pushStateToFaction(MinecraftServer server, UUID factionId) {
        Faction faction = FactionManager.get(server).getFactionById(factionId).orElse(null);
        if (faction == null) {
            return;
        }
        for (UUID memberId : faction.members().keySet()) {
            ServerPlayer online = server.getPlayerList().getPlayer(memberId);
            if (online != null) {
                pushState(online);
            }
        }
    }

    public static Component busyNotice(ScoutOrder order) {
        return Component.translatable(
                "kingdoms.scout.busy",
                formatRemaining(order.remainingMillis(System.currentTimeMillis()))
        );
    }

    public static String formatRemaining(long millis) {
        long totalSeconds = Math.max(0L, millis) / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    public static MapScoutEntity nearbyScout(ServerPlayer player) {
        List<MapScoutEntity> candidates = player.serverLevel().getEntitiesOfClass(
                MapScoutEntity.class,
                player.getBoundingBox().inflate(Math.sqrt(MAX_NPC_DISTANCE_SQUARED)),
                scout -> scout.isAlive() && scout.distanceToSqr(player) <= MAX_NPC_DISTANCE_SQUARED
        );
        return candidates.stream()
                .min(Comparator.comparingDouble(scout -> scout.distanceToSqr(player)))
                .orElse(null);
    }

    public static boolean isAllowedDimension(ResourceLocation dimensionId) {
        for (String allowed : ModConfigSpec.SCOUT_ALLOWED_DIMENSIONS.get()) {
            if (dimensionId.toString().equals(allowed)) {
                return true;
            }
        }
        return false;
    }

    public static ResourceKey<Level> firstAllowedDimension() {
        for (String allowed : ModConfigSpec.SCOUT_ALLOWED_DIMENSIONS.get()) {
            ResourceLocation id = ResourceLocation.tryParse(allowed);
            if (id != null) {
                return ResourceKey.create(Registries.DIMENSION, id);
            }
        }
        return Level.OVERWORLD;
    }

    public static void onServerStarted(MinecraftServer server) {
        onServerStopping();
        runtime = new ScoutRuntime(server);
        ticksUntilStateSync = STATE_SYNC_INTERVAL_TICKS;
    }

    public static void onServerStopping() {
        if (runtime != null) {
            runtime.close();
            runtime = null;
        }
        LAST_ACTION_TICK.clear();
        LAST_STATE.clear();
    }

    public static void onLogout(UUID playerId) {
        LAST_ACTION_TICK.remove(playerId);
        LAST_STATE.remove(playerId);
    }

    private static void broadcastToFaction(
            MinecraftServer server,
            Faction faction,
            Component notice,
            boolean successful
    ) {
        OfflineNoticeQueue offlineQueue = OfflineNoticeQueue.get(server);
        for (UUID memberId : faction.members().keySet()) {
            ServerPlayer online = server.getPlayerList().getPlayer(memberId);
            if (online == null) {
                offlineQueue.enqueue(server, memberId, notice, successful);
            } else {
                FactionServerHooks.sendNotice(online, notice, successful);
            }
        }
    }

    private static boolean withinBorder(ServerLevel level, int centerChunkX, int centerChunkZ, int size) {
        if (Math.abs((long) centerChunkX) > ScoutPayloads.MAX_CHUNK_COORDINATE
                || Math.abs((long) centerChunkZ) > ScoutPayloads.MAX_CHUNK_COORDINATE || size < 1 || size > 64) {
            return false;
        }
        int half = (size - 1) / 2;
        int minChunkX = centerChunkX - half;
        int minChunkZ = centerChunkZ - half;
        int maxChunkX = minChunkX + size - 1;
        int maxChunkZ = minChunkZ + size - 1;
        return level.getWorldBorder().isWithinBounds(new ChunkPos(minChunkX, minChunkZ))
                && level.getWorldBorder().isWithinBounds(new ChunkPos(maxChunkX, maxChunkZ));
    }

    public static boolean spawn(ServerLevel level, double x, double y, double z, float yRot) {
        MapScoutEntity scout = com.geydev.kalfactions.registry.ModEntities.MAP_SCOUT.get().create(level);
        if (scout == null) {
            return false;
        }
        scout.moveTo(x, y, z, yRot, 0.0F);
        scout.setYBodyRot(yRot);
        scout.setYHeadRot(yRot);
        scout.yBodyRotO = yRot;
        scout.yHeadRotO = yRot;
        if (!level.addFreshEntity(scout)) {
            return false;
        }
        ScoutManager.get(level.getServer()).addPost(scout.getUUID(), level.dimension(), scout.blockPosition());
        return true;
    }

    private ScoutService() {
    }
}
