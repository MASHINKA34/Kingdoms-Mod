package com.geydev.kalfactions.war;

import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.faction.InfluenceType;
import com.mojang.logging.LogUtils;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

/**
 * Server-authoritative store of active and ending wars, living next to {@link FactionManager} as
 * {@link SavedData} on the overworld. Holds the lazily captured chunk snapshots, hands out the
 * war-time build override the protection layer consults, and drains the rollback queue a few
 * chunks per tick when a war ends. Persisted so a war (and an unfinished rollback) survives a
 * server restart.
 */
public final class WarManager extends SavedData {
    public static final String DATA_NAME = "kingdoms_wars";
    public static final Factory<WarManager> FACTORY = new Factory<>(WarManager::new, WarManager::load);

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG_WARS = "wars";

    private final Map<UUID, War> wars = new LinkedHashMap<>();
    private final Map<UUID, UUID> factionToWar = new HashMap<>();
    private final Deque<RollbackTask> rollbackQueue = new ArrayDeque<>();
    private final transient Map<UUID, ServerBossEvent> bossBars = new HashMap<>();
    private final transient Map<UUID, long[]> blockPointWindows = new HashMap<>();
    private transient int bossSyncCounter;

    public static WarManager get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static WarManager get(ServerLevel level) {
        return get(Objects.requireNonNull(level, "level").getServer());
    }

    // ------------------------------------------------------------------ queries

    public synchronized Optional<War> warForFaction(UUID factionId) {
        UUID warId = factionToWar.get(factionId);
        return warId == null ? Optional.empty() : Optional.ofNullable(wars.get(warId));
    }

    /** True when both factions are in the same {@link War.State#ACTIVE} war (combat live). */
    public synchronized boolean areAtWar(UUID factionA, UUID factionB) {
        if (wars.isEmpty() || factionA == null || factionB == null || factionA.equals(factionB)) {
            return false;
        }
        War war = warFor(factionA);
        return war != null && war.isActive() && war.involves(factionB);
    }

    /**
     * War-time build override: a belligerent may break and place in the enemy faction's claims while
     * the war is active. The protection layer ORs this with its normal {@code canBuild} check.
     */
    public synchronized boolean canBuildInWar(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (wars.isEmpty()) {
            return false;
        }
        FactionManager factions = FactionManager.get(level);
        UUID owner = factions.getFactionIdAt(ClaimKey.of(level, pos)).orElse(null);
        if (owner == null) {
            return false;
        }
        UUID playerFaction = factions.getFactionIdForMember(player.getUUID()).orElse(null);
        return areAtWar(owner, playerFaction);
    }

    // ------------------------------------------------------------------ war points

    /**
     * Awards war points to the scoring faction's side of its active war. Points only count when the
     * opposing belligerent has at least one member online (anti-grief). Reaching the configured goal
     * ends the war with the scoring side as the winner.
     */
    public synchronized void addWarPoints(MinecraftServer server, UUID scoringFactionId, long amount) {
        if (amount <= 0L || scoringFactionId == null) {
            return;
        }
        War war = warFor(scoringFactionId);
        if (war == null || !war.isActive()) {
            return;
        }
        UUID opponent = war.opponentOf(scoringFactionId);
        if (!hasOnlineMember(server, opponent)) {
            return;
        }
        war.addPoints(scoringFactionId, amount);
        setDirty();
        if (war.points(scoringFactionId) >= ModConfigSpec.WAR_POINTS_GOAL.getAsLong()) {
            concludeWar(server, war, scoringFactionId);
        }
    }

    /** Block-break scoring entry point used by the protection layer; applies the per-minute cap. */
    public synchronized void recordWarBreak(ServerPlayer breaker, ServerLevel level, BlockState state) {
        UUID breakerFaction = FactionManager.get(level).getFactionIdForMember(breaker.getUUID()).orElse(null);
        if (breakerFaction == null || isNaturalBlock(state)) {
            return;
        }
        int cap = ModConfigSpec.WAR_BLOCK_POINT_CAP_PER_MINUTE.getAsInt();
        int points = ModConfigSpec.WAR_BLOCK_BREAK_POINTS.getAsInt();
        if (points <= 0 || !consumeBlockBudget(breakerFaction, points, cap)) {
            return;
        }
        addWarPoints(level.getServer(), breakerFaction, points);
    }

