package com.geydev.kalfactions.tax;

import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.command.NumismaticsEconomy;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.economy.PriceMath;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.faction.FactionMember;
import com.geydev.kalfactions.faction.FactionRole;
import com.geydev.kalfactions.integration.IntegrationManager;
import com.geydev.kalfactions.net.ClaimSyncManager;
import com.geydev.kalfactions.net.FactionServerHooks;
import com.geydev.kalfactions.profiling.ChunkProfiler;
import com.geydev.kalfactions.profiling.FrozenChunks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.network.PacketDistributor;

public final class LagTaxService {
    private static final long DAY_TICKS = 24_000L;
    private static final long HOUR_MILLIS = 3_600_000L;
    private static final long DAY_MILLIS = 24L * HOUR_MILLIS;
    private static final int DETAIL_SAMPLE_COUNT = 25;
    private static final double HARD_CAP_RELEASE_FACTOR = 0.8D;
    private static final double MIN_DISPLAY_MS = 0.005D;
    private static final int LOAD_WINDOW = 5;

    private static final Map<UUID, double[]> RECENT_LOADS = new HashMap<>();
    private static int loadCursor;

    public static void initialize(MinecraftServer server) {
        LagTaxManager manager = LagTaxManager.get(server);
        FactionManager factions = FactionManager.get(server);
        for (UUID factionId : manager.frozenFactionIds()) {
            factions.setForceLoadsSuspended(factionId, true);
        }
        long graceExpiry = System.currentTimeMillis() + 24L * HOUR_MILLIS;
        for (Faction faction : factions.factions()) {
            LagTaxManager.FactionTaxState state = manager.state(faction.id());
            for (ClaimKey key : faction.forceLoadedChunks()) {
                if (!state.chunkLoads().containsKey(key)) {
                    manager.putChunkLoad(faction.id(), key, graceExpiry, 24);
                }
            }
        }
        FrozenChunks.rebuild(server, manager.frozenFactionIds());
        factions.reconcileForceLoads(server);
        checkChunkLoads(server);
    }

    public static void handleSample(MinecraftServer server, ChunkProfiler.SampleResult sample) {
        LagTaxManager manager = LagTaxManager.get(server);
        double quota = ModConfigSpec.LAGTAX_QUOTA_MS.getAsDouble();
        double tier1 = ModConfigSpec.LAGTAX_TIER1_LIMIT_MS.getAsDouble();
        double tier2 = ModConfigSpec.LAGTAX_TIER2_LIMIT_MS.getAsDouble();
        long price1 = ModConfigSpec.LAGTAX_TIER1_PRICE.getAsLong();
        long price2 = ModConfigSpec.LAGTAX_TIER2_PRICE.getAsLong();
        long price3 = ModConfigSpec.LAGTAX_TIER3_PRICE.getAsLong();
        long ticks = sample.ticksCovered();

        loadCursor = (loadCursor + 1) % LOAD_WINDOW;
        java.util.Set<UUID> tracked = new java.util.HashSet<>(RECENT_LOADS.keySet());
        tracked.addAll(sample.factionLoads().keySet());
        for (UUID factionId : tracked) {
            double[] window = RECENT_LOADS.computeIfAbsent(factionId, ignored -> new double[LOAD_WINDOW]);
            window[loadCursor] = sample.factionLoads().getOrDefault(factionId, 0.0D);
            double load = median(window);
            if (load <= 0.0D) {
                if (isIdle(window)) {
                    RECENT_LOADS.remove(factionId);
                }
                continue;
            }
            long costMicros = TaxMath.accrualMicros(
                    load, quota, tier1, tier2, price1, price2, price3, ticks, DAY_TICKS);
            double excess = Math.max(0.0D, load - quota);
            long excessIntegral = Math.round(excess * TaxMath.MICROS * ticks);
            long loadIntegral = Math.round(load * TaxMath.MICROS * ticks);
            if (costMicros > 0L || excessIntegral > 0L || loadIntegral > 0L) {
                manager.accrue(factionId, costMicros, excessIntegral, loadIntegral);
            }
        }

        manager.advancePeriod(ticks);
        if (manager.periodTicks() >= ModConfigSpec.LAGTAX_INTERVAL_TICKS.getAsInt()) {
            runBilling(server);
        }

        if (sample.capture() != null) {
            sendCaptureResult(server, sample.capture());
        }
    }

