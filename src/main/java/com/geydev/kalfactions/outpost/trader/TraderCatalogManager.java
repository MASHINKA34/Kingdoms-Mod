package com.geydev.kalfactions.outpost.trader;

import com.geydev.kalfactions.KalFactions;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class TraderCatalogManager extends SimpleJsonResourceReloadListener {
    private static final TraderCatalogManager INSTANCE = new TraderCatalogManager();
    private static volatile Map<TraderCatalogRole, List<TraderCatalogOffer>> catalogs = Map.of();

    private TraderCatalogManager() {
        super(new Gson(), "trader_catalogs");
    }

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    public static List<TraderCatalogOffer> offers(TraderCatalogRole role) {
        return catalogs.getOrDefault(role, List.of());
    }

    public static java.util.Optional<TraderCatalogOffer> offer(TraderCatalogRole role, String id) {
        return offers(role).stream().filter(offer -> offer.id().equals(id)).findFirst();
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
        EnumMap<TraderCatalogRole, List<TraderCatalogOffer>> loaded = new EnumMap<>(TraderCatalogRole.class);
        Set<String> globalIds = new HashSet<>();
        List<Map.Entry<ResourceLocation, JsonElement>> entries = resources.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries) {
            try {
                TraderCatalogParser.ParsedCatalog parsed = TraderCatalogParser.parse(
                        entry.getValue().getAsJsonObject(),
                        BuiltInRegistries.ITEM::containsKey
                );
                List<TraderCatalogOffer> roleOffers = loaded.computeIfAbsent(parsed.role(), ignored -> new ArrayList<>());
                if (roleOffers.size() + parsed.offers().size() > maximumOffers(parsed.role())) {
                    throw new IllegalArgumentException("Trader role " + parsed.role().id() + " exceeds offer limit");
                }
                Set<String> incomingIds = new HashSet<>();
                for (TraderCatalogOffer offer : parsed.offers()) {
                    String key = parsed.role().id() + ":" + offer.id();
                    if (globalIds.contains(key) || !incomingIds.add(key)) {
                        throw new IllegalArgumentException("Duplicate offer " + key);
                    }
                }
                globalIds.addAll(incomingIds);
                roleOffers.addAll(parsed.offers());
            } catch (RuntimeException exception) {
                KalFactions.LOGGER.error("Rejected trader catalog {}", entry.getKey(), exception);
            }
        }
        EnumMap<TraderCatalogRole, List<TraderCatalogOffer>> immutable = new EnumMap<>(TraderCatalogRole.class);
        loaded.forEach((role, offers) -> immutable.put(role, List.copyOf(offers)));
        catalogs = Map.copyOf(immutable);
        KalFactions.LOGGER.info("Loaded {} trader catalog offers", globalIds.size());
    }

    static int maximumOffers(TraderCatalogRole role) {
        return switch (role) {
            case PERMANENT -> TraderPayloads.MAX_SELL_OFFERS - SellerOfferRotation.OFFER_COUNT;
            case CONTRABAND -> TraderPayloads.MAX_SELL_OFFERS;
            case ROTATING, WANDERING -> 64;
        };
    }
}
