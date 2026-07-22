package com.geydev.kalfactions.outpost.trader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

public final class TraderCatalogParser {
    public static ParsedCatalog parse(JsonObject root, Predicate<ResourceLocation> knownItem) {
        TraderCatalogRole role = TraderCatalogRole.parse(GsonHelper.getAsString(root, "role"))
                .orElseThrow(() -> new JsonParseException("Unknown trader role"));
        JsonArray array = GsonHelper.getAsJsonArray(root, "offers");
        int maximumOffers = TraderCatalogManager.maximumOffers(role);
        if (array.size() > maximumOffers) {
            throw new JsonParseException("Catalog exceeds " + maximumOffers + " offers for " + role.id());
        }
        List<TraderCatalogOffer> offers = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonElement element : array) {
            JsonObject offer = GsonHelper.convertToJsonObject(element, "offer");
            String id = GsonHelper.getAsString(offer, "id");
            if (!id.matches("[a-z0-9_.-]{1,64}") || !ids.add(id)) {
                throw new JsonParseException("Invalid or duplicate offer ID " + id);
            }
            boolean hasItem = offer.has("item");
            boolean hasTag = offer.has("tag");
            if (hasItem == hasTag) {
                throw new JsonParseException("Offer " + id + " must define item or tag");
            }
            ResourceLocation itemId = hasItem ? parseId(GsonHelper.getAsString(offer, "item"), "item") : null;
            ResourceLocation tagId = hasTag ? parseId(GsonHelper.getAsString(offer, "tag"), "tag") : null;
            if (itemId != null && !knownItem.test(itemId)) {
                throw new JsonParseException("Unknown item " + itemId);
            }
            int count = GsonHelper.getAsInt(offer, "count", 1);
            long price = GsonHelper.getAsLong(offer, "price", -1L);
            long minimum = GsonHelper.getAsLong(offer, "min_price", price);
            long maximum = GsonHelper.getAsLong(offer, "max_price", price);
            int dailyLimit = GsonHelper.getAsInt(offer, "daily_limit", 128);
            try {
                offers.add(new TraderCatalogOffer(id, itemId, tagId, count, minimum, maximum, dailyLimit));
            } catch (IllegalArgumentException exception) {
                throw new JsonParseException("Invalid values for offer " + id, exception);
            }
        }
        return new ParsedCatalog(role, List.copyOf(offers));
    }

    private static ResourceLocation parseId(String value, String field) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new JsonParseException("Invalid " + field + " ID " + value);
        }
        return id;
    }

    public record ParsedCatalog(TraderCatalogRole role, List<TraderCatalogOffer> offers) {
    }

    private TraderCatalogParser() {
    }
}
