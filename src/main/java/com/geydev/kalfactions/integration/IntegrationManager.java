package com.geydev.kalfactions.integration;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.integration.bluemap.BlueMapIntegration;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class IntegrationManager {
    private static final int REFRESH_INTERVAL_TICKS = 20;
    private static final AtomicLong REVISION = new AtomicLong();
    private static final BlueMapIntegration BLUE_MAP = new BlueMapIntegration(IntegrationManager::snapshots);

    private static volatile List<FactionMapData> snapshots = List.of();
    private static int ticksUntilRefresh;

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        BLUE_MAP.start();
        refreshFromServer(event.getServer());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (--ticksUntilRefresh <= 0) {
            ticksUntilRefresh = REFRESH_INTERVAL_TICKS;
            refreshFromServer(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        BLUE_MAP.close();
        replaceAll(List.of());
        ticksUntilRefresh = 0;
    }

    public static void refreshFromServer(MinecraftServer server) {
        Collection<Faction> factions = FactionManager.get(server).factions();
        replaceAll(factions.stream().map(FactionMapData::from).toList());
    }

    public static void replaceAll(Collection<FactionMapData> updatedSnapshots) {
        Objects.requireNonNull(updatedSnapshots, "updatedSnapshots");
        List<FactionMapData> normalized = updatedSnapshots.stream()
                .map(Objects::requireNonNull)
                .sorted(Comparator.comparing(data -> data.factionId().toString()))
                .toList();
        if (normalized.equals(snapshots)) {
            return;
        }

        snapshots = normalized;
        REVISION.incrementAndGet();
        BLUE_MAP.refresh();
    }

    public static List<FactionMapData> snapshots() {
        return snapshots;
    }

    public static long revision() {
        return REVISION.get();
    }

    public record FactionMapData(
            UUID factionId,
            String name,
            int color,
            Set<ClaimKey> claims,
            Set<ClaimKey> outpostChunks
    ) {
        public FactionMapData {
            Objects.requireNonNull(factionId, "factionId");
            name = Objects.requireNonNull(name, "name").strip();
            if (name.isEmpty()) {
                name = factionId.toString();
            }
            color &= 0xFFFFFF;
            claims = Set.copyOf(Objects.requireNonNull(claims, "claims"));
            outpostChunks = Set.copyOf(Objects.requireNonNull(outpostChunks, "outpostChunks"));
        }

        public boolean isOutpost(ClaimKey key) {
            return outpostChunks.contains(key);
        }

        public static FactionMapData from(Faction faction) {
            Objects.requireNonNull(faction, "faction");
            Set<ClaimKey> outposts = faction.outpostChunks();
            Set<ClaimKey> territory = new java.util.LinkedHashSet<>(faction.claims());
            territory.addAll(outposts);
            return new FactionMapData(faction.id(), faction.name(), faction.color(), territory, outposts);
        }
    }

    private IntegrationManager() {
    }
}
