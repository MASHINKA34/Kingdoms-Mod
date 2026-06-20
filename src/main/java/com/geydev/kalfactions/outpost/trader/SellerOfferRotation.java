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

    private static final ZoneId REFRESH_ZONE = ZoneId.systemDefault();
    private static final String TAG_EPOCH_DAY = "epochDay";
    private static final String TAG_OFFERS = "offers";
    private static final String TAG_PLAYER_SALES = "playerSales";
    private static final String TAG_PLAYER = "player";
    private static final String TAG_SALES = "sales";
    private static final String TAG_OFFER = "offer";
    private static final String TAG_COUNT = "count";
    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private long activeEpochDay = Long.MIN_VALUE;
    private List<String> activeOfferIds = List.of();
    private final Map<UUID, Map<String, Integer>> soldByPlayer = new HashMap<>();

    public static SellerOfferRotation get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public synchronized Window current(MinecraftServer server) {
        long nowEpochMillis = System.currentTimeMillis();
        ensureFresh(server, nowEpochMillis);
        return new Window(activeOffers(), nextRefreshEpochMillis(nowEpochMillis));
    }

    public synchronized boolean refreshIfNeeded(MinecraftServer server, long nowEpochMillis) {
        return ensureFresh(server, nowEpochMillis);
    }

    public synchronized int remainingLimit(MinecraftServer server, UUID playerId, SellOffer offer) {
        ensureFresh(server, System.currentTimeMillis());
        int sold = soldByPlayer.getOrDefault(playerId, Map.of()).getOrDefault(offer.id(), 0);
        return Math.max(0, offer.dailyLimit() - sold);
    }

    public synchronized void recordSale(MinecraftServer server, UUID playerId, SellOffer offer, int count) {
        if (count <= 0) {
            return;
        }
        ensureFresh(server, System.currentTimeMillis());
        Map<String, Integer> playerSales = soldByPlayer.computeIfAbsent(playerId, ignored -> new HashMap<>());
        int previous = playerSales.getOrDefault(offer.id(), 0);
        int updated = Integer.MAX_VALUE - previous < count ? Integer.MAX_VALUE : previous + count;
        playerSales.put(offer.id(), updated);
        setDirty();
    }

    private boolean ensureFresh(MinecraftServer server, long nowEpochMillis) {
        long today = epochDay(nowEpochMillis);
        if (activeEpochDay == today && hasValidOffers()) {
            return false;
        }
        roll(server, today, nowEpochMillis);
        return true;
    }

    private void roll(MinecraftServer server, long epochDay, long nowEpochMillis) {
        List<SellOffer> pool = new ArrayList<>(Arrays.asList(SellOffer.values()));
        long seed = mix64(server.overworld().getSeed())
                ^ mix64(epochDay * GOLDEN_GAMMA)
                ^ mix64(nowEpochMillis);
        Collections.shuffle(pool, new Random(seed));
        activeOfferIds = pool.stream()
                .limit(OFFER_COUNT)
                .map(SellOffer::id)
                .toList();
        activeEpochDay = epochDay;
        soldByPlayer.clear();
        setDirty();
    }

    private boolean hasValidOffers() {
        if (activeOfferIds.size() != OFFER_COUNT) {
            return false;
        }
        Set<String> seen = new HashSet<>();
        for (String id : activeOfferIds) {
            if (!seen.add(id) || SellOffer.byId(id).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private List<SellOffer> activeOffers() {
        List<SellOffer> offers = new ArrayList<>(activeOfferIds.size());
        for (String id : activeOfferIds) {
            SellOffer.byId(id).ifPresent(offers::add);
        }
        return List.copyOf(offers);
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
        tag.putLong(TAG_EPOCH_DAY, activeEpochDay);
        ListTag offers = new ListTag();
        for (String id : activeOfferIds) {
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
                if (count <= 0 || SellOffer.byId(saleEntry.getKey()).isEmpty()) {
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

    private static SellerOfferRotation load(CompoundTag tag, HolderLookup.Provider registries) {
        SellerOfferRotation rotation = new SellerOfferRotation();
        rotation.activeEpochDay = tag.contains(TAG_EPOCH_DAY, Tag.TAG_LONG)
                ? tag.getLong(TAG_EPOCH_DAY)
                : Long.MIN_VALUE;

        ListTag offers = tag.getList(TAG_OFFERS, Tag.TAG_STRING);
        List<String> ids = new ArrayList<>(offers.size());
        Set<String> seen = new HashSet<>();
        boolean repaired = false;
        for (int index = 0; index < offers.size(); index++) {
            String id = offers.getString(index);
            if (!seen.add(id) || SellOffer.byId(id).isEmpty()) {
                repaired = true;
                continue;
            }
            ids.add(id);
        }
        rotation.activeOfferIds = List.copyOf(ids);

        ListTag playerSales = tag.getList(TAG_PLAYER_SALES, Tag.TAG_COMPOUND);
        for (int index = 0; index < playerSales.size(); index++) {
            CompoundTag playerTag = playerSales.getCompound(index);
            if (!playerTag.hasUUID(TAG_PLAYER)) {
                repaired = true;
                continue;
            }
            Map<String, Integer> sales = new HashMap<>();
            ListTag salesTag = playerTag.getList(TAG_SALES, Tag.TAG_COMPOUND);
            for (int saleIndex = 0; saleIndex < salesTag.size(); saleIndex++) {
                CompoundTag saleTag = salesTag.getCompound(saleIndex);
                String offerId = saleTag.getString(TAG_OFFER);
                int count = saleTag.getInt(TAG_COUNT);
                if (count <= 0 || SellOffer.byId(offerId).isEmpty()) {
                    repaired = true;
                    continue;
                }
                sales.merge(offerId, count, SellerOfferRotation::saturatedIntAdd);
            }
            if (!sales.isEmpty()) {
                rotation.soldByPlayer.put(playerTag.getUUID(TAG_PLAYER), sales);
            }
        }
        if (repaired || !rotation.hasValidOffers()) {
            rotation.setDirty();
        }
        return rotation;
    }

    private static int saturatedIntAdd(int left, int right) {
        return Integer.MAX_VALUE - left < right ? Integer.MAX_VALUE : left + right;
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
