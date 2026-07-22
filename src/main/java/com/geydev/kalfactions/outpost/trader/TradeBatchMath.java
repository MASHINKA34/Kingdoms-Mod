package com.geydev.kalfactions.outpost.trader;

import com.geydev.kalfactions.economy.PriceMath;

public final class TradeBatchMath {
    public static int availableBatches(int ownedItems, int batchSize, int remainingLimit) {
        if (ownedItems <= 0 || batchSize <= 0 || remainingLimit <= 0) {
            return 0;
        }
        return Math.min(ownedItems / batchSize, remainingLimit);
    }

    public static int itemsForBatches(int batches, int batchSize) {
        if (batches <= 0 || batchSize <= 0) {
            return 0;
        }
        long items = (long) batches * batchSize;
        return items > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) items;
    }

    public static long payout(long batchPrice, int batches) {
        return PriceMath.saturatedMultiply(Math.max(0L, batchPrice), Math.max(0, batches));
    }

    private TradeBatchMath() {
    }
}
