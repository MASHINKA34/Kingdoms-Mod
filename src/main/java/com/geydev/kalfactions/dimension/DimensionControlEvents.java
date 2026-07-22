package com.geydev.kalfactions.dimension;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.dimension.DimensionControlManager.ActiveSession;
import com.geydev.kalfactions.dimension.DimensionControlManager.EndedSession;
import com.geydev.kalfactions.dimension.DimensionControlManager.EntryResult;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.market.MarketPlot;
import com.geydev.kalfactions.market.MarketPlotManager;
import com.geydev.kalfactions.outpost.RogueOutpostManager;
import com.geydev.kalfactions.outpost.trader.TraderWorldData;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class DimensionControlEvents {
    private static final String WIPE_GEN_KEY_PREFIX = "kingdoms_wipe_gen_";
    private static final Map<UUID, ServerBossEvent> SESSION_BARS = new HashMap<>();
    private static final Set<UUID> AUTHORIZED_TRANSFERS = new HashSet<>();
    private static final Set<UUID> EXPECTED_NETHER_ARRIVALS = new HashSet<>();
    private static int tickCounter;

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        DimensionControlManager.get(event.getServer()).runPendingWipes(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        for (ResourceKey<Level> dimension : DimensionControlManager.get(server).consumeWipedThisStartup()) {
            cleanupModDataFor(server, dimension);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        clearBossBars();
        AUTHORIZED_TRANSFERS.clear();
        EXPECTED_NETHER_ARRIVALS.clear();
        tickCounter = 0;
    }

    @SubscribeEvent
    public static void onEntityTravelToDimension(EntityTravelToDimensionEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        ResourceKey<Level> target = event.getDimension();
        MinecraftServer server = event.getEntity().getServer();
        if (server == null || !DimensionControlManager.isControlled(target)) {
            return;
        }
        DimensionControlManager control = DimensionControlManager.get(server);
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            if (control.isClosed(target)) {
                event.setCanceled(true);
            }
            return;
        }
        if (Level.NETHER.equals(target) && AUTHORIZED_TRANSFERS.remove(player.getUUID())) {
            return;
        }
        if (player.hasPermissions(2)) {
            return;
        }
        if (Level.END.equals(target)) {
            if (control.isClosed(target)) {
                deny(event, player, Component.translatable("kingdoms.dimension.closed_notice"));
            }
            return;
        }
        if (!Level.OVERWORLD.equals(player.level().dimension())
                || !control.isInsideRegisteredPortal(player.blockPosition())
                || !player.level().getBlockState(player.blockPosition()).is(Blocks.NETHER_PORTAL)) {
            deny(event, player, Component.translatable("kingdoms.nether.portal.unregistered"));
            return;
        }
        FactionManager factions = FactionManager.get(server);
        UUID factionId = factions.getFactionIdForMember(player.getUUID()).orElse(null);
        if (factionId == null) {
            deny(event, player, Component.translatable("kingdoms.nether.session.faction_required"));
            return;
        }
        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether == null) {
            deny(event, player, Component.translatable("kingdoms.nether.session.no_landing"));
            return;
        }
        EntryResult result = control.authorizeNetherEntry(
                factionId,
                player.getUUID(),
                Instant.now(),
                false,
                (occupied, previous, rules) -> NetherLandingFinder.find(nether, occupied, previous, rules)
        );
        if (!result.allowed()) {
            deny(event, player, entryMessage(result, Instant.now()));
            return;
        }
        event.setCanceled(true);
        ActiveSession session = result.session();
        if (session == null) {
            return;
        }
        BlockPos landing = session.landing().blockPos();
        AUTHORIZED_TRANSFERS.add(player.getUUID());
        EXPECTED_NETHER_ARRIVALS.add(player.getUUID());
        try {
            player.teleportTo(
                    nether, landing.getX() + 0.5D, landing.getY(), landing.getZ() + 0.5D,
                    player.getYRot(), player.getXRot()
            );
        } finally {
            AUTHORIZED_TRANSFERS.remove(player.getUUID());
            EXPECTED_NETHER_ARRIVALS.remove(player.getUUID());
        }
        if (result.status() == DimensionControlManager.EntryStatus.STARTED_SESSION) {
            notifyFaction(server, factionId, Component.translatable(
                    "kingdoms.nether.session.started", result.remainingSessions()
            ));
        }
    }

    @SubscribeEvent
    public static void onPortalSpawn(BlockEvent.PortalSpawnEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        MinecraftServer server = level.getServer();
        DimensionControlManager control = DimensionControlManager.get(server);
        if (Level.OVERWORLD.equals(level.dimension()) && control.isInsideRegisteredPortal(event.getPos())) {
            return;
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ResourceKey<Level> dimension = player.level().dimension();
        if (!Level.NETHER.equals(dimension)) {
            NetherReturnIntegration.removeForPlayer(player);
        }
        if (!DimensionControlManager.isControlled(dimension)) {
            return;
        }
        DimensionControlManager control = DimensionControlManager.get(player.serverLevel().getServer());
        if (persistedWipeGen(player, dimension) < control.wipeGeneration(dimension)) {
            evacuatePlayer(player, "kingdoms.dimension.wiped_notice");
            return;
        }
        if (player.hasPermissions(2)) {
            return;
        }
        if (Level.END.equals(dimension) && control.isClosed(dimension)) {
            evacuatePlayer(player, "kingdoms.dimension.evicted");
            return;
        }
        if (Level.NETHER.equals(dimension) && !hasValidNetherSession(player, control, Instant.now())) {
            evacuatePlayer(player, "kingdoms.nether.session.expired");
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        DimensionControlManager control = DimensionControlManager.get(player.serverLevel().getServer());
        if (Level.NETHER.equals(event.getFrom())) {
            control.invalidateReturnsForPlayer(player.getUUID());
            NetherReturnIntegration.removeForPlayer(player);
        }
        ResourceKey<Level> target = event.getTo();
        if (!DimensionControlManager.isControlled(target)) {
            return;
        }
        persistedData(player).putLong(wipeGenKey(target), control.wipeGeneration(target));
        if (!Level.NETHER.equals(target) || player.hasPermissions(2)) {
            return;
        }
        UUID factionId = FactionManager.get(player.serverLevel().getServer())
                .getFactionIdForMember(player.getUUID()).orElse(null);
        Instant now = Instant.now();
        ActiveSession session = factionId == null ? null : control.activeSession(factionId, now).orElse(null);
        boolean expectedArrival = EXPECTED_NETHER_ARRIVALS.remove(player.getUUID());
        if (!expectedArrival || session == null || !hasValidNetherSession(player, control, now)) {
            evacuatePlayer(player, "kingdoms.nether.session.expired");
            return;
        }
        BlockPos landing = session.landing().blockPos();
        player.teleportTo(
                player.serverLevel(), landing.getX() + 0.5D, landing.getY(), landing.getZ() + 0.5D,
                player.getYRot(), player.getXRot()
        );
        control.issueReturn(session.sessionId(), player.getUUID(), Instant.now())
                .ifPresent(binding -> NetherReturnIntegration.give(player, binding));
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !Level.NETHER.equals(player.level().dimension())) {
            return;
        }
        UUID factionId = FactionManager.get(player.serverLevel().getServer())
                .getFactionIdForMember(player.getUUID()).orElse(null);
        if (factionId != null) {
            DimensionControlManager.get(player.serverLevel().getServer())
                    .markDeath(factionId, player.getUUID(), Instant.now());
        }
        DimensionControlManager.get(player.serverLevel().getServer()).invalidateReturnsForPlayer(player.getUUID());
        NetherReturnIntegration.removeForPlayer(player);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter < 20) {
            return;
        }
        tickCounter = 0;
        MinecraftServer server = event.getServer();
        Instant now = Instant.now();
        DimensionControlManager control = DimensionControlManager.get(server);
        FactionManager factions = FactionManager.get(server);
        List<EndedSession> ended = control.expireSessions(now, id -> factions.getFactionById(id).isPresent());
        for (EndedSession session : ended) {
            endSession(server, session);
        }
        if (!control.isNetherOpenForPlayers(now)) {
            evacuateOrdinaryNetherPlayers(server);
        }
        if (control.updateWipeSchedule(now)) {
            notifyOperators(server, Component.translatable("kingdoms.dimension.wipe_scheduled", "nether"));
        }
        if (control.claimDailyResetNotification(now)) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.sendSystemMessage(Component.translatable("kingdoms.nether.session.daily_reset"));
            }
        }
        updateBossBars(server, control.activeSessions(now), factions, now);
    }

    public static int evacuate(MinecraftServer server, ResourceKey<Level> dimension) {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            return 0;
        }
        List<ServerPlayer> players = List.copyOf(level.players());
        for (ServerPlayer player : players) {
            evacuatePlayer(player, "kingdoms.dimension.evicted");
        }
        return players.size();
    }

    public static void teleportToOverworldSpawn(ServerPlayer player) {
        ServerLevel overworld = player.serverLevel().getServer().overworld();
        BlockPos spawn = safeOverworldReturn(overworld);
        player.teleportTo(
                overworld,
                spawn.getX() + 0.5D,
                spawn.getY(),
                spawn.getZ() + 0.5D,
                player.getYRot(),
                player.getXRot()
        );
    }

    private static BlockPos safeOverworldReturn(ServerLevel level) {
        BlockPos origin = level.getSharedSpawnPos();
        for (int radius = 0; radius <= 16; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    int x = origin.getX() + dx;
                    int z = origin.getZ() + dz;
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    BlockPos feet = new BlockPos(x, y, z);
                    if (safeReturnPosition(level, feet)) {
                        return feet;
                    }
                }
            }
        }
        return origin;
    }

    private static boolean safeReturnPosition(ServerLevel level, BlockPos feet) {
        BlockPos floorPos = feet.below();
        var floor = level.getBlockState(floorPos);
        return level.getWorldBorder().isWithinBounds(feet)
                && level.getBlockState(feet).isAir()
                && level.getBlockState(feet.above()).isAir()
                && floor.isFaceSturdy(level, floorPos, net.minecraft.core.Direction.UP)
                && !floor.is(Blocks.MAGMA_BLOCK)
                && floor.getFluidState().isEmpty();
    }

    private static void deny(EntityTravelToDimensionEvent event, ServerPlayer player, Component message) {
        player.displayClientMessage(message, true);
        event.setCanceled(true);
    }

    private static Component entryMessage(EntryResult result, Instant now) {
        return switch (result.status()) {
            case SCHEDULE_CLOSED -> Component.translatable("kingdoms.nether.session.closed");
            case TOO_LATE_TO_START -> Component.translatable("kingdoms.nether.session.too_late");
            case NO_SESSIONS_LEFT -> Component.translatable("kingdoms.nether.session.limit");
            case DEATH_LOCKED -> Component.translatable(
                    "kingdoms.nether.session.death_locked",
                    result.session() == null ? "00:00" : formatDuration(
                            Math.max(0L, Duration.between(now, result.session().endsAt()).getSeconds())
                    )
            );
            case NO_SAFE_LANDING -> Component.translatable("kingdoms.nether.session.no_landing");
            default -> Component.translatable("kingdoms.nether.session.denied");
        };
    }

    private static boolean hasValidNetherSession(
            ServerPlayer player,
            DimensionControlManager control,
            Instant now
    ) {
        if (!control.isNetherOpenForPlayers(now)) {
            return false;
        }
        UUID factionId = FactionManager.get(player.serverLevel().getServer())
                .getFactionIdForMember(player.getUUID()).orElse(null);
        if (factionId == null) {
            return false;
        }
        ActiveSession active = control.activeSession(factionId, now).orElse(null);
        return active != null
                && active.joinedPlayers().contains(player.getUUID())
                && !control.isDeathLocked(factionId, player.getUUID(), now);
    }

    private static void endSession(MinecraftServer server, EndedSession session) {
        for (UUID playerId : session.joinedPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            NetherReturnIntegration.removeForSession(player, session.sessionId());
            if (Level.NETHER.equals(player.level().dimension()) && !player.hasPermissions(2)) {
                evacuatePlayer(player, "kingdoms.nether.session.expired");
            }
        }
        ServerBossEvent bar = SESSION_BARS.remove(session.factionId());
        if (bar != null) {
            bar.removeAllPlayers();
        }
    }

    private static void evacuateOrdinaryNetherPlayers(MinecraftServer server) {
        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether == null) {
            return;
        }
        for (ServerPlayer player : List.copyOf(nether.players())) {
            if (!player.hasPermissions(2)) {
                evacuatePlayer(player, "kingdoms.nether.session.closed");
            }
        }
    }

    private static void evacuatePlayer(ServerPlayer player, String key) {
        DimensionControlManager.get(player.serverLevel().getServer()).invalidateReturnsForPlayer(player.getUUID());
        NetherReturnIntegration.removeForPlayer(player);
        teleportToOverworldSpawn(player);
        player.displayClientMessage(Component.translatable(key), false);
    }

    private static void updateBossBars(
            MinecraftServer server,
            List<ActiveSession> activeSessions,
            FactionManager factions,
            Instant now
    ) {
        Set<UUID> activeFactions = new HashSet<>();
        for (ActiveSession session : activeSessions) {
            Faction faction = factions.getFactionById(session.factionId()).orElse(null);
            if (faction == null) {
                continue;
            }
            activeFactions.add(session.factionId());
            ServerBossEvent bar = SESSION_BARS.computeIfAbsent(
                    session.factionId(),
                    ignored -> new ServerBossEvent(
                            Component.empty(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS
                    )
            );
            long totalMillis = Math.max(1L, Duration.between(session.startedAt(), session.endsAt()).toMillis());
            long remainingMillis = Math.max(0L, Duration.between(now, session.endsAt()).toMillis());
            bar.setProgress(Math.clamp((float) remainingMillis / totalMillis, 0.0F, 1.0F));
            bar.setName(Component.translatable(
                    "kingdoms.nether.session.bossbar",
                    formatDuration(remainingMillis / 1000L),
                    DimensionControlManager.get(server).remainingSessions(session.factionId(), now),
                    DimensionControlManager.get(server).rules().sessionsPerDay(),
                    formatDuration(NetherSchedulePolicy.secondsUntilClose(now))
            ));
            syncBossBarPlayers(server, faction, bar);
        }
        for (UUID factionId : List.copyOf(SESSION_BARS.keySet())) {
            if (!activeFactions.contains(factionId)) {
                ServerBossEvent removed = SESSION_BARS.remove(factionId);
                if (removed != null) {
                    removed.removeAllPlayers();
                }
            }
        }
    }

    private static void syncBossBarPlayers(MinecraftServer server, Faction faction, ServerBossEvent bar) {
        List<ServerPlayer> desired = faction.members().keySet().stream()
                .map(server.getPlayerList()::getPlayer)
                .filter(java.util.Objects::nonNull)
                .toList();
        for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
            if (!desired.contains(player)) {
                bar.removePlayer(player);
            }
        }
        for (ServerPlayer player : desired) {
            if (!bar.getPlayers().contains(player)) {
                bar.addPlayer(player);
            }
        }
    }

    private static void notifyFaction(MinecraftServer server, UUID factionId, Component message) {
        FactionManager.get(server).getFactionById(factionId).ifPresent(faction -> faction.members().keySet().forEach(id -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                player.sendSystemMessage(message);
            }
        }));
    }

    private static void notifyOperators(MinecraftServer server, Component message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.hasPermissions(2)) {
                player.sendSystemMessage(message);
            }
        }
    }

    private static void clearBossBars() {
        for (ServerBossEvent bar : SESSION_BARS.values()) {
            bar.removeAllPlayers();
        }
        SESSION_BARS.clear();
    }

    private static String formatDuration(long seconds) {
        long safe = Math.max(0L, seconds);
        return String.format(java.util.Locale.ROOT, "%02d:%02d", safe / 60L, safe % 60L);
    }

    private static void cleanupModDataFor(MinecraftServer server, ResourceKey<Level> dimension) {
        String dimensionId = dimension.location().toString();
        RogueOutpostManager rogueOutposts = RogueOutpostManager.get(server);
        for (RogueOutpostManager.RogueOutpost outpost : rogueOutposts.all()) {
            if (outpost.dimension().equals(dimensionId)) {
                rogueOutposts.remove(outpost.id());
            }
        }
        MarketPlotManager plots = MarketPlotManager.get(server);
        for (MarketPlot plot : plots.all()) {
            if (plot.dimension().equals(dimension)) {
                plots.remove(plot.id());
            }
        }
        TraderWorldData traderData = TraderWorldData.get(server);
        for (TraderWorldData.SpawnPoint point : traderData.points()) {
            if (point.dimension().equals(dimension)) {
                traderData.removePoint(point.id());
            }
        }
        traderData.contraband().filter(event -> event.dimension().equals(dimension))
                .ifPresent(event -> traderData.clearContraband(0L));
        for (TraderWorldData.WanderingEvent wandering : traderData.wanderingEvents()) {
            if (wandering.claim().dimension().equals(dimension)) {
                traderData.removeWandering(wandering.factionId());
            }
        }
    }

    private static long persistedWipeGen(ServerPlayer player, ResourceKey<Level> dimension) {
        return persistedData(player).getLong(wipeGenKey(dimension));
    }

    private static CompoundTag persistedData(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static String wipeGenKey(ResourceKey<Level> dimension) {
        return WIPE_GEN_KEY_PREFIX + dimension.location().getPath();
    }

    private DimensionControlEvents() {
    }
}
