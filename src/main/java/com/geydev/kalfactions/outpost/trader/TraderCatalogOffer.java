package com.geydev.kalfactions.outpost.trader;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

public record TraderCatalogOffer(
        String id,
        ResourceLocation itemId,
        ResourceLocation itemTag,
        int count,
        long minimumPrice,
        long maximumPrice,
        int dailyLimit
) {
    public TraderCatalogOffer {
        Objects.requireNonNull(id, "id");
        if ((itemId == null) == (itemTag == null)) {
            throw new IllegalArgumentException("exactly one item selector is required");
        }
        if (count < 1 || count > 64 || minimumPrice < 0L || maximumPrice < minimumPrice
                || maximumPrice > 1_000_000_000L || dailyLimit < 1 || dailyLimit > 4096) {
            throw new IllegalArgumentException("offer values are outside safe bounds");
        }
    }

    public long price(long roll) {
        if (minimumPrice == maximumPrice) {
            return minimumPrice;
        }
        long range = maximumPrice - minimumPrice + 1L;
        return minimumPrice + Long.remainderUnsigned(roll, range);
    }
}
