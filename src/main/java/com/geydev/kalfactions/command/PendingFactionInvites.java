package com.geydev.kalfactions.command;

import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

public final class PendingFactionInvites {
    private static final long INVITE_LIFETIME_TICKS = 20L * 60L * 30L;
    private static final Map<MinecraftServer, InviteStore> INVITES = new HashMap<>();

    public static synchronized PutResult put(
        MinecraftServer server,
        UUID factionId,
        UUID inviterId,
        UUID invitedId
    ) {
        Faction faction = FactionManager.get(server).getFactionById(factionId).orElse(null);
        if (faction == null) {
            return PutResult.FACTION_NOT_FOUND;
        }
        if (faction.memberCount() >= FactionManager.MAX_FACTION_MEMBERS) {
            return PutResult.FACTION_FULL;
        }
        long now = server.overworld().getGameTime();
        InviteStore store = invites(server);
        store.purge(now);
        store.put(factionId, inviterId, invitedId, now + INVITE_LIFETIME_TICKS);
        return PutResult.CREATED;
    }

    public static synchronized Optional<Invite> newest(MinecraftServer server, UUID invitedId) {
        InviteStore store = invites(server);
        store.purge(server.overworld().getGameTime());
        return store.newest(invitedId);
    }

    public static synchronized List<Invite> allFor(MinecraftServer server, UUID invitedId) {
        InviteStore store = invites(server);
        store.purge(server.overworld().getGameTime());
        return store.allFor(invitedId);
    }

    public static synchronized Optional<Invite> find(
        MinecraftServer server,
        UUID factionId,
        UUID invitedId
    ) {
        InviteStore store = invites(server);
        store.purge(server.overworld().getGameTime());
        return store.find(factionId, invitedId);
    }

    public static synchronized boolean remove(MinecraftServer server, UUID factionId, UUID invitedId) {
        return invites(server).remove(factionId, invitedId);
    }

    public static synchronized void removeForPlayer(MinecraftServer server, UUID playerId) {
        invites(server).removeForPlayer(playerId);
    }

    public static synchronized void removeForFaction(MinecraftServer server, UUID factionId) {
        invites(server).removeForFaction(factionId);
    }

    public static synchronized void clear(MinecraftServer server) {
        INVITES.remove(server);
    }

    private static InviteStore invites(MinecraftServer server) {
        return INVITES.computeIfAbsent(server, ignored -> new InviteStore());
    }

    public record Invite(UUID factionId, UUID inviterId, UUID invitedId, long expiresAt) {
    }

    public enum PutResult {
        CREATED,
        FACTION_NOT_FOUND,
        FACTION_FULL
    }

    static final class InviteStore {
        private final Map<InviteKey, Invite> entries = new HashMap<>();

        void put(UUID factionId, UUID inviterId, UUID invitedId, long expiresAt) {
            entries.put(
                new InviteKey(factionId, invitedId),
                new Invite(factionId, inviterId, invitedId, expiresAt)
            );
        }

        Optional<Invite> newest(UUID invitedId) {
            return entries.values().stream()
                .filter(invite -> invite.invitedId().equals(invitedId))
                .max(Comparator.comparingLong(Invite::expiresAt));
        }

        List<Invite> allFor(UUID invitedId) {
            return entries.values().stream()
                .filter(invite -> invite.invitedId().equals(invitedId))
                .sorted(Comparator.comparingLong(Invite::expiresAt).reversed())
                .toList();
        }

        Optional<Invite> find(UUID factionId, UUID invitedId) {
            return Optional.ofNullable(entries.get(new InviteKey(factionId, invitedId)));
        }

        boolean remove(UUID factionId, UUID invitedId) {
            return entries.remove(new InviteKey(factionId, invitedId)) != null;
        }

        void removeForPlayer(UUID playerId) {
            entries.values().removeIf(invite ->
                invite.inviterId().equals(playerId) || invite.invitedId().equals(playerId)
            );
        }

        void removeForFaction(UUID factionId) {
            entries.keySet().removeIf(key -> key.factionId().equals(factionId));
        }

        void purge(long now) {
            entries.values().removeIf(invite -> invite.expiresAt() <= now);
        }

        int size() {
            return entries.size();
        }
    }

    private record InviteKey(UUID factionId, UUID invitedId) {
    }

    private PendingFactionInvites() {
    }
}
