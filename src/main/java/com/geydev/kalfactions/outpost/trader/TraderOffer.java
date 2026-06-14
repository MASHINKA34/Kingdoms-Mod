package com.geydev.kalfactions.outpost.trader;

import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.registry.ModItems;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;

public enum TraderOffer {
    OUTPOST_CHARTER(
            "outpost_charter",
            ModItems.OUTPOST_CHARTER,
            ModConfigSpec.OUTPOST_CHARTER_COST::get
    ),
    DRILL(
            "drill",
            ModItems.DRILL,
            ModConfigSpec.OUTPOST_DRILL_COST::get
    );

    private final String id;
    private final Supplier<? extends Item> item;
    private final LongSupplier price;

    TraderOffer(String id, Supplier<? extends Item> item, LongSupplier price) {
        this.id = id;
        this.item = item;
        this.price = price;
    }

    public String id() {
        return id;
    }

    public Item item() {
        return item.get();
    }

    public long price() {
        return price.getAsLong();
    }

    public static Optional<TraderOffer> byId(String id) {
        return Arrays.stream(values()).filter(offer -> offer.id.equals(id)).findFirst();
    }
}
