package com.geydev.kalfactions.scout;

import java.util.concurrent.atomic.AtomicInteger;

final class ScoutDeliveryGate {
    private final AtomicInteger state = new AtomicInteger();

    boolean cancel() {
        return state.compareAndSet(0, 2) || state.get() == 2;
    }

    boolean commit() {
        return state.compareAndSet(0, 1);
    }
}
