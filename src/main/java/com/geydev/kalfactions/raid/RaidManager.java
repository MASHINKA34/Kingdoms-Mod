package com.geydev.kalfactions.raid;

import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.command.NumismaticsEconomy;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.economy.PriceMath;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.integration.IntegrationManager;
import com.geydev.kalfactions.outpost.RogueOutpostManager;
import com.geydev.kalfactions.net.ClaimSyncManager;
import com.geydev.kalfactions.net.FactionServerHooks;
import com.mojang.logging.LogUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

public final class RaidManager extends SavedData {
    public static final String DATA_NAME = "kingdoms_raids";
    public static final String RAIDER_TAG = "kingdoms_raider";
    public static final String RAID_ID_DATA = "kingdoms_raid_id";
    public static final Factory<RaidManager> FACTORY = new Factory<>(RaidManager::new, RaidManager::load);

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG_VERSION = "version";
    private static final String TAG_RAIDS = "raids";
    private static final String TAG_NEXT_ROLLS = "nextRolls";
    private static final String TAG_FACTION = "faction";
    private static final String TAG_TIME = "time";
    private static final int DATA_VERSION = 1;
    private static final long TICK_INTERVAL_MILLIS = 1_000L;
    private static final double RAIDER_SPEED = 1.1D;

    private final Map<UUID, Raid> raids = new LinkedHashMap<>();
    private final Map<UUID, UUID> factionToRaid = new HashMap<>();
    private final Map<UUID, Long> nextRollAtEpochMillis = new HashMap<>();
    private long nextTickAtEpochMillis;

    public static RaidManager get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static RaidManager get(ServerLevel level) {
        return get(Objects.requireNonNull(level, "level").getServer());
    }

    public synchronized Optional<Raid> raidForFaction(UUID factionId) {
        UUID raidId = factionToRaid.get(factionId);
        return raidId == null ? Optional.empty() : Optional.ofNullable(raids.get(raidId));
    }

    public synchronized boolean ownsRaider(UUID raidId, UUID entityId) {
        Raid raid = raids.get(raidId);
        return raid != null && raid.containsRaider(entityId);
    }

    public synchronized void tick(MinecraftServer server, long nowEpochMillis) {
        if (nowEpochMillis < nextTickAtEpochMillis
            && nextTickAtEpochMillis - nowEpochMillis <= TICK_INTERVAL_MILLIS * 5L) {
            return;
        }
        nextTickAtEpochMillis = saturatedAdd(nowEpochMillis, TICK_INTERVAL_MILLIS);

        FactionManager factions = FactionManager.get(server);
        boolean dirty = nextRollAtEpochMillis.keySet().removeIf(id -> factions.getFactionById(id).isEmpty());

        for (Raid raid : List.copyOf(raids.values())) {
            Faction faction = factions.getFactionById(raid.factionId()).orElse(null);
            if (faction == null) {
                removeRaid(server, raid, nowEpochMillis);
                dirty = true;
                continue;
            }
            if (raid.state() == Raid.State.WARNING && nowEpochMillis >= raid.warningEndsAtEpochMillis()) {
                if (!faction.hasClaim(raid.targetClaim()) || !activateRaid(server, raid, faction, nowEpochMillis)) {
                    notifyFaction(
                        server,
                        faction.id(),
                        Component.literal("Рейд отменён: не удалось подготовить точку появления."),
                        false
                    );
                    removeRaid(server, raid, nowEpochMillis);
                }
                dirty = true;
                continue;
            }
            if (raid.state() == Raid.State.ACTIVE) {
                driveRaiders(server, raid, faction);
                sendRemainingNotice(server, raid, nowEpochMillis);
                if (nowEpochMillis >= raid.activeEndsAtEpochMillis()) {
                    resolveDefeat(server, raid, faction, nowEpochMillis);
                }
            }
        }

        long intervalMillis = hoursToMillis(ModConfigSpec.RAID_ROLL_INTERVAL_HOURS.getAsInt());
        long graceMillis = hoursToMillis(ModConfigSpec.RAID_GRACE_PERIOD_HOURS.getAsInt());
        RandomSource random = server.overworld().getRandom();
        for (Faction faction : factions.factions()) {
            if (factionToRaid.containsKey(faction.id())) {
                continue;
            }
            long graceEndsAt = saturatedAdd(faction.createdAtEpochMillis(), graceMillis);
            Long nextRoll = nextRollAtEpochMillis.get(faction.id());
            if (nextRoll == null) {
                nextRoll = graceEndsAt;
                nextRollAtEpochMillis.put(faction.id(), nextRoll);
                dirty = true;
            }
            if (nowEpochMillis < graceEndsAt
                || nowEpochMillis < nextRoll
                || onlineMembers(server, faction).isEmpty()) {
                continue;
            }
            nextRollAtEpochMillis.put(faction.id(), saturatedAdd(nowEpochMillis, intervalMillis));
            dirty = true;
            if (random.nextInt(100) >= ModConfigSpec.RAID_CHANCE_PERCENT.getAsInt()) {
                continue;
            }
            TargetSelection target = selectTarget(server, faction, random).orElse(null);
            if (target != null) {
                startWarning(server, faction, target, nowEpochMillis);
            }
        }
        if (dirty) {
            setDirty();
        }
    }

