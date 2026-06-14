package com.geydev.kalfactions.command;

import com.geydev.kalfactions.faction.FactionManager;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

public final class PendingAllianceRequests {
    private static final long REQUEST_LIFETIME_TICKS = 20L * 60L * 10L;
    private static final Map<MinecraftServer, Map<RequestKey, Request>> REQUESTS = new HashMap<>();

    public static synchronized void put(
        MinecraftServer server,
        UUID fromFactionId,
        UUID requesterId,
        UUID toFactionId
    ) {
        purge(server);
        long expiresAt = server.overworld().getGameTime() + REQUEST_LIFETIME_TICKS;
        REQUESTS.computeIfAbsent(server, ignored -> new HashMap<>())
            .put(new RequestKey(fromFactionId, toFactionId), new Request(fromFactionId, requesterId, toFactionId, expiresAt));
    }

    public static synchronized Optional<Request> find(
        MinecraftServer server,
        UUID fromFactionId,
        UUID toFactionId
    ) {
        purge(server);
        return Optional.ofNullable(requests(server).get(new RequestKey(fromFactionId, toFactionId)));
    }

    public static synchronized List<Request> allFor(MinecraftServer server, UUID toFactionId) {
        purge(server);
        return requests(server).values().stream()
            .filter(request -> request.toFactionId().equals(toFactionId))
            .sorted(Comparator.comparingLong(Request::expiresAt).reversed())
            .toList();
    }

    public static synchronized boolean remove(MinecraftServer server, UUID fromFactionId, UUID toFactionId) {
        return requests(server).remove(new RequestKey(fromFactionId, toFactionId)) != null;
    }

    public static synchronized void removeBetween(MinecraftServer server, UUID firstFactionId, UUID secondFactionId) {
        Map<RequestKey, Request> requests = requests(server);
        requests.remove(new RequestKey(firstFactionId, secondFactionId));
        requests.remove(new RequestKey(secondFactionId, firstFactionId));
    }

    public static synchronized void removeForFaction(MinecraftServer server, UUID factionId) {
        requests(server).keySet().removeIf(key ->
            key.fromFactionId().equals(factionId) || key.toFactionId().equals(factionId)
        );
    }

    public static synchronized void clear(MinecraftServer server) {
        REQUESTS.remove(server);
    }

    private static void purge(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        FactionManager manager = FactionManager.get(server);
        requests(server).values().removeIf(request ->
            request.expiresAt() <= now
                || manager.getFactionById(request.fromFactionId()).isEmpty()
                || manager.getFactionById(request.toFactionId()).isEmpty()
                || manager.areAllied(request.fromFactionId(), request.toFactionId())
        );
    }

    private static Map<RequestKey, Request> requests(MinecraftServer server) {
        return REQUESTS.computeIfAbsent(server, ignored -> new HashMap<>());
    }

    public record Request(UUID fromFactionId, UUID requesterId, UUID toFactionId, long expiresAt) {
    }

    private record RequestKey(UUID fromFactionId, UUID toFactionId) {
    }

    private PendingAllianceRequests() {
    }
}
