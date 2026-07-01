package com.geydev.kalfactions.client;

import com.geydev.kalfactions.net.FactionSnapshot;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public final class ClientFactionMembership {
    private static volatile State state = new State(FactionSnapshot.NO_FACTION, Set.of());

    public static void accept(UUID factionId, Collection<UUID> memberIds) {
        state = new State(
                factionId == null ? FactionSnapshot.NO_FACTION : factionId,
                memberIds == null ? Set.of() : Set.copyOf(memberIds)
        );
    }

    public static boolean isVisibleMember(UUID playerId) {
        State current = state;
        return playerId != null && current.hasFaction() && current.memberIds.contains(playerId);
    }

    public static void clear() {
        state = new State(FactionSnapshot.NO_FACTION, Set.of());
    }

    private record State(UUID factionId, Set<UUID> memberIds) {
        private boolean hasFaction() {
            return !FactionSnapshot.NO_FACTION.equals(factionId);
        }
    }

    private ClientFactionMembership() {
    }
}
