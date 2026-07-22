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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
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
    private static final int FORMAT_VERSION = 2;
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

    public synchronized EntryResult authorizeNetherEntry(
            UUID factionId,
            UUID playerId,
            Instant now,
            boolean operator,
            LandingAllocator allocator
    ) {
        if (operator) {
            return new EntryResult(EntryStatus.OPERATOR_BYPASS, null, rules.sessionsPerDay());
        }
        if (state.netherClosed || !NetherSchedulePolicy.isOpen(now)) {
            return new EntryResult(EntryStatus.SCHEDULE_CLOSED, null, remainingSessions(factionId, now));
        }
        String factionKey = factionId.toString();
        FactionLedger ledger = state.netherFactions.computeIfAbsent(factionKey, ignored -> new FactionLedger());
        if (ledger.active != null && ledger.active.endsAt <= now.toEpochMilli()) {
            pendingEndedSessions.add(retireSession(factionId, ledger));
            save();
        }
        ActiveSessionData active = activeData(ledger, now);
        if (active != null) {
            if (active.id.equals(state.deathLocks.get(playerId.toString()))) {
                return new EntryResult(EntryStatus.DEATH_LOCKED, active.toValue(factionId), remainingSessions(ledger, now));
            }
            boolean joined = active.joinedPlayers.add(playerId.toString());
            if (joined) {
                save();
            }
            return new EntryResult(EntryStatus.JOINED_ACTIVE, active.toValue(factionId), remainingSessions(ledger, now));
        }
        if (!NetherSchedulePolicy.canStartSession(now, rules.sessionDuration(), rules.requireFullSessionBeforeClose())) {
            return new EntryResult(EntryStatus.TOO_LATE_TO_START, null, remainingSessions(ledger, now));
        }
        resetUsageDate(ledger, now);
        if (ledger.sessionsUsed >= rules.sessionsPerDay()) {
            return new EntryResult(EntryStatus.NO_SESSIONS_LEFT, null, 0);
        }
        List<LandingPos> occupied = state.netherFactions.values().stream()
                .map(value -> activeData(value, now))
                .filter(java.util.Objects::nonNull)
                .map(value -> value.landing.toValue())
                .toList();
        LandingPos previous = ledger.lastLanding == null ? null : ledger.lastLanding.toValue();
        Optional<LandingPos> allocated = allocator.allocate(occupied, previous, rules);
        if (allocated.isEmpty()) {
            return new EntryResult(EntryStatus.NO_SAFE_LANDING, null, rules.sessionsPerDay() - ledger.sessionsUsed);
        }
        Instant naturalEnd = now.plus(rules.sessionDuration());
        Instant end = naturalEnd.isAfter(NetherSchedulePolicy.closeInstant(now))
                ? NetherSchedulePolicy.closeInstant(now)
                : naturalEnd;
        ActiveSessionData created = new ActiveSessionData();
        created.id = UUID.randomUUID().toString();
        created.startedAt = now.toEpochMilli();
        created.endsAt = end.toEpochMilli();
        created.landing = LandingData.from(allocated.get());
        created.joinedPlayers.add(playerId.toString());
        ledger.active = created;
        ledger.lastLanding = created.landing;
        ledger.sessionsUsed++;
        save();
        return new EntryResult(
                EntryStatus.STARTED_SESSION,
                created.toValue(factionId),
                Math.max(0, rules.sessionsPerDay() - ledger.sessionsUsed)
        );
    }

    public synchronized Optional<ActiveSession> activeSession(UUID factionId, Instant now) {
        FactionLedger ledger = state.netherFactions.get(factionId.toString());
        ActiveSessionData active = activeData(ledger, now);
        return active == null ? Optional.empty() : Optional.of(active.toValue(factionId));
    }

    public synchronized Optional<ActiveSession> activeSessionById(UUID sessionId, Instant now) {
        for (Map.Entry<String, FactionLedger> entry : state.netherFactions.entrySet()) {
            ActiveSessionData active = activeData(entry.getValue(), now);
            if (active != null && active.id.equals(sessionId.toString())) {
                return Optional.of(active.toValue(UUID.fromString(entry.getKey())));
            }
        }
        return Optional.empty();
    }

    public synchronized boolean markDeath(UUID factionId, UUID playerId, Instant now) {
        FactionLedger ledger = state.netherFactions.get(factionId.toString());
        ActiveSessionData active = activeData(ledger, now);
        if (active == null) {
            return false;
        }
        state.deathLocks.put(playerId.toString(), active.id);
        save();
        return true;
    }

    public synchronized boolean isDeathLocked(UUID factionId, UUID playerId, Instant now) {
        FactionLedger ledger = state.netherFactions.get(factionId.toString());
        ActiveSessionData active = activeData(ledger, now);
        return active != null && active.id.equals(state.deathLocks.get(playerId.toString()));
    }

    public synchronized boolean isValidReturn(ReturnBinding binding, Instant now) {
        ActiveSessionData active = activeDataById(binding.sessionId(), now);
        return active != null && binding.token().toString().equals(active.returnTokens.get(binding.playerId().toString()));
    }

    public synchronized Optional<ReturnBinding> issueReturn(UUID sessionId, UUID playerId, Instant now) {
        ActiveSessionData active = activeDataById(sessionId, now);
        if (active == null || !active.joinedPlayers.contains(playerId.toString())
                || !active.returnIssuedPlayers.add(playerId.toString())) {
            return Optional.empty();
        }
        UUID token = UUID.randomUUID();
        active.returnTokens.put(playerId.toString(), token.toString());
        save();
        return Optional.of(new ReturnBinding(playerId, sessionId, token));
    }

    public synchronized boolean consumeReturn(ReturnBinding binding, Instant now) {
        ActiveSessionData active = activeDataById(binding.sessionId(), now);
        if (active == null || !binding.token().toString().equals(active.returnTokens.get(binding.playerId().toString()))) {
            return false;
        }
        active.returnTokens.remove(binding.playerId().toString());
        save();
        return true;
    }

    public synchronized void invalidateReturnsForPlayer(UUID playerId) {
        boolean changed = false;
        for (FactionLedger ledger : state.netherFactions.values()) {
            if (ledger.active != null) {
                changed |= ledger.active.returnTokens.remove(playerId.toString()) != null;
            }
        }
        if (changed) {
            save();
        }
    }

    public synchronized int remainingSessions(UUID factionId, Instant now) {
        FactionLedger ledger = state.netherFactions.get(factionId.toString());
        return ledger == null ? rules.sessionsPerDay() : remainingSessions(ledger, now);
    }

    public synchronized List<EndedSession> expireSessions(Instant now, Predicate<UUID> factionExists) {
        List<EndedSession> ended = new ArrayList<>(pendingEndedSessions);
        pendingEndedSessions.clear();
        for (Map.Entry<String, FactionLedger> entry : state.netherFactions.entrySet()) {
            UUID factionId;
            try {
                factionId = UUID.fromString(entry.getKey());
            } catch (IllegalArgumentException exception) {
                continue;
            }
            ActiveSessionData active = entry.getValue().active;
            if (active != null && (active.endsAt <= now.toEpochMilli() || !factionExists.test(factionId))) {
                ended.add(retireSession(factionId, entry.getValue()));
            }
        }
        if (!ended.isEmpty()) {
            save();
        }
        return List.copyOf(ended);
    }

    public synchronized List<ActiveSession> activeSessions(Instant now) {
        List<ActiveSession> result = new ArrayList<>();
        for (Map.Entry<String, FactionLedger> entry : state.netherFactions.entrySet()) {
            ActiveSessionData active = activeData(entry.getValue(), now);
            if (active != null) {
                try {
                    result.add(active.toValue(UUID.fromString(entry.getKey())));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return List.copyOf(result);
    }

    public synchronized boolean updateWipeSchedule(Instant now) {
        LocalDate today = now.atZone(rules.wipeTimezone()).toLocalDate();
        if (state.lastNetherWipeBaseline == null) {
            state.lastNetherWipeBaseline = today.toString();
            save();
            return false;
        }
        LocalDate baseline;
        try {
            baseline = LocalDate.parse(state.lastNetherWipeBaseline);
        } catch (RuntimeException exception) {
            baseline = today;
            state.lastNetherWipeBaseline = today.toString();
            save();
            return false;
        }
        ZonedDateTime local = now.atZone(rules.wipeTimezone());
        boolean due = !today.isBefore(baseline.plusDays(rules.wipeIntervalDays()))
                && !local.toLocalTime().isBefore(LocalTime.of(rules.wipeHour(), 0));
        if (due && !state.netherWipePending) {
            state.netherWipePending = true;
            save();
            return true;
        }
        return false;
    }

    public synchronized boolean claimDailyResetNotification(Instant now) {
        String today = NetherSchedulePolicy.date(now).toString();
        if (today.equals(state.lastDailyResetNotification)) {
            return false;
        }
        state.lastDailyResetNotification = today;
        save();
        return true;
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
            if (state.netherWipePending == pending) {
                return false;
            }
            state.netherWipePending = pending;
        } else if (Level.END.equals(dimension)) {
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
        boolean changed = false;
        if (state.netherWipePending && wipeFolder(server, Level.NETHER)) {
            state.netherWipePending = false;
            state.netherWipeGen++;
            state.netherSeed = nextSeed(generationSeed(Level.NETHER, server.getWorldData().worldGenOptions().seed()));
            state.netherFactions.clear();
            state.deathLocks.clear();
            state.lastNetherWipeBaseline = Instant.now().atZone(rules.wipeTimezone()).toLocalDate().toString();
            wipedThisStartup.add(Level.NETHER);
            changed = true;
        }
        if (state.endWipePending && wipeFolder(server, Level.END)) {
            state.endWipePending = false;
            state.endWipeGen++;
            state.endSeed = nextSeed(generationSeed(Level.END, server.getWorldData().worldGenOptions().seed()));
            server.getWorldData().setEndDragonFightData(EndDragonFight.Data.DEFAULT);
            wipedThisStartup.add(Level.END);
            changed = true;
        }
        if (changed) {
            save();
        }
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
        }
    }

    private ActiveSessionData activeData(FactionLedger ledger, Instant now) {
        if (ledger == null || ledger.active == null) {
            return null;
        }
        if (ledger.active.endsAt <= now.toEpochMilli()) {
            return null;
        }
        return ledger.active;
    }

    private ActiveSessionData activeDataById(UUID sessionId, Instant now) {
        for (FactionLedger ledger : state.netherFactions.values()) {
            ActiveSessionData active = activeData(ledger, now);
            if (active != null && active.id.equals(sessionId.toString())) {
                return active;
            }
        }
        return null;
    }

    private EndedSession retireSession(UUID factionId, FactionLedger ledger) {
        ActiveSessionData active = ledger.active;
        EndedSession ended = new EndedSession(factionId, UUID.fromString(active.id), active.joinedUuidSet());
        clearSession(ledger, active.id);
        return ended;
    }

    private void clearSession(FactionLedger ledger, String sessionId) {
        ledger.active = null;
        state.deathLocks.entrySet().removeIf(entry -> sessionId.equals(entry.getValue()));
    }

    private static long nextSeed(long currentSeed) {
        long seed;
        do {
            seed = ThreadLocalRandom.current().nextLong();
        } while (seed == currentSeed);
        return seed;
    }

    private boolean wipeFolder(MinecraftServer server, ResourceKey<Level> dimension) {
        Path folder = DimensionType.getStorageFolder(dimension, server.getWorldPath(LevelResource.ROOT).normalize());
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
        state.formatVersion = FORMAT_VERSION;
        if (state.netherFactions == null) {
            state.netherFactions = new HashMap<>();
        }
        if (state.deathLocks == null) {
            state.deathLocks = new HashMap<>();
        }
        state.netherFactions.entrySet().removeIf(entry -> !validUuid(entry.getKey()) || entry.getValue() == null);
        for (FactionLedger ledger : state.netherFactions.values()) {
            ledger.sessionsUsed = Math.max(0, ledger.sessionsUsed);
            if (ledger.active != null && !validActive(ledger.active)) {
                ledger.active = null;
            }
            if (ledger.active != null) {
                ledger.active.joinedPlayers.removeIf(player -> !validUuid(player));
                ledger.active.returnIssuedPlayers.removeIf(player -> !validUuid(player));
                ledger.active.returnTokens.entrySet().removeIf(entry -> !validUuid(entry.getKey()) || !validUuid(entry.getValue()));
            }
        }
        state.deathLocks.entrySet().removeIf(entry -> !validUuid(entry.getKey()) || !validUuid(entry.getValue()));
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
        SCHEDULE_CLOSED,
        TOO_LATE_TO_START,
        NO_SESSIONS_LEFT,
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
            LandingPos landing,
            Set<UUID> joinedPlayers
    ) {
        public ActiveSession {
            joinedPlayers = Set.copyOf(joinedPlayers);
        }
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
        private Map<String, FactionLedger> netherFactions = new HashMap<>();
        private Map<String, String> deathLocks = new HashMap<>();
        private String lastNetherWipeBaseline;
        private String lastDailyResetNotification;
    }

    private static final class FactionLedger {
        private String usageDate;
        private int sessionsUsed;
        private ActiveSessionData active;
        private LandingData lastLanding;
    }

    private static final class ActiveSessionData {
        private String id;
        private long startedAt;
        private long endsAt;
        private LandingData landing;
        private Set<String> joinedPlayers = new HashSet<>();
        private Set<String> returnIssuedPlayers = new HashSet<>();
        private Map<String, String> returnTokens = new HashMap<>();

        private ActiveSession toValue(UUID factionId) {
            Set<UUID> joined = joinedUuidSet();
            return new ActiveSession(
                    factionId,
                    UUID.fromString(id),
                    Instant.ofEpochMilli(startedAt),
                    Instant.ofEpochMilli(endsAt),
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