    private boolean consumeBlockBudget(UUID factionId, int points, int cap) {
        if (cap <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        long[] window = blockPointWindows.computeIfAbsent(factionId, ignored -> new long[]{now, 0L});
        if (now - window[0] >= 60_000L) {
            window[0] = now;
            window[1] = 0L;
        }
        if (window[1] + points > cap) {
            return false;
        }
        window[1] += points;
        return true;
    }

    private static boolean isNaturalBlock(BlockState state) {
        return state.is(Blocks.DIRT)
            || state.is(Blocks.COARSE_DIRT)
            || state.is(Blocks.GRASS_BLOCK)
            || state.is(Blocks.STONE)
            || state.is(Blocks.COBBLESTONE)
            || state.is(Blocks.DEEPSLATE)
            || state.is(Blocks.SAND)
            || state.is(Blocks.RED_SAND)
            || state.is(Blocks.GRAVEL)
            || state.is(Blocks.NETHERRACK);
    }

    private boolean hasOnlineMember(MinecraftServer server, UUID factionId) {
        Faction faction = FactionManager.get(server).getFactionById(factionId).orElse(null);
        if (faction == null) {
            return false;
        }
        for (UUID memberId : faction.members().keySet()) {
            if (server.getPlayerList().getPlayer(memberId) != null) {
                return true;
            }
        }
        return false;
    }

    /** A belligerent leader concedes the war; the opponent is declared the winner. */
    public synchronized Optional<UUID> surrender(MinecraftServer server, UUID factionId) {
        War war = warFor(factionId);
        if (war == null || !war.isActive()) {
            return Optional.empty();
        }
        UUID opponent = war.opponentOf(factionId);
        concludeWar(server, war, opponent);
        return Optional.ofNullable(opponent);
    }

    private void concludeWar(MinecraftServer server, War war, UUID winnerId) {
        if (!war.isActive() || winnerId == null) {
            beginRollback(server, war);
            return;
        }
        UUID loserId = war.opponentOf(winnerId);
        awardSpoils(server, winnerId, loserId);
        broadcastToServer(server, Component.translatable(
            "kingdoms.war.victory_broadcast",
            factionName(server, winnerId),
            factionName(server, loserId)
        ));
        beginRollback(server, war);
    }

    private void awardSpoils(MinecraftServer server, UUID winnerId, UUID loserId) {
        FactionManager factions = FactionManager.get(server);
        factions.grantInfluence(winnerId, InfluenceType.MILITARY, ModConfigSpec.INFLUENCE_WAR_WIN_INFLUENCE.getAsLong());
        Faction loser = factions.getFactionById(loserId).orElse(null);
        if (loser == null) {
            return;
        }
        long spoils = loser.treasuryBalance() * 30L / 100L;
        if (spoils > 0L
            && factions.withdraw(loserId, spoils).successful()
            && !factions.deposit(winnerId, spoils).successful()) {
            factions.deposit(loserId, spoils);
        }
    }

    private void refreshBossBars(MinecraftServer server) {
        bossBars.keySet().removeIf(warId -> {
            War war = wars.get(warId);
            if (war == null || !war.isActive()) {
                ServerBossEvent bar = bossBars.get(warId);
                if (bar != null) {
                    bar.removeAllPlayers();
                }
                return true;
            }
            return false;
        });
        boolean syncPlayers = (++bossSyncCounter % 40) == 0;
        long goal = Math.max(1L, ModConfigSpec.WAR_POINTS_GOAL.getAsLong());
        for (War war : wars.values()) {
            if (!war.isActive()) {
                continue;
            }
            ServerBossEvent bar = bossBars.computeIfAbsent(war.id(), id -> new ServerBossEvent(
                Component.empty(),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.NOTCHED_10
            ));
            bar.setName(Component.translatable(
                "kingdoms.war.bossbar",
                factionName(server, war.attackerFactionId()),
                war.attackerPoints(),
                war.defenderPoints(),
                factionName(server, war.defenderFactionId())
            ));
            bar.setProgress(Math.clamp(Math.max(war.attackerPoints(), war.defenderPoints()) / (float) goal, 0.0F, 1.0F));
            if (syncPlayers) {
                syncBossBarPlayers(server, war, bar);
            }
        }
    }

    private void syncBossBarPlayers(MinecraftServer server, War war, ServerBossEvent bar) {
        List<ServerPlayer> desired = new ArrayList<>();
        addOnlineMembers(server, war.attackerFactionId(), desired);
        addOnlineMembers(server, war.defenderFactionId(), desired);
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

    private static void addOnlineMembers(MinecraftServer server, UUID factionId, List<ServerPlayer> out) {
        FactionManager.get(server).getFactionById(factionId).ifPresent(faction -> {
            for (UUID memberId : faction.members().keySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(memberId);
                if (player != null) {
                    out.add(player);
                }
            }
        });
    }

    private void clearBossBar(UUID warId) {
        ServerBossEvent bar = bossBars.remove(warId);
        if (bar != null) {
            bar.removeAllPlayers();
        }
    }

    // ------------------------------------------------------------------ snapshot capture (copy-on-write)

    /**
     * Copy-on-write hook: if the chunk belongs to a faction in an active war and has not been captured
     * yet, snapshot it now (before the modification is applied). Cheap no-op once captured, or when no
     * war touches this chunk. Must be called before the block change for break/interact/explosion paths.
     */
    public synchronized void onChunkModified(ServerLevel level, ChunkPos chunkPos) {
        if (wars.isEmpty()) {
            return;
        }
        ClaimKey key = ClaimKey.of(level, chunkPos);
        War war = activeWarForClaim(level, key);
        if (war == null || war.hasSnapshot(key)) {
            return;
        }
        war.putSnapshot(key, WarChunkSnapshot.capture(level, chunkPos, level.registryAccess()));
        setDirty();
    }

    /**
     * Copy-on-write hook for placements. The place event fires after the new block is already in the
     * world, so for a chunk captured for the first time here we patch each placed position back to its
     * pre-placement state. Chunks already snapshotted (an earlier break/explosion captured the true
     * pre-war state) are left alone.
     */
    public synchronized void onBlocksPlaced(ServerLevel level, Map<BlockPos, BlockState> placedWithOldStates) {
        if (wars.isEmpty() || placedWithOldStates.isEmpty()) {
            return;
        }
        Map<ClaimKey, List<Map.Entry<BlockPos, BlockState>>> byChunk = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, BlockState> placed : placedWithOldStates.entrySet()) {
            byChunk.computeIfAbsent(ClaimKey.of(level, placed.getKey()), ignored -> new ArrayList<>()).add(placed);
        }

        boolean dirty = false;
        for (Map.Entry<ClaimKey, List<Map.Entry<BlockPos, BlockState>>> chunkEntry : byChunk.entrySet()) {
            ClaimKey key = chunkEntry.getKey();
            War war = activeWarForClaim(level, key);
            if (war == null || war.hasSnapshot(key)) {
                continue;
            }
            WarChunkSnapshot snapshot = WarChunkSnapshot.capture(level, key.chunk(), level.registryAccess());
            for (Map.Entry<BlockPos, BlockState> placed : chunkEntry.getValue()) {
                snapshot.setBlockState(placed.getKey(), placed.getValue());
                snapshot.removeBlockEntity(placed.getKey());
            }
            war.putSnapshot(key, snapshot);
            dirty = true;
        }
        if (dirty) {
            setDirty();
        }
    }

    // ------------------------------------------------------------------ lifecycle

    public synchronized DeclareResult declareWar(
        MinecraftServer server,
        UUID attackerFactionId,
        UUID defenderFactionId,
        long startGameTime
    ) {
        Objects.requireNonNull(attackerFactionId, "attackerFactionId");
        Objects.requireNonNull(defenderFactionId, "defenderFactionId");
        if (attackerFactionId.equals(defenderFactionId)) {
            return DeclareResult.SAME_FACTION;
        }
        if (factionToWar.containsKey(attackerFactionId)) {
            return DeclareResult.ATTACKER_BUSY;
        }
        if (factionToWar.containsKey(defenderFactionId)) {
            return DeclareResult.DEFENDER_BUSY;
        }

        UUID warId;
        do {
            warId = UUID.randomUUID();
        } while (wars.containsKey(warId));

        War war = new War(warId, attackerFactionId, defenderFactionId, War.State.ACTIVE, startGameTime);
        wars.put(warId, war);
        factionToWar.put(attackerFactionId, warId);
        factionToWar.put(defenderFactionId, warId);
        setDirty();

        Component attacker = factionName(server, attackerFactionId);
        Component defender = factionName(server, defenderFactionId);
        LOGGER.info("War declared: {} -> {} (war {})", attacker.getString(), defender.getString(), warId);
        broadcastToServer(server, Component.translatable("kingdoms.war.declared_broadcast", attacker, defender));
        return DeclareResult.SUCCESS;
    }

    private static void broadcastToServer(MinecraftServer server, Component message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
        }
    }

