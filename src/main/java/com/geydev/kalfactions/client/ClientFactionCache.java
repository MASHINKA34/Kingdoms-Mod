package com.geydev.kalfactions.client;

import com.geydev.kalfactions.net.FactionSnapshot;
import java.util.Optional;

public final class ClientFactionCache {
    private static volatile State state = new State(null, true, "", 0L);

    public static void accept(FactionSnapshot snapshot, boolean successful, String message) {
        State previous = state;
        state = new State(snapshot, successful, message == null ? "" : message, previous.revision + 1L);
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
    }

    public record State(FactionSnapshot snapshot, boolean successful, String message, long revision) {
    }

    private ClientFactionCache() {
    }
}
