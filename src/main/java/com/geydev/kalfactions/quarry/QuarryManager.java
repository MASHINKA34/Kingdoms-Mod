package com.geydev.kalfactions.quarry;

import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.command.NumismaticsEconomy;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.net.ClaimSyncManager;
import com.geydev.kalfactions.net.FactionServerHooks;
import com.geydev.kalfactions.registry.ModBlocks;
import com.geydev.kalfactions.registry.ModItems;
import com.geydev.kalfactions.sanctuary.SanctuaryManager;
import com.geydev.kalfactions.territory.WorldZonePolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;

public final class QuarryManager extends SavedData {
    public static final String DATA_NAME = "kingdoms_quarries";
    public static final UUID NEUTRAL_MAP_ID =
            new UUID(0x5155_4152_5259_5F4EL, 0x4555_5452_414C_3031L);
    public static final int NEUTRAL_COLOR = 0x777777;
    public static final int MINIMUM_SPACING_CHUNKS = 10;
    public static final int TERRITORY_RADIUS_CHUNKS = 1;
    public static final int CAPTURE_TICKS = 5 * 60 * 20;
    public static final int MAX_LEVEL = 5;
    public static final Factory<QuarryManager> FACTORY =
            new Factory<>(QuarryManager::new, QuarryManager::load);

    private static final String TAG_QUARRIES = "quarries";
    private final Map<UUID, Quarry> quarries = new LinkedHashMap<>();
    private final Map<Long, UUID> coreIndex = new LinkedHashMap<>();
    private final transient Map<UUID, ServerBossEvent> bossBars = new LinkedHashMap<>();
    private transient long mapRevision;

    public static QuarryManager get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static QuarryManager get(ServerLevel level) {
        return get(level.getServer());
    }

    public synchronized Collection<QuarryView> all() {
        return quarries.values().stream()
                .sorted(Comparator.comparing(quarry -> quarry.id.toString()))
                .map(Quarry::view)
                .toList();
    }

    public synchronized Optional<QuarryView> byCore(BlockPos pos) {
        UUID id = coreIndex.get(pos.asLong());
        Quarry quarry = id == null ? null : quarries.get(id);
        return quarry == null ? Optional.empty() : Optional.of(quarry.view());
    }

    public synchronized long mapRevision() {
        return mapRevision;
    }

    public synchronized boolean isQuarry(ServerLevel level, BlockPos pos) {
        if (!level.dimension().equals(Level.OVERWORLD)) {
            return false;
        }
        ClaimKey key = ClaimKey.of(level, pos);
        return quarries.values().stream().anyMatch(quarry -> quarry.chunks.contains(key));
    }

    public synchronized void generateIfCandidate(ServerLevel level, ChunkPos chunk) {
        if (!level.dimension().equals(Level.OVERWORLD)
                || !WorldZonePolicy.isBlack(level, chunk)
                || !QuarryDistribution.isCandidate(
                        level.getSeed() ^ 0x5155415252594C31L,
                        chunk.x,
                        chunk.z,
                        MINIMUM_SPACING_CHUNKS
                )) {
            return;
        }
        createAtChunk(level, chunk);
    }

