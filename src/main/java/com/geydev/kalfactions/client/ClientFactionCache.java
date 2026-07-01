package com.geydev.kalfactions.client;

import com.geydev.kalfactions.net.FactionSnapshot;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ClientFactionCache {
    private static volatile State state = new State(null, true, "", 0L);

    public static void accept(FactionSnapshot snapshot, boolean successful, String message) {
        State previous = state;
        state = new State(snapshot, successful, message == null ? "" : message, previous.revision + 1L);
        if (snapshot == null) {
            ClientFactionMembership.clear();
        } else {
            Set<UUID> memberIds = snapshot.members().stream()
                    .map(FactionSnapshot.Member::playerId)
                    .collect(Collectors.toUnmodifiableSet());
            ClientFactionMembership.accept(snapshot.factionId(), memberIds);
        }
    }

    public static Optional<FactionSnapshot> snapshot() {
        return Optional.ofNullable(state.snapshot);
    }

    public static State state() {
        return state;
    }

    public static void clear() {
        State previous = state;
        state = new State(null, true, "", previous.revision + 1L);
        ClientFactionMembership.clear();
    }

    public record State(FactionSnapshot snapshot, boolean successful, String message, long revision) {
    }

    private ClientFactionCache() {
    }
}
