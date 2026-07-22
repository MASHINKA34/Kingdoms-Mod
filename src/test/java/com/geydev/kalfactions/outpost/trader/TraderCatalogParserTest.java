package com.geydev.kalfactions.outpost.trader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
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
}
