package com.geydev.kalfactions.outpost.trader;

final class TradeSequenceGuard {
    private long lastAccepted;

    synchronized boolean accept(long sequence) {
        if (sequence < 1L || sequence <= lastAccepted) {
            return false;
        }
        lastAccepted = sequence;
        return true;
    }

    synchronized long lastAccepted() {
        return lastAccepted;
    }
}