    private static void runBilling(MinecraftServer server) {
        LagTaxManager manager = LagTaxManager.get(server);
        FactionManager factions = FactionManager.get(server);
        long periodTicks = Math.max(1L, manager.periodTicks());
        Map<UUID, List<ChunkProfiler.ChunkSample>> topChunks = topChunksByFaction(server, 3);

        for (Faction faction : factions.factions()) {
            LagTaxManager.FactionTaxState state = manager.existingState(faction.id()).orElse(null);
            if (state == null) {
                continue;
            }
            long bill = TaxMath.billFromMicros(state.accruedMicros());
            if (bill <= 0L) {
                continue;
            }
            double averageExcess = TaxMath.averageMs(state.excessMsTicksMicros(), periodTicks);
            if (factions.withdraw(faction.id(), bill).successful()) {
                Component receipt = Component.translatable(
                                "kingdoms.lagtax.notice.receipt",
                                NumismaticsEconomy.format(bill),
                                formatMs(averageExcess),
                                describeChunks(topChunks.getOrDefault(faction.id(), List.of())))
                        .withStyle(ChatFormatting.GOLD);
                notifyRoles(server, faction, FactionRole.OFFICER, receipt, true, true);
            } else {
                manager.setUnpaidBill(faction.id(), PriceMath.saturatedAdd(state.unpaidBill(), bill));
                freeze(server, faction, LagTaxManager.FreezeReason.TAX, Component.translatable(
                                "kingdoms.lagtax.notice.frozen",
                                NumismaticsEconomy.format(bill))
                        .withStyle(ChatFormatting.RED));
            }
        }
        manager.resetPeriod();
    }

    private static void freeze(
            MinecraftServer server,
            Faction faction,
            LagTaxManager.FreezeReason reason,
            Component message
    ) {
        LagTaxManager manager = LagTaxManager.get(server);
        FactionManager factions = FactionManager.get(server);
        manager.setFreezeReason(faction.id(), reason);
        factions.setForceLoadsSuspended(faction.id(), true);
        factions.reconcileForceLoads(server);
        FrozenChunks.rebuild(server, manager.frozenFactionIds());
        notifyRoles(server, faction, FactionRole.MEMBER, message, false, true);
        ClaimSyncManager.resyncAll(server);
    }

    private static void unfreeze(MinecraftServer server, Faction faction, Component message) {
        LagTaxManager manager = LagTaxManager.get(server);
        FactionManager factions = FactionManager.get(server);
        manager.setFreezeReason(faction.id(), LagTaxManager.FreezeReason.NONE);
        factions.setForceLoadsSuspended(faction.id(), false);
        factions.reconcileForceLoads(server);
        FrozenChunks.rebuild(server, manager.frozenFactionIds());
        notifyRoles(server, faction, FactionRole.MEMBER, message, true, true);
        ClaimSyncManager.resyncAll(server);
    }

    public static void tryCollectUnpaid(MinecraftServer server, UUID factionId) {
        LagTaxManager manager = LagTaxManager.get(server);
        LagTaxManager.FactionTaxState state = manager.existingState(factionId).orElse(null);
        if (state == null || state.unpaidBill() <= 0L) {
            return;
        }
        FactionManager factions = FactionManager.get(server);
        Faction faction = factions.getFactionById(factionId).orElse(null);
        if (faction == null) {
            return;
        }
        long bill = state.unpaidBill();
        if (!factions.withdraw(factionId, bill).successful()) {
            return;
        }
        manager.setUnpaidBill(factionId, 0L);
        Component message = Component.translatable(
                        "kingdoms.lagtax.notice.unfrozen",
                        NumismaticsEconomy.format(bill))
                .withStyle(ChatFormatting.GREEN);
        if (state.freezeReason() == LagTaxManager.FreezeReason.TAX) {
            unfreeze(server, faction, message);
        } else {
            notifyRoles(server, faction, FactionRole.MEMBER, message, true, true);
        }
    }

