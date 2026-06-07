package com.geydev.kalfactions.integration.xaero;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.client.ClientFactionCache;
import com.geydev.kalfactions.integration.IntegrationManager;
import com.geydev.kalfactions.integration.IntegrationManager.FactionMapData;
import com.geydev.kalfactions.net.FactionSnapshot;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID, value = Dist.CLIENT)
public final class XaeroClientEvents {
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            XaeroIntegration.clear();
            return;
        }
        ClientFactionCache.State clientState = ClientFactionCache.state();
        List<FactionMapData> snapshots = IntegrationManager.snapshots();
        long revision = IntegrationManager.revision();
        if (snapshots.isEmpty() && clientState.snapshot() != null) {
            snapshots = fromClientSnapshot(clientState.snapshot(), minecraft.level.dimension());
            revision = 31L * revision + clientState.revision();
        }
        XaeroIntegration.refresh(
                minecraft.level.dimension(),
                revision,
                snapshots,
                (x, z) -> minecraft.level.hasChunk(x >> 4, z >> 4)
                        ? minecraft.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z)
                        : 64
        );
    }

    private static List<FactionMapData> fromClientSnapshot(
            FactionSnapshot snapshot,
            ResourceKey<Level> dimension
    ) {
        Map<ClaimOwner, java.util.LinkedHashSet<ClaimKey>> claims = new LinkedHashMap<>();
        for (FactionSnapshot.Claim claim : snapshot.claims()) {
            UUID factionId = claim.own() && snapshot.hasFaction()
                    ? snapshot.factionId()
                    : UUID.nameUUIDFromBytes(
                            (KalFactions.MOD_ID + ":" + claim.label() + ":" + claim.color())
                                    .getBytes(StandardCharsets.UTF_8)
                    );
            ClaimOwner owner = new ClaimOwner(factionId, claim.label(), claim.color());
            claims.computeIfAbsent(owner, ignored -> new java.util.LinkedHashSet<>())
                    .add(new ClaimKey(dimension, claim.chunkX(), claim.chunkZ()));
        }
        return claims.entrySet().stream()
                .map(entry -> new FactionMapData(
                        entry.getKey().id(),
                        entry.getKey().name(),
                        entry.getKey().color(),
                        Set.copyOf(entry.getValue())
                ))
                .toList();
    }

    private record ClaimOwner(UUID id, String name, int color) {
    }

    private XaeroClientEvents() {
    }
}