    public synchronized ForceOutcome forceRaid(MinecraftServer server, UUID factionId) {
        Faction faction = FactionManager.get(server).getFactionById(factionId).orElse(null);
        if (faction == null) {
            return new ForceOutcome(ForceStatus.FACTION_NOT_FOUND, null);
        }
        if (factionToRaid.containsKey(factionId)) {
            return new ForceOutcome(ForceStatus.ALREADY_ACTIVE, raidForFaction(factionId).orElse(null));
        }
        TargetSelection target = selectMainTarget(server, faction).orElse(null);
        if (target == null) {
            return new ForceOutcome(ForceStatus.NO_TARGET, null);
        }
        long now = System.currentTimeMillis();
        Raid raid = Raid.warning(
            uniqueRaidId(),
            faction.id(),
            target.type(),
            target.claim(),
            target.position(),
            target.outpostId(),
            now
        );
        raids.put(raid.id(), raid);
        factionToRaid.put(faction.id(), raid.id());
        nextRollAtEpochMillis.put(
            faction.id(),
            saturatedAdd(now, hoursToMillis(ModConfigSpec.RAID_ROLL_INTERVAL_HOURS.getAsInt()))
        );
        setDirty();
        if (!activateRaid(server, raid, faction, now)) {
            removeRaid(server, raid, now);
            return new ForceOutcome(ForceStatus.SPAWN_FAILED, null);
        }
        return new ForceOutcome(ForceStatus.STARTED, raid);
    }

    public synchronized void onRaiderDeath(MinecraftServer server, UUID raidId, UUID entityId) {
        Raid raid = raids.get(raidId);
        if (raid == null || raid.state() != Raid.State.ACTIVE || !raid.containsRaider(entityId)) {
            return;
        }
        raid.removeRaider(entityId);
        setDirty();
        if (raid.remainingRaiderCount() != 0) {
            return;
        }
        Faction faction = FactionManager.get(server).getFactionById(raid.factionId()).orElse(null);
        if (faction == null) {
            removeRaid(server, raid, System.currentTimeMillis());
            return;
        }
        resolveVictory(server, raid, faction, System.currentTimeMillis());
    }

    private void startWarning(
        MinecraftServer server,
        Faction faction,
        TargetSelection target,
        long nowEpochMillis
    ) {
        long warningEndsAt = saturatedAdd(
            nowEpochMillis,
            secondsToMillis(ModConfigSpec.RAID_WARNING_SECONDS.getAsInt())
        );
        Raid raid = Raid.warning(
            uniqueRaidId(),
            faction.id(),
            target.type(),
            target.claim(),
            target.position(),
            target.outpostId(),
            warningEndsAt
        );
        raids.put(raid.id(), raid);
        factionToRaid.put(faction.id(), raid.id());
        setDirty();
        BlockPos pos = target.position();
        String where = target.type() == Raid.TargetType.OUTPOST ? "форпост" : "основная территория";
        notifyFaction(
            server,
            faction.id(),
            Component.literal(
                "На вашу фракцию готовится нападение! Цель: "
                    + where
                    + " ("
                    + pos.getX()
                    + ", "
                    + pos.getY()
                    + ", "
                    + pos.getZ()
                    + "). Рейд начнётся через "
                    + ModConfigSpec.RAID_WARNING_SECONDS.getAsInt()
                    + " сек."
            ),
            false
        );
        LOGGER.info("Raid {} warning started for faction {} at {}", raid.id(), faction.name(), pos);
    }

