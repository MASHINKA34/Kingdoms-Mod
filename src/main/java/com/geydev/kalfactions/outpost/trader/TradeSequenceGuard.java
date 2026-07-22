package com.geydev.kalfactions.outpost.trader;

final class TradeSequenceGuard {
    private long lastAccepted;

    synchronized boolean accept(long sequence) {
        if (sequence != lastAccepted + 1L) {
            return false;
        }
        lastAccepted = sequence;
        return true;
    }

    synchronized long lastAccepted() {
        return lastAccepted;
    }
}
