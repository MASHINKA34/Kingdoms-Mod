package com.geydev.kalfactions.outpost.trader;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntUnaryOperator;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class SellerOfferRotation extends SavedData {
    public static final String DATA_NAME = "kingdoms_seller_offer_rotation";
    public static final int OFFER_COUNT = 9;
    public static final Factory<SellerOfferRotation> FACTORY =
            new Factory<>(SellerOfferRotation::new, SellerOfferRotation::load);

    private static final ZoneId REFRESH_ZONE = ZoneId.of("Europe/Moscow");
    private static final int DATA_VERSION = 1;
    private static final String TAG_VERSION = "formatVersion";
    private static final String TAG_SHOPS = "shops";
    private static final String TAG_TRADER = "trader";
    private static final String TAG_INDEX = "index";
    private static final String TAG_EPOCH_DAY = "epochDay";
    private static final String TAG_OFFERS = "offers";
    private static final String TAG_PLAYER_SALES = "playerSales";
    private static final String TAG_PLAYER = "player";
    private static final String TAG_SALES = "sales";
    private static final String TAG_OFFER = "offer";
    private static final String TAG_COUNT = "count";
    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private final Map<UUID, TraderShop> shops = new HashMap<>();

    public static SellerOfferRotation get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public synchronized Window current(MinecraftServer server, UUID traderId) {
        long nowEpochMillis = System.currentTimeMillis();
        TraderShop shop = shopFor(server, traderId, nowEpochMillis);
        return new Window(shop.activeOffers(), nextRefreshEpochMillis(nowEpochMillis));
    }

    public synchronized int remainingLimit(MinecraftServer server, UUID traderId, UUID playerId, SellOffer offer) {
        return remainingLimit(server, traderId, playerId, offer.id(), offer.dailyLimit());
    }

    public synchronized int remainingLimit(
            MinecraftServer server,
            UUID traderId,
            UUID playerId,
            String offerId,
            int dailyLimit
    ) {
        TraderShop shop = shopFor(server, traderId, System.currentTimeMillis());
        int sold = shop.soldByPlayer.getOrDefault(playerId, Map.of()).getOrDefault(offerId, 0);
        return Math.max(0, dailyLimit - sold);
    }

    public synchronized void recordSale(MinecraftServer server, UUID traderId, UUID playerId, SellOffer offer, int count) {
        recordSale(server, traderId, playerId, offer.id(), count);
    }

    public synchronized void recordSale(
            MinecraftServer server,
            UUID traderId,
            UUID playerId,
            String offerId,
            int count
    ) {
        if (count <= 0) {
            return;
        }
        TraderShop shop = shopFor(server, traderId, System.currentTimeMillis());
        Map<String, Integer> playerSales = shop.soldByPlayer.computeIfAbsent(playerId, ignored -> new HashMap<>());
        playerSales.merge(offerId, count, SellerOfferRotation::saturatedIntAdd);
        setDirty();
    }

    public synchronized int transactSale(
            MinecraftServer server,
            UUID traderId,
            UUID playerId,
            String offerId,
            int dailyLimit,
            int requestedCount,
            IntUnaryOperator transaction
    ) {
        if (requestedCount <= 0) {
            return 0;
        }
        TraderShop shop = shopFor(server, traderId, System.currentTimeMillis());
        Map<String, Integer> playerSales = shop.soldByPlayer.computeIfAbsent(playerId, ignored -> new HashMap<>());
        int sold = playerSales.getOrDefault(offerId, 0);
        int allowed = Math.min(requestedCount, Math.max(0, dailyLimit - sold));
        if (allowed <= 0) {
            return 0;
        }
        int committed = Math.clamp(transaction.applyAsInt(allowed), 0, allowed);
        if (committed > 0) {
            playerSales.merge(offerId, committed, SellerOfferRotation::saturatedIntAdd);
            setDirty();
        }
        return committed;
    }

    public synchronized void removeShop(UUID traderId) {
        if (shops.remove(traderId) != null) {
            setDirty();
        }
    }

    public synchronized void ensureShop(MinecraftServer server, UUID traderId) {
        shopFor(server, traderId, System.currentTimeMillis());
    }

    public synchronized List<ShopEntry> shopEntries(MinecraftServer server) {
        long nowEpochMillis = System.currentTimeMillis();
        List<ShopEntry> entries = new ArrayList<>(shops.size());
        for (UUID traderId : List.copyOf(shops.keySet())) {
            TraderShop shop = shopFor(server, traderId, nowEpochMillis);
            entries.add(new ShopEntry(
                    traderId,
                    shop.displayIndex,
                    shop.activeOffers(),
                    nextRefreshEpochMillis(nowEpochMillis)
            ));
        }
        entries.sort(java.util.Comparator.comparingInt(ShopEntry::index));
        return List.copyOf(entries);
    }

    private TraderShop shopFor(MinecraftServer server, UUID traderId, long nowEpochMillis) {
        TraderShop shop = shops.computeIfAbsent(traderId, ignored -> {
            TraderShop created = new TraderShop();
            created.displayIndex = nextDisplayIndex();
            return created;
        });
        long today = epochDay(nowEpochMillis);
        if (shop.epochDay != today || !shop.hasValidOffers()) {
            roll(server, shop, traderId, today);
        }
        return shop;
    }

    private int nextDisplayIndex() {
        return shops.values().stream().mapToInt(shop -> shop.displayIndex).max().orElse(0) + 1;
    }

    private void roll(MinecraftServer server, TraderShop shop, UUID traderId, long epochDay) {
        List<SellOffer> pool = new ArrayList<>(Arrays.asList(SellOffer.values()));
        long seed = mix64(server.overworld().getSeed())
                ^ mix64(epochDay * GOLDEN_GAMMA)
                ^ mix64(traderId.getMostSignificantBits())
                ^ mix64(traderId.getLeastSignificantBits());
        Collections.shuffle(pool, new Random(seed));
        shop.offerIds = pool.stream()
                .limit(OFFER_COUNT)
                .map(SellOffer::id)
                .toList();
        shop.epochDay = epochDay;
        shop.soldByPlayer.clear();
        setDirty();
    }

    private static long epochDay(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(REFRESH_ZONE).toLocalDate().toEpochDay();
    }

    private static long nextRefreshEpochMillis(long epochMillis) {
        LocalDate tomorrow = Instant.ofEpochMilli(epochMillis)
                .atZone(REFRESH_ZONE)
                .toLocalDate()
                .plusDays(1L);
        return tomorrow.atStartOfDay(REFRESH_ZONE).toInstant().toEpochMilli();
    }

    private static long mix64(long value) {
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(TAG_VERSION, DATA_VERSION);
        ListTag shopsTag = new ListTag();
        for (Map.Entry<UUID, TraderShop> entry : shops.entrySet()) {
            CompoundTag shopTag = entry.getValue().save();
            shopTag.putUUID(TAG_TRADER, entry.getKey());
            shopsTag.add(shopTag);
        }
        tag.put(TAG_SHOPS, shopsTag);
        return tag;
    }

    private static SellerOfferRotation load(CompoundTag tag, HolderLookup.Provider registries) {
        SellerOfferRotation rotation = new SellerOfferRotation();
        ListTag shopsTag = tag.getList(TAG_SHOPS, Tag.TAG_COMPOUND);
        for (int index = 0; index < shopsTag.size(); index++) {
            CompoundTag shopTag = shopsTag.getCompound(index);
            if (!shopTag.hasUUID(TAG_TRADER)) {
                rotation.setDirty();
                continue;
            }
            rotation.shops.put(shopTag.getUUID(TAG_TRADER), TraderShop.load(shopTag));
        }
        int maxIndex = rotation.shops.values().stream().mapToInt(shop -> shop.displayIndex).max().orElse(0);
        for (TraderShop shop : rotation.shops.values()) {
            if (shop.displayIndex <= 0) {
                shop.displayIndex = ++maxIndex;
                rotation.setDirty();
            }
        }
        return rotation;
    }

    private static int saturatedIntAdd(int left, int right) {
        return Integer.MAX_VALUE - left < right ? Integer.MAX_VALUE : left + right;
    }

    private static final class TraderShop {
        private long epochDay = Long.MIN_VALUE;
        private int displayIndex;
        private List<String> offerIds = List.of();
        private final Map<UUID, Map<String, Integer>> soldByPlayer = new HashMap<>();

        private boolean hasValidOffers() {
            if (offerIds.size() != OFFER_COUNT) {
                return false;
            }
            Set<String> seen = new HashSet<>();
            for (String id : offerIds) {
                if (!seen.add(id) || SellOffer.byId(id).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        private List<SellOffer> activeOffers() {
            List<SellOffer> offers = new ArrayList<>(offerIds.size());
            for (String id : offerIds) {
                SellOffer.byId(id).ifPresent(offers::add);
            }
            return List.copyOf(offers);
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong(TAG_EPOCH_DAY, epochDay);
            tag.putInt(TAG_INDEX, displayIndex);
            ListTag offers = new ListTag();
            for (String id : offerIds) {
                offers.add(StringTag.valueOf(id));
            }
            tag.put(TAG_OFFERS, offers);

            ListTag playerSales = new ListTag();
            for (Map.Entry<UUID, Map<String, Integer>> playerEntry : soldByPlayer.entrySet()) {
                CompoundTag playerTag = new CompoundTag();
                playerTag.putUUID(TAG_PLAYER, playerEntry.getKey());
                ListTag sales = new ListTag();
                for (Map.Entry<String, Integer> saleEntry : playerEntry.getValue().entrySet()) {
                    int count = saleEntry.getValue();
                    if (count <= 0 || !isSafeOfferId(saleEntry.getKey())) {
                        continue;
                    }
                    CompoundTag saleTag = new CompoundTag();
                    saleTag.putString(TAG_OFFER, saleEntry.getKey());
                    saleTag.putInt(TAG_COUNT, count);
                    sales.add(saleTag);
                }
                if (!sales.isEmpty()) {
                    playerTag.put(TAG_SALES, sales);
                    playerSales.add(playerTag);
                }
            }
            tag.put(TAG_PLAYER_SALES, playerSales);
            return tag;
        }

        private static TraderShop load(CompoundTag tag) {
            TraderShop shop = new TraderShop();
            shop.epochDay = tag.contains(TAG_EPOCH_DAY, Tag.TAG_LONG)
                    ? tag.getLong(TAG_EPOCH_DAY)
                    : Long.MIN_VALUE;
            shop.displayIndex = tag.getInt(TAG_INDEX);

            ListTag offers = tag.getList(TAG_OFFERS, Tag.TAG_STRING);
            List<String> ids = new ArrayList<>(offers.size());
            Set<String> seen = new HashSet<>();
            for (int index = 0; index < offers.size(); index++) {
                String id = offers.getString(index);
                if (!seen.add(id) || SellOffer.byId(id).isEmpty()) {
                    continue;
                }
                ids.add(id);
            }
            shop.offerIds = List.copyOf(ids);

            ListTag playerSales = tag.getList(TAG_PLAYER_SALES, Tag.TAG_COMPOUND);
            for (int index = 0; index < playerSales.size(); index++) {
                CompoundTag playerTag = playerSales.getCompound(index);
                if (!playerTag.hasUUID(TAG_PLAYER)) {
                    continue;
                }
                Map<String, Integer> sales = new HashMap<>();
                ListTag salesTag = playerTag.getList(TAG_SALES, Tag.TAG_COMPOUND);
                for (int saleIndex = 0; saleIndex < salesTag.size(); saleIndex++) {
                    CompoundTag saleTag = salesTag.getCompound(saleIndex);
                    String offerId = saleTag.getString(TAG_OFFER);
                    int count = saleTag.getInt(TAG_COUNT);
                    if (count <= 0 || !isSafeOfferId(offerId)) {
                        continue;
                    }
                    sales.merge(offerId, count, SellerOfferRotation::saturatedIntAdd);
                }
                if (!sales.isEmpty()) {
                    shop.soldByPlayer.put(playerTag.getUUID(TAG_PLAYER), sales);
                }
            }
            return shop;
        }
    }

    private static boolean isSafeOfferId(String value) {
        return value != null && value.matches("[a-z0-9_.-]{1,64}");
    }

    public record ShopEntry(UUID traderId, int index, List<SellOffer> offers, long nextRefreshEpochMillis) {
        public ShopEntry {
            offers = List.copyOf(offers);
        }
    }

    public record Window(List<SellOffer> offers, long nextRefreshEpochMillis) {
        public Window {
            offers = List.copyOf(offers);
        }

        public Optional<SellOffer> offer(String offerId) {
            return offers.stream().filter(offer -> offer.id().equals(offerId)).findFirst();
        }
    }

    private SellerOfferRotation() {
    }
}