    /**
     * Ends the active war the faction is part of, moving it to {@link War.State#ENDING} and queuing its
     * snapshots for rollback. Returns the opponent's faction id on success (for caller feedback), or
     * empty if the faction was not in an active war.
     */
    public synchronized Optional<UUID> endWarForFaction(MinecraftServer server, UUID factionId) {
        War war = warFor(factionId);
        if (war == null || !war.isActive()) {
            return Optional.empty();
        }
        UUID opponent = war.opponentOf(factionId);
        beginRollback(server, war);
        return Optional.ofNullable(opponent);
    }

    private void beginRollback(MinecraftServer server, War war) {
        war.setState(War.State.ENDING);
        for (ClaimKey key : war.snapshotKeys()) {
            rollbackQueue.add(new RollbackTask(war.id(), key));
        }
        setDirty();
        LOGGER.info("War {} ending; {} chunk snapshot(s) queued for rollback", war.id(), war.snapshotCount());

        Component message = Component.translatable("kingdoms.war.ended_restoring");
        broadcast(server, war.attackerFactionId(), message);
        broadcast(server, war.defenderFactionId(), message);

        if (war.snapshotsEmpty()) {
            finalizeWar(server, war); // nothing was touched: complete immediately
        }
    }

    // ------------------------------------------------------------------ per-tick rollback