    public static void minuteCheck(MinecraftServer server) {
        checkChunkLoads(server);
        LagTaxManager manager = LagTaxManager.get(server);
        FactionManager factions = FactionManager.get(server);

        for (UUID factionId : manager.frozenFactionIds()) {
            if (factions.getFactionById(factionId).isEmpty()) {
                manager.removeFaction(factionId);
                factions.setForceLoadsSuspended(factionId, false);
                FrozenChunks.rebuild(server, manager.frozenFactionIds());
                continue;
            }
            LagTaxManager.FactionTaxState state = manager.state(factionId);
            if (state.freezeReason() == LagTaxManager.FreezeReason.TAX && state.unpaidBill() > 0L) {
                tryCollectUnpaid(server, factionId);
            }
        }

        double hardCap = ModConfigSpec.LAGTAX_HARD_CAP_MS.getAsDouble();
        double quota = ModConfigSpec.LAGTAX_QUOTA_MS.getAsDouble();
        Map<UUID, Double> loads = factionLoads(server);
        for (Faction faction : factions.factions()) {
            double load = loads.getOrDefault(faction.id(), 0.0D);
            LagTaxManager.FactionTaxState state = manager.state(faction.id());
            if (hardCap > 0.0D) {
                if (!state.frozen() && load > hardCap) {
                    freeze(server, faction, LagTaxManager.FreezeReason.HARD_CAP, Component.translatable(
                                    "kingdoms.lagtax.notice.hardcap_frozen",
                                    formatMs(load),
                                    formatMs(hardCap))
                            .withStyle(ChatFormatting.RED));
                    continue;
                }
                if (state.freezeReason() == LagTaxManager.FreezeReason.HARD_CAP
                        && load <= hardCap * HARD_CAP_RELEASE_FACTOR
                        && state.unpaidBill() <= 0L) {
                    unfreeze(server, faction, Component.translatable("kingdoms.lagtax.notice.hardcap_unfrozen")
                            .withStyle(ChatFormatting.GREEN));
                    continue;
                }
            }
            if (quota > 0.0D && !state.frozen() && load >= quota * 0.75D && !state.warned75()) {
                if (manager.markWarned75(faction.id())) {
                    notifyRoles(server, faction, FactionRole.OFFICER, quota75Message(load, quota), false, false);
                }
            }
        }
    }