    private boolean activateRaid(
        MinecraftServer server,
        Raid raid,
        Faction faction,
        long nowEpochMillis
    ) {
        ServerLevel level = server.getLevel(raid.targetClaim().dimension());
        if (level == null) {
            return false;
        }
        int requestedRaiders = Math.min(15, 3 + faction.claimCount() / 3);
        List<ClaimKey> boundary;
        if (raid.targetType() == Raid.TargetType.OUTPOST) {
            boundary = faction.outpost(raid.outpostId())
                .map(outpost -> List.copyOf(outpost.chunks()))
                .orElse(List.of(raid.targetClaim()));
        } else {
            boundary = boundaryClaims(faction, raid.targetClaim().dimension());
        }
        if (boundary.isEmpty()) {
            boundary = List.of(raid.targetClaim());
        }
        int spawned = 0;
        for (int index = 0; index < requestedRaiders; index++) {
            Raider raider = spawnRaider(server, level, raid, faction, boundary, index);
            if (raider != null) {
                spawned++;
            }
        }
        if (spawned != requestedRaiders) {
            cleanupLoadedRaiders(server, raid);
            for (UUID raiderId : raid.raiderIds()) {
                raid.removeRaider(raiderId);
            }
            return false;
        }
        long activeEndsAt = saturatedAdd(
            nowEpochMillis,
            minutesToMillis(ModConfigSpec.RAID_COMBAT_MINUTES.getAsInt())
        );
        raid.activate(activeEndsAt, spawned, nowEpochMillis);
        setDirty();
        driveRaiders(server, raid, faction);
        notifyFaction(
            server,
            faction.id(),
            Component.literal(
                "Рейд начался. Рейдеров: "
                    + spawned
                    + ". На защиту: "
                    + formatDuration(raid.secondsRemaining(nowEpochMillis))
                    + "."
            ),
            false
        );
        LOGGER.info("Raid {} activated for faction {} with {} raiders", raid.id(), faction.name(), spawned);
        return true;
    }