    public synchronized CreateResult createAtChunk(ServerLevel level, ChunkPos chunk) {
        if (!level.dimension().equals(Level.OVERWORLD) || !WorldZonePolicy.isBlack(level, chunk)) {
            return CreateResult.NOT_BLACK;
        }
        Set<ClaimKey> territory = territory(level.dimension(), chunk);
        FactionManager factions = FactionManager.get(level);
        SanctuaryManager sanctuary = SanctuaryManager.get(level);
        if (territory.stream().anyMatch(key ->
                factions.getFactionAt(key).isPresent() || sanctuary.isSanctuary(key))) {
            return CreateResult.OVERLAP;
        }
        if (quarries.values().stream().anyMatch(quarry -> {
            ChunkPos existing = new ChunkPos(quarry.core);
            return Math.max(Math.abs(existing.x - chunk.x), Math.abs(existing.z - chunk.z))
                    <= MINIMUM_SPACING_CHUNKS;
        })) {
            return CreateResult.TOO_CLOSE;
        }
        int x = chunk.getMiddleBlockX();
        int z = chunk.getMiddleBlockZ();
        if (!level.getWorldBorder().isWithinBounds(new BlockPos(x, level.getSeaLevel(), z))) {
            return CreateResult.BLOCKED;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos core = new BlockPos(x, y, z);
        if (!level.getBlockState(core).canBeReplaced()) {
            core = core.above();
        }
        if (!level.getBlockState(core).canBeReplaced()) {
            return CreateResult.BLOCKED;
        }
        UUID id = UUID.nameUUIDFromBytes(("kingdoms:quarry:" + level.getSeed() + ":" + chunk.toLong())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (quarries.containsKey(id) || coreIndex.containsKey(core.asLong())) {
            return CreateResult.OVERLAP;
        }
        Quarry quarry = new Quarry(id, core.immutable(), territory, null, 0, null, CAPTURE_TICKS, false);
        quarries.put(id, quarry);
        coreIndex.put(core.asLong(), id);
        level.setBlockAndUpdate(core, ModBlocks.QUARRY_CORE.get().defaultBlockState());
        setDirty();
        mapRevision++;
        sync(level.getServer());
        return CreateResult.CREATED;
    }

    public synchronized void interact(ServerPlayer player, BlockPos core, ItemStack held, boolean secondaryUse) {
        if (!player.serverLevel().dimension().equals(Level.OVERWORLD)) {
            return;
        }
        Quarry quarry = quarry(core);
        if (quarry == null) {
            return;
        }
        FactionManager factions = FactionManager.get(player.serverLevel());
        Faction faction = factions.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            notice(player, "kingdoms.quarry.not_in_faction", false);
            return;
        }
        if (quarry.ownerFactionId == null) {
            if (!held.is(ModItems.QUARRY_ACTIVATOR.get())) {
                notice(player, "kingdoms.quarry.requires_activator", false);
                return;
            }
            quarry.ownerFactionId = faction.id();
            quarry.level = Math.max(1, quarry.level);
            if (!player.isCreative()) {
                held.shrink(1);
            }
            setDirty();
            mapRevision++;
            sync(player.getServer());
            notifyFaction(player.getServer(), faction.id(),
                    Component.translatable("kingdoms.quarry.activated", quarry.core.getX(), quarry.core.getZ()), true);
            return;
        }
        if (quarry.ownerFactionId.equals(faction.id())) {
            if (secondaryUse) {
                upgrade(player, factions, faction, quarry);
            } else {
                long nextCost = quarry.level >= MAX_LEVEL ? 0L : upgradeCost(quarry.level);
                FactionServerHooks.sendNotice(player, Component.translatable(
                        "kingdoms.quarry.status",
                        quarry.level,
                        MAX_LEVEL,
                        nextCost == 0L ? "-" : NumismaticsEconomy.format(nextCost)
                ), true);
            }
            return;
        }
        startCapture(player, faction, quarry);
    }

    public synchronized void tick(MinecraftServer server) {
        boolean dirty = false;
        for (Quarry quarry : quarries.values()) {
            FactionManager factions = FactionManager.get(server);
            if (quarry.ownerFactionId != null && factions.getFactionById(quarry.ownerFactionId).isEmpty()) {
                quarry.ownerFactionId = null;
                quarry.attackerFactionId = null;
                quarry.captureTicksRemaining = CAPTURE_TICKS;
                quarry.capturePaused = false;
                removeBossBar(quarry.id);
                mapRevision++;
                sync(server);
                dirty = true;
                continue;
            }
            if (quarry.attackerFactionId == null) {
                removeBossBar(quarry.id);
                continue;
            }
            if (factions.getFactionById(quarry.attackerFactionId).isEmpty()
                    || quarry.ownerFactionId == null
                    || factions.getFactionById(quarry.ownerFactionId).isEmpty()) {
                resetCapture(server, quarry, false);
                dirty = true;
                continue;
            }
            boolean attackersPresent = hasFactionPlayerInTerritory(server, quarry, quarry.attackerFactionId);
            boolean defendersPresent = hasFactionPlayerInTerritory(server, quarry, quarry.ownerFactionId);
            QuarryCaptureRules.TickResult capture = QuarryCaptureRules.tick(
                    attackersPresent,
                    defendersPresent,
                    quarry.captureTicksRemaining,
                    20,
                    CAPTURE_TICKS
            );
            if (capture.action() == QuarryCaptureRules.Action.RESET) {
                resetCapture(server, quarry, true);
                dirty = true;
                continue;
            }
            boolean paused = capture.action() == QuarryCaptureRules.Action.PAUSED;
            if (quarry.capturePaused != paused || quarry.captureTicksRemaining != capture.remainingTicks()) {
                quarry.capturePaused = paused;
                quarry.captureTicksRemaining = capture.remainingTicks();
                dirty = true;
            }
            updateBossBar(server, quarry);
            if (capture.action() == QuarryCaptureRules.Action.CAPTURED) {
                UUID previousOwner = quarry.ownerFactionId;
                UUID newOwner = quarry.attackerFactionId;
                quarry.ownerFactionId = newOwner;
                quarry.attackerFactionId = null;
                quarry.captureTicksRemaining = CAPTURE_TICKS;
                quarry.capturePaused = false;
                removeBossBar(quarry.id);
                notifyFaction(server, previousOwner,
                        Component.translatable("kingdoms.quarry.lost", quarry.core.getX(), quarry.core.getZ()), false);
                notifyFaction(server, newOwner,
                        Component.translatable("kingdoms.quarry.captured", quarry.core.getX(), quarry.core.getZ()), true);
                mapRevision++;
                sync(server);
                dirty = true;
            }
        }
        if (dirty) {
            setDirty();
        }
    }

    public synchronized boolean removeByCore(ServerLevel level, BlockPos core) {
        if (!level.dimension().equals(Level.OVERWORLD)) {
            return false;
        }
        UUID id = coreIndex.remove(core.asLong());
        if (id == null) {
            return false;
        }
        quarries.remove(id);
        removeBossBar(id);
        setDirty();
        mapRevision++;
        sync(level.getServer());
        return true;
    }

    public synchronized void clearBossBars() {
        for (ServerBossEvent bossBar : bossBars.values()) {
            bossBar.removeAllPlayers();
        }
        bossBars.clear();
    }

    private void upgrade(ServerPlayer player, FactionManager factions, Faction faction, Quarry quarry) {
        if (quarry.level >= MAX_LEVEL) {
            notice(player, "kingdoms.quarry.max_level", false);
            return;
        }
        long cost = upgradeCost(quarry.level);
        FactionManager.OperationResult result = factions.withdraw(faction.id(), cost);
        if (!result.successful()) {
            FactionServerHooks.sendNotice(
                    player,
                    Component.translatable("kingdoms.quarry.not_enough_money", NumismaticsEconomy.format(cost)),
                    false
            );
            return;
        }
        quarry.level++;
        setDirty();
        notifyFaction(player.getServer(), faction.id(),
                Component.translatable("kingdoms.quarry.upgraded", quarry.level), true);
    }

    private void startCapture(ServerPlayer player, Faction attacker, Quarry quarry) {
        if (quarry.attackerFactionId != null && !quarry.attackerFactionId.equals(attacker.id())) {
            notice(player, "kingdoms.quarry.capture_busy", false);
            return;
        }
        if (quarry.attackerFactionId == null) {
            quarry.attackerFactionId = attacker.id();
            quarry.captureTicksRemaining = CAPTURE_TICKS;
            quarry.capturePaused = false;
            setDirty();
            Faction owner = FactionManager.get(player.getServer())
                    .getFactionById(quarry.ownerFactionId)
                    .orElse(null);
            notifyFaction(
                    player.getServer(),
                    quarry.ownerFactionId,
                    Component.translatable(
                            "kingdoms.quarry.under_attack",
                            attacker.name(),
                            quarry.core.getX(),
                            quarry.core.getZ()
                    ),
                    false
            );
            notifyFaction(
                    player.getServer(),
                    attacker.id(),
                    Component.translatable(
                            "kingdoms.quarry.capture_started",
                            owner == null ? "?" : owner.name()
                    ),
                    true
            );
        }
        updateBossBar(player.getServer(), quarry);
    }

    private void resetCapture(MinecraftServer server, Quarry quarry, boolean notify) {
        UUID attacker = quarry.attackerFactionId;
        quarry.attackerFactionId = null;
        quarry.captureTicksRemaining = CAPTURE_TICKS;
        quarry.capturePaused = false;
        removeBossBar(quarry.id);
        if (notify && attacker != null) {
            notifyFaction(server, attacker, Component.translatable("kingdoms.quarry.capture_reset"), false);
            notifyFaction(server, quarry.ownerFactionId, Component.translatable("kingdoms.quarry.defended"), true);
        }
    }

    private void updateBossBar(MinecraftServer server, Quarry quarry) {
        if (quarry.attackerFactionId == null) {
            return;
        }
        ServerBossEvent bar = bossBars.computeIfAbsent(quarry.id, ignored -> new ServerBossEvent(
                Component.empty(),
                BossEvent.BossBarColor.YELLOW,
                BossEvent.BossBarOverlay.PROGRESS
        ));
        bar.setName(Component.translatable(
                quarry.capturePaused ? "kingdoms.quarry.bossbar_paused" : "kingdoms.quarry.bossbar",
                Math.max(0, quarry.captureTicksRemaining / 20)
        ));
        bar.setProgress(Math.clamp(quarry.captureTicksRemaining / (float) CAPTURE_TICKS, 0.0F, 1.0F));
        bar.removeAllPlayers();
        FactionManager factions = FactionManager.get(server);
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            UUID factionId = factions.getFactionIdForMember(online.getUUID()).orElse(null);
            if (factionId != null
                    && (factionId.equals(quarry.ownerFactionId) || factionId.equals(quarry.attackerFactionId))) {
                bar.addPlayer(online);
            }
        }
    }