    private static void checkChunkLoads(MinecraftServer server) {
        LagTaxManager manager = LagTaxManager.get(server);
        FactionManager factions = FactionManager.get(server);
        long now = System.currentTimeMillis();
        long pricePerHour = ModConfigSpec.CHUNK_LOAD_PRICE_PER_HOUR.getAsLong();
        boolean changed = false;

        for (Faction faction : factions.factions()) {
            LagTaxManager.FactionTaxState state = manager.existingState(faction.id()).orElse(null);
            if (state == null || state.chunkLoads().isEmpty()) {
                continue;
            }
            for (Map.Entry<ClaimKey, LagTaxManager.ChunkLoad> entry : state.chunkLoads().entrySet()) {
                ClaimKey key = entry.getKey();
                LagTaxManager.ChunkLoad load = entry.getValue();
                if (load.expiresAtMillis() > now) {
                    if (!load.warnedExpiry() && load.expiresAtMillis() - now <= HOUR_MILLIS) {
                        manager.markLoadWarned(faction.id(), key);
                        notifyRolesActionbar(server, faction, Component.translatable(
                                        "kingdoms.lagtax.load.expiring",
                                        key.x() * 16 + 8,
                                        key.z() * 16 + 8)
                                .withStyle(ChatFormatting.YELLOW));
                    }
                    continue;
                }
                long renewCost = PriceMath.saturatedMultiply(pricePerHour, load.lastHours());
                boolean canRenew = state.autoRenew()
                        && !state.frozen()
                        && state.unpaidBill() <= 0L
                        && factions.withdraw(faction.id(), renewCost).successful();
                if (canRenew) {
                    manager.putChunkLoad(faction.id(), key, now + load.lastHours() * HOUR_MILLIS, load.lastHours());
                    notifyRoles(server, faction, FactionRole.OFFICER, Component.translatable(
                                    "kingdoms.lagtax.load.renewed",
                                    key.x() * 16 + 8,
                                    key.z() * 16 + 8,
                                    load.lastHours(),
                                    NumismaticsEconomy.format(renewCost))
                            .withStyle(ChatFormatting.GOLD), true, false);
                } else {
                    manager.removeChunkLoad(faction.id(), key);
                    factions.disableForceLoad(server, faction.id(), key);
                    notifyRoles(server, faction, FactionRole.OFFICER, Component.translatable(
                                    "kingdoms.lagtax.load.expired",
                                    key.x() * 16 + 8,
                                    key.z() * 16 + 8)
                            .withStyle(ChatFormatting.YELLOW), false, true);
                    changed = true;
                }
            }
        }
        if (changed) {
            IntegrationManager.refreshFromServer(server);
            ClaimSyncManager.resyncAll(server);
        }
    }