    public synchronized void tick(MinecraftServer server, int chunksPerTick, long autoEndTicks) {
        if (!wars.isEmpty()) {
            FactionManager factions = FactionManager.get(server);
            long now = server.overworld().getGameTime();
            for (War war : List.copyOf(wars.values())) {
                if (!war.isActive()) {
                    continue;
                }
                boolean factionsGone = factions.getFactionById(war.attackerFactionId()).isEmpty()
                    || factions.getFactionById(war.defenderFactionId()).isEmpty();
                boolean timedOut = autoEndTicks > 0L && now - war.startGameTime() >= autoEndTicks;
                if (factionsGone) {
                    beginRollback(server, war);
                } else if (timedOut) {
                    concludeWar(server, war, timeoutWinner(war));
                }
            }
            refreshBossBars(server);
        }

        if (rollbackQueue.isEmpty()) {
            return;
        }
        int budget = chunksPerTick;
        while (budget-- > 0 && !rollbackQueue.isEmpty()) {
            RollbackTask task = rollbackQueue.poll();
            War war = wars.get(task.warId());
            if (war == null) {
                continue;
            }
            WarChunkSnapshot snapshot = war.removeSnapshot(task.key());
            if (snapshot != null) {
                ServerLevel level = server.getLevel(task.key().dimension());
                if (level != null) {
                    snapshot.restore(level, task.key().chunk(), level.registryAccess());
                } else {
                    LOGGER.warn("Skipping war rollback for missing dimension {}", task.key().dimension().location());
                }
            }
            setDirty();
            if (war.snapshotsEmpty()) {
                finalizeWar(server, war);
            }
        }
    }

