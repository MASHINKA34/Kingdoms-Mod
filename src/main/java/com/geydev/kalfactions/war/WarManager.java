package com.geydev.kalfactions.war;

import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.command.NumismaticsEconomy;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.faction.InfluenceType;
import com.mojang.logging.LogUtils;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
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
    private static final String TAG_PENDING_SPOILS = "pendingSpoils";

    private final Map<UUID, War> wars = new LinkedHashMap<>();
    private final Map<UUID, UUID> factionToWar = new HashMap<>();
    private final Map<UUID, PendingSpoils> pendingSpoils = new LinkedHashMap<>();
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

    /**
     * True when both factions are in the same {@link War.State#ACTIVE} war and sit on opposing sides,
     * i.e. they are enemies. Two factions on the same side (a defender and a joined ally) are not at war.
     */
    public synchronized boolean areAtWar(UUID factionA, UUID factionB) {
        if (wars.isEmpty() || factionA == null || factionB == null || factionA.equals(factionB)) {
            return false;
        }
        War war = warFor(factionA);
        return war != null && war.isActive() && war.opposingSides(factionA, factionB);
    }

    /**
     * Read-only check for the UI: whether {@code joinerFactionId} could join the defense of
     * {@code allyFactionId}. Mirrors {@link #joinWar} without mutating. Assumes the two are allied.
     */
    public synchronized boolean canJoinDefense(UUID joinerFactionId, UUID allyFactionId) {
        if (joinerFactionId == null || allyFactionId == null || joinerFactionId.equals(allyFactionId)) {
            return false;
        }
        if (factionToWar.containsKey(joinerFactionId)) {
            return false;
        }
        War war = warFor(allyFactionId);
        if (war == null || !war.isActive() || war.sideOf(allyFactionId) != War.Side.DEFENDER) {
            return false;
        }
        return !war.involves(joinerFactionId);
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
        War.Side scoringSide = war.sideOf(scoringFactionId);
        if (scoringSide == null) {
            return;
        }
        War.Side opposingSide = scoringSide == War.Side.ATTACKER ? War.Side.DEFENDER : War.Side.ATTACKER;
        if (!hasOnlineMemberOnSide(server, war, opposingSide)) {
            return;
        }
        war.addPoints(scoringFactionId, amount);
        setDirty();
        if (war.pointsForSide(scoringSide) >= ModConfigSpec.WAR_POINTS_GOAL.getAsLong()) {
            concludeWar(server, war, war.leadOf(scoringSide));
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

    private boolean hasOnlineMemberOnSide(MinecraftServer server, War war, War.Side side) {
        Set<UUID> factionsOnSide = side == War.Side.ATTACKER ? war.attackerSide() : war.defenderSide();
        for (UUID factionId : factionsOnSide) {
            if (hasOnlineMember(server, factionId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A faction leaves the war. A lead belligerent concedes (the opposing side wins); a joined ally
     * merely withdraws its support, rolling back its own claims while the war continues.
     */
    public synchronized Optional<UUID> surrender(MinecraftServer server, UUID factionId) {
        War war = warFor(factionId);
        if (war == null || !war.isActive()) {
            return Optional.empty();
        }
        UUID opponent = war.opponentOf(factionId);
        if (war.isLead(factionId)) {
            concludeWar(server, war, opponent);
        } else {
            withdrawParticipant(server, war, factionId);
        }
        return Optional.ofNullable(opponent);
    }

    /**
     * An ally of the defender voluntarily joins the defending side. The joiner's claims become part of
     * the war zone and its members can score war points like the original belligerents. Only the
     * defender's allies may join (a defensive coalition); the attacker cannot recruit.
     */
    public synchronized JoinResult joinWar(MinecraftServer server, UUID joinerFactionId, UUID allyFactionId) {
        Objects.requireNonNull(joinerFactionId, "joinerFactionId");
        Objects.requireNonNull(allyFactionId, "allyFactionId");
        if (joinerFactionId.equals(allyFactionId)) {
            return JoinResult.SAME_FACTION;
        }
        if (factionToWar.containsKey(joinerFactionId)) {
            return JoinResult.JOINER_BUSY;
        }
        FactionManager factions = FactionManager.get(server);
        if (!factions.areAllied(joinerFactionId, allyFactionId)) {
            return JoinResult.NOT_ALLIED;
        }
        War war = warFor(allyFactionId);
        if (war == null || !war.isActive() || war.sideOf(allyFactionId) != War.Side.DEFENDER) {
            return JoinResult.ALLY_NOT_DEFENDING;
        }
        if (factions.areAllied(joinerFactionId, war.attackerFactionId())) {
            return JoinResult.ALLIED_WITH_ENEMY;
        }
        if (!war.joinDefenders(joinerFactionId)) {
            return JoinResult.JOINER_BUSY;
        }
        factionToWar.put(joinerFactionId, war.id());
        setDirty();

        Component joiner = factionName(server, joinerFactionId);
        Component defender = factionName(server, allyFactionId);
        Component attacker = factionName(server, war.attackerFactionId());
        LOGGER.info("Faction {} joined war {} on the defending side of {}",
            joiner.getString(), war.id(), defender.getString());
        broadcastToServer(server, Component.translatable("kingdoms.war.joined_broadcast", joiner, defender, attacker));
        return JoinResult.SUCCESS;
    }

    /** Drops a joined ally from the war and restores its captured claims immediately. */
    private void withdrawParticipant(MinecraftServer server, War war, UUID factionId) {
        FactionManager factions = FactionManager.get(server);
        for (ClaimKey key : war.snapshotKeys()) {
            UUID owner = factions.getFactionIdAt(key).orElse(null);
            if (!factionId.equals(owner)) {
                continue;
            }
            WarChunkSnapshot snapshot = war.removeSnapshot(key);
            if (snapshot == null) {
                continue;
            }
            ServerLevel level = server.getLevel(key.dimension());
            if (level != null) {
                snapshot.restore(level, key.chunk(), level.registryAccess());
            }
        }
        war.removeParticipant(factionId);
        factionToWar.remove(factionId, war.id());
        blockPointWindows.remove(factionId);
        setDirty();

        Component joiner = factionName(server, factionId);
        broadcast(server, war.attackerFactionId(), Component.translatable("kingdoms.war.ally_withdrew", joiner));
        broadcast(server, war.defenderFactionId(), Component.translatable("kingdoms.war.ally_withdrew", joiner));
    }

    private void concludeWar(MinecraftServer server, War war, UUID winnerId) {
        if (!war.isActive() || winnerId == null) {
            beginRollback(server, war);
            return;
        }
        War.Side winningSide = war.sideOf(winnerId);
        if (winningSide == null) {
            beginRollback(server, war);
            return;
        }
        UUID winnerLead = war.leadOf(winningSide);
        UUID loserLead = war.opponentOf(winnerLead);
        Set<UUID> winningFactions = winningSide == War.Side.ATTACKER ? war.attackerSide() : war.defenderSide();
        prepareSpoils(server, war.id(), winnerLead, loserLead, winningFactions);
        broadcastToServer(server, Component.translatable(
            "kingdoms.war.victory_broadcast",
            factionName(server, winnerLead),
            factionName(server, loserLead)
        ));
        beginRollback(server, war);
    }

    private void prepareSpoils(
            MinecraftServer server,
            UUID spoilsId,
            UUID winnerId,
            UUID loserId,
            Set<UUID> winningFactions
    ) {
        FactionManager factions = FactionManager.get(server);
        long winInfluence = ModConfigSpec.INFLUENCE_WAR_WIN_INFLUENCE.getAsLong();
        // Every faction on the winning side (the lead belligerent and any joined allies) earns the
        // military-influence reward; only the lead winner gets the lootable money/resource spoils.
        for (UUID winningFactionId : winningFactions) {
            factions.grantInfluence(winningFactionId, InfluenceType.MILITARY, winInfluence);
            if (winInfluence <= 0L) {
                continue;
            }
            factions.getFactionById(winningFactionId).ifPresent(winner -> {
                for (UUID memberId : winner.members().keySet()) {
                    net.minecraft.server.level.ServerPlayer member = server.getPlayerList().getPlayer(memberId);
                    if (member != null) {
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                                member,
                                new com.geydev.kalfactions.net.FactionPayloads.S2CInfluenceGain(
                                        InfluenceType.MILITARY.id(), winInfluence
                                )
                        );
                    }
                }
            });
        }
        if (factions.getFactionById(winnerId).isEmpty() || factions.getFactionById(loserId).isEmpty()) {
            return;
        }
        PendingSpoils spoils = new PendingSpoils(
                spoilsId,
                winnerId,
                loserId,
                server.overworld().getGameTime()
        );
        pendingSpoils.put(spoilsId, spoils);
        setDirty();
    }

    public synchronized Optional<PendingSpoilsView> pendingSpoilsForWinner(MinecraftServer server, UUID winnerId) {
        if (winnerId == null) {
            return Optional.empty();
        }
        for (PendingSpoils spoils : List.copyOf(pendingSpoils.values())) {
            if (!spoils.winnerId().equals(winnerId)) {
                continue;
            }
            Optional<PendingSpoilsView> view = spoilsView(server, spoils);
            if (view.isPresent()) {
                return view;
            }
            pendingSpoils.remove(spoils.spoilsId());
            setDirty();
        }
        return Optional.empty();
    }

    public synchronized Optional<PendingSpoilsView> pendingSpoils(MinecraftServer server, UUID spoilsId) {
        PendingSpoils spoils = pendingSpoils.get(spoilsId);
        if (spoils == null) {
            return Optional.empty();
        }
        Optional<PendingSpoilsView> view = spoilsView(server, spoils);
        if (view.isEmpty()) {
            pendingSpoils.remove(spoilsId);
            setDirty();
        }
        return view;
    }

    public synchronized ClaimSpoilsResult claimSpoils(
            MinecraftServer server,
            ServerPlayer rewardReceiver,
            UUID winnerId,
            UUID spoilsId,
            SpoilsChoice choice
    ) {
        if (server == null || rewardReceiver == null || winnerId == null || spoilsId == null || choice == null) {
            return ClaimSpoilsResult.NOT_FOUND;
        }
        PendingSpoils spoils = pendingSpoils.get(spoilsId);
        if (spoils == null) {
            return ClaimSpoilsResult.NOT_FOUND;
        }
        if (!spoils.winnerId().equals(winnerId)) {
            return ClaimSpoilsResult.NOT_WINNER;
        }

        FactionManager factions = FactionManager.get(server);
        Faction winner = factions.getFactionById(spoils.winnerId()).orElse(null);
        Faction loser = factions.getFactionById(spoils.loserId()).orElse(null);
        if (winner == null || loser == null) {
            pendingSpoils.remove(spoilsId);
            setDirty();
            return ClaimSpoilsResult.NOT_FOUND;
        }

        boolean transferred = true;
        if (choice.moneyPercent() > 0) {
            transferred = transferMoney(server, factions, spoils.winnerId(), loser, choice.moneyPercent());
        }
        if (transferred && choice.resourcePercent() > 0) {
            transferred = transferResources(server, rewardReceiver, loser, choice.resourcePercent());
        }
        if (!transferred) {
            return ClaimSpoilsResult.TRANSFER_FAILED;
        }

        pendingSpoils.remove(spoilsId);
        setDirty();
        broadcast(server, spoils.winnerId(), Component.translatable(
                "kingdoms.war.spoils_claimed_winner",
                factionName(server, spoils.loserId())
        ));
        broadcast(server, spoils.loserId(), Component.translatable(
                "kingdoms.war.spoils_claimed_loser",
                factionName(server, spoils.winnerId())
        ));
        return ClaimSpoilsResult.SUCCESS;
    }

    private boolean transferMoney(MinecraftServer server, FactionManager factions, UUID winnerId, Faction loser, int percent) {
        long amount = percent(totalMoney(server, loser), percent);
        if (amount <= 0L) {
            return true;
        }
        long collected = 0L;
        long fromTreasury = Math.min(amount, loser.treasuryBalance());
        if (fromTreasury > 0L && factions.withdraw(loser.id(), fromTreasury).successful()) {
            collected += fromTreasury;
        }
        long remaining = amount - collected;
        for (UUID memberId : loser.members().keySet()) {
            if (remaining <= 0L) {
                break;
            }
            ServerPlayer member = server.getPlayerList().getPlayer(memberId);
            if (member == null) {
                continue;
            }
            long take = Math.min(remaining, NumismaticsEconomy.balance(member));
            if (take <= 0L) {
                continue;
            }
            NumismaticsEconomy.Payment payment = NumismaticsEconomy.preparePayment(member, take);
            if (payment.ready() && NumismaticsEconomy.commitPayment(member, payment)) {
                collected += take;
                remaining -= take;
            }
        }
        if (collected <= 0L) {
            return false;
        }
        if (factions.deposit(winnerId, collected).successful()) {
            return true;
        }
        factions.deposit(loser.id(), collected);
        return false;
    }

    private long totalMoney(MinecraftServer server, Faction loser) {
        long total = Math.max(0L, loser.treasuryBalance());
        for (UUID memberId : loser.members().keySet()) {
            ServerPlayer member = server.getPlayerList().getPlayer(memberId);
            if (member != null) {
                total += NumismaticsEconomy.balance(member);
            }
        }
        return total;
    }

    private Optional<PendingSpoilsView> spoilsView(MinecraftServer server, PendingSpoils spoils) {
        Faction loser = FactionManager.get(server).getFactionById(spoils.loserId()).orElse(null);
        if (loser == null || FactionManager.get(server).getFactionById(spoils.winnerId()).isEmpty()) {
            return Optional.empty();
        }
        List<ResourceBucket> buckets = selectedResourceBuckets(server, loser);
        return Optional.of(new PendingSpoilsView(
                spoils.spoilsId(),
                spoils.loserId(),
                loser.name(),
                percent(totalMoney(server, loser), 30),
                bucketCount(buckets, 0, 30),
                bucketItemId(buckets, 0),
                bucketCount(buckets, 1, 30),
                bucketItemId(buckets, 1),
                bucketCount(buckets, 2, 30),
                bucketItemId(buckets, 2)
        ));
    }

    private long bucketCount(List<ResourceBucket> buckets, int index, int percent) {
        return index < buckets.size() ? percent(buckets.get(index).count(), percent) : 0L;
    }

    private static String bucketItemId(List<ResourceBucket> buckets, int index) {
        if (index >= buckets.size()) {
            return "";
        }
        return BuiltInRegistries.ITEM.getKey(buckets.get(index).item()).toString();
    }

    private static long percent(long value, int percent) {
        if (value <= 0L || percent <= 0) {
            return 0L;
        }
        return (value / 100L) * percent + ((value % 100L) * percent) / 100L;
    }

    private boolean transferResources(MinecraftServer server, ServerPlayer receiver, Faction loser, int percent) {
        List<ResourceBucket> buckets = selectedResourceBuckets(server, loser);
        for (ResourceBucket bucket : buckets) {
            long remaining = percent(bucket.count(), percent);
            if (remaining <= 0L) {
                continue;
            }
            for (ResourceSource source : bucket.sources()) {
                if (remaining <= 0L) {
                    break;
                }
                ItemStack stack = source.stack();
                if (!isResourceStack(stack) || stack.getItem() != bucket.item()) {
                    continue;
                }
                int taken = (int) Math.min(remaining, stack.getCount());
                ItemStack payout = source.extract(taken);
                if (!payout.isEmpty()) {
                    giveResource(receiver, payout);
                    remaining -= payout.getCount();
                }
            }
        }
        receiver.getInventory().setChanged();
        receiver.inventoryMenu.broadcastChanges();
        return true;
    }

    private List<ResourceBucket> selectedResourceBuckets(MinecraftServer server, Faction loser) {
        Map<Item, ResourceBucket> buckets = new LinkedHashMap<>();
        collectResourceBuckets(server, loser, buckets);
        return buckets.values().stream()
                .filter(bucket -> bucket.count() > 0L)
                .sorted(Comparator
                        .comparingLong(ResourceBucket::count)
                        .reversed()
                        .thenComparing(bucket -> bucket.item().getDescriptionId()))
                .limit(3)
                .toList();
    }

    private void collectResourceBuckets(MinecraftServer server, Faction loser, Map<Item, ResourceBucket> buckets) {
        Set<ClaimKey> territory = new LinkedHashSet<>(loser.claims());
        territory.addAll(loser.outpostChunks());
        for (ClaimKey claim : territory) {
            ServerLevel level = server.getLevel(claim.dimension());
            if (level == null) {
                continue;
            }
            LevelChunk chunk = level.getChunk(claim.x(), claim.z());
            for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                if (blockEntity instanceof Container container) {
                    collectContainerResources(container, buckets);
                } else {
                    IItemHandler handler = level.getCapability(
                            Capabilities.ItemHandler.BLOCK,
                            blockEntity.getBlockPos(),
                            null
                    );
                    if (handler != null) {
                        collectHandlerResources(handler, buckets);
                    }
                }
            }
        }
        for (UUID memberId : loser.members().keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberId);
            if (player != null) {
                collectContainerResources(player.getInventory(), buckets);
            }
        }
    }

    private static void collectContainerResources(Container container, Map<Item, ResourceBucket> buckets) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!isResourceStack(stack)) {
                continue;
            }
            buckets.computeIfAbsent(stack.getItem(), ResourceBucket::new)
                    .add(stack.getCount(), new ContainerResourceSource(container, slot));
        }
    }

    private static void collectHandlerResources(IItemHandler handler, Map<Item, ResourceBucket> buckets) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!isResourceStack(stack)) {
                continue;
            }
            buckets.computeIfAbsent(stack.getItem(), ResourceBucket::new)
                    .add(stack.getCount(), new HandlerResourceSource(handler, slot));
        }
    }

    private static boolean isResourceStack(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getCount() > 0
                && stack.getMaxStackSize() > 1
                && !stack.isDamageableItem();
    }

    private static void giveResource(ServerPlayer receiver, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack remaining = stack.copy();
        receiver.getInventory().add(remaining);
        if (!remaining.isEmpty()) {
            receiver.drop(remaining, false);
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
        for (UUID participant : war.participants()) {
            addOnlineMembers(server, participant, desired);
        }
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
        if (war.isLead(factionId)) {
            beginRollback(server, war);
        } else {
            withdrawParticipant(server, war, factionId);
        }
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
        for (UUID participant : war.participants()) {
            broadcast(server, participant, message);
        }

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
        for (UUID participant : war.participants()) {
            factionToWar.remove(participant, war.id());
            blockPointWindows.remove(participant);
        }
        clearBossBar(war.id());
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
        ListTag pendingSpoilsTag = new ListTag();
        for (PendingSpoils spoils : pendingSpoils.values()) {
            pendingSpoilsTag.add(spoils.save());
        }
        tag.put(TAG_PENDING_SPOILS, pendingSpoilsTag);
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
            boolean participantBusy = war.participants().stream()
                .anyMatch(manager.factionToWar::containsKey);
            if (war.state() == War.State.ENDED || participantBusy) {
                repaired = true;
                continue;
            }
            manager.wars.put(war.id(), war);
            for (UUID participant : war.participants()) {
                manager.factionToWar.put(participant, war.id());
            }
            if (war.state() == War.State.ENDING) {
                for (ClaimKey key : war.snapshotKeys()) {
                    manager.rollbackQueue.add(new RollbackTask(war.id(), key));
                }
            }
        }
        ListTag pendingSpoilsTag = tag.getList(TAG_PENDING_SPOILS, Tag.TAG_COMPOUND);
        for (int index = 0; index < pendingSpoilsTag.size(); index++) {
            Optional<PendingSpoils> spoils = PendingSpoils.load(pendingSpoilsTag.getCompound(index));
            if (spoils.isEmpty() || manager.pendingSpoils.putIfAbsent(spoils.get().spoilsId(), spoils.get()) != null) {
                repaired = true;
                LOGGER.warn("Skipped invalid pending war spoils entry at NBT index {}", index);
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

    public enum JoinResult {
        SUCCESS,
        SAME_FACTION,
        NOT_ALLIED,
        ALLY_NOT_DEFENDING,
        ALLIED_WITH_ENEMY,
        JOINER_BUSY
    }

    public enum ClaimSpoilsResult {
        SUCCESS,
        NOT_FOUND,
        NOT_WINNER,
        TRANSFER_FAILED
    }

    public enum SpoilsChoice {
        MONEY(30, 0),
        RESOURCES(0, 30),
        SPLIT(15, 15);

        private final int moneyPercent;
        private final int resourcePercent;

        SpoilsChoice(int moneyPercent, int resourcePercent) {
            this.moneyPercent = moneyPercent;
            this.resourcePercent = resourcePercent;
        }

        public int moneyPercent() {
            return moneyPercent;
        }

        public int resourcePercent() {
            return resourcePercent;
        }

        public static Optional<SpoilsChoice> parse(String value) {
            if (value == null) {
                return Optional.empty();
            }
            try {
                return Optional.of(valueOf(value.trim().toUpperCase(java.util.Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
        }
    }

    public record PendingSpoilsView(
            UUID spoilsId,
            UUID loserId,
            String loserName,
            long money,
            long resourceOne,
            String resourceOneItem,
            long resourceTwo,
            String resourceTwoItem,
            long resourceThree,
            String resourceThreeItem
    ) {
    }

    private interface ResourceSource {
        ItemStack stack();

        ItemStack extract(int amount);
    }

    private record ContainerResourceSource(Container container, int slot) implements ResourceSource {
        @Override
        public ItemStack stack() {
            return container.getItem(slot);
        }

        @Override
        public ItemStack extract(int amount) {
            ItemStack stack = container.getItem(slot);
            if (!isResourceStack(stack) || amount <= 0) {
                return ItemStack.EMPTY;
            }
            int taken = Math.min(amount, stack.getCount());
            ItemStack result = stack.copy();
            result.setCount(taken);
            stack.shrink(taken);
            container.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
            container.setChanged();
            return result;
        }
    }

    private record HandlerResourceSource(IItemHandler handler, int slot) implements ResourceSource {
        @Override
        public ItemStack stack() {
            return handler.getStackInSlot(slot);
        }

        @Override
        public ItemStack extract(int amount) {
            return amount <= 0 ? ItemStack.EMPTY : handler.extractItem(slot, amount, false);
        }
    }

    private static final class ResourceBucket {
        private final Item item;
        private final List<ResourceSource> sources = new ArrayList<>();
        private long count;

        private ResourceBucket(Item item) {
            this.item = item;
        }

        private Item item() {
            return item;
        }

        private List<ResourceSource> sources() {
            return sources;
        }

        private long count() {
            return count;
        }

        private void add(long amount, ResourceSource source) {
            if (amount <= 0L) {
                return;
            }
            count += amount;
            sources.add(source);
        }
    }

    private record PendingSpoils(UUID spoilsId, UUID winnerId, UUID loserId, long gameTime) {
        private static final String TAG_ID = "id";
        private static final String TAG_WINNER = "winner";
        private static final String TAG_LOSER = "loser";
        private static final String TAG_GAME_TIME = "gameTime";

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID(TAG_ID, spoilsId);
            tag.putUUID(TAG_WINNER, winnerId);
            tag.putUUID(TAG_LOSER, loserId);
            tag.putLong(TAG_GAME_TIME, gameTime);
            return tag;
        }

        private static Optional<PendingSpoils> load(CompoundTag tag) {
            if (!tag.hasUUID(TAG_ID) || !tag.hasUUID(TAG_WINNER) || !tag.hasUUID(TAG_LOSER)) {
                return Optional.empty();
            }
            return Optional.of(new PendingSpoils(
                    tag.getUUID(TAG_ID),
                    tag.getUUID(TAG_WINNER),
                    tag.getUUID(TAG_LOSER),
                    tag.getLong(TAG_GAME_TIME)
            ));
        }
    }

    private record RollbackTask(UUID warId, ClaimKey key) {
    }
}
