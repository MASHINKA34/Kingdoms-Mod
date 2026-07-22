package com.geydev.kalfactions.outpost.trader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

final class TraderCatalogParserTest {
    @Test
    void parsesBoundedCatalogAndPriceRange() {
        var root = JsonParser.parseString("""
                {"role":"wandering","offers":[
                  {"id":"iron","item":"minecraft:iron_ingot","count":2,"min_price":5,"max_price":8,"daily_limit":32}
                ]}
                """).getAsJsonObject();
        TraderCatalogParser.ParsedCatalog parsed = TraderCatalogParser.parse(root, id -> true);

        assertEquals(TraderCatalogRole.WANDERING, parsed.role());
        assertEquals(1, parsed.offers().size());
        assertEquals(2, parsed.offers().getFirst().count());
        assertEquals(5, parsed.offers().getFirst().price(0));
        assertEquals(8, parsed.offers().getFirst().price(3));
    }

    @Test
    void rejectsUnknownItemsAndDuplicateIds() {
        var unknown = JsonParser.parseString("""
                {"role":"permanent","offers":[{"id":"bad","item":"missing:item","price":1}]}
                """).getAsJsonObject();
        var duplicate = JsonParser.parseString("""
                {"role":"permanent","offers":[
                  {"id":"same","item":"minecraft:stone","price":1},
                  {"id":"same","item":"minecraft:dirt","price":1}
                ]}
                """).getAsJsonObject();

        assertThrows(JsonParseException.class, () -> TraderCatalogParser.parse(unknown, id -> false));
        assertThrows(JsonParseException.class, () -> TraderCatalogParser.parse(duplicate, id -> true));
    }

    @Test
    void rejectsUnsafeAmounts() {
        var root = JsonParser.parseString("""
                {"role":"contraband","offers":[{"id":"bad","item":"minecraft:stone","count":65,"price":1}]}
                """).getAsJsonObject();

        assertThrows(JsonParseException.class, () -> TraderCatalogParser.parse(root, id -> true));
    }

    @Test
    void roleBoundsMatchOutboundShopCapacity() {
        JsonObject permanent = catalog("permanent", 10);
        JsonObject contraband = catalog("contraband", TraderPayloads.MAX_SELL_OFFERS + 1);
        JsonObject rotating = catalog("rotating", 64);

        assertThrows(JsonParseException.class, () -> TraderCatalogParser.parse(permanent, id -> true));
        assertThrows(JsonParseException.class, () -> TraderCatalogParser.parse(contraband, id -> true));
        assertEquals(64, TraderCatalogParser.parse(rotating, id -> true).offers().size());
    }

    private static JsonObject catalog(String role, int count) {
        JsonObject root = new JsonObject();
        root.addProperty("role", role);
        JsonArray offers = new JsonArray();
        for (int index = 0; index < count; index++) {
            JsonObject offer = new JsonObject();
            offer.addProperty("id", "offer_" + index);
            offer.addProperty("item", "minecraft:stone");
            offer.addProperty("price", 1);
            offers.add(offer);
        }
        root.add("offers", offers);
        return root;
    }
}