    private void finalizeWar(MinecraftServer server, War war) {
        war.setState(War.State.ENDED);
        wars.remove(war.id());
        factionToWar.remove(war.attackerFactionId(), war.id());
        factionToWar.remove(war.defenderFactionId(), war.id());
        clearBossBar(war.id());
        blockPointWindows.remove(war.attackerFactionId());
        blockPointWindows.remove(war.defenderFactionId());
        setDirty();
        LOGGER.info("War {} finished and removed", war.id());

        broadcastToServer(server, Component.translatable(
            "kingdoms.war.over_broadcast",
            factionName(server, war.attackerFactionId()),
            factionName(server, war.defenderFactionId())
        ));
    }

    // ------------------------------------------------------------------ helpers

    private static UUID timeoutWinner(War war) {
        if (war.attackerPoints() > war.defenderPoints()) {
            return war.attackerFactionId();
        }
        if (war.defenderPoints() > war.attackerPoints()) {
            return war.defenderFactionId();
        }
        return null;
    }

    private War warFor(UUID factionId) {
        UUID warId = factionToWar.get(factionId);
        return warId == null ? null : wars.get(warId);
    }

    private War activeWarForClaim(ServerLevel level, ClaimKey key) {
        UUID owner = FactionManager.get(level).getFactionIdAt(key).orElse(null);
        if (owner == null) {
            return null;
        }
        War war = warFor(owner);
        return war != null && war.isActive() ? war : null;
    }

    private static Component factionName(MinecraftServer server, UUID factionId) {
        return FactionManager.get(server).getFactionById(factionId)
            .map(faction -> (Component) Component.literal(faction.name()))
            .orElseGet(() -> Component.translatable("kingdoms.command.faction.war.opponent.disbanded"));
    }

    private static void broadcast(MinecraftServer server, UUID factionId, Component message) {
        FactionManager.get(server).getFactionById(factionId).ifPresent(faction -> {
            for (UUID memberId : faction.members().keySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(memberId);
                if (player != null) {
                    player.sendSystemMessage(message);
                }
            }
        });
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag warsTag = new ListTag();
        for (War war : wars.values()) {
            warsTag.add(war.save());
        }
        tag.put(TAG_WARS, warsTag);
        return tag;
    }

    private static WarManager load(CompoundTag tag, HolderLookup.Provider registries) {
        WarManager manager = new WarManager();
        boolean repaired = false;
        ListTag warsTag = tag.getList(TAG_WARS, Tag.TAG_COMPOUND);
        for (int index = 0; index < warsTag.size(); index++) {
            Optional<War> loaded = War.load(warsTag.getCompound(index));
            if (loaded.isEmpty()) {
                repaired = true;
                LOGGER.warn("Skipped invalid war entry at NBT index {}", index);
                continue;
            }
            War war = loaded.get();
            if (war.state() == War.State.ENDED
                || manager.factionToWar.containsKey(war.attackerFactionId())
                || manager.factionToWar.containsKey(war.defenderFactionId())) {
                repaired = true;
                continue;
            }
            manager.wars.put(war.id(), war);
            manager.factionToWar.put(war.attackerFactionId(), war.id());
            manager.factionToWar.put(war.defenderFactionId(), war.id());
            if (war.state() == War.State.ENDING) {
                for (ClaimKey key : war.snapshotKeys()) {
                    manager.rollbackQueue.add(new RollbackTask(war.id(), key));
                }
            }
        }
        if (repaired) {
            manager.setDirty();
        }
        return manager;
    }

    public enum DeclareResult {
        SUCCESS,
        SAME_FACTION,
        ATTACKER_BUSY,
        DEFENDER_BUSY
    }

    private record RollbackTask(UUID warId, ClaimKey key) {
    }
}
