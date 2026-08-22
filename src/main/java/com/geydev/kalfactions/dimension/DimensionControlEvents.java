package com.geydev.kalfactions.dimension;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.dimension.DimensionControlManager.ActiveSession;
import com.geydev.kalfactions.dimension.DimensionControlManager.EndedSession;
import com.geydev.kalfactions.dimension.DimensionControlManager.EntryResult;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.market.MarketPlot;
import com.geydev.kalfactions.market.MarketPlotManager;
import com.geydev.kalfactions.outpost.RogueOutpostManager;
import com.geydev.kalfactions.outpost.trader.TraderWorldData;
import com.geydev.kalfactions.registry.ModBlocks;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.common.ItemAbilities;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class DimensionControlEvents {
    private static final String WIPE_GEN_KEY_PREFIX = "kingdoms_wipe_gen_";
    private static final Map<UUID, ServerBossEvent> SESSION_BARS = new HashMap<>();
    private static final Set<UUID> AUTHORIZED_TRANSFERS = new HashSet<>();
    private static final Set<UUID> EXPECTED_NETHER_ARRIVALS = new HashSet<>();
    private static final Map<UUID, Long> ENTRY_GUARD_UNTIL = new HashMap<>();
    private static final Map<UUID, BlockPos> PENDING_RETURN_POSITIONS = new HashMap<>();
    private static final Map<UUID, PendingNetherEntry> PENDING_NETHER_ENTRIES = new HashMap<>();
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
        ENTRY_GUARD_UNTIL.clear();
        PENDING_RETURN_POSITIONS.clear();
        PENDING_NETHER_ENTRIES.clear();
        NetherPortalIgnition.clear();
        NetherEntryConfirmations.clear();
        DimensionNetwork.clear();
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
        if (Level.END.equals(target)) {
            if (!player.hasPermissions(2) && control.isClosed(target)) {
                deny(event, player, Component.translatable("kingdoms.dimension.closed_notice"));
            }
            return;
        }
        event.setCanceled(true);
        if (PENDING_NETHER_ENTRIES.containsKey(player.getUUID())) {
            return;
        }
        long serverTick = server.getTickCount();
        Long guardedUntil = ENTRY_GUARD_UNTIL.get(player.getUUID());
        if (guardedUntil != null && guardedUntil > serverTick) {
            return;
        }
        ENTRY_GUARD_UNTIL.put(player.getUUID(), serverTick + 60L);
        BlockPos portalPosition = player.portalProcess == null
                ? player.blockPosition().immutable()
                : player.portalProcess.getEntryPosition().immutable();
        boolean portalTravel = Level.OVERWORLD.equals(player.level().dimension())
                && player.level().getBlockState(portalPosition).is(Blocks.NETHER_PORTAL);
        if (!portalTravel) {
            deny(event, player, Component.translatable("kingdoms.nether.portal.unregistered"));
            return;
        }
        if (!control.isInsideRegisteredPortal(portalPosition)) {
            deny(event, player, Component.translatable("kingdoms.nether.portal.unregistered"));
            return;
        }
        if (!control.isNetherPortalCharged(Instant.now())) {
            deny(event, player, Component.translatable("kingdoms.nether.portal.not_ignited"));
            return;
        }
        boolean operator = player.hasPermissions(2);
        FactionManager factions = FactionManager.get(server);
        UUID factionId = factions.getFactionIdForMember(player.getUUID()).orElse(null);
        if (!operator && factionId == null) {
            deny(event, player, Component.translatable("kingdoms.nether.session.faction_required"));
            return;
        }
        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether == null) {
            deny(event, player, Component.translatable("kingdoms.nether.session.no_landing"));
            return;
        }
        if (!NetherReturnIntegration.hasFreeInventorySlot(player)) {
            deny(event, player, Component.translatable("kingdoms.nether.return.inventory_full"));
            return;
        }
        Instant entryTime = Instant.now();
        UUID confirmationOwner = factionId == null ? player.getUUID() : factionId;
        boolean hadSecondConfirmation = !operator && NetherEntryConfirmations.isPending(
                player.getUUID(), confirmationOwner, serverTick
        );
        boolean secondSessionConfirmed = !operator && NetherEntryConfirmations.consume(
                player.getUUID(), confirmationOwner, serverTick, player.isShiftKeyDown()
        );
        EntryResult preview = control.previewNetherEntry(
                factionId,
                player.getUUID(),
                entryTime,
                operator,
                secondSessionConfirmed
        );
        if (preview.status() == DimensionControlManager.EntryStatus.SECOND_SESSION_CONFIRMATION_REQUIRED) {
            if (!hadSecondConfirmation) {
                NetherEntryConfirmations.request(player.getUUID(), confirmationOwner, serverTick);
                player.sendSystemMessage(Component.translatable("kingdoms.nether.session.second_confirm"));
            }
            player.displayClientMessage(
                    Component.translatable("kingdoms.nether.session.second_confirm_action"), true
            );
            return;
        }
        if (!preview.allowed()) {
            deny(event, player, entryMessage(preview, Instant.now()));
            return;
        }
        NetherEntryConfirmations.remove(player.getUUID());
        boolean factionBacked = factionId != null
                && preview.status() != DimensionControlManager.EntryStatus.OPERATOR_BYPASS;
        UUID sessionOwner = factionBacked ? factionId : player.getUUID();
        DimensionControlManager.LandingPos reusableLanding = factionBacked && preview.session() == null
                ? control.reusableLanding(factionId, entryTime).orElse(null)
                : null;
        PendingNetherEntry pending = new PendingNetherEntry(
                player.getUUID(), sessionOwner, factionId, operator, factionBacked, secondSessionConfirmed,
                portalPosition, portalPosition, preview, reusableLanding
        );
        PENDING_NETHER_ENTRIES.put(player.getUUID(), pending);
        player.displayClientMessage(Component.translatable("kingdoms.nether.session.preparing"), true);
        prepareNetherLanding(server, nether, pending);
    }

    private static void prepareNetherLanding(
            MinecraftServer server,
            ServerLevel nether,
            PendingNetherEntry pending
    ) {
        ActiveSession previewSession = pending.preview().session();
        if (previewSession != null) {
            loadExistingLanding(server, nether, pending, previewSession.landing().blockPos(), null);
            return;
        }
        if (pending.reusableLanding() != null) {
            loadReusableLanding(server, nether, pending, pending.reusableLanding().blockPos());
            return;
        }
        DimensionControlManager control = DimensionControlManager.get(server);
        Instant now = Instant.now();
        List<DimensionControlManager.LandingPos> occupied = control.activeSessions(now).stream()
                .map(ActiveSession::landing)
                .toList();
        DimensionControlManager.LandingPos previous = pending.factionBacked()
                ? control.lastLanding(pending.ownerId()).orElse(null)
                : null;
        List<BlockPos> candidates = NetherLandingFinder.candidateCenters(
                nether, occupied, previous, control.rules()
        );
        tryLandingCandidate(server, nether, pending, candidates, 0);
    }

    private static void loadReusableLanding(
            MinecraftServer server,
            ServerLevel nether,
            PendingNetherEntry pending,
            BlockPos stored
    ) {
        loadChunks(server, nether, pending, stored, 1, loaded -> {
            if (!loaded || !isCurrentPending(pending)) {
                if (!loaded) {
                    failPending(server, pending, Component.translatable("kingdoms.nether.session.no_landing"));
                }
                return;
            }
            Optional<DimensionControlManager.LandingPos> safe = NetherLandingFinder.isSafe(nether, stored)
                    ? Optional.of(pending.reusableLanding())
                    : NetherLandingFinder.findNear(nether, stored);
            if (safe.isPresent()) {
                DimensionControlManager.LandingPos landing = safe.get();
                if (!landing.equals(pending.reusableLanding())) {
                    DimensionControlManager.get(server).updateReusableLanding(
                            pending.ownerId(), Instant.now(), landing
                    );
                }
                authorizePreparedEntry(server, nether, pending, landing.blockPos());
                return;
            }
            DimensionControlManager control = DimensionControlManager.get(server);
            List<DimensionControlManager.LandingPos> occupied = control.activeSessions(Instant.now()).stream()
                    .map(ActiveSession::landing)
                    .toList();
            List<BlockPos> candidates = NetherLandingFinder.candidateCenters(
                    nether, occupied, pending.reusableLanding(), control.rules()
            );
            tryLandingCandidate(server, nether, pending, candidates, 0);
        });
    }

    private static void tryLandingCandidate(
            MinecraftServer server,
            ServerLevel nether,
            PendingNetherEntry pending,
            List<BlockPos> candidates,
            int index
    ) {
        if (!isCurrentPending(pending)) {
            return;
        }
        if (index >= candidates.size()) {
            failPending(server, pending, Component.translatable("kingdoms.nether.session.no_landing"));
            return;
        }
        BlockPos center = candidates.get(index);
        loadChunks(server, nether, pending, center, 0, loaded -> {
            if (!loaded || !isCurrentPending(pending)) {
                if (!loaded) {
                    tryLandingCandidate(server, nether, pending, candidates, index + 1);
                }
                return;
            }
            Optional<DimensionControlManager.LandingPos> landing = NetherLandingFinder.findInLoadedChunk(nether, center);
            if (landing.isEmpty()) {
                tryLandingCandidate(server, nether, pending, candidates, index + 1);
                return;
            }
            ActiveSession previewSession = pending.preview().session();
            if (previewSession != null) {
                DimensionControlManager.get(server).updateSessionLanding(previewSession.sessionId(), landing.get());
            } else if (pending.reusableLanding() != null) {
                DimensionControlManager.get(server).updateReusableLanding(
                        pending.ownerId(), Instant.now(), landing.get()
                );
            }
            authorizePreparedEntry(server, nether, pending, landing.get().blockPos());
        });
    }

    private static void loadExistingLanding(
            MinecraftServer server,
            ServerLevel nether,
            PendingNetherEntry pending,
            BlockPos stored,
            AuthorizedNetherEntry authorized
    ) {
        loadChunks(server, nether, pending, stored, 1, loaded -> {
            if (!loaded || !isCurrentPending(pending)) {
                if (!loaded) {
                    failAuthorizedOrPending(server, pending, authorized);
                }
                return;
            }
            DimensionControlManager control = DimensionControlManager.get(server);
            ActiveSession session = authorized == null ? pending.preview().session() : authorized.result().session();
            BlockPos landing = session == null ? null : resolveLanding(nether, control, session);
            if (landing != null) {
                if (authorized == null) {
                    authorizePreparedEntry(server, nether, pending, landing);
                } else {
                    teleportAuthorizedEntry(server, nether, pending, authorized, landing);
                }
                return;
            }
            if (authorized != null) {
                failAuthorizedOrPending(server, pending, authorized);
                return;
            }
            Instant now = Instant.now();
            List<DimensionControlManager.LandingPos> occupied = control.activeSessions(now).stream()
                    .filter(candidate -> !candidate.sessionId().equals(session.sessionId()))
                    .map(ActiveSession::landing)
                    .toList();
            List<BlockPos> candidates = NetherLandingFinder.candidateCenters(
                    nether, occupied, session.landing(), control.rules()
            );
            tryLandingCandidate(server, nether, pending, candidates, 0);
        });
    }

    private static void authorizePreparedEntry(
            MinecraftServer server,
            ServerLevel nether,
            PendingNetherEntry pending,
            BlockPos preparedLanding
    ) {
        ServerPlayer player = validPendingPlayer(server, pending);
        if (player == null) {
            PENDING_NETHER_ENTRIES.remove(pending.playerId(), pending);
            return;
        }
        DimensionControlManager control = DimensionControlManager.get(server);
        Instant now = Instant.now();
        EntryResult result = control.authorizeNetherEntry(
                pending.factionId(),
                player.getUUID(),
                now,
                pending.operator(),
                pending.secondSessionConfirmed(),
                (occupied, previous, rules) -> Optional.of(new DimensionControlManager.LandingPos(
                        preparedLanding.getX(), preparedLanding.getY(), preparedLanding.getZ()
                ))
        );
        if (!result.allowed() || result.session() == null) {
            failPending(server, pending, entryMessage(result, now));
            return;
        }
        boolean alreadyJoined = pending.preview().session() != null
                && pending.preview().session().sessionId().equals(result.session().sessionId())
                && pending.preview().session().joinedPlayers().contains(player.getUUID());
        boolean startedSession = result.status() == DimensionControlManager.EntryStatus.STARTED_SESSION
                || result.status() == DimensionControlManager.EntryStatus.OPERATOR_BYPASS
                && pending.preview().session() == null;
        AuthorizedNetherEntry authorized = new AuthorizedNetherEntry(result, startedSession, !alreadyJoined);
        BlockPos actualLanding = result.session().landing().blockPos();
        if (!actualLanding.equals(preparedLanding)) {
            loadExistingLanding(server, nether, pending, actualLanding, authorized);
            return;
        }
        teleportAuthorizedEntry(server, nether, pending, authorized, preparedLanding);
    }

    private static void teleportAuthorizedEntry(
            MinecraftServer server,
            ServerLevel nether,
            PendingNetherEntry pending,
            AuthorizedNetherEntry authorized,
            BlockPos landing
    ) {
        ServerPlayer player = validPendingPlayer(server, pending);
        if (player == null
                || !DimensionControlManager.get(server).isNetherPortalCharged(Instant.now())
                || (!pending.operator()
                        && !DimensionControlManager.get(server).isNetherOpenForPlayers(Instant.now()))) {
            failAuthorizedOrPending(server, pending, authorized);
            return;
        }
        if (!NetherReturnIntegration.prepareCentralSlot(player)) {
            rollbackAuthorized(server, pending, authorized);
            failPending(server, pending, Component.translatable("kingdoms.nether.return.inventory_full"));
            return;
        }
        PENDING_NETHER_ENTRIES.remove(player.getUUID(), pending);
        PENDING_RETURN_POSITIONS.put(player.getUUID(), pending.returnPosition());
        AUTHORIZED_TRANSFERS.add(player.getUUID());
        EXPECTED_NETHER_ARRIVALS.add(player.getUUID());
        boolean moved;
        try {
            player.teleportTo(
                    nether, landing.getX() + 0.5D, landing.getY(), landing.getZ() + 0.5D,
                    player.getYRot(), player.getXRot()
            );
            moved = player.level() == nether && player.blockPosition().closerThan(landing, 4.0D);
        } finally {
            AUTHORIZED_TRANSFERS.remove(player.getUUID());
            EXPECTED_NETHER_ARRIVALS.remove(player.getUUID());
            PENDING_RETURN_POSITIONS.remove(player.getUUID());
        }
        if (!moved) {
            rollbackAuthorized(server, pending, authorized);
            player.displayClientMessage(Component.translatable("kingdoms.nether.session.no_landing"), true);
            return;
        }
        if (authorized.result().status() == DimensionControlManager.EntryStatus.STARTED_SESSION
                && authorized.result().session().ordinal() > 0) {
            notifyFaction(server, pending.ownerId(), Component.translatable(
                    "kingdoms.nether.session.started", authorized.result().remainingSessions()
            ));
        }
    }

    private static void loadChunks(
            MinecraftServer server,
            ServerLevel level,
            PendingNetherEntry pending,
            BlockPos center,
            int chunkRadius,
            Consumer<Boolean> completion
    ) {
        ChunkPos origin = new ChunkPos(center);
        List<ChunkPos> chunks = new ArrayList<>();
        List<CompletableFuture<ChunkResult<ChunkAccess>>> futures = new ArrayList<>();
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                ChunkPos chunk = new ChunkPos(origin.x + dx, origin.z + dz);
                chunks.add(chunk);
                level.getChunkSource().addRegionTicket(TicketType.PORTAL, chunk, 3, pending.portalPosition());
                futures.add(level.getChunkSource().getChunkFuture(chunk.x, chunk.z, ChunkStatus.FULL, true));
            }
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).whenComplete((ignored, error) ->
                server.execute(() -> {
                    boolean loaded = error == null && futures.stream().allMatch(future -> {
                        try {
                            return future.join().isSuccess();
                        } catch (RuntimeException exception) {
                            return false;
                        }
                    });
                    for (ChunkPos chunk : chunks) {
                        level.getChunkSource().removeRegionTicket(
                                TicketType.PORTAL, chunk, 3, pending.portalPosition()
                        );
                    }
                    completion.accept(loaded);
                })
        );
    }

    private static ServerPlayer validPendingPlayer(MinecraftServer server, PendingNetherEntry pending) {
        if (!isCurrentPending(pending)) {
            return null;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(pending.playerId());
        if (player == null || !Level.OVERWORLD.equals(player.level().dimension())
                || !player.blockPosition().closerThan(pending.portalPosition(), 4.0D)
                || !DimensionControlManager.get(server).isInsideRegisteredPortal(pending.portalPosition())) {
            return null;
        }
        if (pending.operator()) {
            return player.hasPermissions(2) ? player : null;
        }
        UUID currentFaction = FactionManager.get(server).getFactionIdForMember(player.getUUID()).orElse(null);
        return pending.ownerId().equals(currentFaction) ? player : null;
    }

    private static boolean isCurrentPending(PendingNetherEntry pending) {
        return PENDING_NETHER_ENTRIES.get(pending.playerId()) == pending;
    }

    private static void failAuthorizedOrPending(
            MinecraftServer server,
            PendingNetherEntry pending,
            AuthorizedNetherEntry authorized
    ) {
        if (authorized != null) {
            rollbackAuthorized(server, pending, authorized);
        }
        failPending(server, pending, Component.translatable("kingdoms.nether.session.no_landing"));
    }

    private static void rollbackAuthorized(
            MinecraftServer server,
            PendingNetherEntry pending,
            AuthorizedNetherEntry authorized
    ) {
        DimensionControlManager.get(server).rollbackNetherEntry(
                pending.ownerId(),
                pending.playerId(),
                authorized.result().session().sessionId(),
                authorized.startedSession(),
                authorized.joinedNow()
        );
    }

    private static void failPending(MinecraftServer server, PendingNetherEntry pending, Component message) {
        if (!PENDING_NETHER_ENTRIES.remove(pending.playerId(), pending)) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(pending.playerId());
        if (player != null) {
            player.displayClientMessage(message, true);
        }
    }

    @SubscribeEvent
    public static void onPortalIgnition(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)
                || !player.getItemInHand(event.getHand()).canPerformAction(ItemAbilities.FIRESTARTER_LIGHT)) {
            return;
        }
        Direction face = event.getFace();
        if (face == null) {
            return;
        }
        BlockPos firePosition = event.getPos().relative(face).immutable();
        if (PortalShape.findEmptyPortalShape(level, firePosition, Direction.Axis.X).isEmpty()) {
            return;
        }
        event.setCancellationResult(InteractionResult.FAIL);
        event.setCanceled(true);
        player.displayClientMessage(Component.translatable("kingdoms.nether.portal.igniter_required"), true);
    }

    @SubscribeEvent
    public static void onPortalSpawn(BlockEvent.PortalSpawnEvent event) {
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onAnchorBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)
                || player.hasPermissions(2)
                || !event.getState().is(ModBlocks.NETHER_PORTAL_ANCHOR.get())) {
            return;
        }
        event.setCanceled(true);
        player.displayClientMessage(
                Component.translatable("kingdoms.nether.portal.anchor.operator_only"), true
        );
    }

    @SubscribeEvent
    public static void onAnchorPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.hasPermissions(2)
                || !event.getPlacedBlock().is(ModBlocks.NETHER_PORTAL_ANCHOR.get())) {
            return;
        }
        event.setCanceled(true);
        player.displayClientMessage(
                Component.translatable("kingdoms.nether.portal.anchor.operator_only"), true
        );
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
        if (Level.END.equals(dimension) && !player.hasPermissions(2) && control.isClosed(dimension)) {
            evacuatePlayer(player, "kingdoms.dimension.evicted");
            return;
        }
        if (Level.NETHER.equals(dimension) && !hasValidNetherSession(player, control, Instant.now())) {
            evacuatePlayer(player, "kingdoms.nether.session.expired");
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId = event.getEntity().getUUID();
        ENTRY_GUARD_UNTIL.remove(playerId);
        AUTHORIZED_TRANSFERS.remove(playerId);
        EXPECTED_NETHER_ARRIVALS.remove(playerId);
        PENDING_RETURN_POSITIONS.remove(playerId);
        PENDING_NETHER_ENTRIES.remove(playerId);
        NetherPortalIgnition.removePlayer(playerId);
        removePlayerFromBossBars(event.getEntity().getUUID());
        NetherEntryConfirmations.remove(playerId);
        DimensionNetwork.removePlayer(playerId);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        DimensionControlManager control = DimensionControlManager.get(player.serverLevel().getServer());
        if (Level.NETHER.equals(event.getFrom())) {
            control.leaveNether(player.getUUID());
            NetherReturnIntegration.removeForPlayer(player);
            removePlayerFromBossBars(player.getUUID());
        }
        ResourceKey<Level> target = event.getTo();
        if (!DimensionControlManager.isControlled(target)) {
            return;
        }
        persistedData(player).putLong(wipeGenKey(target), control.wipeGeneration(target));
        if (!Level.NETHER.equals(target)) {
            return;
        }
        Instant now = Instant.now();
        ActiveSession session = control.assignedSession(player.getUUID(), now).orElse(null);
        boolean expectedArrival = EXPECTED_NETHER_ARRIVALS.remove(player.getUUID());
        BlockPos returnPos = PENDING_RETURN_POSITIONS.remove(player.getUUID());
        if (!expectedArrival || session == null || !hasValidNetherSession(player, control, now)) {
            evacuatePlayer(player, "kingdoms.nether.session.expired");
            return;
        }
        BlockPos landing = session.landing().blockPos();
        player.teleportTo(
                player.serverLevel(), landing.getX() + 0.5D, landing.getY(), landing.getZ() + 0.5D,
                player.getYRot(), player.getXRot()
        );
        control.issueReturn(
                session.sessionId(),
                player.getUUID(),
                returnPos == null ? BlockPos.ZERO : returnPos,
                Instant.now()
        )
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
            player.sendSystemMessage(Component.translatable("kingdoms.nether.session.death_notice"));
        }
        DimensionControlManager.get(player.serverLevel().getServer()).invalidateReturnsForPlayer(player.getUUID());
        NetherReturnIntegration.removeForPlayer(player);
        removePlayerFromBossBars(player.getUUID());
    }

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        ReturnBinding binding = NetherReturnIntegration.binding(event.getEntity().getItem())
                .filter(candidate -> candidate.playerId().equals(player.getUUID()))
                .orElse(null);
        if (binding == null) {
            return;
        }
        event.setCanceled(true);
        event.getEntity().setItem(net.minecraft.world.item.ItemStack.EMPTY);
        NetherReturnIntegration.ensureInInventory(player, binding);
        player.displayClientMessage(Component.translatable("kingdoms.nether.return.cannot_drop"), true);
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
        control.applyScheduledNetherOpening(now);
        NetherPortalIgnition.tick(server, now);
        validateRegisteredPortal(server, control);
        FactionManager factions = FactionManager.get(server);
        List<EndedSession> ended = control.expireSessions(now, id -> factions.getFactionById(id).isPresent());
        for (EndedSession session : ended) {
            endSession(server, session);
        }
        if (!control.isNetherOpenForPlayers(now)) {
            evacuateOrdinaryNetherPlayers(server);
        } else {
            ServerLevel nether = server.getLevel(Level.NETHER);
            if (nether != null) {
                for (ServerPlayer player : List.copyOf(nether.players())) {
                    if (!hasValidNetherSession(player, control, now)) {
                        evacuatePlayer(player, "kingdoms.nether.session.expired");
                    }
                }
            }
        }
        List<ActiveSession> activeSessions = control.activeSessions(now);
        updateBossBars(server, activeSessions, now);
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

    public static int evacuateNether(MinecraftServer server, String noticeKey) {
        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether == null) {
            return 0;
        }
        List<ServerPlayer> players = List.copyOf(nether.players());
        for (ServerPlayer player : players) {
            evacuatePlayer(player, noticeKey);
        }
        return players.size();
    }

    public static int evacuateForClosure(MinecraftServer server, ResourceKey<Level> dimension) {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            return 0;
        }
        int moved = 0;
        for (ServerPlayer player : List.copyOf(level.players())) {
            if (Level.NETHER.equals(dimension) && player.hasPermissions(2)) {
                continue;
            }
            evacuatePlayer(player, "kingdoms.dimension.evicted");
            moved++;
        }
        return moved;
    }

    public static void broadcastOpened(MinecraftServer server, ResourceKey<Level> dimension) {
        Component message;
        if (Level.NETHER.equals(dimension)) {
            message = DimensionControlManager.get(server).isNetherOpenForPlayers(Instant.now())
                    ? Component.translatable("kingdoms.nether.access.opened")
                    : Component.translatable("kingdoms.nether.access.enabled");
        } else {
            message = Component.translatable(
                    "kingdoms.dimension.notice.opened",
                    Component.translatable("kingdoms.dimension.name.end")
            );
        }
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    public static void teleportToOverworldSpawn(ServerPlayer player) {
        ServerLevel overworld = player.serverLevel().getServer().overworld();
        BlockPos spawn = safeOverworldReturn(overworld);
        teleportToOverworld(player, overworld, spawn);
    }

    public static void teleportToOverworldReturn(ServerPlayer player, BlockPos preferred) {
        ServerLevel overworld = player.serverLevel().getServer().overworld();
        BlockPos destination = preferred.equals(BlockPos.ZERO)
                ? safeOverworldReturn(overworld)
                : safeOverworldReturn(overworld, preferred);
        teleportToOverworld(player, overworld, destination);
    }

    private static void teleportToOverworld(ServerPlayer player, ServerLevel overworld, BlockPos destination) {
        player.teleportTo(
                overworld,
                destination.getX() + 0.5D,
                destination.getY(),
                destination.getZ() + 0.5D,
                player.getYRot(),
                player.getXRot()
        );
    }

    private static BlockPos safeOverworldReturn(ServerLevel level) {
        BlockPos origin = DimensionControlManager.get(level.getServer()).netherPortal()
                .map(bounds -> new BlockPos(
                        (bounds.minX() + bounds.maxX()) / 2,
                        bounds.minY(),
                        (bounds.minZ() + bounds.maxZ()) / 2
                ))
                .orElse(level.getSharedSpawnPos());
        return safeOverworldReturn(level, origin);
    }

    private static BlockPos safeOverworldReturn(ServerLevel level, BlockPos origin) {
        for (int radius = 0; radius <= 8; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos feet = origin.offset(dx, dy, dz);
                        if (safeReturnPosition(level, feet)) {
                            return feet;
                        }
                    }
                }
            }
        }
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
            case FACTION_REQUIRED -> Component.translatable("kingdoms.nether.session.faction_required");
            case SCHEDULE_CLOSED -> Component.translatable("kingdoms.nether.session.closed");
            case NO_SESSIONS_LEFT -> Component.translatable("kingdoms.nether.session.limit");
            case DEATH_LOCKED -> Component.translatable(
                    "kingdoms.nether.session.death_locked",
                    result.session() == null ? "00:00:00" : formatClock(
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
        ActiveSession active = control.assignedSession(player.getUUID(), now).orElse(null);
        if (!control.isNetherPortalCharged(now)) {
            return false;
        }
        if (player.hasPermissions(2)) {
            return active != null && active.joinedPlayers().contains(player.getUUID());
        }
        if (!control.isNetherOpenForPlayers(now)) {
            return false;
        }
        UUID factionId = FactionManager.get(player.serverLevel().getServer())
                .getFactionIdForMember(player.getUUID()).orElse(null);
        if (factionId == null) {
            return false;
        }
        return active != null
                && active.factionId().equals(factionId)
                && active.joinedPlayers().contains(player.getUUID())
                && !control.isDeathLocked(factionId, player.getUUID(), now);
    }

    private static BlockPos resolveLanding(
            ServerLevel nether,
            DimensionControlManager control,
            ActiveSession session
    ) {
        BlockPos stored = session.landing().blockPos();
        if (NetherLandingFinder.isSafe(nether, stored)) {
            return stored;
        }
        var repaired = NetherLandingFinder.findNear(nether, stored);
        if (repaired.isPresent()) {
            control.updateSessionLanding(session.sessionId(), repaired.get());
            return repaired.get().blockPos();
        }
        return null;
    }

    private static void endSession(MinecraftServer server, EndedSession session) {
        for (UUID playerId : session.joinedPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            NetherReturnIntegration.removeForSession(player, session.sessionId());
            if (Level.NETHER.equals(player.level().dimension())) {
                evacuatePlayer(player, "kingdoms.nether.session.expired");
            }
        }
        ServerBossEvent bar = SESSION_BARS.remove(session.sessionId());
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
        removePlayerFromBossBars(player.getUUID());
        teleportToOverworldSpawn(player);
        player.displayClientMessage(Component.translatable(key), false);
    }

    private static void updateBossBars(
            MinecraftServer server,
            List<ActiveSession> activeSessions,
            Instant now
    ) {
        Set<UUID> activeSessionIds = new HashSet<>();
        for (ActiveSession session : activeSessions) {
            activeSessionIds.add(session.sessionId());
            ServerBossEvent bar = SESSION_BARS.computeIfAbsent(
                    session.sessionId(),
                    ignored -> new ServerBossEvent(
                            Component.empty(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS
                    )
            );
            long totalMillis = Math.max(1L, Duration.between(session.startedAt(), session.endsAt()).toMillis());
            long remainingMillis = Math.max(0L, Duration.between(now, session.endsAt()).toMillis());
            bar.setProgress(Math.clamp((float) remainingMillis / totalMillis, 0.0F, 1.0F));
            bar.setName(Component.translatable(
                    "kingdoms.nether.session.bossbar",
                    formatClock(remainingMillis / 1000L)
            ));
            long remainingSeconds = remainingMillis / 1000L;
            bar.setColor(remainingSeconds <= 60L
                    ? BossEvent.BossBarColor.RED
                    : remainingSeconds <= 300L ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.PURPLE);
            syncBossBarPlayers(server, session, bar, now);
        }
        for (UUID sessionId : List.copyOf(SESSION_BARS.keySet())) {
            if (!activeSessionIds.contains(sessionId)) {
                ServerBossEvent removed = SESSION_BARS.remove(sessionId);
                if (removed != null) {
                    removed.removeAllPlayers();
                }
            }
        }
    }

    private static void syncBossBarPlayers(
            MinecraftServer server,
            ActiveSession session,
            ServerBossEvent bar,
            Instant now
    ) {
        List<ServerPlayer> desired = server.getPlayerList().getPlayers().stream()
                .filter(player -> Level.NETHER.equals(player.level().dimension()))
                .filter(player -> DimensionControlManager.get(server).assignedSession(player.getUUID(), now)
                        .map(assigned -> assigned.sessionId().equals(session.sessionId()))
                        .orElse(false))
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

    private static void removePlayerFromBossBars(UUID playerId) {
        for (ServerBossEvent bar : SESSION_BARS.values()) {
            for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
                if (player.getUUID().equals(playerId)) {
                    bar.removePlayer(player);
                }
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

    private static void clearBossBars() {
        for (ServerBossEvent bar : SESSION_BARS.values()) {
            bar.removeAllPlayers();
        }
        SESSION_BARS.clear();
    }

    private static String formatClock(long seconds) {
        long safe = Math.max(0L, seconds);
        return String.format(
                java.util.Locale.ROOT,
                "%02d:%02d:%02d",
                safe / 3600L,
                (safe % 3600L) / 60L,
                safe % 60L
        );
    }

    private static void validateRegisteredPortal(MinecraftServer server, DimensionControlManager control) {
        var registered = control.netherPortal().orElse(null);
        if (registered != null && !NetherPortalRegistration.isIntact(server.overworld(), registered)) {
            control.clearNetherPortal();
        }
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

    private record PendingNetherEntry(
            UUID playerId,
            UUID ownerId,
            UUID factionId,
            boolean operator,
            boolean factionBacked,
            boolean secondSessionConfirmed,
            BlockPos portalPosition,
            BlockPos returnPosition,
            EntryResult preview,
            DimensionControlManager.LandingPos reusableLanding
    ) {
    }

    private record AuthorizedNetherEntry(EntryResult result, boolean startedSession, boolean joinedNow) {
    }

    private DimensionControlEvents() {
    }
}
