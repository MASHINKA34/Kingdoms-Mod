package com.geydev.kalfactions.command;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

public final class PendingFactionInvites {
    private static final long INVITE_LIFETIME_TICKS = 20L * 60L * 5L;
    private static final Map<MinecraftServer, Map<InviteKey, Invite>> INVITES = new HashMap<>();

    public static synchronized void put(
        MinecraftServer server,
        UUID factionId,
        UUID inviterId,
        UUID invitedId
    ) {
        purge(server);
        long expiresAt = server.overworld().getGameTime() + INVITE_LIFETIME_TICKS;
        INVITES.computeIfAbsent(server, ignored -> new HashMap<>())
            .put(new InviteKey(factionId, invitedId), new Invite(factionId, inviterId, invitedId, expiresAt));
    }

    public static synchronized Optional<Invite> newest(MinecraftServer server, UUID invitedId) {
        purge(server);
        return invites(server).values().stream()
            .filter(invite -> invite.invitedId().equals(invitedId))
            .max(Comparator.comparingLong(Invite::expiresAt));
    }

    public static synchronized Optional<Invite> find(
        MinecraftServer server,
        UUID factionId,
        UUID invitedId
    ) {
        purge(server);
        return Optional.ofNullable(invites(server).get(new InviteKey(factionId, invitedId)));
    }

    public static synchronized boolean remove(MinecraftServer server, UUID factionId, UUID invitedId) {
        return invites(server).remove(new InviteKey(factionId, invitedId)) != null;
    }

    public static synchronized void removeForPlayer(MinecraftServer server, UUID playerId) {
        invites(server).values().removeIf(invite ->
            invite.inviterId().equals(playerId) || invite.invitedId().equals(playerId)
        );
    }

    public static synchronized void removeForFaction(MinecraftServer server, UUID factionId) {
        invites(server).keySet().removeIf(key -> key.factionId().equals(factionId));
    }

    public static synchronized void clear(MinecraftServer server) {
        INVITES.remove(server);
    }

    private static void purge(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        invites(server).values().removeIf(invite -> invite.expiresAt() <= now);
    }

    private static Map<InviteKey, Invite> invites(MinecraftServer server) {
        return INVITES.computeIfAbsent(server, ignored -> new HashMap<>());
    }

    public record Invite(UUID factionId, UUID inviterId, UUID invitedId, long expiresAt) {
    }

    private record InviteKey(UUID factionId, UUID invitedId) {
    }

    private PendingFactionInvites() {
    }
}
