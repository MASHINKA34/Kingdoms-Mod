package com.geydev.kalfactions.outpost.trader;

import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.registry.ModItems;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public enum TraderOffer {
    OUTPOST_CHARTER(
            "outpost_charter",
            ModItems.OUTPOST_CHARTER,
            ModConfigSpec.OUTPOST_CHARTER_COST::get,
            Shop.KINGDOMS
    ),
    DRILL(
            "drill",
            ModItems.DRILL,
            ModConfigSpec.OUTPOST_DRILL_COST::get,
            Shop.KINGDOMS
    ),
    FACTION_TABLE(
            "faction_table",
            ModItems.FACTION_TABLE,
            ModConfigSpec.FACTION_TABLE_COST::get,
            Shop.KINGDOMS
    ),
    ACCESS_TOOL(
            "access_tool",
            ModItems.ACCESS_TOOL,
            ModConfigSpec.ACCESS_TOOL_COST::get,
            Shop.KINGDOMS
    ),
    SELLER_CATALOG(
            "seller_catalog",
            ModItems.SELLER_CATALOG,
            () -> 200L,
            Shop.KINGDOMS
    ),
    BANK_TERMINAL(
            "bank_terminal",
            () -> numismaticsItem("bank_terminal"),
            () -> 300L,
            Shop.BANKER
    ),
    VENDOR(
            "vendor",
            () -> numismaticsItem("vendor"),
            () -> 250L,
            Shop.BANKER
    ),
    WHITE_CARD(
            "white_card",
            () -> numismaticsItem("white_card"),
            () -> 100L,
            Shop.BANKER
    ),
    RED_ID_CARD(
            "red_id_card",
            () -> numismaticsItem("red_id_card"),
            () -> 200L,
            Shop.BANKER
    ),
    BANKING_GUIDE(
            "banking_guide",
            () -> numismaticsItem("banking_guide"),
            () -> 150L,
            Shop.BANKER
    );

    private final String id;
    private final Supplier<? extends Item> item;
    private final LongSupplier price;
    private final Shop shop;

    TraderOffer(String id, Supplier<? extends Item> item, LongSupplier price, Shop shop) {
        this.id = id;
        this.item = item;
        this.price = price;
        this.shop = shop;
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

    public Shop shop() {
        return shop;
    }

    public static Optional<TraderOffer> byId(String id) {
        return Arrays.stream(values()).filter(offer -> offer.id.equals(id)).findFirst();
    }

    public static List<TraderOffer> forShop(Shop shop) {
        return Arrays.stream(values())
                .filter(offer -> offer.shop == shop && offer.item() != Items.AIR)
                .toList();
    }

    private static Item numismaticsItem(String path) {
        return BuiltInRegistries.ITEM
                .getOptional(ResourceLocation.fromNamespaceAndPath("numismatics", path))
                .orElse(Items.AIR);
    }

    public enum Shop {
        KINGDOMS,
        BANKER
    }
}
