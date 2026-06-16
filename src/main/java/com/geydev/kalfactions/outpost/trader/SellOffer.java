package com.geydev.kalfactions.outpost.trader;

import java.util.Arrays;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public enum SellOffer {
    COAL("coal", Items.COAL, 1L),
    COPPER("copper", Items.COPPER_INGOT, 2L),
    REDSTONE("redstone", Items.REDSTONE, 1L),
    LAPIS("lapis", Items.LAPIS_LAZULI, 1L),
    WHEAT("wheat", Items.WHEAT, 1L),
    LEATHER("leather", Items.LEATHER, 2L),
    IRON("iron", Items.IRON_INGOT, 3L),
    GOLD("gold", Items.GOLD_INGOT, 5L),
    EMERALD("emerald", Items.EMERALD, 8L),
    DIAMOND("diamond", Items.DIAMOND, 20L);

    private final String id;
    private final Item item;
    private final long price;

    SellOffer(String id, Item item, long price) {
        this.id = id;
        this.item = item;
        this.price = price;
    }

    public String id() {
        return id;
    }

    public Item item() {
        return item;
    }

    public long price() {
        return price;
    }

    public static Optional<SellOffer> byId(String id) {
        return Arrays.stream(values()).filter(offer -> offer.id.equals(id)).findFirst();
    }

    public static Optional<SellOffer> byItem(Item item) {
        return Arrays.stream(values()).filter(offer -> offer.item == item).findFirst();
    }
}
