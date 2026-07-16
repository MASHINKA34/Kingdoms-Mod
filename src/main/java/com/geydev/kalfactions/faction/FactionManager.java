package com.geydev.kalfactions.faction;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.chest.ChestAccess;
import com.geydev.kalfactions.chest.ChestAccessMode;
import com.geydev.kalfactions.chest.ChestLinks;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.economy.PriceMath;
import com.mojang.logging.LogUtils;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import org.slf4j.Logger;

public final class FactionManager extends SavedData {
    public static final String DATA_NAME = "kingdoms_factions";
    public static final int DEFAULT_STARTER_SIZE = 3;
    public static final int MAX_NAME_LENGTH = 32;
    public static final int MAX_RGB_COLOR = 0xFFFFFF;
    public static final int DEFAULT_COLOR = 0xFFFFFF;
    public static final ResourceLocation DEFAULT_ICON = ResourceLocation.fromNamespaceAndPath("kingdoms", "default");
    public static final Set<FactionBonus> DEFAULT_BONUSES = Set.of(FactionBonus.MERCHANTS);
    public static final Factory<FactionManager> FACTORY = new Factory<>(FactionManager::new, FactionManager::load);

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int DATA_VERSION = 2;
    private static final String TAG_VERSION = "version";
    private static final String TAG_FACTIONS = "factions";
    private static final String TAG_CHESTS = "chests";
    private static final String TAG_LAST_INFLUENCE_DECAY = "lastInfluenceDecay";
    private static final String TAG_CHUNK_TICKETS_MIGRATED = "chunkTicketsMigrated";
    private static final TicketController CHUNK_TICKETS = new TicketController(
        ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "faction_chunks"),
        FactionManager::validateChunkTickets
    );

    private final Map<UUID, Faction> factions = new LinkedHashMap<>();
    private final Map<String, UUID> nameIndex = new HashMap<>();
    private final Map<UUID, UUID> memberIndex = new HashMap<>();
    private final Map<ClaimKey, UUID> claimIndex = new HashMap<>();
    private final Map<ChestAccess.Key, ChestAccess> chestAccess = new LinkedHashMap<>();
    private long lastInfluenceDecayMillis = -1L;
    private boolean chunkTicketsMigrated;
    private final transient Map<ClaimKey, UUID> appliedForceLoads = new HashMap<>();
    private final transient Set<UUID> forceLoadSuspended = new HashSet<>();

    public static void registerChunkTicketController(RegisterTicketControllersEvent event) {
        event.register(CHUNK_TICKETS);
    }

    public static FactionManager get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static FactionManager get(ServerLevel level) {
        return get(Objects.requireNonNull(level, "level").getServer());
    }

    private static void validateChunkTickets(ServerLevel level, TicketHelper helper) {
        FactionManager manager = get(level);
        helper.getEntityTickets().forEach((factionId, tickets) -> {
            for (long packedChunk : tickets.nonTicking()) {
                helper.removeTicket(factionId, packedChunk, false);
            }
            Faction faction = manager.getFactionById(factionId).orElse(null);
            for (long packedChunk : tickets.ticking()) {
                ClaimKey key = new ClaimKey(level.dimension(), new ChunkPos(packedChunk));
                if (faction == null || !faction.isForceLoaded(key)) {
                    helper.removeTicket(factionId, packedChunk, true);
                }
            }
        });
    }

    public synchronized Collection<Faction> factions() {
        return List.copyOf(factions.values());
    }

    public synchronized Optional<Faction> getFaction(UUID factionId) {
        Faction faction = factions.get(factionId);
        if (faction != null) {
            return Optional.of(faction);
        }
        return Optional.ofNullable(memberIndex.get(factionId)).map(factions::get);
    }

    public synchronized Optional<Faction> getFactionById(UUID factionId) {
        return Optional.ofNullable(factions.get(factionId));
    }

    public synchronized Optional<Faction> getFactionByName(String name) {
        String normalized = normalizeName(name);
        return Optional.ofNullable(nameIndex.get(normalized)).map(factions::get);
    }

    public synchronized Optional<Faction> getFactionForMember(UUID playerId) {
        return Optional.ofNullable(memberIndex.get(playerId)).map(factions::get);
    }

    public synchronized Optional<UUID> getFactionIdForMember(UUID playerId) {
        return Optional.ofNullable(memberIndex.get(playerId));
    }

    public synchronized Optional<Faction> getFactionAt(ClaimKey key) {
        return Optional.ofNullable(claimIndex.get(key)).map(factions::get);
    }

    public synchronized Optional<Faction> getFactionAt(Level level, BlockPos position) {
        return getFactionAt(ClaimKey.of(level, position));
    }

    public synchronized Optional<Faction> getFactionAt(Level level, ChunkPos chunk) {
        return getFactionAt(ClaimKey.of(level, chunk));
    }

    public synchronized Optional<Faction> getFactionAt(
        net.minecraft.resources.ResourceKey<Level> dimension,
        ChunkPos chunk
    ) {
        return getFactionAt(new ClaimKey(dimension, chunk));
    }

    public synchronized Optional<FactionRole> getRole(UUID playerId) {
        return getFactionForMember(playerId).flatMap(faction -> faction.roleOf(playerId));
    }

    public synchronized Optional<UUID> getFactionIdAt(ClaimKey key) {
        return Optional.ofNullable(claimIndex.get(key));
    }

    public synchronized OperationResult createFaction(UUID ownerId, String name, ClaimKey center) {
        return createFaction(
            ownerId,
            name,
            DEFAULT_COLOR,
            DEFAULT_ICON,
            DEFAULT_BONUSES,
            false,
            center,
            ModConfigSpec.STARTER_CLAIM_SIZE.getAsInt()
        );
    }

    public synchronized OperationResult createFaction3x3(UUID ownerId, String name, ClaimKey center) {
        return createFaction(
            ownerId,
            name,
            DEFAULT_COLOR,
            DEFAULT_ICON,
            DEFAULT_BONUSES,
            false,
            center,
            DEFAULT_STARTER_SIZE
        );
    }

    public synchronized OperationResult createFaction(UUID ownerId, String name, ClaimKey center, int starterSize) {
        return createFaction(
            ownerId,
            name,
            DEFAULT_COLOR,
            DEFAULT_ICON,
            DEFAULT_BONUSES,
            false,
            center,
            starterSize
        );
    }

    public synchronized OperationResult createFaction(
        UUID ownerId,
        String name,
        int color,
        ResourceLocation iconId,
        Set<FactionBonus> bonuses,
        boolean internalPvp,
        ClaimKey center
    ) {
        return createFaction(
            ownerId,
            name,
            color,
            iconId,
            bonuses,
            internalPvp,
            center,
            ModConfigSpec.STARTER_CLAIM_SIZE.getAsInt()
        );
    }

    public synchronized OperationResult createFaction(
        UUID ownerId,
        String name,
        int color,
        ResourceLocation iconId,
        Set<FactionBonus> bonuses,
        boolean internalPvp,
        ClaimKey center,
        int starterSize
    ) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(iconId, "iconId");
        Objects.requireNonNull(bonuses, "bonuses");
        Objects.requireNonNull(center, "center");
        String cleanedName = cleanName(name);
        if (!isValidName(cleanedName)) {
            return OperationResult.failure(Status.INVALID_NAME);
        }
        if (!isValidColor(color)) {
            return OperationResult.failure(Status.INVALID_COLOR);
        }
        if (starterSize < 1 || starterSize > 255) {
            return OperationResult.failure(Status.INVALID_STARTER_SIZE);
        }
        if (memberIndex.containsKey(ownerId)) {
            return OperationResult.failure(Status.PLAYER_ALREADY_MEMBER);
        }
        if (nameIndex.containsKey(normalizeName(cleanedName))) {
            return OperationResult.failure(Status.NAME_TAKEN);
        }

        Set<ClaimKey> starterClaims = square(center, starterSize);
        if (starterClaims.stream().anyMatch(claimIndex::containsKey)) {
            return OperationResult.failure(Status.CLAIM_ALREADY_OWNED);
        }

        UUID factionId;
        do {
            factionId = UUID.randomUUID();
        } while (factions.containsKey(factionId));

        Faction faction = new Faction(
            factionId,
            cleanedName,
            ownerId,
            color,
            iconId,
            bonuses,
            internalPvp,
            System.currentTimeMillis()
        );
        for (ClaimKey claim : starterClaims) {
            faction.addClaim(claim, 0L);
            faction.addProtectedClaim(claim);
        }

        factions.put(factionId, faction);
        nameIndex.put(normalizeName(cleanedName), factionId);
        memberIndex.put(ownerId, factionId);
        for (ClaimKey claim : starterClaims) {
            claimIndex.put(claim, factionId);
        }
        setDirty();
        return OperationResult.success(factionId, 0L);
    }

    public synchronized OperationResult renameFaction(UUID factionId, String newName) {
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return OperationResult.failure(Status.FACTION_NOT_FOUND);
        }
        String cleanedName = cleanName(newName);
        if (!isValidName(cleanedName)) {
            return OperationResult.failure(Status.INVALID_NAME);
        }

        String normalized = normalizeName(cleanedName);
        UUID existing = nameIndex.get(normalized);
        if (existing != null && !existing.equals(factionId)) {
            return OperationResult.failure(Status.NAME_TAKEN);
        }

        nameIndex.remove(normalizeName(faction.name()));
        faction.rename(cleanedName);
        nameIndex.put(normalized, factionId);
        setDirty();
        return OperationResult.success(factionId, 0L);
    }

    public synchronized OperationResult setFactionColor(UUID factionId, int color) {
        if (!isValidColor(color)) {
            return OperationResult.failure(Status.INVALID_COLOR);
        }
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return OperationResult.failure(Status.FACTION_NOT_FOUND);
        }
        faction.setColor(color);
        setDirty();
        return OperationResult.success(factionId, 0L);
    }

    public synchronized OperationResult setFactionIcon(UUID factionId, ResourceLocation iconId) {
        Objects.requireNonNull(iconId, "iconId");
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return OperationResult.failure(Status.FACTION_NOT_FOUND);
        }
        faction.setIconId(iconId);
        setDirty();
        return OperationResult.success(factionId, 0L);
    }

    public synchronized OperationResult setFactionBonuses(UUID factionId, Set<FactionBonus> bonuses) {
        Objects.requireNonNull(bonuses, "bonuses");
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return OperationResult.failure(Status.FACTION_NOT_FOUND);
        }
        faction.setBonuses(bonuses);
        setDirty();
        return OperationResult.success(factionId, 0L);
    }

    public synchronized OperationResult setFactionEmblem(UUID factionId, int[] pixels, String url) {
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return OperationResult.failure(Status.FACTION_NOT_FOUND);
        }
        faction.setEmblem(pixels, url);
        setDirty();
        return OperationResult.success(factionId, 0L);
    }

    public synchronized OperationResult setInternalPvp(UUID factionId, boolean enabled) {
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return OperationResult.failure(Status.FACTION_NOT_FOUND);
        }
        faction.setInternalPvp(enabled);
        setDirty();
        return OperationResult.success(factionId, 0L);
    }

    public synchronized OperationResult disbandFaction(UUID factionId) {
        Faction faction = factions.remove(factionId);
        if (faction == null) {
            return OperationResult.failure(Status.FACTION_NOT_FOUND);
        }

        nameIndex.remove(normalizeName(faction.name()));
        faction.members().keySet().forEach(memberIndex::remove);
        faction.claims().forEach(claimIndex::remove);
        faction.outpostChunks().forEach(claimIndex::remove);
        chestAccess.entrySet().removeIf(entry -> entry.getValue().factionId().equals(factionId));
        factions.values().forEach(other -> other.removeAlly(factionId));
        setDirty();
        return OperationResult.success(factionId, faction.treasuryBalance());
    }

    public synchronized boolean areAllied(UUID firstFactionId, UUID secondFactionId) {
        if (firstFactionId == null || secondFactionId == null) {
            return false;
        }
        Faction first = factions.get(firstFactionId);
        Faction second = factions.get(secondFactionId);
        return first != null
            && second != null
            && first.isAlliedWith(secondFactionId)
            && second.isAlliedWith(firstFactionId);
    }

    public synchronized OperationResult addAlliance(UUID firstFactionId, UUID secondFactionId) {
        Faction first = factions.get(firstFactionId);
        Faction second = factions.get(secondFactionId);
        if (first == null || second == null) {
            return OperationResult.failure(Status.FACTION_NOT_FOUND);
        }
        if (firstFactionId.equals(secondFactionId)) {
            return OperationResult.failure(Status.INVALID_ALLIANCE);
        }
        if (areAllied(firstFactionId, secondFactionId)) {
            return OperationResult.failure(Status.INVALID_ALLIANCE);
        }
        first.addAlly(secondFactionId);
        second.addAlly(firstFactionId);
        setDirty();
        return OperationResult.success(firstFactionId, 0L);
    }

    public synchronized OperationResult breakAlliance(UUID firstFactionId, UUID secondFactionId) {
        Faction first = factions.get(firstFactionId);
        Faction second = factions.get(secondFactionId);
        if (first == null || second == null) {
            return OperationResult.failure(Status.FACTION_NOT_FOUND);
        }
        boolean removed = first.removeAlly(secondFactionId) | second.removeAlly(firstFactionId);
        if (!removed) {
            return OperationResult.failure(Status.NOT_ALLIED);
        }
        setDirty();
        return OperationResult.success(firstFactionId, 0L);
    }

    public synchronized long quoteClaimPrice(UUID factionId, UUID actingMemberId) {
        return quoteClaimPrice(factionId);
    }

    public synchronized long quoteClaimPrice(UUID factionId) {
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return -1L;
        }
        double discount = faction.claimDiscount();
        return PriceMath.claimPrice(
            faction.claimCount(),
            ModConfigSpec.FREE_CLAIMS.getAsInt(),
            ModConfigSpec.EXPANSION_BASE_COST.getAsLong(),
            ModConfigSpec.EXPANSION_GROWTH.getAsDouble(),
            discount
        );
    }

    public synchronized OperationResult claim(UUID factionId, ClaimKey key) {
        return claim(factionId, key, null);
    }

    public synchronized OperationResult claim(UUID factionId, ClaimKey key, UUID actingMemberId) {
        Objects.requireNonNull(key, "key");
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return OperationResult.failure(Status.FACTION_NOT_FOUND);
        }
        if (actingMemberId != null && !faction.isMember(actingMemberId)) {
            return OperationResult.failure(Status.PLAYER_NOT_MEMBER);
        }
        if (claimIndex.containsKey(key)) {
            return OperationResult.failure(Status.CLAIM_ALREADY_OWNED);
        }

        Set<ClaimKey> claimsInDimension = claimsInDimension(faction, key.dimension());
        if (!claimsInDimension.isEmpty() && key.cardinalNeighbors().stream().noneMatch(claimsInDimension::contains)) {
            return OperationResult.failure(Status.CLAIM_NOT_ADJACENT);
        }
        if (claimsInDimension.isEmpty()
            && faction.claimCount() > 0
            && ModConfigSpec.REQUIRE_ADJACENT.getAsBoolean()) {
            return OperationResult.failure(Status.CLAIM_NOT_ADJACENT);
        }

        double discount = faction.claimDiscount();
        long price = PriceMath.claimPrice(
            faction.claimCount(),
            ModConfigSpec.FREE_CLAIMS.getAsInt(),
            ModConfigSpec.EXPANSION_BASE_COST.getAsLong(),
            ModConfigSpec.EXPANSION_GROWTH.getAsDouble(),
            discount
        );
        if (!faction.withdraw(price)) {
            return OperationResult.failure(Status.INSUFFICIENT_FUNDS);
        }

        faction.addClaim(key, price);
        claimIndex.put(key, factionId);
        setDirty();
        return OperationResult.success(factionId, price);
    }

    public synchronized OperationResult unclaim(UUID factionId, ClaimKey key) {
        Objects.requireNonNull(key, "key");
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return OperationResult.failure(Status.FACTION_NOT_FOUND);
        }
        if (!faction.hasClaim(key)) {
            return OperationResult.failure(Status.CLAIM_NOT_OWNED);
        }
        if (faction.isProtectedClaim(key)) {
            return OperationResult.failure(Status.CLAIM_PROTECTED);
        }

        Set<ClaimKey> remaining = claimsInDimension(faction, key.dimension());
        remaining.remove(key);
        if (!isConnected(remaining)) {
            return OperationResult.failure(Status.CLAIM_WOULD_DISCONNECT);
        }

        long paidPrice = faction.claimPrices().getOrDefault(key, 0L);
        long refund = PriceMath.refund(paidPrice, ModConfigSpec.UNCLAIM_REFUND_PERCENT.getAsDouble());
        if (!faction.canDeposit(refund)) {
            return OperationResult.failure(Status.TREASURY_OVERFLOW);
        }

        faction.removeClaim(key);
        faction.deposit(refund);
        claimIndex.remove(key);
        chestAccess.entrySet().removeIf(entry -> isInside(entry.getKey(), key));
        setDirty();
        return OperationResult.success(factionId, refund);
    }

    public synchronized OperationResult claimOutpost(
        UUID factionId,
        net.minecraft.resources.ResourceKey<Level> dimension,
        BlockPos corePos,
        ChunkPos baseChunk
    ) {
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return OperationResult.failure(Status.FACTION_NOT_FOUND);
        }
        Set<ClaimKey> chunks = new LinkedHashSet<>();
        int size = outpostSize(faction);
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                chunks.add(new ClaimKey(dimension, baseChunk.x + dx, baseChunk.z + dz));
            }
        }
        if (chunks.stream().anyMatch(claimIndex::containsKey)) {
            return OperationResult.failure(Status.CLAIM_ALREADY_OWNED);
        }
        UUID outpostId;
        do {
            outpostId = UUID.randomUUID();
        } while (faction.outpost(outpostId).isPresent());
        faction.addOutpost(new Faction.Outpost(outpostId, dimension, corePos, chunks));
        chunks.forEach(chunk -> claimIndex.put(chunk, factionId));
        setDirty();
        return OperationResult.success(factionId, 0L);
    }

    public synchronized Optional<Faction.Outpost> detachOutpost(UUID factionId, UUID outpostId) {
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return Optional.empty();
        }
        Optional<Faction.Outpost> removed = faction.removeOutpost(outpostId);
        removed.ifPresent(outpost -> {
            outpost.chunks().forEach(claimIndex::remove);
            setDirty();
        });
        return removed;
    }

    public synchronized OperationResult attachOutpost(UUID factionId, BlockPos corePos, Set<ClaimKey> chunks) {
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return OperationResult.failure(Status.FACTION_NOT_FOUND);
        }
        if (chunks.isEmpty() || chunks.stream().anyMatch(claimIndex::containsKey)) {
            return OperationResult.failure(Status.CLAIM_ALREADY_OWNED);
        }
        net.minecraft.resources.ResourceKey<Level> dimension = chunks.iterator().next().dimension();
        UUID outpostId;
        do {
            outpostId = UUID.randomUUID();
        } while (faction.outpost(outpostId).isPresent());
        faction.addOutpost(new Faction.Outpost(outpostId, dimension, corePos, chunks));
        chunks.forEach(chunk -> claimIndex.put(chunk, factionId));
        setDirty();
        return OperationResult.success(factionId, 0L);
    }

    public synchronized OperationResult deposit(UUID factionId, long amount) {
        if (amount < 0L) {
            return OperationResult.failure(Status.INVALID_AMOUNT);
        }
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return OperationResult.failure(Status.FACTION_NOT_FOUND);
        }
        if (!faction.deposit(amount)) {
            return OperationResult.failure(Status.TREASURY_OVERFLOW);
        }
        if (amount > 0L) {
            setDirty();
        }
        return OperationResult.success(factionId, amount);
    }

    public synchronized OperationResult withdraw(UUID factionId, long amount) {
        if (amount < 0L) {
            return OperationResult.failure(Status.INVALID_AMOUNT);
        }
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return OperationResult.failure(Status.FACTION_NOT_FOUND);
        }
        if (!faction.withdraw(amount)) {
            return OperationResult.failure(Status.INSUFFICIENT_FUNDS);
        }
        if (amount > 0L) {
            setDirty();
        }
        return OperationResult.success(factionId, amount);
    }

    public synchronized OperationResult addMember(UUID factionId, UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return OperationResult.failure(Status.FACTION_NOT_FOUND);
        }
        if (memberIndex.containsKey(playerId)) {
            return OperationResult.failure(Status.PLAYER_ALREADY_MEMBER);
        }
        faction.addMember(playerId);
        memberIndex.put(playerId, factionId);
        setDirty();
        return OperationResult.success(factionId, 0L);
    }

    public synchronized OperationResult removeMember(UUID factionId, UUID playerId) {
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return OperationResult.failure(Status.FACTION_NOT_FOUND);
        }
        if (!faction.isMember(playerId)) {
            return OperationResult.failure(Status.PLAYER_NOT_MEMBER);
        }
        if (faction.ownerId().equals(playerId)) {
            return OperationResult.failure(Status.OWNER_CANNOT_LEAVE);
        }

        faction.removeMember(playerId);
        memberIndex.remove(playerId);
        List<Map.Entry<ChestAccess.Key, ChestAccess>> entries = new ArrayList<>(chestAccess.entrySet());
        for (Map.Entry<ChestAccess.Key, ChestAccess> entry : entries) {
            if (!entry.getValue().factionId().equals(factionId)) {
                continue;
            }
            ChestAccess access = entry.getValue();
            if (access.ownerId().equals(playerId)) {
                access = new ChestAccess(
                    access.key(),
                    access.factionId(),
                    faction.ownerId(),
                    access.mode(),
                    access.whitelistedPlayers()
                );
            }
            chestAccess.put(entry.getKey(), access);
        }
        setDirty();
        return OperationResult.success(factionId, 0L);
    }

    public synchronized OperationResult setMemberRole(UUID factionId, UUID playerId, FactionRole role) {
        Objects.requireNonNull(role, "role");
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return OperationResult.failure(Status.FACTION_NOT_FOUND);
        }
        if (!faction.isMember(playerId)) {
            return OperationResult.failure(Status.PLAYER_NOT_MEMBER);
        }
        if (faction.ownerId().equals(playerId) || role == FactionRole.LEADER) {
            return OperationResult.failure(Status.INVALID_ROLE_CHANGE);
        }
        faction.setMemberRole(playerId, role);
        setDirty();
        return OperationResult.success(factionId, 0L);
    }

    public synchronized OperationResult transferLeadership(UUID factionId, UUID newOwnerId) {
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return OperationResult.failure(Status.FACTION_NOT_FOUND);
        }
        if (!faction.isMember(newOwnerId)) {
            return OperationResult.failure(Status.PLAYER_NOT_MEMBER);
        }
        if (faction.ownerId().equals(newOwnerId)) {
            return OperationResult.success(factionId, 0L);
        }
        faction.transferLeadership(newOwnerId);
        setDirty();
        return OperationResult.success(factionId, 0L);
    }

    public synchronized OperationResult addInfluence(UUID factionId, long amount) {
        if (amount < 0L) {
            return OperationResult.failure(Status.INVALID_AMOUNT);
        }
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return OperationResult.failure(Status.FACTION_NOT_FOUND);
        }
        faction.addInfluence(amount);
        if (amount > 0L) {
            setDirty();
        }
        return OperationResult.success(factionId, amount);
    }

    public synchronized OperationResult spendInfluence(UUID factionId, long amount) {
        if (amount < 0L) {
            return OperationResult.failure(Status.INVALID_AMOUNT);
        }
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return OperationResult.failure(Status.FACTION_NOT_FOUND);
        }
        if (!faction.spendInfluence(amount)) {
            return OperationResult.failure(Status.INSUFFICIENT_INFLUENCE);
        }
        if (amount > 0L) {
            setDirty();
        }
        return OperationResult.success(factionId, amount);
    }

    public synchronized OperationResult addInfluence(UUID factionId, InfluenceType type, long amount) {
        if (amount < 0L) {
            return OperationResult.failure(Status.INVALID_AMOUNT);
        }
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return OperationResult.failure(Status.FACTION_NOT_FOUND);
        }
        faction.addInfluence(type, amount);
        if (amount > 0L) {
            setDirty();
        }
        return OperationResult.success(factionId, amount);
    }

    public synchronized OperationResult spendInfluence(UUID factionId, InfluenceType type, long amount) {
        if (amount < 0L) {
            return OperationResult.failure(Status.INVALID_AMOUNT);
        }
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return OperationResult.failure(Status.FACTION_NOT_FOUND);
        }
        if (!faction.spendInfluence(type, amount)) {
            return OperationResult.failure(Status.INSUFFICIENT_INFLUENCE);
        }
        if (amount > 0L) {
            setDirty();
        }
        return OperationResult.success(factionId, amount);
    }

    public synchronized OperationResult grantInfluence(UUID factionId, InfluenceType type, long baseAmount) {
        if (baseAmount < 0L) {
            return OperationResult.failure(Status.INVALID_AMOUNT);
        }
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return OperationResult.failure(Status.FACTION_NOT_FOUND);
        }
        faction.addInfluence(type, baseAmount);
        if (baseAmount > 0L) {
            setDirty();
        }
        return OperationResult.success(factionId, baseAmount);
    }

    public enum StartResearchResult {
        STARTED,
        NO_FACTION,
        ALREADY_ACTIVE,
        UNAVAILABLE,
        INSUFFICIENT_INFLUENCE
    }

    public enum ForceLoadResult {
        ENABLED,
        DISABLED,
        NO_FACTION,
        NOT_OWN_CLAIM,
        LIMIT_REACHED,
        DIMENSION_MISSING
    }

    public synchronized ForceLoadResult toggleForceLoad(MinecraftServer server, UUID factionId, ClaimKey key) {
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return ForceLoadResult.NO_FACTION;
        }
        return faction.isForceLoaded(key)
            ? disableForceLoad(server, factionId, key)
            : enableForceLoad(server, factionId, key);
    }

    public synchronized ForceLoadResult enableForceLoad(MinecraftServer server, UUID factionId, ClaimKey key) {
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return ForceLoadResult.NO_FACTION;
        }
        if (!faction.hasClaim(key) && !faction.isOutpostChunk(key)) {
            return ForceLoadResult.NOT_OWN_CLAIM;
        }
        ServerLevel level = server.getLevel(key.dimension());
        if (level == null) {
            return ForceLoadResult.DIMENSION_MISSING;
        }
        if (faction.isForceLoaded(key)) {
            return ForceLoadResult.ENABLED;
        }
        if (faction.forceLoadedCount() >= forceLoadLimit(faction)) {
            return ForceLoadResult.LIMIT_REACHED;
        }
        faction.addForceLoaded(key);
        if (!forceLoadSuspended.contains(factionId)) {
            CHUNK_TICKETS.forceChunk(level, faction.id(), key.x(), key.z(), true, true);
            appliedForceLoads.put(key, faction.id());
        }
        setDirty();
        return ForceLoadResult.ENABLED;
    }

    public synchronized ForceLoadResult disableForceLoad(MinecraftServer server, UUID factionId, ClaimKey key) {
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return ForceLoadResult.NO_FACTION;
        }
        if (!faction.isForceLoaded(key)) {
            return ForceLoadResult.DISABLED;
        }
        ServerLevel level = server.getLevel(key.dimension());
        if (level == null) {
            return ForceLoadResult.DIMENSION_MISSING;
        }
        faction.removeForceLoaded(key);
        CHUNK_TICKETS.forceChunk(level, faction.id(), key.x(), key.z(), false, true);
        appliedForceLoads.remove(key);
        setDirty();
        return ForceLoadResult.DISABLED;
    }

    public synchronized void setForceLoadsSuspended(UUID factionId, boolean suspended) {
        if (suspended) {
            forceLoadSuspended.add(factionId);
        } else {
            forceLoadSuspended.remove(factionId);
        }
    }

    public synchronized boolean areForceLoadsSuspended(UUID factionId) {
        return forceLoadSuspended.contains(factionId);
    }

    public synchronized int forceLoadLimit(UUID factionId) {
        Faction faction = factions.get(factionId);
        return faction == null ? 0 : forceLoadLimit(faction);
    }

    private int forceLoadLimit(Faction faction) {
        return ModConfigSpec.FORCE_LOAD_SLOTS.getAsInt() + faction.researchChunkSlots();
    }

    private static int outpostSize(Faction faction) {
        return faction.hasBonus(FactionBonus.BUILDERS)
            ? ModConfigSpec.BUILDER_OUTPOST_SIZE.getAsInt()
            : 2;
    }

    public enum RelocateStatus {
        SUCCESS,
        FACTION_NOT_FOUND,
        NO_CLAIMS,
        OBSTRUCTED
    }

    public record RelocateResult(RelocateStatus status, Map<ClaimKey, ClaimKey> mapping) {
        public boolean successful() {
            return status == RelocateStatus.SUCCESS;
        }
    }

    public synchronized RelocateResult relocateFaction(
        MinecraftServer server,
        UUID factionId,
        net.minecraft.resources.ResourceKey<Level> targetDimension,
        ChunkPos targetChunk,
        java.util.function.Predicate<ClaimKey> obstructed
    ) {
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return new RelocateResult(RelocateStatus.FACTION_NOT_FOUND, Map.of());
        }
        Map<net.minecraft.resources.ResourceKey<Level>, Integer> claimsPerDimension = new HashMap<>();
        for (ClaimKey claim : faction.claims()) {
            claimsPerDimension.merge(claim.dimension(), 1, Integer::sum);
        }
        net.minecraft.resources.ResourceKey<Level> sourceDimension = claimsPerDimension.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
        if (sourceDimension == null) {
            return new RelocateResult(RelocateStatus.NO_CLAIMS, Map.of());
        }

        List<ClaimKey> sourceClaims = new ArrayList<>();
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (ClaimKey claim : faction.claims()) {
            if (!claim.dimension().equals(sourceDimension)) {
                continue;
            }
            sourceClaims.add(claim);
            minX = Math.min(minX, claim.x());
            maxX = Math.max(maxX, claim.x());
            minZ = Math.min(minZ, claim.z());
            maxZ = Math.max(maxZ, claim.z());
        }
        int deltaX = targetChunk.x - (minX + maxX) / 2;
        int deltaZ = targetChunk.z - (minZ + maxZ) / 2;

        Map<ClaimKey, ClaimKey> mapping = new LinkedHashMap<>();
        for (ClaimKey claim : sourceClaims) {
            ClaimKey moved = new ClaimKey(targetDimension, claim.x() + deltaX, claim.z() + deltaZ);
            UUID occupant = claimIndex.get(moved);
            if ((occupant != null && !occupant.equals(factionId)) || obstructed.test(moved)) {
                return new RelocateResult(RelocateStatus.OBSTRUCTED, Map.of());
            }
            mapping.put(claim, moved);
        }

        Map<ClaimKey, Long> paidPrices = faction.claimPrices();
        Set<ClaimKey> protectedClaims = faction.protectedClaims();
        Set<ClaimKey> forceLoaded = faction.forceLoadedChunks();
        for (ClaimKey claim : sourceClaims) {
            claimIndex.remove(claim);
            faction.removeClaim(claim);
            faction.removeProtectedClaim(claim);
            if (forceLoaded.contains(claim)) {
                faction.removeForceLoaded(claim);
                ServerLevel level = server.getLevel(claim.dimension());
                if (level != null) {
                    CHUNK_TICKETS.forceChunk(level, factionId, claim.x(), claim.z(), false, true);
                }
                appliedForceLoads.remove(claim);
            }
        }
        for (Map.Entry<ClaimKey, ClaimKey> entry : mapping.entrySet()) {
            ClaimKey moved = entry.getValue();
            faction.addClaim(moved, paidPrices.getOrDefault(entry.getKey(), 0L));
            claimIndex.put(moved, factionId);
            if (protectedClaims.contains(entry.getKey())) {
                faction.addProtectedClaim(moved);
            }
            if (forceLoaded.contains(entry.getKey())) {
                faction.addForceLoaded(moved);
            }
        }
        chestAccess.entrySet().removeIf(entry -> entry.getValue().factionId().equals(factionId)
            && mapping.containsKey(new ClaimKey(
                entry.getKey().dimension(),
                new ChunkPos(entry.getKey().position()))));
        setDirty();
        return new RelocateResult(RelocateStatus.SUCCESS, Map.copyOf(mapping));
    }

    public synchronized void reconcileForceLoads(MinecraftServer server) {
        Map<ClaimKey, UUID> desired = new HashMap<>();
        for (Faction faction : factions.values()) {
            boolean suspended = forceLoadSuspended.contains(faction.id());
            for (ClaimKey key : faction.forceLoadedChunks()) {
                if (!faction.hasClaim(key) && !faction.isOutpostChunk(key)) {
                    if (faction.removeForceLoaded(key)) {
                        setDirty();
                    }
                    continue;
                }
                if (suspended) {
                    ServerLevel level = server.getLevel(key.dimension());
                    if (level != null) {
                        CHUNK_TICKETS.forceChunk(level, faction.id(), key.x(), key.z(), false, true);
                    }
                    appliedForceLoads.remove(key);
                    continue;
                }
                desired.put(key, faction.id());
            }
        }
        if (!chunkTicketsMigrated) {
            for (ClaimKey key : desired.keySet()) {
                ServerLevel level = server.getLevel(key.dimension());
                if (level != null) {
                    level.setChunkForced(key.x(), key.z(), false);
                }
            }
            chunkTicketsMigrated = true;
            setDirty();
        }
        for (Map.Entry<ClaimKey, UUID> applied : new ArrayList<>(appliedForceLoads.entrySet())) {
            ClaimKey key = applied.getKey();
            UUID factionId = applied.getValue();
            if (!factionId.equals(desired.get(key))) {
                ServerLevel level = server.getLevel(key.dimension());
                if (level != null) {
                    CHUNK_TICKETS.forceChunk(level, factionId, key.x(), key.z(), false, true);
                }
                appliedForceLoads.remove(key);
            }
        }
        for (Map.Entry<ClaimKey, UUID> requested : desired.entrySet()) {
            ClaimKey key = requested.getKey();
            UUID factionId = requested.getValue();
            if (!factionId.equals(appliedForceLoads.get(key))) {
                ServerLevel level = server.getLevel(key.dimension());
                if (level != null) {
                    CHUNK_TICKETS.forceChunk(level, factionId, key.x(), key.z(), true, true);
                    appliedForceLoads.put(key, factionId);
                }
            }
        }
    }

    public synchronized StartResearchResult startResearch(UUID factionId, ResearchNode node, long nowMillis) {
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return StartResearchResult.NO_FACTION;
        }
        if (faction.hasActiveResearch()) {
            return StartResearchResult.ALREADY_ACTIVE;
        }
        if (!faction.isResearchAvailable(node)) {
            return StartResearchResult.UNAVAILABLE;
        }
        if (faction.influence(node.type()) < node.cost()) {
            return StartResearchResult.INSUFFICIENT_INFLUENCE;
        }
        faction.spendInfluence(node.type(), node.cost());
        faction.startResearch(node, nowMillis);
        setDirty();
        return StartResearchResult.STARTED;
    }

    public synchronized boolean grantResearch(UUID factionId, ResearchNode node) {
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return false;
        }
        boolean changed = faction.grantResearch(node);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public synchronized void clearAllResearch(UUID factionId) {
        Faction faction = factions.get(factionId);
        if (faction != null) {
            faction.clearAllResearch();
            setDirty();
        }
    }

    public synchronized int completeFinishedResearch(long nowMillis, long baselinePerNode) {
        int completed = 0;
        for (Faction faction : factions.values()) {
            Optional<Faction.ActiveResearch> active = faction.activeResearch();
            if (active.isEmpty() || nowMillis < faction.researchEndMillis(active.get())) {
                continue;
            }
            ResearchNode node = active.get().node();
            faction.completeResearch(node);
            faction.addSafeBaseline(node.type(), baselinePerNode);
            faction.clearActiveResearch();
            completed++;
        }
        if (completed > 0) {
            setDirty();
        }
        return completed;
    }

    public synchronized long recordSellEarnings(UUID factionId, long spurs, long threshold) {
        Faction faction = factions.get(factionId);
        if (faction == null || spurs <= 0L || threshold <= 0L) {
            return 0L;
        }
        long total = faction.sellAccumulator() + spurs;
        long units = total / threshold;
        faction.setSellAccumulator(total % threshold);
        long influenceGained = units * ModConfigSpec.INFLUENCE_SELL_PER_THRESHOLD.getAsLong();
        if (influenceGained > 0L) {
            faction.addInfluence(InfluenceType.ECONOMIC, influenceGained);
        }
        setDirty();
        return influenceGained;
    }

    public synchronized long grantTreasuryIncome(long nowMillis, long intervalMillis, double incomePercent) {
        if (intervalMillis <= 0L || incomePercent <= 0.0D) {
            return 0L;
        }
        boolean changed = false;
        long totalIncome = 0L;
        for (Faction faction : factions.values()) {
            if (!faction.hasBonus(FactionBonus.MERCHANTS)) {
                continue;
            }
            long last = faction.lastTreasuryIncomeMillis();
            if (last <= 0L || nowMillis < last) {
                faction.setLastTreasuryIncomeMillis(nowMillis);
                changed = true;
                continue;
            }
            long periods = (nowMillis - last) / intervalMillis;
            if (periods <= 0L) {
                continue;
            }
            for (long period = 0L; period < periods; period++) {
                long balance = faction.treasuryBalance();
                long income = PriceMath.percentageCeil(balance, incomePercent);
                long available = Long.MAX_VALUE - balance;
                long deposit = Math.min(income, available);
                if (deposit <= 0L) {
                    break;
                }
                if (faction.deposit(deposit)) {
                    totalIncome = PriceMath.saturatedAdd(totalIncome, deposit);
                    changed = true;
                }
                if (deposit < income) {
                    break;
                }
            }
            faction.setLastTreasuryIncomeMillis(last + periods * intervalMillis);
            changed = true;
        }
        if (changed) {
            setDirty();
        }
        return totalIncome;
    }

    public synchronized boolean decayInfluence(long nowMillis, long intervalMillis, double decayPercent) {
        if (intervalMillis <= 0L) {
            return false;
        }
        if (lastInfluenceDecayMillis < 0L || nowMillis < lastInfluenceDecayMillis) {
            lastInfluenceDecayMillis = nowMillis;
            setDirty();
            return false;
        }
        if (nowMillis - lastInfluenceDecayMillis < intervalMillis) {
            return false;
        }
        boolean changed = false;
        for (Faction faction : factions.values()) {
            for (InfluenceType type : InfluenceType.VALUES) {
                long current = faction.influence(type);
                long baseline = faction.safeBaseline(type);
                if (current <= baseline) {
                    continue;
                }
                long reduction = Math.round((current - baseline) * decayPercent);
                if (reduction <= 0L) {
                    continue;
                }
                faction.setInfluence(type, Math.max(baseline, current - reduction));
                changed = true;
            }
        }
        lastInfluenceDecayMillis = nowMillis;
        setDirty();
        return changed;
    }


    public synchronized Optional<ChestAccess> getChestAccess(ChestAccess.Key key) {
        return Optional.ofNullable(chestAccess.get(key));
    }

    public synchronized Optional<ChestAccess> getChestAccess(Level level, BlockPos position) {
        return getChestAccess(ChestAccess.Key.of(level, position));
    }

    public synchronized Collection<ChestAccess> chestAccessEntries() {
        return List.copyOf(chestAccess.values());
    }

    public synchronized OperationResult setChestAccess(ChestAccess access) {
        Objects.requireNonNull(access, "access");
        Faction faction = factions.get(access.factionId());
        if (faction == null) {
            return OperationResult.failure(Status.FACTION_NOT_FOUND);
        }
        if (!faction.isMember(access.ownerId())) {
            return OperationResult.failure(Status.PLAYER_NOT_MEMBER);
        }
        ClaimKey claim = new ClaimKey(access.key().dimension(), new ChunkPos(access.key().position()));
        if (!access.factionId().equals(claimIndex.get(claim))) {
            return OperationResult.failure(Status.CHEST_OUTSIDE_TERRITORY);
        }
        chestAccess.put(access.key(), access);
        setDirty();
        return OperationResult.success(access.factionId(), 0L);
    }

    public synchronized OperationResult setChestAccess(
        UUID factionId,
        UUID ownerId,
        Level level,
        BlockPos position,
        ChestAccessMode mode
    ) {
        return setChestAccess(new ChestAccess(ChestAccess.Key.of(level, position), factionId, ownerId, mode));
    }

    public synchronized boolean removeChestAccess(ChestAccess.Key key) {
        if (chestAccess.remove(key) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    public synchronized boolean canAccessChest(UUID playerId, ChestAccess.Key key) {
        ChestAccess access = chestAccess.get(key);
        if (access == null) {
            return true;
        }
        UUID playerFactionId = memberIndex.get(playerId);
        return access.canAccess(playerId, playerFactionId);
    }

    /**
     * Claim-aware container access check. Unclaimed land is unprotected; on a claim
     * with no per-container rule only members of the owning faction may access, and a
     * {@link ChestAccess} rule, when present, takes precedence.
     */
    public synchronized boolean canAccessContainer(UUID playerId, Level level, BlockPos position) {
        Objects.requireNonNull(playerId, "playerId");
        UUID claimFactionId = claimIndex.get(ClaimKey.of(level, position));
        if (claimFactionId == null) {
            return true;
        }
        ChestAccess access = chestAccess.get(ChestAccess.Key.of(level, position));
        if (access == null) {
            BlockPos linked = ChestLinks.linkedPosition(level, position);
            if (linked != null) {
                access = chestAccess.get(ChestAccess.Key.of(level, linked));
            }
        }
        UUID playerFactionId = memberIndex.get(playerId);
        if (access == null) {
            return claimFactionId.equals(playerFactionId);
        }
        return access.canAccess(playerId, playerFactionId);
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(TAG_VERSION, DATA_VERSION);
        tag.putLong(TAG_LAST_INFLUENCE_DECAY, lastInfluenceDecayMillis);
        tag.putBoolean(TAG_CHUNK_TICKETS_MIGRATED, chunkTicketsMigrated);

        ListTag factionsTag = new ListTag();
        factions.values().stream()
            .sorted(Comparator.comparing(faction -> faction.id().toString()))
            .map(Faction::save)
            .forEach(factionsTag::add);
        tag.put(TAG_FACTIONS, factionsTag);

        ListTag chestsTag = new ListTag();
        chestAccess.values().stream()
            .sorted(Comparator.comparing(ChestAccess::key))
            .map(ChestAccess::save)
            .forEach(chestsTag::add);
        tag.put(TAG_CHESTS, chestsTag);
        return tag;
    }

    private static FactionManager load(CompoundTag tag, HolderLookup.Provider registries) {
        FactionManager manager = new FactionManager();
        manager.lastInfluenceDecayMillis = tag.contains(TAG_LAST_INFLUENCE_DECAY)
            ? tag.getLong(TAG_LAST_INFLUENCE_DECAY)
            : -1L;
        manager.chunkTicketsMigrated = tag.getBoolean(TAG_CHUNK_TICKETS_MIGRATED);
        boolean repaired = tag.getInt(TAG_VERSION) != DATA_VERSION;

        ListTag factionsTag = tag.getList(TAG_FACTIONS, Tag.TAG_COMPOUND);
        for (int index = 0; index < factionsTag.size(); index++) {
            Optional<Faction> loaded = Faction.load(factionsTag.getCompound(index));
            if (loaded.isEmpty() || !manager.indexLoadedFaction(loaded.get())) {
                repaired = true;
                LOGGER.warn("Skipped invalid or conflicting faction entry at NBT index {}", index);
            }
        }

        ListTag chestsTag = tag.getList(TAG_CHESTS, Tag.TAG_COMPOUND);
        for (int index = 0; index < chestsTag.size(); index++) {
            Optional<ChestAccess> loaded = ChestAccess.load(chestsTag.getCompound(index));
            if (loaded.isEmpty() || !manager.indexLoadedChest(loaded.get())) {
                repaired = true;
                LOGGER.warn("Skipped invalid or conflicting chest access entry at NBT index {}", index);
            }
        }

        for (Faction faction : manager.factions.values()) {
            for (UUID ally : faction.allies()) {
                Faction alliedFaction = manager.factions.get(ally);
                if (alliedFaction == null) {
                    faction.removeAlly(ally);
                    repaired = true;
                } else if (!alliedFaction.isAlliedWith(faction.id())) {
                    alliedFaction.addAlly(faction.id());
                    repaired = true;
                }
            }
        }

        if (repaired) {
            manager.setDirty();
        }
        return manager;
    }

    private boolean indexLoadedFaction(Faction faction) {
        String normalizedName = normalizeName(faction.name());
        if (!isValidName(faction.name())
            || !isValidColor(faction.color())
            || factions.containsKey(faction.id())
            || nameIndex.containsKey(normalizedName)
            || faction.members().keySet().stream().anyMatch(memberIndex::containsKey)
            || faction.claims().stream().anyMatch(claimIndex::containsKey)
            || faction.outpostChunks().stream().anyMatch(claimIndex::containsKey)
            || !claimsAreConnectedByDimension(faction.claims())) {
            return false;
        }

        factions.put(faction.id(), faction);
        nameIndex.put(normalizedName, faction.id());
        faction.members().keySet().forEach(playerId -> memberIndex.put(playerId, faction.id()));
        faction.claims().forEach(claim -> claimIndex.put(claim, faction.id()));
        faction.outpostChunks().forEach(claim -> claimIndex.put(claim, faction.id()));
        return true;
    }

    private boolean indexLoadedChest(ChestAccess access) {
        Faction faction = factions.get(access.factionId());
        ClaimKey claim = new ClaimKey(access.key().dimension(), new ChunkPos(access.key().position()));
        if (faction == null
            || !faction.isMember(access.ownerId())
            || !access.factionId().equals(claimIndex.get(claim))
            || chestAccess.containsKey(access.key())) {
            return false;
        }
        chestAccess.put(access.key(), access);
        return true;
    }

    private static Set<ClaimKey> square(ClaimKey center, int size) {
        int minimumOffset = -(size / 2);
        Set<ClaimKey> claims = new LinkedHashSet<>();
        for (int xOffset = minimumOffset; xOffset < minimumOffset + size; xOffset++) {
            for (int zOffset = minimumOffset; zOffset < minimumOffset + size; zOffset++) {
                claims.add(center.offset(xOffset, zOffset));
            }
        }
        return claims;
    }

    private static Set<ClaimKey> claimsInDimension(Faction faction, net.minecraft.resources.ResourceKey<Level> dimension) {
        Set<ClaimKey> result = new HashSet<>();
        for (ClaimKey claim : faction.claims()) {
            if (claim.dimension().equals(dimension)) {
                result.add(claim);
            }
        }
        return result;
    }

    private static boolean claimsAreConnectedByDimension(Collection<ClaimKey> claims) {
        Map<net.minecraft.resources.ResourceKey<Level>, Set<ClaimKey>> byDimension = new HashMap<>();
        for (ClaimKey claim : claims) {
            byDimension.computeIfAbsent(claim.dimension(), ignored -> new HashSet<>()).add(claim);
        }
        return byDimension.values().stream().allMatch(FactionManager::isConnected);
    }

    private static boolean isConnected(Set<ClaimKey> claims) {
        if (claims.size() < 2) {
            return true;
        }
        Set<ClaimKey> visited = new HashSet<>();
        Deque<ClaimKey> pending = new ArrayDeque<>();
        pending.add(claims.iterator().next());
        while (!pending.isEmpty()) {
            ClaimKey current = pending.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            for (ClaimKey neighbor : current.cardinalNeighbors()) {
                if (claims.contains(neighbor) && !visited.contains(neighbor)) {
                    pending.addLast(neighbor);
                }
            }
        }
        return visited.size() == claims.size();
    }

    private static boolean isInside(ChestAccess.Key chest, ClaimKey claim) {
        return chest.dimension().equals(claim.dimension())
            && new ChunkPos(chest.position()).equals(claim.chunk());
    }

    private static String cleanName(String name) {
        return name == null ? "" : name.strip();
    }

    private static String normalizeName(String name) {
        return cleanName(name).toLowerCase(Locale.ROOT);
    }

    private static boolean isValidName(String name) {
        if (name == null || name.isBlank() || name.length() > MAX_NAME_LENGTH) {
            return false;
        }
        return name.codePoints().noneMatch(Character::isISOControl);
    }

    private static boolean isValidColor(int color) {
        return color >= 0 && color <= MAX_RGB_COLOR;
    }

    public enum Status {
        SUCCESS,
        FACTION_NOT_FOUND,
        INVALID_NAME,
        INVALID_COLOR,
        NAME_TAKEN,
        INVALID_STARTER_SIZE,
        PLAYER_ALREADY_MEMBER,
        PLAYER_NOT_MEMBER,
        OWNER_CANNOT_LEAVE,
        INVALID_ROLE_CHANGE,
        CLAIM_ALREADY_OWNED,
        CLAIM_NOT_OWNED,
        CLAIM_NOT_ADJACENT,
        CLAIM_WOULD_DISCONNECT,
        CLAIM_PROTECTED,
        CHEST_OUTSIDE_TERRITORY,
        INVALID_AMOUNT,
        INSUFFICIENT_FUNDS,
        TREASURY_OVERFLOW,
        INSUFFICIENT_INFLUENCE,
        INVALID_ALLIANCE,
        NOT_ALLIED,
        OUTPOST_CHUNK
    }

    public record OperationResult(Status status, UUID factionId, long amount) {
        public OperationResult {
            Objects.requireNonNull(status, "status");
        }

        public boolean successful() {
            return status == Status.SUCCESS;
        }

        private static OperationResult success(UUID factionId, long amount) {
            return new OperationResult(Status.SUCCESS, factionId, amount);
        }

        private static OperationResult failure(Status status) {
            return new OperationResult(status, null, 0L);
        }
    }
}
