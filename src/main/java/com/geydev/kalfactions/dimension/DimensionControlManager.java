package com.geydev.kalfactions.dimension;

import com.geydev.kalfactions.KalFactions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.storage.LevelResource;

public final class DimensionControlManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "kingdoms_dimension_control.json";
    private static final int FORMAT_VERSION = 5;
    private static DimensionControlManager instance;

    private final Path file;
    private final State state;
    private final Set<ResourceKey<Level>> wipedThisStartup = new HashSet<>();
    private final List<EndedSession> pendingEndedSessions = new ArrayList<>();
    private NetherRules rules = NetherRules.DEFAULT;

    private DimensionControlManager(Path file, State state) {
        this.file = file;
        this.state = normalize(state);
    }

    public static synchronized DimensionControlManager get(MinecraftServer server) {
        Path path = server.getWorldPath(LevelResource.ROOT).normalize().resolve(FILE_NAME);
        if (instance == null || !instance.file.equals(path)) {
            instance = new DimensionControlManager(path, loadState(path));
        }
        instance.configure(NetherRules.configured());
        return instance;
    }

    static DimensionControlManager forTesting(Path file) {
        return new DimensionControlManager(file, loadState(file));
    }

    public static boolean isControlled(ResourceKey<Level> dimension) {
        return Level.NETHER.equals(dimension) || Level.END.equals(dimension);
    }

    public synchronized void configure(NetherRules rules) {
        this.rules = rules;
    }

    public synchronized NetherRules rules() {
        return rules;
    }

    public synchronized boolean isClosed(ResourceKey<Level> dimension) {
        if (Level.NETHER.equals(dimension)) {
            return state.netherClosed;
        }
        if (Level.END.equals(dimension)) {
            return state.endClosed;
        }
        return false;
    }

    public synchronized boolean isNetherOpenForPlayers(Instant now) {
        return !state.netherClosed && NetherSchedulePolicy.isOpen(now);
    }

    public synchronized boolean setClosed(ResourceKey<Level> dimension, boolean closed) {
        if (Level.NETHER.equals(dimension)) {
            if (state.netherClosed == closed) {
                return false;
            }
            state.netherClosed = closed;
        } else if (Level.END.equals(dimension)) {
            if (state.endClosed == closed) {
                return false;
            }
            state.endClosed = closed;
        } else {
            return false;
        }
        save();
        return true;
    }

    public synchronized Optional<PortalBounds> netherPortal() {
        return Optional.ofNullable(state.netherPortal).map(PortalBoundsData::toValue);
    }

    public synchronized void setNetherPortal(PortalBounds bounds) {
        state.netherPortal = PortalBoundsData.from(bounds);
        save();
    }

    public synchronized void clearNetherPortal() {
        if (state.netherPortal != null) {
            state.netherPortal = null;
            save();
        }
    }

    public synchronized boolean isInsideRegisteredPortal(BlockPos pos) {
        return state.netherPortal != null && state.netherPortal.toValue().contains(pos);
    }

    public synchronized Optional<PortalCharge> netherPortalCharge() {
        if (state.netherPortalChargedUntil == null) {
            return Optional.empty();
        }
        return Optional.of(new PortalCharge(
                Instant.ofEpochMilli(state.netherPortalIgnitedAt == null ? 0L : state.netherPortalIgnitedAt),
                Instant.ofEpochMilli(state.netherPortalChargedUntil),
                state.netherPortalIgnitedBy == null ? "" : state.netherPortalIgnitedBy
        ));
    }

    public synchronized boolean isNetherPortalCharged(Instant now) {
        return state.netherPortalChargedUntil != null && state.netherPortalChargedUntil > now.toEpochMilli();
    }

    public synchronized PortalCharge igniteNetherPortal(
            Instant now,
            Duration lifetime,
            String ignitedBy,
            BlockPos anchor
    ) {
        state.netherPortalIgnitedAt = now.toEpochMilli();
        state.netherPortalChargedUntil = now.plus(lifetime).toEpochMilli();
        state.netherPortalIgnitedBy = ignitedBy;
        state.netherPortalAnchor = LandingData.from(anchor);
        save();
        return netherPortalCharge().orElseThrow();
    }

    public synchronized boolean clearNetherPortalCharge() {
        if (state.netherPortalChargedUntil == null) {
            return false;
        }
        state.netherPortalChargedUntil = null;
        state.netherPortalIgnitedAt = null;
        state.netherPortalIgnitedBy = null;
        save();
        return true;
    }

    public synchronized Optional<BlockPos> netherPortalAnchor() {
        return Optional.ofNullable(state.netherPortalAnchor).map(anchor -> anchor.toValue().blockPos());
    }

    public synchronized void setNetherPortalAnchor(BlockPos anchor) {
        state.netherPortalAnchor = LandingData.from(anchor);
        save();
    }

    public synchronized EntryResult authorizeNetherEntry(
            UUID factionId,
            UUID playerId,
            Instant now,
            boolean operator,
            LandingAllocator allocator
    ) {
        return authorizeNetherEntry(factionId, playerId, now, operator, false, allocator);
    }

    public synchronized EntryResult authorizeNetherEntry(
            UUID factionId,
            UUID playerId,
            Instant now,
            boolean operator,
            boolean secondSessionConfirmed,
            LandingAllocator allocator
    ) {
        if (operator && factionId == null) {
            return authorizeOperatorEntry(playerId, now, allocator);
        }
        if (factionId == null) {
            return new EntryResult(EntryStatus.FACTION_REQUIRED, null, 0);
        }
        if (!operator && (state.netherClosed || !NetherSchedulePolicy.isOpen(now))) {
            return new EntryResult(EntryStatus.SCHEDULE_CLOSED, null, remainingSessions(factionId, now));
        }
        String factionKey = factionId.toString();
        FactionLedger ledger = state.netherFactions.computeIfAbsent(factionKey, ignored -> new FactionLedger());
        retireExpiredSessions(factionId, ledger, now);
        LandingPos previousDailyLanding = ledger.lastLanding == null ? null : ledger.lastLanding.toValue();
        resetUsageDate(ledger, now);
        ActiveSessionData active = newestActiveData(ledger, now);
        String playerKey = playerId.toString();
        String deathLockedSession = state.deathLocks.get(playerKey);
        if (active != null && !active.id.equals(deathLockedSession)) {
            boolean joined = active.joinedPlayers.add(playerKey);
            boolean assigned = !active.id.equals(state.playerSessions.put(playerKey, active.id));
            if (joined || assigned) {
                save();
            }
            return new EntryResult(EntryStatus.JOINED_ACTIVE, active.toValue(factionId), remainingSessions(ledger, now));
        }
        if (!operator && !NetherSchedulePolicy.canStartSession(now)) {
            return new EntryResult(EntryStatus.SCHEDULE_CLOSED, null, remainingSessions(ledger, now));
        }
        if (ledger.sessionsUsed >= rules.sessionsPerDay()) {
            if (operator) {
                return authorizeOperatorEntry(playerId, now, allocator);
            }
            return new EntryResult(
                    deathLockedSession == null ? EntryStatus.NO_SESSIONS_LEFT : EntryStatus.DEATH_LOCKED,
                    active == null ? null : active.toValue(factionId),
                    0
            );
        }
        if (!operator && ledger.sessionsUsed > 0 && !secondSessionConfirmed) {
            return new EntryResult(
                    EntryStatus.SECOND_SESSION_CONFIRMATION_REQUIRED,
                    active == null ? null : active.toValue(factionId),
                    rules.sessionsPerDay() - ledger.sessionsUsed
            );
        }
        Optional<LandingPos> allocated;
        if (ledger.sessionsUsed > 0 && ledger.lastLanding != null) {
            allocated = Optional.of(ledger.lastLanding.toValue());
        } else {
            List<LandingPos> occupied = state.netherFactions.values().stream()
                    .flatMap(value -> activeData(value, now).stream())
                    .map(value -> value.landing.toValue())
                    .toList();
            allocated = allocator.allocate(occupied, previousDailyLanding, rules);
        }
        if (allocated.isEmpty()) {
            return new EntryResult(EntryStatus.NO_SAFE_LANDING, null, rules.sessionsPerDay() - ledger.sessionsUsed);
        }
        Instant end = operator && !NetherSchedulePolicy.isOpen(now)
                ? now.plus(rules.sessionDuration())
                : NetherSchedulePolicy.sessionEnd(now, rules.sessionDuration());
        ActiveSessionData created = new ActiveSessionData();
        created.id = UUID.randomUUID().toString();
        created.startedAt = now.toEpochMilli();
        created.endsAt = end.toEpochMilli();
        created.landing = LandingData.from(allocated.get());
        created.ordinal = ledger.sessionsUsed + 1;
        created.joinedPlayers.add(playerKey);
        ledger.activeSessions.add(created);
        state.playerSessions.put(playerKey, created.id);
        ledger.lastLanding = created.landing;
        ledger.sessionsUsed++;
        save();
        return new EntryResult(
                EntryStatus.STARTED_SESSION,
                created.toValue(factionId),
                Math.max(0, rules.sessionsPerDay() - ledger.sessionsUsed)
        );
    }

    /**
     * Performs every admission check without consuming a session or assigning the player. This is used before
     * asynchronously loading a remote Nether chunk; the real authorization is repeated after that chunk is ready.
     */
    public synchronized EntryResult previewNetherEntry(
            UUID factionId,
            UUID playerId,
            Instant now,
            boolean operator,
            boolean secondSessionConfirmed
    ) {
        if (operator && factionId == null) {
            return previewOperatorEntry(playerId, now);
        }
        if (factionId == null) {
            return new EntryResult(EntryStatus.FACTION_REQUIRED, null, 0);
        }
        if (!operator && (state.netherClosed || !NetherSchedulePolicy.isOpen(now))) {
            return new EntryResult(
                    EntryStatus.SCHEDULE_CLOSED,
                    null,
                    remainingSessions(factionId, now)
            );
        }
        FactionLedger ledger = state.netherFactions.get(factionId.toString());
        ActiveSessionData active = newestActiveData(ledger, now);
        String deathLockedSession = state.deathLocks.get(playerId.toString());
        if (active != null && !active.id.equals(deathLockedSession)) {
            return new EntryResult(EntryStatus.JOINED_ACTIVE, active.toValue(factionId), remainingSessions(ledger, now));
        }
        if (!operator && !NetherSchedulePolicy.canStartSession(now)) {
            return new EntryResult(EntryStatus.SCHEDULE_CLOSED, null, remainingSessions(factionId, now));
        }
        int used = ledger == null || !NetherSchedulePolicy.date(now).toString().equals(ledger.usageDate)
                ? 0 : ledger.sessionsUsed;
        if (used >= rules.sessionsPerDay()) {
            if (operator) {
                return previewOperatorEntry(playerId, now);
            }
            return new EntryResult(
                    deathLockedSession == null ? EntryStatus.NO_SESSIONS_LEFT : EntryStatus.DEATH_LOCKED,
                    active == null ? null : active.toValue(factionId),
                    0
            );
        }
        if (!operator && used > 0 && !secondSessionConfirmed) {
            return new EntryResult(
                    EntryStatus.SECOND_SESSION_CONFIRMATION_REQUIRED,
                    active == null ? null : active.toValue(factionId),
                    rules.sessionsPerDay() - used
            );
        }
        return new EntryResult(EntryStatus.STARTED_SESSION, null, rules.sessionsPerDay() - used);
    }

    public synchronized Optional<LandingPos> lastLanding(UUID factionId) {
        FactionLedger ledger = state.netherFactions.get(factionId.toString());
        return ledger == null || ledger.lastLanding == null
                ? Optional.empty()
                : Optional.of(ledger.lastLanding.toValue());
    }

    private EntryResult previewOperatorEntry(UUID playerId, Instant now) {
        ActiveSessionData active = activeOperatorData(playerId, now);
        return new EntryResult(
                EntryStatus.OPERATOR_BYPASS,
                active == null ? null : active.toValue(playerId),
                rules.sessionsPerDay()
        );
    }

    public synchronized Optional<LandingPos> reusableLanding(UUID factionId, Instant now) {
        FactionLedger ledger = state.netherFactions.get(factionId.toString());
        if (ledger == null
                || ledger.sessionsUsed <= 0
                || ledger.lastLanding == null
                || !NetherSchedulePolicy.date(now).toString().equals(ledger.usageDate)) {
            return Optional.empty();
        }
        return Optional.of(ledger.lastLanding.toValue());
    }

    public synchronized boolean updateReusableLanding(UUID factionId, Instant now, LandingPos landing) {
        FactionLedger ledger = state.netherFactions.get(factionId.toString());
        if (ledger == null
                || ledger.sessionsUsed <= 0
                || !NetherSchedulePolicy.date(now).toString().equals(ledger.usageDate)) {
            return false;
        }
        LandingData updated = LandingData.from(landing);
        ledger.lastLanding = updated;
        long epochMillis = now.toEpochMilli();
        for (ActiveSessionData active : ledger.activeSessions) {
            if (active.endsAt > epochMillis) {
                active.landing = updated;
            }
        }
        save();
        return true;
    }

    private EntryResult authorizeOperatorEntry(UUID playerId, Instant now, LandingAllocator allocator) {
        String playerKey = playerId.toString();
        ActiveSessionData active = state.operatorSessions.get(playerKey);
        if (active != null && active.endsAt <= now.toEpochMilli()) {
            pendingEndedSessions.add(retireOperatorSession(playerId, active));
            active = null;
        }
        if (active != null) {
            active.joinedPlayers.add(playerKey);
            state.playerSessions.put(playerKey, active.id);
            save();
            return new EntryResult(EntryStatus.OPERATOR_BYPASS, active.toValue(playerId), rules.sessionsPerDay());
        }
        List<LandingPos> occupied = allActiveData(now).stream()
                .map(value -> value.landing.toValue())
                .toList();
        Optional<LandingPos> allocated = allocator.allocate(occupied, null, rules);
        if (allocated.isEmpty()) {
            return new EntryResult(EntryStatus.NO_SAFE_LANDING, null, rules.sessionsPerDay());
        }
        ActiveSessionData created = new ActiveSessionData();
        created.id = UUID.randomUUID().toString();
        created.startedAt = now.toEpochMilli();
        created.endsAt = now.plus(rules.sessionDuration()).toEpochMilli();
        created.ordinal = 0;
        created.landing = LandingData.from(allocated.get());
        created.joinedPlayers.add(playerKey);
        state.operatorSessions.put(playerKey, created);
        state.playerSessions.put(playerKey, created.id);
        save();
        return new EntryResult(EntryStatus.OPERATOR_BYPASS, created.toValue(playerId), rules.sessionsPerDay());
    }

    public synchronized Optional<ActiveSession> activeSession(UUID factionId, Instant now) {
        FactionLedger ledger = state.netherFactions.get(factionId.toString());
        ActiveSessionData active = newestActiveData(ledger, now);
        return active == null ? Optional.empty() : Optional.of(active.toValue(factionId));
    }

    public synchronized List<ActiveSession> activeSessions(UUID factionId, Instant now) {
        FactionLedger ledger = state.netherFactions.get(factionId.toString());
        if (ledger == null) {
            return List.of();
        }
        return activeData(ledger, now).stream().map(active -> active.toValue(factionId)).toList();
    }

    public synchronized Optional<ActiveSession> assignedSession(UUID playerId, Instant now) {
        String assigned = state.playerSessions.get(playerId.toString());
        if (!validUuid(assigned)) {
            return Optional.empty();
        }
        return activeSessionById(UUID.fromString(assigned), now);
    }

    public synchronized boolean updateSessionLanding(UUID sessionId, LandingPos landing) {
        String id = sessionId.toString();
        for (ActiveSessionData active : state.operatorSessions.values()) {
            if (active.id.equals(id)) {
                active.landing = LandingData.from(landing);
                save();
                return true;
            }
        }
        for (FactionLedger ledger : state.netherFactions.values()) {
            ActiveSessionData active = ledger.activeSessions.stream()
                    .filter(candidate -> candidate.id.equals(id))
                    .findFirst().orElse(null);
            if (active != null) {
                LandingData updated = LandingData.from(landing);
                active.landing = updated;
                ledger.lastLanding = updated;
                save();
                return true;
            }
        }
        return false;
    }

    public synchronized void rollbackNetherEntry(
            UUID factionId,
            UUID playerId,
            UUID sessionId,
            boolean startedSession,
            boolean joinedNow
    ) {
        ActiveSessionData operatorSession = state.operatorSessions.get(playerId.toString());
        if (operatorSession != null && operatorSession.id.equals(sessionId.toString())) {
            if (startedSession) {
                state.operatorSessions.remove(playerId.toString());
            } else if (joinedNow) {
                operatorSession.joinedPlayers.remove(playerId.toString());
            }
            state.playerSessions.remove(playerId.toString(), sessionId.toString());
            save();
            return;
        }
        FactionLedger ledger = state.netherFactions.get(factionId.toString());
        if (ledger == null) {
            return;
        }
        ActiveSessionData active = sessionData(ledger, sessionId);
        if (active == null) {
            return;
        }
        if (startedSession) {
            ledger.activeSessions.remove(active);
            ledger.sessionsUsed = Math.max(0, ledger.sessionsUsed - 1);
            if (ledger.sessionsUsed == 0) {
                ledger.lastLanding = null;
            }
        } else if (joinedNow) {
            active.joinedPlayers.remove(playerId.toString());
        }
        state.playerSessions.remove(playerId.toString(), sessionId.toString());
        state.deathLocks.remove(playerId.toString(), sessionId.toString());
        save();
    }

    public synchronized Optional<ActiveSession> activeSessionById(UUID sessionId, Instant now) {
        for (Map.Entry<String, ActiveSessionData> entry : state.operatorSessions.entrySet()) {
            ActiveSessionData active = entry.getValue();
            if (active.endsAt > now.toEpochMilli() && active.id.equals(sessionId.toString())) {
                return Optional.of(active.toValue(UUID.fromString(entry.getKey())));
            }
        }
        for (Map.Entry<String, FactionLedger> entry : state.netherFactions.entrySet()) {
            for (ActiveSessionData active : activeData(entry.getValue(), now)) {
                if (active.id.equals(sessionId.toString())) {
                    return Optional.of(active.toValue(UUID.fromString(entry.getKey())));
                }
            }
        }
        return Optional.empty();
    }

    public synchronized boolean markDeath(UUID factionId, UUID playerId, Instant now) {
        FactionLedger ledger = state.netherFactions.get(factionId.toString());
        ActiveSessionData active = assignedData(playerId, now);
        if (active == null && ledger != null) {
            active = newestActiveData(ledger, now);
        }
        if (active == null) {
            return false;
        }
        state.deathLocks.put(playerId.toString(), active.id);
        save();
        return true;
    }

    public synchronized boolean isDeathLocked(UUID factionId, UUID playerId, Instant now) {
        FactionLedger ledger = state.netherFactions.get(factionId.toString());
        String locked = state.deathLocks.get(playerId.toString());
        String assigned = state.playerSessions.get(playerId.toString());
        return locked != null && locked.equals(assigned)
                && activeData(ledger, now).stream().anyMatch(active -> active.id.equals(locked));
    }

    public synchronized boolean isValidReturn(ReturnBinding binding, Instant now) {
        ActiveSessionData active = activeDataById(binding.sessionId(), now);
        return matchesReturn(active, binding);
    }

    public synchronized Optional<ReturnBinding> issueReturn(UUID sessionId, UUID playerId, Instant now) {
        return issueReturn(sessionId, playerId, BlockPos.ZERO, now);
    }

    public synchronized Optional<ReturnBinding> issueReturn(
            UUID sessionId,
            UUID playerId,
            BlockPos returnPos,
            Instant now
    ) {
        ActiveSessionData active = activeDataById(sessionId, now);
        if (active == null || !active.joinedPlayers.contains(playerId.toString())
                || active.returnTokens.containsKey(playerId.toString())) {
            return Optional.empty();
        }
        UUID token = UUID.randomUUID();
        active.returnTokens.put(playerId.toString(), token.toString());
        active.returnPoints.put(playerId.toString(), LandingData.from(returnPos));
        save();
        return Optional.of(new ReturnBinding(playerId, sessionId, token, returnPos.immutable()));
    }

    public synchronized Optional<ReturnBinding> currentReturn(UUID playerId, Instant now) {
        String playerKey = playerId.toString();
        ReturnBinding operatorReturn = returnBinding(state.operatorSessions.get(playerKey), playerId, now);
        if (operatorReturn != null) {
            return Optional.of(operatorReturn);
        }
        for (FactionLedger ledger : state.netherFactions.values()) {
            for (ActiveSessionData active : activeData(ledger, now)) {
                ReturnBinding binding = returnBinding(active, playerId, now);
                if (binding != null) {
                    return Optional.of(binding);
                }
            }
        }
        return Optional.empty();
    }

    private static ReturnBinding returnBinding(ActiveSessionData active, UUID playerId, Instant now) {
        if (active == null || active.endsAt <= now.toEpochMilli()) {
            return null;
        }
        String playerKey = playerId.toString();
        String token = active.returnTokens.get(playerKey);
        if (token == null) {
            return null;
        }
        try {
            LandingData point = active.returnPoints.get(playerKey);
            BlockPos returnPos = point == null ? BlockPos.ZERO : point.toValue().blockPos();
            return new ReturnBinding(playerId, UUID.fromString(active.id), UUID.fromString(token), returnPos);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public synchronized boolean consumeReturn(ReturnBinding binding, Instant now) {
        ActiveSessionData active = activeDataById(binding.sessionId(), now);
        if (!matchesReturn(active, binding)) {
            return false;
        }
        active.returnTokens.remove(binding.playerId().toString());
        active.returnPoints.remove(binding.playerId().toString());
        save();
        return true;
    }

    public synchronized void invalidateReturnsForPlayer(UUID playerId) {
        boolean changed = false;
        ActiveSessionData operatorSession = state.operatorSessions.get(playerId.toString());
        if (operatorSession != null) {
            changed |= operatorSession.returnTokens.remove(playerId.toString()) != null;
            changed |= operatorSession.returnPoints.remove(playerId.toString()) != null;
        }
        for (FactionLedger ledger : state.netherFactions.values()) {
            for (ActiveSessionData active : ledger.activeSessions) {
                changed |= active.returnTokens.remove(playerId.toString()) != null;
                changed |= active.returnPoints.remove(playerId.toString()) != null;
            }
        }
        if (changed) {
            save();
        }
    }

    private static boolean matchesReturn(ActiveSessionData active, ReturnBinding binding) {
        if (active == null || !binding.token().toString().equals(active.returnTokens.get(binding.playerId().toString()))) {
            return false;
        }
        LandingData point = active.returnPoints.get(binding.playerId().toString());
        BlockPos expected = point == null ? BlockPos.ZERO : point.toValue().blockPos();
        return expected.equals(binding.returnPos());
    }

    public synchronized int remainingSessions(UUID factionId, Instant now) {
        FactionLedger ledger = state.netherFactions.get(factionId.toString());
        return ledger == null ? rules.sessionsPerDay() : remainingSessions(ledger, now);
    }

    public synchronized void leaveNether(UUID playerId) {
        String playerKey = playerId.toString();
        boolean changed = state.playerSessions.remove(playerKey) != null;
        ActiveSessionData operatorSession = state.operatorSessions.get(playerKey);
        if (operatorSession != null) {
            changed |= operatorSession.returnTokens.remove(playerKey) != null;
            changed |= operatorSession.returnPoints.remove(playerKey) != null;
        }
        for (FactionLedger ledger : state.netherFactions.values()) {
            for (ActiveSessionData active : ledger.activeSessions) {
                changed |= active.returnTokens.remove(playerKey) != null;
                changed |= active.returnPoints.remove(playerKey) != null;
            }
        }
        if (changed) {
            save();
        }
    }

    public synchronized List<EndedSession> expireSessions(Instant now, Predicate<UUID> factionExists) {
        List<EndedSession> ended = new ArrayList<>(pendingEndedSessions);
        pendingEndedSessions.clear();
        for (Map.Entry<String, ActiveSessionData> entry : List.copyOf(state.operatorSessions.entrySet())) {
            if (entry.getValue().endsAt <= now.toEpochMilli()) {
                ended.add(retireOperatorSession(UUID.fromString(entry.getKey()), entry.getValue()));
            }
        }
        for (Map.Entry<String, FactionLedger> entry : state.netherFactions.entrySet()) {
            UUID factionId;
            try {
                factionId = UUID.fromString(entry.getKey());
            } catch (IllegalArgumentException exception) {
                continue;
            }
            FactionLedger ledger = entry.getValue();
            for (ActiveSessionData active : List.copyOf(ledger.activeSessions)) {
                if (active.endsAt <= now.toEpochMilli() || !factionExists.test(factionId)) {
                    ended.add(retireSession(factionId, ledger, active));
                }
            }
        }
        if (!ended.isEmpty()) {
            save();
        }
        return List.copyOf(ended);
    }

    public synchronized List<ActiveSession> activeSessions(Instant now) {
        List<ActiveSession> result = new ArrayList<>();
        for (Map.Entry<String, ActiveSessionData> entry : state.operatorSessions.entrySet()) {
            if (entry.getValue().endsAt > now.toEpochMilli()) {
                result.add(entry.getValue().toValue(UUID.fromString(entry.getKey())));
            }
        }
        for (Map.Entry<String, FactionLedger> entry : state.netherFactions.entrySet()) {
            try {
                for (ActiveSessionData active : activeData(entry.getValue(), now)) {
                    result.add(active.toValue(UUID.fromString(entry.getKey())));
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return List.copyOf(result);
    }

    /**
     * Applies the daily 18:00 Moscow opening edge. A manual close made before the opening cannot suppress it; a
     * manual close made after today's edge remains in force until the next day's edge.
     */
    public synchronized boolean applyScheduledNetherOpening(Instant now) {
        if (!NetherSchedulePolicy.isOpen(now)) {
            return false;
        }
        ZonedDateTime local = now.atZone(NetherSchedulePolicy.MOSCOW);
        String today = local.toLocalDate().toString();
        if (today.equals(state.lastAutomaticNetherOpening)) {
            return false;
        }
        state.lastAutomaticNetherOpening = today;
        boolean changed = state.netherClosed;
        state.netherClosed = false;
        save();
        return changed;
    }

    public synchronized boolean isWipePending(ResourceKey<Level> dimension) {
        if (Level.NETHER.equals(dimension)) {
            return state.netherWipePending;
        }
        if (Level.END.equals(dimension)) {
            return state.endWipePending;
        }
        return false;
    }

    public synchronized boolean setWipePending(ResourceKey<Level> dimension, boolean pending) {
        if (Level.NETHER.equals(dimension)) {
            return false;
        }
        if (Level.END.equals(dimension)) {
            if (state.endWipePending == pending) {
                return false;
            }
            state.endWipePending = pending;
        } else {
            return false;
        }
        save();
        return true;
    }

    synchronized boolean requestNetherWipeFromDimensionKey() {
        if (state.netherWipePending) {
            return false;
        }
        state.netherWipePending = true;
        save();
        return true;
    }

    synchronized boolean cancelNetherWipeFromDimensionKey() {
        if (!state.netherWipePending) {
            return false;
        }
        state.netherWipePending = false;
        save();
        return true;
    }

    public synchronized long wipeGeneration(ResourceKey<Level> dimension) {
        if (Level.NETHER.equals(dimension)) {
            return state.netherWipeGen;
        }
        if (Level.END.equals(dimension)) {
            return state.endWipeGen;
        }
        return 0L;
    }

    public synchronized long generationSeed(ResourceKey<Level> dimension, long defaultSeed) {
        if (Level.NETHER.equals(dimension)) {
            return state.netherSeed == null ? defaultSeed : state.netherSeed;
        }
        if (Level.END.equals(dimension)) {
            return state.endSeed == null ? defaultSeed : state.endSeed;
        }
        return defaultSeed;
    }

    public synchronized void runPendingWipes(MinecraftServer server) {
        if (state.netherWipePending && wipeFolder(server, Level.NETHER)) {
            completePendingWipe(Level.NETHER, server.getWorldData().worldGenOptions().seed());
        }
        if (state.endWipePending && wipeFolder(server, Level.END)) {
            completePendingWipe(Level.END, server.getWorldData().worldGenOptions().seed());
            server.getWorldData().setEndDragonFightData(EndDragonFight.Data.DEFAULT);
        }
    }

    synchronized boolean completePendingWipe(ResourceKey<Level> dimension, long defaultSeed) {
        if (Level.NETHER.equals(dimension) && state.netherWipePending) {
            state.netherWipePending = false;
            state.netherWipeGen++;
            state.netherSeed = nextSeed(generationSeed(Level.NETHER, defaultSeed));
            state.netherFactions.clear();
            state.operatorSessions.clear();
            state.deathLocks.clear();
            state.playerSessions.clear();
            wipedThisStartup.add(Level.NETHER);
            save();
            return true;
        }
        if (Level.END.equals(dimension) && state.endWipePending) {
            state.endWipePending = false;
            state.endWipeGen++;
            state.endSeed = nextSeed(generationSeed(Level.END, defaultSeed));
            wipedThisStartup.add(Level.END);
            save();
            return true;
        }
        return false;
    }

    public synchronized Set<ResourceKey<Level>> consumeWipedThisStartup() {
        Set<ResourceKey<Level>> wiped = Set.copyOf(wipedThisStartup);
        wipedThisStartup.clear();
        return wiped;
    }

    private int remainingSessions(FactionLedger ledger, Instant now) {
        LocalDate today = NetherSchedulePolicy.date(now);
        if (!today.toString().equals(ledger.usageDate)) {
            return rules.sessionsPerDay();
        }
        return Math.max(0, rules.sessionsPerDay() - ledger.sessionsUsed);
    }

    private void resetUsageDate(FactionLedger ledger, Instant now) {
        String date = NetherSchedulePolicy.date(now).toString();
        if (!date.equals(ledger.usageDate)) {
            ledger.usageDate = date;
            ledger.sessionsUsed = 0;
            ledger.lastLanding = null;
        }
    }

    private List<ActiveSessionData> activeData(FactionLedger ledger, Instant now) {
        if (ledger == null) {
            return List.of();
        }
        long epochMillis = now.toEpochMilli();
        return ledger.activeSessions.stream()
                .filter(active -> active.endsAt > epochMillis)
                .sorted(Comparator.comparingLong(active -> active.startedAt))
                .toList();
    }

    private ActiveSessionData newestActiveData(FactionLedger ledger, Instant now) {
        List<ActiveSessionData> active = activeData(ledger, now);
        return active.isEmpty() ? null : active.getLast();
    }

    private ActiveSessionData sessionData(FactionLedger ledger, UUID sessionId) {
        String id = sessionId.toString();
        return ledger.activeSessions.stream().filter(active -> active.id.equals(id)).findFirst().orElse(null);
    }

    private ActiveSessionData assignedData(UUID playerId, Instant now) {
        String assigned = state.playerSessions.get(playerId.toString());
        if (!validUuid(assigned)) {
            return null;
        }
        return activeDataById(UUID.fromString(assigned), now);
    }

    private ActiveSessionData activeDataById(UUID sessionId, Instant now) {
        for (ActiveSessionData active : state.operatorSessions.values()) {
            if (active.endsAt > now.toEpochMilli() && active.id.equals(sessionId.toString())) {
                return active;
            }
        }
        for (FactionLedger ledger : state.netherFactions.values()) {
            for (ActiveSessionData active : activeData(ledger, now)) {
                if (active.id.equals(sessionId.toString())) {
                    return active;
                }
            }
        }
        return null;
    }

    private ActiveSessionData activeOperatorData(UUID playerId, Instant now) {
        ActiveSessionData active = state.operatorSessions.get(playerId.toString());
        return active != null && active.endsAt > now.toEpochMilli() ? active : null;
    }

    private List<ActiveSessionData> allActiveData(Instant now) {
        List<ActiveSessionData> result = new ArrayList<>();
        for (ActiveSessionData active : state.operatorSessions.values()) {
            if (active.endsAt > now.toEpochMilli()) {
                result.add(active);
            }
        }
        for (FactionLedger ledger : state.netherFactions.values()) {
            result.addAll(activeData(ledger, now));
        }
        return result;
    }

    private void retireExpiredSessions(UUID factionId, FactionLedger ledger, Instant now) {
        boolean changed = false;
        for (ActiveSessionData active : List.copyOf(ledger.activeSessions)) {
            if (active.endsAt <= now.toEpochMilli()) {
                pendingEndedSessions.add(retireSession(factionId, ledger, active));
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }

    private EndedSession retireSession(UUID factionId, FactionLedger ledger, ActiveSessionData active) {
        Set<UUID> assigned = assignedPlayers(active.id);
        EndedSession ended = new EndedSession(factionId, UUID.fromString(active.id), assigned);
        clearSession(ledger, active.id);
        return ended;
    }

    private EndedSession retireOperatorSession(UUID playerId, ActiveSessionData active) {
        Set<UUID> assigned = assignedPlayers(active.id);
        EndedSession ended = new EndedSession(playerId, UUID.fromString(active.id), assigned);
        state.operatorSessions.remove(playerId.toString());
        state.playerSessions.entrySet().removeIf(entry -> active.id.equals(entry.getValue()));
        return ended;
    }

    private void clearSession(FactionLedger ledger, String sessionId) {
        ledger.activeSessions.removeIf(active -> sessionId.equals(active.id));
        state.playerSessions.entrySet().removeIf(entry -> sessionId.equals(entry.getValue()));
        state.deathLocks.entrySet().removeIf(entry -> sessionId.equals(entry.getValue()));
    }

    private Set<UUID> assignedPlayers(String sessionId) {
        Set<UUID> result = new HashSet<>();
        state.playerSessions.forEach((player, assigned) -> {
            if (sessionId.equals(assigned) && validUuid(player)) {
                result.add(UUID.fromString(player));
            }
        });
        return result;
    }

    private static long nextSeed(long currentSeed) {
        long seed;
        do {
            seed = ThreadLocalRandom.current().nextLong();
        } while (seed == currentSeed);
        return seed;
    }

    private boolean wipeFolder(MinecraftServer server, ResourceKey<Level> dimension) {
        Path worldRoot = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path folder = DimensionType.getStorageFolder(dimension, worldRoot).toAbsolutePath().normalize();
        if (!isSafeWipeFolder(worldRoot, folder)) {
            KalFactions.LOGGER.error("Refusing to wipe unsafe dimension path {} outside world root {}", folder, worldRoot);
            return false;
        }
        if (!Files.exists(folder)) {
            KalFactions.LOGGER.info("Dimension folder {} is already absent, nothing to wipe", folder);
            return true;
        }
        try {
            deleteRecursively(folder);
            KalFactions.LOGGER.info("Wiped dimension folder {}", folder);
            return true;
        } catch (IOException exception) {
            KalFactions.LOGGER.error("Failed to wipe dimension folder {}, will retry on next startup", folder, exception);
            return false;
        }
    }

    static boolean isSafeWipeFolder(Path worldRoot, Path folder) {
        Path normalizedRoot = worldRoot.toAbsolutePath().normalize();
        Path normalizedFolder = folder.toAbsolutePath().normalize();
        return !normalizedFolder.equals(normalizedRoot) && normalizedFolder.startsWith(normalizedRoot);
    }

    private static void deleteRecursively(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) throws IOException {
                Files.delete(path);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                if (exception != null) {
                    throw exception;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static State loadState(Path path) {
        if (!Files.exists(path)) {
            return new State();
        }
        try {
            State loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), State.class);
            return loaded == null ? new State() : loaded;
        } catch (IOException | RuntimeException exception) {
            KalFactions.LOGGER.error("Failed to read {}, starting with defaults", path, exception);
            return new State();
        }
    }

    private static State normalize(State state) {
        boolean legacy = state.formatVersion < FORMAT_VERSION;
        state.formatVersion = FORMAT_VERSION;
        if (legacy) {
            state.netherPortalIgnitedAt = null;
            state.netherPortalChargedUntil = null;
            state.netherPortalIgnitedBy = null;
            state.netherPortalAnchor = null;
        }
        if (state.netherFactions == null) {
            state.netherFactions = new HashMap<>();
        }
        if (state.operatorSessions == null) {
            state.operatorSessions = new HashMap<>();
        }
        if (state.deathLocks == null) {
            state.deathLocks = new HashMap<>();
        }
        if (state.playerSessions == null) {
            state.playerSessions = new HashMap<>();
        }
        state.netherFactions.entrySet().removeIf(entry -> !validUuid(entry.getKey()) || entry.getValue() == null);
        state.operatorSessions.entrySet().removeIf(entry -> !validUuid(entry.getKey())
                || entry.getValue() == null || !validActive(entry.getValue()));
        for (Map.Entry<String, ActiveSessionData> entry : state.operatorSessions.entrySet()) {
            ActiveSessionData active = entry.getValue();
            active.ordinal = 0;
            active.joinedPlayers.clear();
            active.joinedPlayers.add(entry.getKey());
            active.returnIssuedPlayers.removeIf(player -> !validUuid(player));
            active.returnTokens.entrySet().removeIf(candidate -> !validUuid(candidate.getKey())
                    || !validUuid(candidate.getValue()));
            active.returnPoints.entrySet().removeIf(candidate -> !validUuid(candidate.getKey())
                    || candidate.getValue() == null);
        }
        for (FactionLedger ledger : state.netherFactions.values()) {
            ledger.sessionsUsed = Math.max(0, ledger.sessionsUsed);
            if (ledger.activeSessions == null) {
                ledger.activeSessions = new ArrayList<>();
            }
            if (ledger.active != null) {
                ledger.activeSessions.add(ledger.active);
                ledger.active = null;
            }
            ledger.activeSessions.removeIf(active -> !validActive(active));
            ledger.activeSessions.sort(Comparator.comparingLong(active -> active.startedAt));
            for (int index = 0; index < ledger.activeSessions.size(); index++) {
                ActiveSessionData active = ledger.activeSessions.get(index);
                if (active.ordinal <= 0) {
                    active.ordinal = index + 1;
                }
                active.joinedPlayers.removeIf(player -> !validUuid(player));
                active.returnIssuedPlayers.removeIf(player -> !validUuid(player));
                active.returnTokens.entrySet().removeIf(entry -> !validUuid(entry.getKey()) || !validUuid(entry.getValue()));
                active.returnPoints.entrySet().removeIf(entry -> !validUuid(entry.getKey()) || entry.getValue() == null);
            }
        }
        state.deathLocks.entrySet().removeIf(entry -> !validUuid(entry.getKey()) || !validUuid(entry.getValue()));
        state.playerSessions.entrySet().removeIf(entry -> !validUuid(entry.getKey()) || !validUuid(entry.getValue()));
        return state;
    }

    private static boolean validActive(ActiveSessionData active) {
        if (!validUuid(active.id) || active.landing == null || active.endsAt <= active.startedAt) {
            return false;
        }
        if (active.joinedPlayers == null) {
            active.joinedPlayers = new HashSet<>();
        }
        if (active.returnIssuedPlayers == null) {
            active.returnIssuedPlayers = new HashSet<>();
        }
        if (active.returnTokens == null) {
            active.returnTokens = new HashMap<>();
        }
        if (active.returnPoints == null) {
            active.returnPoints = new HashMap<>();
        }
        return true;
    }

    private static boolean validUuid(String value) {
        if (value == null) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void save() {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(temporary, GSON.toJson(state), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            KalFactions.LOGGER.error("Failed to write {}", file, exception);
        }
    }

    public enum EntryStatus {
        STARTED_SESSION,
        JOINED_ACTIVE,
        OPERATOR_BYPASS,
        FACTION_REQUIRED,
        SCHEDULE_CLOSED,
        NO_SESSIONS_LEFT,
        SECOND_SESSION_CONFIRMATION_REQUIRED,
        DEATH_LOCKED,
        NO_SAFE_LANDING
    }

    public record EntryResult(EntryStatus status, ActiveSession session, int remainingSessions) {
        public boolean allowed() {
            return status == EntryStatus.STARTED_SESSION
                    || status == EntryStatus.JOINED_ACTIVE
                    || status == EntryStatus.OPERATOR_BYPASS;
        }
    }

    public record LandingPos(int x, int y, int z) {
        public BlockPos blockPos() {
            return new BlockPos(x, y, z);
        }
    }

    public record ActiveSession(
            UUID factionId,
            UUID sessionId,
            Instant startedAt,
            Instant endsAt,
            int ordinal,
            LandingPos landing,
            Set<UUID> joinedPlayers
    ) {
        public ActiveSession {
            joinedPlayers = Set.copyOf(joinedPlayers);
        }
    }

    public record PortalCharge(Instant ignitedAt, Instant expiresAt, String ignitedBy) {
    }

    public record EndedSession(UUID factionId, UUID sessionId, Set<UUID> joinedPlayers) {
        public EndedSession {
            joinedPlayers = Set.copyOf(joinedPlayers);
        }
    }

    public record PortalBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public PortalBounds {
            int lowX = Math.min(minX, maxX);
            int lowY = Math.min(minY, maxY);
            int lowZ = Math.min(minZ, maxZ);
            int highX = Math.max(minX, maxX);
            int highY = Math.max(minY, maxY);
            int highZ = Math.max(minZ, maxZ);
            minX = lowX;
            minY = lowY;
            minZ = lowZ;
            maxX = highX;
            maxY = highY;
            maxZ = highZ;
        }

        public boolean contains(BlockPos pos) {
            return pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getY() >= minY && pos.getY() <= maxY
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }
    }

    @FunctionalInterface
    public interface LandingAllocator {
        Optional<LandingPos> allocate(List<LandingPos> occupied, LandingPos previous, NetherRules rules);
    }

    private static final class State {
        private int formatVersion = FORMAT_VERSION;
        private boolean netherClosed;
        private boolean endClosed;
        private boolean netherWipePending;
        private boolean endWipePending;
        private long netherWipeGen;
        private long endWipeGen;
        private Long netherSeed;
        private Long endSeed;
        private PortalBoundsData netherPortal;
        private Long netherPortalIgnitedAt;
        private Long netherPortalChargedUntil;
        private String netherPortalIgnitedBy;
        private LandingData netherPortalAnchor;
        private Map<String, FactionLedger> netherFactions = new HashMap<>();
        private Map<String, ActiveSessionData> operatorSessions = new HashMap<>();
        private Map<String, String> deathLocks = new HashMap<>();
        private Map<String, String> playerSessions = new HashMap<>();
        private String lastAutomaticNetherOpening;
    }

    private static final class FactionLedger {
        private String usageDate;
        private int sessionsUsed;
        private List<ActiveSessionData> activeSessions = new ArrayList<>();
        // Format v2 migration field. It is cleared during normalization and never written again.
        private ActiveSessionData active;
        private LandingData lastLanding;
    }

    private static final class ActiveSessionData {
        private String id;
        private long startedAt;
        private long endsAt;
        private int ordinal;
        private LandingData landing;
        private Set<String> joinedPlayers = new HashSet<>();
        private Set<String> returnIssuedPlayers = new HashSet<>();
        private Map<String, String> returnTokens = new HashMap<>();
        private Map<String, LandingData> returnPoints = new HashMap<>();

        private ActiveSession toValue(UUID factionId) {
            Set<UUID> joined = joinedUuidSet();
            return new ActiveSession(
                    factionId,
                    UUID.fromString(id),
                    Instant.ofEpochMilli(startedAt),
                    Instant.ofEpochMilli(endsAt),
                    ordinal,
                    landing.toValue(),
                    joined
            );
        }

        private Set<UUID> joinedUuidSet() {
            Set<UUID> joined = new HashSet<>();
            for (String player : joinedPlayers) {
                try {
                    joined.add(UUID.fromString(player));
                } catch (IllegalArgumentException ignored) {
                }
            }
            return joined;
        }
    }

    private static final class LandingData {
        private int x;
        private int y;
        private int z;

        private static LandingData from(LandingPos value) {
            LandingData data = new LandingData();
            data.x = value.x();
            data.y = value.y();
            data.z = value.z();
            return data;
        }

        private static LandingData from(BlockPos value) {
            return from(new LandingPos(value.getX(), value.getY(), value.getZ()));
        }

        private LandingPos toValue() {
            return new LandingPos(x, y, z);
        }
    }

    private static final class PortalBoundsData {
        private int minX;
        private int minY;
        private int minZ;
        private int maxX;
        private int maxY;
        private int maxZ;

        private static PortalBoundsData from(PortalBounds value) {
            PortalBoundsData data = new PortalBoundsData();
            data.minX = value.minX();
            data.minY = value.minY();
            data.minZ = value.minZ();
            data.maxX = value.maxX();
            data.maxY = value.maxY();
            data.maxZ = value.maxZ();
            return data;
        }

        private PortalBounds toValue() {
            return new PortalBounds(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }
}