    public static void onLogin(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        for (OfflineNoticeQueue.StoredNotice notice : OfflineNoticeQueue.get(server).drain(player.getUUID())) {
            Component message = Component.Serializer.fromJson(notice.messageJson(), server.registryAccess());
            if (message != null) {
                player.sendSystemMessage(message);
                FactionServerHooks.sendNotice(player, message, notice.successful());
            }
        }

        FactionManager factions = FactionManager.get(server);
        Faction faction = factions.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            return;
        }
        FactionMember member = faction.member(player.getUUID()).orElse(null);
        if (member == null || !member.role().isAtLeast(FactionRole.OFFICER)) {
            return;
        }
        double quota = ModConfigSpec.LAGTAX_QUOTA_MS.getAsDouble();
        if (quota <= 0.0D) {
            return;
        }
        double load = ChunkProfiler.factionLoadMs(server, faction.id());
        if (load >= quota * 0.75D) {
            player.sendSystemMessage(quota75Message(load, quota));
        }
    }

    public static void buyChunkLoad(ServerPlayer player, ResourceLocation dimensionId, long packedChunk, int hours) {
        MinecraftServer server = player.getServer();
        if (server == null || hours <= 0 || hours > 24 * ModConfigSpec.CHUNK_LOAD_MAX_DAYS.getAsInt()) {
            return;
        }
        FactionManager factions = FactionManager.get(server);
        Faction faction = factions.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.error.not_in_faction"), false);
            return;
        }
        FactionMember member = faction.member(player.getUUID()).orElse(null);
        if (member == null || !member.role().canManageClaims()) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.error.role_cannot_change_claims"), false);
            return;
        }
        LagTaxManager manager = LagTaxManager.get(server);
        LagTaxManager.FactionTaxState state = manager.state(faction.id());
        if (state.frozen() || state.unpaidBill() > 0L) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.lagtax.load.blocked_unpaid"), false);
            return;
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        ClaimKey key = new ClaimKey(dimension, new ChunkPos(packedChunk));
        if (!faction.hasClaim(key) && !faction.isOutpostChunk(key)) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.command.faction.forceload.not_own"), false);
            return;
        }
        long now = System.currentTimeMillis();
        LagTaxManager.ChunkLoad existing = state.chunkLoads().get(key);
        long base = existing == null ? now : Math.max(now, existing.expiresAtMillis());
        long newExpiry = base + hours * HOUR_MILLIS;
        long maxExpiry = now + ModConfigSpec.CHUNK_LOAD_MAX_DAYS.getAsInt() * DAY_MILLIS;
        if (newExpiry > maxExpiry) {
            FactionServerHooks.sendNotice(player, Component.translatable(
                    "kingdoms.lagtax.load.max_days",
                    ModConfigSpec.CHUNK_LOAD_MAX_DAYS.getAsInt()), false);
            return;
        }
        long price = PriceMath.saturatedMultiply(ModConfigSpec.CHUNK_LOAD_PRICE_PER_HOUR.getAsLong(), hours);
        if (!factions.withdraw(faction.id(), price).successful()) {
            FactionServerHooks.sendNotice(player, Component.translatable(
                    "kingdoms.lagtax.load.insufficient",
                    NumismaticsEconomy.format(price)), false);
            return;
        }
        if (!faction.isForceLoaded(key)) {
            FactionManager.ForceLoadResult result = factions.enableForceLoad(server, faction.id(), key);
            if (result != FactionManager.ForceLoadResult.ENABLED) {
                factions.deposit(faction.id(), price);
                Component message = result == FactionManager.ForceLoadResult.LIMIT_REACHED
                        ? Component.translatable(
                                "kingdoms.command.faction.forceload.limit",
                                factions.forceLoadLimit(faction.id()))
                        : Component.translatable("kingdoms.error.faction_action_failed");
                FactionServerHooks.sendNotice(player, message, false);
                return;
            }
        }
        manager.putChunkLoad(faction.id(), key, newExpiry, hours);
        double chunkMs = ChunkProfiler.chunkLoadMs(dimension, packedChunk);
        FactionServerHooks.sendNotice(player, Component.translatable(
                existing == null ? "kingdoms.lagtax.load.bought" : "kingdoms.lagtax.load.extended",
                hours,
                NumismaticsEconomy.format(price),
                formatDuration(newExpiry - now),
                formatMs(chunkMs)), true);
        IntegrationManager.refreshFromServer(server);
        ClaimSyncManager.resync(player);
    }

    public static void disableChunkLoadFromMap(ServerPlayer player, ResourceLocation dimensionId, long packedChunk) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        FactionManager factions = FactionManager.get(server);
        Faction faction = factions.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.error.not_in_faction"), false);
            return;
        }
        FactionMember member = faction.member(player.getUUID()).orElse(null);
        if (member == null || !member.role().canManageClaims()) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.error.role_cannot_change_claims"), false);
            return;
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        ClaimKey key = new ClaimKey(dimension, new ChunkPos(packedChunk));
        if (!faction.isForceLoaded(key)) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.lagtax.load.buy_hint"), false);
            return;
        }
        factions.disableForceLoad(server, faction.id(), key);
        LagTaxManager.get(server).removeChunkLoad(faction.id(), key);
        FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.lagtax.load.disabled"), true);
        IntegrationManager.refreshFromServer(server);
        ClaimSyncManager.resync(player);
    }

    public static void setAutoRenew(ServerPlayer player, boolean enabled) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        FactionManager factions = FactionManager.get(server);
        Faction faction = factions.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            return;
        }
        FactionMember member = faction.member(player.getUUID()).orElse(null);
        if (member == null || !member.role().canManageTreasury()) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.error.role_cannot_change_claims"), false);
            return;
        }
        LagTaxManager.get(server).setAutoRenew(faction.id(), enabled);
        sendMeterData(player);
    }

    public static void sendAnalyzerData(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        if (!player.hasPermissions(2)) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.lagtax.analyzer.no_permission"), false);
            return;
        }
        FactionManager factions = FactionManager.get(server);
        LagTaxManager manager = LagTaxManager.get(server);
        List<LagTaxPayloads.ChunkEntry> chunkEntries = new ArrayList<>();
        Map<UUID, Double> factionSums = new HashMap<>();
        double wildSum = 0.0D;

        for (ChunkProfiler.ChunkSample sample : ChunkProfiler.allChunks()) {
            if (sample.loadMs() < MIN_DISPLAY_MS) {
                continue;
            }
            ClaimKey key = new ClaimKey(sample.dimension(), new ChunkPos(sample.packedChunk()));
            Faction owner = factions.getFactionAt(key).orElse(null);
            if (owner == null) {
                wildSum += sample.loadMs();
            } else {
                factionSums.merge(owner.id(), sample.loadMs(), Double::sum);
            }
            if (chunkEntries.size() < LagTaxPayloads.MAX_CHUNK_ENTRIES) {
                ChunkPos pos = new ChunkPos(sample.packedChunk());
                chunkEntries.add(new LagTaxPayloads.ChunkEntry(
                        sample.dimension().location(),
                        pos.x,
                        pos.z,
                        Math.round(sample.loadMs() * TaxMath.MICROS),
                        owner == null ? "" : owner.name(),
                        owner != null && manager.state(owner.id()).frozen()
                ));
            }
        }

        List<LagTaxPayloads.FactionEntry> factionEntries = new ArrayList<>();
        factionSums.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(LagTaxPayloads.MAX_FACTION_ENTRIES - 1)
                .forEach(entry -> {
                    Faction owner = factions.getFactionById(entry.getKey()).orElse(null);
                    if (owner == null) {
                        return;
                    }
                    LagTaxManager.FactionTaxState state = manager.state(owner.id());
                    factionEntries.add(new LagTaxPayloads.FactionEntry(
                            owner.name(),
                            Math.round(entry.getValue() * TaxMath.MICROS),
                            state.unpaidBill(),
                            state.frozen()
                    ));
                });
        if (wildSum >= MIN_DISPLAY_MS) {
            factionEntries.add(new LagTaxPayloads.FactionEntry(
                    "",
                    Math.round(wildSum * TaxMath.MICROS),
                    0L,
                    false
            ));
        }

        PacketDistributor.sendToPlayer(player, new LagTaxPayloads.S2CAnalyzerData(
                chunkEntries,
                factionEntries,
                Math.round(ModConfigSpec.LAGTAX_QUOTA_MS.getAsDouble() * TaxMath.MICROS)
        ));
    }

    public static void sendMeterData(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        FactionManager factions = FactionManager.get(server);
        Faction faction = factions.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.error.not_in_faction"), false);
            return;
        }
        LagTaxManager manager = LagTaxManager.get(server);
        LagTaxManager.FactionTaxState state = manager.state(faction.id());
        Map<ClaimKey, LagTaxManager.ChunkLoad> loads = state.chunkLoads();

        double total = 0.0D;
        List<LagTaxPayloads.MeterChunk> chunks = new ArrayList<>();
        Map<ClaimKey, Double> seen = new HashMap<>();
        for (ChunkProfiler.ChunkSample sample : ChunkProfiler.allChunks()) {
            if (sample.loadMs() < MIN_DISPLAY_MS) {
                continue;
            }
            ClaimKey key = new ClaimKey(sample.dimension(), new ChunkPos(sample.packedChunk()));
            UUID owner = factions.getFactionIdAt(key).orElse(null);
            if (!faction.id().equals(owner)) {
                continue;
            }
            total += sample.loadMs();
            seen.put(key, sample.loadMs());
        }
        seen.entrySet().stream()
                .sorted(Map.Entry.<ClaimKey, Double>comparingByValue().reversed())
                .limit(LagTaxPayloads.MAX_METER_CHUNKS)
                .forEach(entry -> {
                    LagTaxManager.ChunkLoad load = loads.get(entry.getKey());
                    chunks.add(new LagTaxPayloads.MeterChunk(
                            entry.getKey().dimension().location(),
                            entry.getKey().x(),
                            entry.getKey().z(),
                            Math.round(entry.getValue() * TaxMath.MICROS),
                            load != null,
                            load == null ? 0L : load.expiresAtMillis()
                    ));
                });
        for (Map.Entry<ClaimKey, LagTaxManager.ChunkLoad> entry : loads.entrySet()) {
            if (seen.containsKey(entry.getKey()) || chunks.size() >= LagTaxPayloads.MAX_METER_CHUNKS) {
                continue;
            }
            chunks.add(new LagTaxPayloads.MeterChunk(
                    entry.getKey().dimension().location(),
                    entry.getKey().x(),
                    entry.getKey().z(),
                    0L,
                    true,
                    entry.getValue().expiresAtMillis()
            ));
        }

        double quota = ModConfigSpec.LAGTAX_QUOTA_MS.getAsDouble();
        double forecast = TaxMath.costPerDay(
                Math.max(0.0D, total - quota),
                ModConfigSpec.LAGTAX_TIER1_LIMIT_MS.getAsDouble(),
                ModConfigSpec.LAGTAX_TIER2_LIMIT_MS.getAsDouble(),
                ModConfigSpec.LAGTAX_TIER1_PRICE.getAsLong(),
                ModConfigSpec.LAGTAX_TIER2_PRICE.getAsLong(),
                ModConfigSpec.LAGTAX_TIER3_PRICE.getAsLong()
        );
        PacketDistributor.sendToPlayer(player, new LagTaxPayloads.S2CMeterData(
                Math.round(total * TaxMath.MICROS),
                Math.round(quota * TaxMath.MICROS),
                Math.round(forecast),
                TaxMath.billFromMicros(state.accruedMicros()),
                state.unpaidBill(),
                state.frozen(),
                state.autoRenew(),
                ModConfigSpec.CHUNK_LOAD_PRICE_PER_HOUR.getAsLong(),
                ModConfigSpec.CHUNK_LOAD_MAX_DAYS.getAsInt(),
                chunks
        ));
    }

    public static void startDetail(ServerPlayer player, ResourceLocation dimensionId, int chunkX, int chunkZ) {
        if (!player.hasPermissions(2)) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.lagtax.analyzer.no_permission"), false);
            return;
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        if (!ChunkProfiler.startCapture(dimension, ChunkPos.asLong(chunkX, chunkZ), DETAIL_SAMPLE_COUNT, player.getUUID())) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.lagtax.detail.busy"), false);
        }
    }

    public static void analyzerTeleport(ServerPlayer player, ResourceLocation dimensionId, int chunkX, int chunkZ) {
        MinecraftServer server = player.getServer();
        if (server == null || !player.hasPermissions(2)) {
            return;
        }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
        if (level == null) {
            return;
        }
        int blockX = chunkX * 16 + 8;
        int blockZ = chunkZ * 16 + 8;
        level.getChunk(chunkX, chunkZ);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ) + 1;
        player.teleportTo(level, blockX + 0.5D, y, blockZ + 0.5D, player.getYRot(), player.getXRot());
    }

    private static void sendCaptureResult(MinecraftServer server, ChunkProfiler.CaptureResult capture) {
        ServerPlayer player = server.getPlayerList().getPlayer(capture.playerId());
        if (player == null) {
            return;
        }
        List<LagTaxPayloads.DetailEntry> entries = new ArrayList<>();
        for (ChunkProfiler.BlockEntitySample sample : capture.entries()) {
            if (entries.size() >= LagTaxPayloads.MAX_DETAIL_ENTRIES) {
                break;
            }
            entries.add(new LagTaxPayloads.DetailEntry(
                    sample.pos(),
                    sample.blockId(),
                    Math.round(sample.loadMs() * TaxMath.MICROS)
            ));
        }
        ChunkPos pos = new ChunkPos(capture.packedChunk());
        PacketDistributor.sendToPlayer(player, new LagTaxPayloads.S2CChunkDetail(
                capture.dimension().location(),
                pos.x,
                pos.z,
                entries
        ));
    }

    private static Component quota75Message(double load, double quota) {
        return Component.translatable("kingdoms.lagtax.notice.quota75", formatMs(load), formatMs(quota))
                .withStyle(ChatFormatting.YELLOW);
    }

    private static Map<UUID, Double> factionLoads(MinecraftServer server) {
        FactionManager factions = FactionManager.get(server);
        Map<UUID, Double> loads = new HashMap<>();
        for (ChunkProfiler.ChunkSample sample : ChunkProfiler.allChunks()) {
            factions.getFactionIdAt(new ClaimKey(sample.dimension(), new ChunkPos(sample.packedChunk())))
                    .ifPresent(owner -> loads.merge(owner, sample.loadMs(), Double::sum));
        }
        return loads;
    }

    private static Map<UUID, List<ChunkProfiler.ChunkSample>> topChunksByFaction(MinecraftServer server, int limit) {
        FactionManager factions = FactionManager.get(server);
        Map<UUID, List<ChunkProfiler.ChunkSample>> top = new HashMap<>();
        for (ChunkProfiler.ChunkSample sample : ChunkProfiler.allChunks()) {
            UUID owner = factions
                    .getFactionIdAt(new ClaimKey(sample.dimension(), new ChunkPos(sample.packedChunk())))
                    .orElse(null);
            if (owner == null) {
                continue;
            }
            List<ChunkProfiler.ChunkSample> samples = top.computeIfAbsent(owner, ignored -> new ArrayList<>());
            if (samples.size() < limit) {
                samples.add(sample);
            }
        }
        return top;
    }

    private static String describeChunks(List<ChunkProfiler.ChunkSample> samples) {
        if (samples.isEmpty()) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        for (ChunkProfiler.ChunkSample sample : samples) {
            if (!builder.isEmpty()) {
                builder.append("; ");
            }
            ChunkPos pos = new ChunkPos(sample.packedChunk());
            builder.append('[')
                    .append(pos.x * 16 + 8)
                    .append(", ")
                    .append(pos.z * 16 + 8)
                    .append("] ")
                    .append(formatMs(sample.loadMs()))
                    .append(" мс");
        }
        return builder.toString();
    }

    private static void notifyRoles(
            MinecraftServer server,
            Faction faction,
            FactionRole minimumRole,
            Component message,
            boolean successful,
            boolean queueOffline
    ) {
        OfflineNoticeQueue queue = OfflineNoticeQueue.get(server);
        for (Map.Entry<UUID, FactionMember> entry : faction.members().entrySet()) {
            if (!entry.getValue().role().isAtLeast(minimumRole)) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                player.sendSystemMessage(message);
                FactionServerHooks.sendNotice(player, message, successful);
            } else if (queueOffline) {
                queue.enqueue(server, entry.getKey(), message, successful);
            }
        }
    }

    private static void notifyRolesActionbar(MinecraftServer server, Faction faction, Component message) {
        for (Map.Entry<UUID, FactionMember> entry : faction.members().entrySet()) {
            if (!entry.getValue().role().isAtLeast(FactionRole.OFFICER)) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                player.displayClientMessage(message, true);
                player.sendSystemMessage(message);
            }
        }
    }

    private static double median(double[] window) {
        double[] sorted = window.clone();
        java.util.Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    private static boolean isIdle(double[] window) {
        for (double value : window) {
            if (value > 0.0D) {
                return false;
            }
        }
        return true;
    }

    private static String formatMs(double ms) {
        return String.format(Locale.ROOT, "%.2f", ms);
    }

    private static String formatDuration(long millis) {
        long hours = millis / HOUR_MILLIS;
        long minutes = millis % HOUR_MILLIS / 60_000L;
        return hours + " ч " + minutes + " мин";
    }

    private LagTaxService() {
    }
}