    private void removeBossBar(UUID quarryId) {
        ServerBossEvent bar = bossBars.remove(quarryId);
        if (bar != null) {
            bar.removeAllPlayers();
        }
    }

    private static boolean hasFactionPlayerInTerritory(MinecraftServer server, Quarry quarry, UUID factionId) {
        FactionManager factions = FactionManager.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive() || player.isSpectator()
                    || !factionId.equals(factions.getFactionIdForMember(player.getUUID()).orElse(null))) {
                continue;
            }
            if (quarry.chunks.contains(ClaimKey.of(player.serverLevel(), player.blockPosition()))) {
                return true;
            }
        }
        return false;
    }

    private Quarry quarry(BlockPos core) {
        UUID id = coreIndex.get(core.asLong());
        return id == null ? null : quarries.get(id);
    }

    private static Set<ClaimKey> territory(ResourceKey<Level> dimension, ChunkPos center) {
        Set<ClaimKey> result = new LinkedHashSet<>();
        for (int dx = -TERRITORY_RADIUS_CHUNKS; dx <= TERRITORY_RADIUS_CHUNKS; dx++) {
            for (int dz = -TERRITORY_RADIUS_CHUNKS; dz <= TERRITORY_RADIUS_CHUNKS; dz++) {
                result.add(new ClaimKey(dimension, center.x + dx, center.z + dz));
            }
        }
        return Set.copyOf(result);
    }

    private static void notice(ServerPlayer player, String key, boolean successful) {
        FactionServerHooks.sendNotice(player, Component.translatable(key), successful);
    }

    private static void notifyFaction(MinecraftServer server, UUID factionId, Component message, boolean successful) {
        if (factionId == null) {
            return;
        }
        FactionManager factions = FactionManager.get(server);
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (factionId.equals(factions.getFactionIdForMember(online.getUUID()).orElse(null))) {
                FactionServerHooks.sendNotice(online, message, successful);
            }
        }
    }

    private static void sync(MinecraftServer server) {
        ClaimSyncManager.resyncAll(server);
    }

    private static long upgradeCost(int currentLevel) {
        return switch (currentLevel) {
            case 1 -> ModConfigSpec.QUARRY_LEVEL_2_COST.getAsLong();
            case 2 -> ModConfigSpec.QUARRY_LEVEL_3_COST.getAsLong();
            case 3 -> ModConfigSpec.QUARRY_LEVEL_4_COST.getAsLong();
            case 4 -> ModConfigSpec.QUARRY_LEVEL_5_COST.getAsLong();
            default -> 0L;
        };
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Quarry quarry : quarries.values()) {
            list.add(quarry.save());
        }
        tag.put(TAG_QUARRIES, list);
        return tag;
    }

    private static QuarryManager load(CompoundTag tag, HolderLookup.Provider registries) {
        QuarryManager manager = new QuarryManager();
        ListTag list = tag.getList(TAG_QUARRIES, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            Quarry.load(list.getCompound(index)).ifPresent(quarry -> {
                if (!manager.quarries.containsKey(quarry.id)
                        && !manager.coreIndex.containsKey(quarry.core.asLong())) {
                    manager.quarries.put(quarry.id, quarry);
                    manager.coreIndex.put(quarry.core.asLong(), quarry.id);
                }
            });
        }
        return manager;
    }

    public record QuarryView(
            UUID id,
            BlockPos core,
            Set<ClaimKey> chunks,
            UUID ownerFactionId,
            int level,
            UUID attackerFactionId,
            int captureTicksRemaining,
            boolean capturePaused
    ) {
    }

    public enum CreateResult {
        CREATED,
        NOT_BLACK,
        OVERLAP,
        TOO_CLOSE,
        BLOCKED
    }

    private static final class Quarry {
        private final UUID id;
        private final BlockPos core;
        private final Set<ClaimKey> chunks;
        private UUID ownerFactionId;
        private int level;
        private UUID attackerFactionId;
        private int captureTicksRemaining;
        private boolean capturePaused;

        private Quarry(
                UUID id,
                BlockPos core,
                Set<ClaimKey> chunks,
                UUID ownerFactionId,
                int level,
                UUID attackerFactionId,
                int captureTicksRemaining,
                boolean capturePaused
        ) {
            this.id = id;
            this.core = core;
            this.chunks = Set.copyOf(chunks);
            this.ownerFactionId = ownerFactionId;
            this.level = level;
            this.attackerFactionId = attackerFactionId;
            this.captureTicksRemaining = captureTicksRemaining;
            this.capturePaused = capturePaused;
        }

        private QuarryView view() {
            return new QuarryView(
                    id,
                    core,
                    chunks,
                    ownerFactionId,
                    level,
                    attackerFactionId,
                    captureTicksRemaining,
                    capturePaused
            );
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", id);
            tag.putLong("core", core.asLong());
            ListTag chunkList = new ListTag();
            chunks.stream().sorted().map(ClaimKey::save).forEach(chunkList::add);
            tag.put("chunks", chunkList);
            if (ownerFactionId != null) {
                tag.putUUID("owner", ownerFactionId);
            }
            tag.putInt("level", level);
            if (attackerFactionId != null) {
                tag.putUUID("attacker", attackerFactionId);
            }
            tag.putInt("captureTicks", captureTicksRemaining);
            tag.putBoolean("capturePaused", capturePaused);
            return tag;
        }

        private static Optional<Quarry> load(CompoundTag tag) {
            if (!tag.hasUUID("id") || !tag.contains("core", Tag.TAG_LONG)) {
                return Optional.empty();
            }
            Set<ClaimKey> chunks = new LinkedHashSet<>();
            ListTag chunkList = tag.getList("chunks", Tag.TAG_COMPOUND);
            for (int index = 0; index < chunkList.size(); index++) {
                ClaimKey.load(chunkList.getCompound(index)).ifPresent(chunks::add);
            }
            if (chunks.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new Quarry(
                    tag.getUUID("id"),
                    BlockPos.of(tag.getLong("core")),
                    chunks,
                    tag.hasUUID("owner") ? tag.getUUID("owner") : null,
                    Math.clamp(tag.getInt("level"), 0, MAX_LEVEL),
                    tag.hasUUID("attacker") ? tag.getUUID("attacker") : null,
                    Math.clamp(tag.getInt("captureTicks"), 0, CAPTURE_TICKS),
                    tag.getBoolean("capturePaused")
            ));
        }
    }

    private QuarryManager() {
    }
}