    private Raider spawnRaider(
        MinecraftServer server,
        ServerLevel level,
        Raid raid,
        Faction faction,
        List<ClaimKey> boundary,
        int index
    ) {
        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < 64; attempt++) {
            ClaimKey spawnClaim = boundary.get(random.nextInt(boundary.size()));
            BlockPos spawnPos = findSurfacePosition(level, spawnClaim.chunk(), random);
            if (spawnPos == null) {
                continue;
            }
            Raider raider = index % 3 == 2
                ? createRaider(EntityType.VINDICATOR, level, raid, spawnPos)
                : createRaider(EntityType.PILLAGER, level, raid, spawnPos);
            if (raider == null) {
                continue;
            }
            raid.addRaider(raider.getUUID());
            if (!level.addFreshEntity(raider)) {
                raid.removeRaider(raider.getUUID());
                continue;
            }
            ServerPlayer target = nearestMember(raider, onlineMembers(server, faction));
            if (target != null) {
                raider.setTarget(target);
                raider.getNavigation().moveTo(target, RAIDER_SPEED);
            } else {
                BlockPos targetPos = raid.targetPos();
                raider.getNavigation().moveTo(
                    targetPos.getX() + 0.5D,
                    targetPos.getY(),
                    targetPos.getZ() + 0.5D,
                    RAIDER_SPEED
                );
            }
            return raider;
        }
        return null;
    }

    private static <T extends Raider> T createRaider(
        EntityType<T> type,
        ServerLevel level,
        Raid raid,
        BlockPos spawnPos
    ) {
        return type.create(
            level,
            raider -> {
                raider.addTag(RAIDER_TAG);
                raider.getPersistentData().putUUID(RAID_ID_DATA, raid.id());
                raider.setPersistenceRequired();
                raider.setCanJoinRaid(false);
                raider.setCurrentRaid(null);
                raider.restrictTo(raid.targetPos(), 192);
            },
            spawnPos,
            MobSpawnType.EVENT,
            false,
            false
        );
    }

    private static BlockPos findSurfacePosition(ServerLevel level, ChunkPos chunk, RandomSource random) {
        for (int attempt = 0; attempt < 24; attempt++) {
            int x = chunk.getMinBlockX() + 1 + random.nextInt(14);
            int z = chunk.getMinBlockZ() + 1 + random.nextInt(14);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getWorldBorder().isWithinBounds(pos)
                || !level.getBlockState(pos).isAir()
                || !level.getBlockState(pos.above()).isAir()
                || !level.getFluidState(pos).isEmpty()
                || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
                continue;
            }
            return pos;
        }
        return null;
    }

    private void driveRaiders(MinecraftServer server, Raid raid, Faction faction) {
        ServerLevel level = server.getLevel(raid.targetClaim().dimension());
        if (level == null) {
            return;
        }
        List<ServerPlayer> members = onlineMembers(server, faction).stream()
            .filter(player -> player.serverLevel() == level)
            .toList();
        for (UUID raiderId : raid.raiderIds()) {
            Entity entity = level.getEntity(raiderId);
            if (!(entity instanceof Raider raider) || !raider.isAlive()) {
                continue;
            }
            raider.setPersistenceRequired();
            ServerPlayer target = nearestMember(raider, members);
            if (target != null) {
                raider.setTarget(target);
                raider.getNavigation().moveTo(target, RAIDER_SPEED);
            } else {
                raider.setTarget(null);
                BlockPos targetPos = raid.targetPos();
                raider.getNavigation().moveTo(
                    targetPos.getX() + 0.5D,
                    targetPos.getY(),
                    targetPos.getZ() + 0.5D,
                    RAIDER_SPEED
                );
            }
        }
    }

    private void sendRemainingNotice(MinecraftServer server, Raid raid, long nowEpochMillis) {
        int seconds = raid.secondsRemaining(nowEpochMillis);
        if (!crossedNoticeThreshold(raid.lastNotifiedRemainingSeconds(), seconds)) {
            return;
        }
        raid.setLastNotifiedRemainingSeconds(seconds);
        setDirty();
        notifyFaction(
            server,
            raid.factionId(),
            Component.literal(
                "До поражения: "
                    + formatDuration(seconds)
                    + ". Осталось рейдеров: "
                    + raid.remainingRaiderCount()
                    + "."
            ),
            false
        );
    }

    private void resolveVictory(
        MinecraftServer server,
        Raid raid,
        Faction faction,
        long nowEpochMillis
    ) {
        long reward = 0L;
        long minimum = Math.min(
            ModConfigSpec.RAID_REWARD_PER_RAIDER_MIN.getAsLong(),
            ModConfigSpec.RAID_REWARD_PER_RAIDER_MAX.getAsLong()
        );
        long maximum = Math.max(
            ModConfigSpec.RAID_REWARD_PER_RAIDER_MIN.getAsLong(),
            ModConfigSpec.RAID_REWARD_PER_RAIDER_MAX.getAsLong()
        );
        RandomSource random = server.overworld().getRandom();
        for (int index = 0; index < raid.originalRaiderCount(); index++) {
            reward = saturatedAdd(reward, randomInclusive(random, minimum, maximum));
        }
        long room = Long.MAX_VALUE - faction.treasuryBalance();
        long deposited = Math.min(reward, room);
        if (deposited > 0L) {
            FactionManager.get(server).deposit(faction.id(), deposited);
        }
        MutableComponent message = Component.literal("Рейд отражён. Награда в казну: ")
            .append(NumismaticsEconomy.format(deposited))
            .append(Component.literal("."));
        notifyFaction(server, faction.id(), message, true);
        LOGGER.info("Raid {} won by faction {}; reward {}", raid.id(), faction.name(), deposited);
        removeRaid(server, raid, nowEpochMillis);
    }

    private void resolveDefeat(
        MinecraftServer server,
        Raid raid,
        Faction faction,
        long nowEpochMillis
    ) {
        if (raid.targetType() == Raid.TargetType.OUTPOST) {
            resolveOutpostDefeat(server, raid, faction, nowEpochMillis);
            return;
        }
        FactionManager manager = FactionManager.get(server);
        int lostClaims = removeExtremeClaims(manager, faction, raid);
        Faction currentFaction = manager.getFactionById(faction.id()).orElse(faction);
        long balance = currentFaction.treasuryBalance();
        long percentLoss = BigDecimal.valueOf(balance)
            .multiply(BigDecimal.valueOf(ModConfigSpec.RAID_TREASURY_STEAL_PERCENT.getAsDouble()))
            .setScale(0, RoundingMode.FLOOR)
            .longValue();
        long desiredTreasuryLoss = Math.max(
            ModConfigSpec.RAID_TREASURY_STEAL_MIN.getAsLong(),
            percentLoss
        );
        long treasuryStolen = Math.min(balance, desiredTreasuryLoss);
        if (treasuryStolen > 0L) {
            manager.withdraw(faction.id(), treasuryStolen);
        }
        long inventoryStolen = balance == 0L
            ? collectOnlineCoins(server, currentFaction, ModConfigSpec.RAID_TREASURY_STEAL_MIN.getAsLong())
            : 0L;
        if (lostClaims > 0) {
            IntegrationManager.refreshFromServer(server);
            for (ServerPlayer player : onlineMembers(server, currentFaction)) {
                ClaimSyncManager.resync(player);
            }
        }
        MutableComponent message = Component.literal(
            "Рейд проигран. Потеряно крайних чанков: " + lostClaims + ". Украдено: "
        ).append(NumismaticsEconomy.format(saturatedAdd(treasuryStolen, inventoryStolen)))
            .append(Component.literal("."));
        notifyFaction(server, faction.id(), message, false);
        LOGGER.info(
            "Raid {} lost by faction {}; claims {}, treasury {}, inventories {}",
            raid.id(),
            faction.name(),
            lostClaims,
            treasuryStolen,
            inventoryStolen
        );
        removeRaid(server, raid, nowEpochMillis);
    }

    private void resolveOutpostDefeat(
        MinecraftServer server,
        Raid raid,
        Faction faction,
        long nowEpochMillis
    ) {
        FactionManager manager = FactionManager.get(server);
        Faction.Outpost outpost = manager.detachOutpost(faction.id(), raid.outpostId()).orElse(null);
        if (outpost == null) {
            removeRaid(server, raid, nowEpochMillis);
            return;
        }
        ServerLevel level = server.getLevel(raid.targetClaim().dimension());
        List<UUID> garrison = new ArrayList<>();
        if (level != null) {
            for (UUID raiderId : raid.raiderIds()) {
                Entity entity = level.getEntity(raiderId);
                if (entity instanceof Raider raider && raider.isAlive()) {
                    raider.getPersistentData().remove(RAID_ID_DATA);
                    raider.getPersistentData().putUUID(RogueOutpostManager.OUTPOST_ID_DATA, outpost.id());
                    raider.addTag(RogueOutpostManager.GARRISON_TAG);
                    raider.setPersistenceRequired();
                    garrison.add(raiderId);
                }
            }
        }
        RogueOutpostManager.get(server).add(new RogueOutpostManager.RogueOutpost(
            outpost.id(),
            outpost.dimension().location().toString(),
            outpost.core(),
            outpost.chunks(),
            garrison,
            garrison.size(),
            faction.ownerId(),
            faction.name()
        ));
        raids.remove(raid.id());
        factionToRaid.remove(raid.factionId(), raid.id());
        nextRollAtEpochMillis.put(
            raid.factionId(),
            saturatedAdd(nowEpochMillis, hoursToMillis(ModConfigSpec.RAID_ROLL_INTERVAL_HOURS.getAsInt()))
        );
        setDirty();
        IntegrationManager.refreshFromServer(server);
        for (ServerPlayer player : onlineMembers(server, faction)) {
            ClaimSyncManager.resync(player);
        }
        BlockPos core = outpost.core();
        notifyFaction(
            server,
            faction.id(),
            Component.literal(
                "Форпост потерян! Его захватили разбойники: "
                    + core.getX() + ", " + core.getY() + ", " + core.getZ() + "."
            ),
            false
        );
        LOGGER.info(
            "Raid {} turned outpost {} of faction {} rogue with {} garrison",
            raid.id(),
            outpost.id(),
            faction.name(),
            garrison.size()
        );
    }

    private static int removeExtremeClaims(FactionManager manager, Faction faction, Raid raid) {
        List<ClaimKey> candidates = boundaryClaims(faction, raid.targetClaim().dimension()).stream()
            .sorted(Comparator
                .comparingLong((ClaimKey key) -> -distanceSquared(key, raid.targetClaim()))
                .thenComparing(ClaimKey::compareTo))
            .toList();
        int removed = 0;
        for (ClaimKey candidate : candidates) {
            if (removed >= 2) {
                break;
            }
            Faction current = manager.getFactionById(faction.id()).orElse(null);
            if (current == null || !current.hasClaim(candidate)) {
                continue;
            }
            long paidPrice = current.claimPrices().getOrDefault(candidate, 0L);
            long expectedRefund = PriceMath.refund(
                paidPrice,
                ModConfigSpec.UNCLAIM_REFUND_PERCENT.getAsDouble()
            );
            long room = Long.MAX_VALUE - current.treasuryBalance();
            long reserved = Math.max(0L, expectedRefund - room);
            if (reserved > 0L && !manager.withdraw(faction.id(), reserved).successful()) {
                continue;
            }
            FactionManager.OperationResult result = manager.unclaim(faction.id(), candidate);
            if (!result.successful()) {
                if (reserved > 0L) {
                    manager.deposit(faction.id(), reserved);
                }
                continue;
            }
            if (result.amount() > 0L) {
                manager.withdraw(faction.id(), result.amount());
            }
            if (reserved > 0L) {
                manager.deposit(faction.id(), reserved);
            }
            removed++;
        }
        return removed;
    }

    private static long collectOnlineCoins(MinecraftServer server, Faction faction, long limit) {
        long remaining = Math.max(0L, limit);
        long collected = 0L;
        List<ServerPlayer> members = onlineMembers(server, faction).stream()
            .sorted(Comparator.comparing(player -> player.getUUID().toString()))
            .toList();
        for (ServerPlayer player : members) {
            if (remaining <= 0L) {
                break;
            }
            long amount = Math.min(remaining, NumismaticsEconomy.balance(player));
            if (amount <= 0L) {
                continue;
            }
            NumismaticsEconomy.Payment payment = NumismaticsEconomy.preparePayment(player, amount);
            if (!payment.ready() || !NumismaticsEconomy.commitPayment(player, payment)) {
                continue;
            }
            collected = saturatedAdd(collected, amount);
            remaining -= amount;
        }
        return collected;
    }

    private void removeRaid(MinecraftServer server, Raid raid, long nowEpochMillis) {
        cleanupLoadedRaiders(server, raid);
        raids.remove(raid.id());
        factionToRaid.remove(raid.factionId(), raid.id());
        nextRollAtEpochMillis.put(
            raid.factionId(),
            saturatedAdd(
                nowEpochMillis,
                hoursToMillis(ModConfigSpec.RAID_ROLL_INTERVAL_HOURS.getAsInt())
            )
        );
        setDirty();
    }

    private static void cleanupLoadedRaiders(MinecraftServer server, Raid raid) {
        for (ServerLevel level : server.getAllLevels()) {
            for (UUID raiderId : raid.raiderIds()) {
                Entity entity = level.getEntity(raiderId);
                if (entity != null) {
                    entity.discard();
                }
            }
        }
    }

    private static Optional<TargetSelection> selectMainTarget(MinecraftServer server, Faction faction) {
        Map<ResourceKey<Level>, List<ClaimKey>> byDimension = new HashMap<>();
        for (ClaimKey claim : faction.claims()) {
            byDimension.computeIfAbsent(claim.dimension(), ignored -> new ArrayList<>()).add(claim);
        }
        List<ClaimKey> claims = byDimension.entrySet().stream()
            .filter(entry -> server.getLevel(entry.getKey()) != null)
            .sorted(Comparator
                .<Map.Entry<ResourceKey<Level>, List<ClaimKey>>>comparingInt(entry -> entry.getValue().size())
                .reversed()
                .thenComparing(entry -> entry.getKey().location()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(List.of());
        if (claims.isEmpty()) {
            return Optional.empty();
        }
        double centerX = claims.stream().mapToInt(ClaimKey::x).average().orElse(0.0D);
        double centerZ = claims.stream().mapToInt(ClaimKey::z).average().orElse(0.0D);
        ClaimKey targetClaim = claims.stream()
            .min(Comparator
                .comparingDouble((ClaimKey key) -> {
                    double dx = key.x() - centerX;
                    double dz = key.z() - centerZ;
                    return dx * dx + dz * dz;
                })
                .thenComparing(ClaimKey::compareTo))
            .orElseThrow();
        ServerLevel level = server.getLevel(targetClaim.dimension());
        if (level == null) {
            return Optional.empty();
        }
        int x = targetClaim.chunk().getMiddleBlockX();
        int z = targetClaim.chunk().getMiddleBlockZ();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return Optional.of(new TargetSelection(Raid.TargetType.MAIN, targetClaim, new BlockPos(x, y, z), null));
    }

    private static Optional<TargetSelection> selectTarget(MinecraftServer server, Faction faction, RandomSource random) {
        List<Faction.Outpost> outposts = faction.outposts().stream()
            .filter(outpost -> server.getLevel(outpost.dimension()) != null)
            .sorted(Comparator.comparing(outpost -> outpost.id().toString()))
            .toList();
        if (!outposts.isEmpty() && random.nextInt(100) < 40) {
            Faction.Outpost outpost = outposts.get(random.nextInt(outposts.size()));
            ClaimKey claim = new ClaimKey(outpost.dimension(), new ChunkPos(outpost.core()));
            return Optional.of(new TargetSelection(
                Raid.TargetType.OUTPOST,
                claim,
                outpost.core(),
                outpost.id()
            ));
        }
        return selectMainTarget(server, faction);
    }

    private static List<ClaimKey> boundaryClaims(Faction faction, ResourceKey<Level> dimension) {
        Set<ClaimKey> claims = new LinkedHashSet<>();
        for (ClaimKey claim : faction.claims()) {
            if (claim.dimension().equals(dimension)) {
                claims.add(claim);
            }
        }
        return claims.stream()
            .filter(claim -> claim.cardinalNeighbors().stream().anyMatch(neighbor -> !claims.contains(neighbor)))
            .sorted()
            .toList();
    }

    private static long distanceSquared(ClaimKey first, ClaimKey second) {
        long dx = (long) first.x() - second.x();
        long dz = (long) first.z() - second.z();
        return dx * dx + dz * dz;
    }

    private static List<ServerPlayer> onlineMembers(MinecraftServer server, Faction faction) {
        List<ServerPlayer> players = new ArrayList<>();
        for (UUID memberId : faction.members().keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberId);
            if (player != null && player.isAlive() && !player.isSpectator()) {
                players.add(player);
            }
        }
        return players;
    }

    private static ServerPlayer nearestMember(Raider raider, List<ServerPlayer> members) {
        return members.stream()
            .filter(player -> player.serverLevel() == raider.level())
            .min(Comparator.comparingDouble(raider::distanceToSqr))
            .orElse(null);
    }

    private static void notifyFaction(
        MinecraftServer server,
        UUID factionId,
        Component message,
        boolean successful
    ) {
        FactionManager.get(server).getFactionById(factionId).ifPresent(faction -> {
            for (UUID memberId : faction.members().keySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(memberId);
                if (player != null) {
                    FactionServerHooks.sendNotice(player, message, successful);
                }
            }
        });
    }

    private static boolean crossedNoticeThreshold(int previousSeconds, int currentSeconds) {
        if (currentSeconds <= 0 || previousSeconds <= currentSeconds) {
            return false;
        }
        int nextMinuteThreshold = ((previousSeconds - 1) / 60) * 60;
        if (nextMinuteThreshold >= 60 && currentSeconds <= nextMinuteThreshold) {
            return true;
        }
        int[] shortThresholds = {30, 20, 10, 5, 4, 3, 2, 1};
        for (int threshold : shortThresholds) {
            if (previousSeconds > threshold && currentSeconds <= threshold) {
                return true;
            }
        }
        return false;
    }

    private static String formatDuration(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        if (minutes <= 0) {
            return remainingSeconds + " сек";
        }
        return minutes + " мин " + remainingSeconds + " сек";
    }

    private UUID uniqueRaidId() {
        UUID id;
        do {
            id = UUID.randomUUID();
        } while (raids.containsKey(id));
        return id;
    }

    private static long randomInclusive(RandomSource random, long minimum, long maximum) {
        if (minimum >= maximum) {
            return minimum;
        }
        long distance = maximum - minimum;
        if (distance == Long.MAX_VALUE) {
            return random.nextLong() & Long.MAX_VALUE;
        }
        return minimum + Math.floorMod(random.nextLong(), distance + 1L);
    }

    private static long hoursToMillis(long hours) {
        return saturatedMultiply(Math.max(0L, hours), 3_600_000L);
    }

    private static long minutesToMillis(long minutes) {
        return saturatedMultiply(Math.max(0L, minutes), 60_000L);
    }

    private static long secondsToMillis(long seconds) {
        return saturatedMultiply(Math.max(0L, seconds), 1_000L);
    }

    private static long saturatedMultiply(long left, long right) {
        if (left == 0L || right == 0L) {
            return 0L;
        }
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    private static long saturatedAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(TAG_VERSION, DATA_VERSION);
        ListTag raidsTag = new ListTag();
        for (Raid raid : raids.values()) {
            raidsTag.add(raid.save());
        }
        tag.put(TAG_RAIDS, raidsTag);
        ListTag nextRollsTag = new ListTag();
        nextRollAtEpochMillis.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                CompoundTag rollTag = new CompoundTag();
                rollTag.putUUID(TAG_FACTION, entry.getKey());
                rollTag.putLong(TAG_TIME, entry.getValue());
                nextRollsTag.add(rollTag);
            });
        tag.put(TAG_NEXT_ROLLS, nextRollsTag);
        return tag;
    }

    private static RaidManager load(CompoundTag tag, HolderLookup.Provider registries) {
        RaidManager manager = new RaidManager();
        boolean repaired = tag.getInt(TAG_VERSION) != DATA_VERSION;
        ListTag raidsTag = tag.getList(TAG_RAIDS, Tag.TAG_COMPOUND);
        for (int index = 0; index < raidsTag.size(); index++) {
            Optional<Raid> loaded = Raid.load(raidsTag.getCompound(index));
            if (loaded.isEmpty()) {
                repaired = true;
                continue;
            }
            Raid raid = loaded.get();
            if (manager.raids.containsKey(raid.id()) || manager.factionToRaid.containsKey(raid.factionId())) {
                repaired = true;
                continue;
            }
            manager.raids.put(raid.id(), raid);
            manager.factionToRaid.put(raid.factionId(), raid.id());
        }
        ListTag nextRollsTag = tag.getList(TAG_NEXT_ROLLS, Tag.TAG_COMPOUND);
        for (int index = 0; index < nextRollsTag.size(); index++) {
            CompoundTag rollTag = nextRollsTag.getCompound(index);
            if (!rollTag.hasUUID(TAG_FACTION) || !rollTag.contains(TAG_TIME, Tag.TAG_LONG)) {
                repaired = true;
                continue;
            }
            manager.nextRollAtEpochMillis.put(
                rollTag.getUUID(TAG_FACTION),
                Math.max(0L, rollTag.getLong(TAG_TIME))
            );
        }
        if (repaired) {
            manager.setDirty();
        }
        return manager;
    }

    public enum ForceStatus {
        STARTED,
        FACTION_NOT_FOUND,
        ALREADY_ACTIVE,
        NO_TARGET,
        SPAWN_FAILED
    }

    public record ForceOutcome(ForceStatus status, Raid raid) {
    }

    private record TargetSelection(Raid.TargetType type, ClaimKey claim, BlockPos position, UUID outpostId) {
    }
}
