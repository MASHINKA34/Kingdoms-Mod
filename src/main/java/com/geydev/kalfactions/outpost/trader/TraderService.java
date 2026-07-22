package com.geydev.kalfactions.outpost.trader;

import com.geydev.kalfactions.command.NumismaticsEconomy;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.entity.BankerEntity;
import com.geydev.kalfactions.entity.OutpostTraderEntity;
import com.geydev.kalfactions.entity.SellerTraderEntity;
import com.geydev.kalfactions.economy.PriceMath;
import com.geydev.kalfactions.faction.FactionBonus;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.protection.FactionAccess;
import com.geydev.kalfactions.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.PacketDistributor;

public final class TraderService {
    private static final double MAX_DISTANCE_SQUARED = 64.0D;

    public static boolean spawn(ServerLevel level, double x, double y, double z, float yRot) {
        return spawn(level, x, y, z, yRot, null);
    }

    public static boolean spawn(
            ServerLevel level,
            double x,
            double y,
            double z,
            float yRot,
            @Nullable Player spawner
    ) {
        OutpostTraderEntity trader = ModEntities.OUTPOST_TRADER.get().create(level);
        if (trader == null) {
            return false;
        }
        trader.moveTo(x, y, z, yRot, 0.0F);
        if (spawner != null) {
            trader.lookAt(EntityAnchorArgument.Anchor.EYES, spawner.getEyePosition());
            trader.setYBodyRot(trader.getYRot());
            trader.yBodyRotO = trader.getYRot();
            trader.yHeadRotO = trader.getYHeadRot();
        }
        return level.addFreshEntity(trader);
    }

    public static boolean spawnSeller(
            ServerLevel level,
            double x,
            double y,
            double z,
            float yRot,
            @Nullable Player spawner
    ) {
        SellerTraderEntity trader = spawnSellerEntity(
                level, x, y, z, yRot, spawner, SellerTraderRole.PERMANENT, null, null, 0L
        );
        return trader != null;
    }

    public static SellerTraderEntity spawnSellerEntity(
            ServerLevel level,
            double x,
            double y,
            double z,
            float yRot,
            @Nullable Player spawner,
            SellerTraderRole role,
            @Nullable UUID eventId,
            @Nullable UUID targetFactionId,
            long expiresAtMillis
    ) {
        SellerTraderEntity trader = createSellerEntity(
                level, x, y, z, yRot, spawner, role, eventId, targetFactionId, expiresAtMillis
        );
        return trader != null && level.addFreshEntity(trader) ? trader : null;
    }

    public static SellerTraderEntity createSellerEntity(
            ServerLevel level,
            double x,
            double y,
            double z,
            float yRot,
            @Nullable Player spawner,
            SellerTraderRole role,
            @Nullable UUID eventId,
            @Nullable UUID targetFactionId,
            long expiresAtMillis
    ) {
        SellerTraderEntity trader = ModEntities.SELLER_TRADER.get().create(level);
        if (trader == null) {
            return null;
        }
        trader.setTraderRole(role);
        trader.setEventId(eventId);
        trader.setTargetFactionId(targetFactionId);
        trader.setExpiresAtMillis(expiresAtMillis);
        trader.moveTo(x, y, z, yRot, 0.0F);
        if (spawner != null) {
            trader.lookAt(EntityAnchorArgument.Anchor.EYES, spawner.getEyePosition());
            trader.setYBodyRot(trader.getYRot());
            trader.yBodyRotO = trader.getYRot();
            trader.yHeadRotO = trader.getYHeadRot();
        }
        return trader;
    }

    public static boolean spawnBanker(
            ServerLevel level,
            double x,
            double y,
            double z,
            float yRot,
            @Nullable Player spawner
    ) {
        BankerEntity banker = ModEntities.BANKER.get().create(level);
        if (banker == null) {
            return false;
        }
        banker.moveTo(x, y, z, yRot, 0.0F);
        if (spawner != null) {
            banker.lookAt(EntityAnchorArgument.Anchor.EYES, spawner.getEyePosition());
            banker.setYBodyRot(banker.getYRot());
            banker.yBodyRotO = banker.getYRot();
            banker.yHeadRotO = banker.getYHeadRot();
        }
        return level.addFreshEntity(banker);
    }

    public static boolean isMarked(Entity entity) {
        return entity instanceof OutpostTraderEntity
                || entity instanceof SellerTraderEntity
                || entity instanceof BankerEntity;
    }

    public static void open(ServerPlayer player, OutpostTraderEntity trader) {
        TradeSessionManager.open(player, trader.getUUID());
        if (!isAvailable(player, trader)) {
            sendBuyState(
                    player,
                    trader.getUUID(),
                    TraderOffer.Shop.KINGDOMS,
                    Component.translatable("screen.kingdoms.trader.notice.unavailable"),
                    false
            );
            return;
        }
        sendBuyState(player, trader.getUUID(), TraderOffer.Shop.KINGDOMS, Component.empty(), true);
    }

    public static void openBanker(ServerPlayer player, BankerEntity banker) {
        TradeSessionManager.open(player, banker.getUUID());
        if (!isAvailable(player, banker)) {
            sendBuyState(
                    player,
                    banker.getUUID(),
                    TraderOffer.Shop.BANKER,
                    Component.translatable("screen.kingdoms.trader.notice.unavailable"),
                    false
            );
            return;
        }
        sendBuyState(player, banker.getUUID(), TraderOffer.Shop.BANKER, Component.empty(), true);
    }

    public static void openSeller(ServerPlayer player, SellerTraderEntity trader) {
        SellerOfferRotation.get(player.getServer()).setCatalogVisible(
                player.getServer(), trader.getUUID(), trader.traderRole() == SellerTraderRole.PERMANENT
        );
        TradeSessionManager.open(player, trader.getUUID());
        if (!isAvailable(player, trader)) {
            sendSellUnavailable(
                    player, trader.getUUID(), Component.translatable("screen.kingdoms.trader.notice.unavailable")
            );
            return;
        }
        sendSellState(player, trader, Component.empty(), true);
    }

    public static void refreshSeller(ServerPlayer player, UUID traderId, UUID sessionId) {
        if (!TradeSessionManager.refresh(player, traderId, sessionId)) {
            return;
        }
        Entity entity = player.serverLevel().getEntity(traderId);
        if (!(entity instanceof SellerTraderEntity trader)) {
            sendSellUnavailable(player, traderId, Component.translatable("screen.kingdoms.trader.notice.too_far"));
            return;
        }
        if (!isAvailable(player, trader)) {
            sendSellUnavailable(
                    player, trader.getUUID(), Component.translatable("screen.kingdoms.trader.notice.too_far")
            );
            return;
        }
        sendSellState(player, trader, Component.empty(), true);
    }

    public static void buy(ServerPlayer player, UUID traderId, UUID sessionId, long sequence, String offerId) {
        TradeSessionManager.Validation validation =
                TradeSessionManager.validate(player, traderId, sessionId, sequence);
        if (validation != TradeSessionManager.Validation.ACCEPTED) {
            Entity current = player.serverLevel().getEntity(traderId);
            TraderOffer.Shop currentShop = current instanceof BankerEntity
                    ? TraderOffer.Shop.BANKER
                    : TraderOffer.Shop.KINGDOMS;
            sendBuyState(
                    player,
                    traderId,
                    currentShop,
                    Component.translatable("screen.kingdoms.trader.notice.unavailable"),
                    false
            );
            return;
        }
        Entity entity = player.serverLevel().getEntity(traderId);
        TraderOffer.Shop shop;
        if (entity instanceof OutpostTraderEntity) {
            shop = TraderOffer.Shop.KINGDOMS;
        } else if (entity instanceof BankerEntity) {
            shop = TraderOffer.Shop.BANKER;
        } else {
            sendBuyState(
                    player,
                    traderId,
                    TraderOffer.Shop.KINGDOMS,
                    Component.translatable("screen.kingdoms.trader.notice.unavailable"),
                    false
            );
            return;
        }
        if (!isAvailable(player, entity)) {
            sendBuyState(
                    player,
                    traderId,
                    shop,
                    Component.translatable("screen.kingdoms.trader.notice.too_far"),
                    false
            );
            return;
        }

        TraderOffer offer = TraderOffer.byId(offerId).orElse(null);
        if (offer == null || offer.shop() != shop) {
            sendBuyState(
                    player,
                    traderId,
                    shop,
                    Component.translatable("screen.kingdoms.trader.notice.invalid_offer"),
                    false
            );
            return;
        }

        long price = buyUnitPrice(player, offer);
        ItemStack product = new ItemStack(offer.item());
        if (product.isEmpty()) {
            sendBuyState(
                    player,
                    traderId,
                    shop,
                    Component.translatable("screen.kingdoms.trader.notice.invalid_offer"),
                    false
            );
            return;
        }
        if (!hasInventorySpace(player, product)) {
            sendBuyState(
                    player,
                    traderId,
                    shop,
                    Component.translatable("screen.kingdoms.trader.notice.inventory_full"),
                    false
            );
            return;
        }

        if (price > 0L) {
            NumismaticsEconomy.Payment payment = NumismaticsEconomy.preparePayment(player, price);
            if (!payment.ready()) {
                sendBuyState(
                        player,
                        traderId,
                        shop,
                        Component.translatable(
                                "screen.kingdoms.trader.notice.insufficient_funds",
                                NumismaticsEconomy.format(price),
                                NumismaticsEconomy.format(payment.available())
                        ),
                        false
                );
                return;
            }
            if (!NumismaticsEconomy.commitPayment(player, payment)) {
                sendBuyState(
                        player,
                        traderId,
                        shop,
                        Component.translatable("screen.kingdoms.trader.notice.payment_changed"),
                        false
                );
                return;
            }
        }

        if (!player.getInventory().add(product) || !product.isEmpty()) {
            player.getInventory().placeItemBackInInventory(product);
        }
        player.inventoryMenu.broadcastChanges();
        sendBuyState(
                player,
                traderId,
                shop,
                Component.translatable(
                        "screen.kingdoms.trader.notice.purchased",
                        offer.item().getName(new ItemStack(offer.item())),
                        NumismaticsEconomy.format(price)
                ),
                true
        );
    }

    public static void openSellerCatalog(ServerPlayer player) {
        MinecraftServer server = player.serverLevel().getServer();
        SellerOfferRotation rotation = SellerOfferRotation.get(server);
        List<TraderPayloads.SellerInfo> sellers = new ArrayList<>();
        for (SellerOfferRotation.ShopEntry entry : rotation.shopEntries(server)) {
            if (sellers.size() >= TraderPayloads.MAX_SELLERS) {
                break;
            }
            List<TraderPayloads.OfferInfo> offers = entry.offers().stream()
                    .filter(offer -> offer.itemId() != null)
                    .map(offer -> new TraderPayloads.OfferInfo(
                            offer.id(),
                            offer.itemId().toString(),
                            offer.count(),
                            sellUnitPrice(player, offer.minimumPrice()),
                            rotation.remainingLimit(
                                    server, entry.traderId(), player.getUUID(), offer.id(), offer.dailyLimit()
                            ),
                            false
                    ))
                    .toList();
            sellers.add(new TraderPayloads.SellerInfo(
                    entry.traderId(),
                    entry.index(),
                    offers,
                    entry.nextRefreshEpochMillis()
            ));
        }
        PacketDistributor.sendToPlayer(player, new TraderPayloads.S2CSellerCatalog(sellers));
    }

    private static boolean isAvailable(ServerPlayer player, Entity trader) {
        if (!trader.isAlive()
                || trader.isRemoved()
                || trader.level() != player.level()
                || player.distanceToSqr(trader) > MAX_DISTANCE_SQUARED) {
            return false;
        }
        if (!(trader instanceof SellerTraderEntity seller)) {
            return true;
        }
        if (seller.traderRole() == SellerTraderRole.PERMANENT) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (seller.expiresAtMillis() <= now || seller.eventId().isEmpty()) {
            return false;
        }
        TraderWorldData data = TraderWorldData.get(player.getServer());
        if (seller.traderRole() == SellerTraderRole.CONTRABAND) {
            return data.contraband()
                    .filter(active -> active.entityId().equals(seller.getUUID()))
                    .filter(active -> active.eventId().equals(seller.eventId().orElse(null)))
                    .filter(active -> active.expiresAt() > now)
                    .isPresent();
        }
        UUID targetFaction = seller.targetFactionId().orElse(null);
        FactionManager manager = FactionManager.get(player.serverLevel());
        TraderWorldData.WanderingEvent event = targetFaction == null
                ? null
                : data.wandering(targetFaction).orElse(null);
        return TraderAccessPolicy.canUseWandering(
                manager.getFactionIdForMember(player.getUUID()).orElse(null),
                targetFaction,
                manager.getFactionIdAt(ClaimKey.of(player.level(), seller.blockPosition())).orElse(null),
                seller.getUUID(),
                seller.eventId().orElse(null),
                seller.expiresAtMillis(),
                event,
                now
        );
    }

    private static boolean hasInventorySpace(ServerPlayer player, ItemStack product) {
        return player.getInventory().getSlotWithRemainingSpace(product) >= 0
                || player.getInventory().getFreeSlot() >= 0;
    }

    public static void sell(
            ServerPlayer player,
            UUID traderId,
            UUID sessionId,
            long sequence,
            String offerId,
            int requestedCount
    ) {
        TradeSessionManager.Validation validation =
                TradeSessionManager.validate(player, traderId, sessionId, sequence);
        if (validation != TradeSessionManager.Validation.ACCEPTED) {
            Entity current = player.serverLevel().getEntity(traderId);
            if (current instanceof SellerTraderEntity trader) {
                sendSellUnavailable(
                        player, trader.getUUID(), Component.translatable("screen.kingdoms.trader.notice.unavailable")
                );
            }
            return;
        }
        Entity entity = player.serverLevel().getEntity(traderId);
        if (!(entity instanceof SellerTraderEntity trader)) {
            sendSellUnavailable(player, traderId, Component.translatable("screen.kingdoms.trader.notice.too_far"));
            return;
        }
        if (!isAvailable(player, trader)) {
            sendSellUnavailable(
                    player, trader.getUUID(), Component.translatable("screen.kingdoms.trader.notice.too_far")
            );
            return;
        }
        if (requestedCount <= 0) {
            sendSellState(
                    player,
                    trader,
                    Component.translatable("screen.kingdoms.trader.notice.invalid_offer"),
                    false
            );
            return;
        }

        MinecraftServer server = player.serverLevel().getServer();
        SellerOfferRotation rotation = SellerOfferRotation.get(server);
        SellSelection selection = selectionFor(trader, offerId).orElse(null);
        if (selection == null) {
            sendSellState(
                    player,
                    trader,
                    Component.translatable("screen.kingdoms.trader.notice.invalid_offer"),
                    false
            );
            return;
        }

        int owned = countItems(player, selection);
        if (owned <= 0) {
            sendSellState(
                    player,
                    trader,
                    Component.translatable("screen.kingdoms.trader.notice.nothing_to_sell"),
                    false
            );
            return;
        }
        int remainingLimit = rotation.remainingLimit(
                server,
                trader.getUUID(),
                player.getUUID(),
                selection.id(),
                selection.dailyLimit()
        );
        if (remainingLimit <= 0) {
            sendSellState(
                    player,
                    trader,
                    Component.translatable("screen.kingdoms.trader.notice.sell_limit_reached"),
                    false
            );
            return;
        }

        int count = Math.min(owned, Math.min(requestedCount, 4096));
        long maximumPayout = PriceMath.saturatedMultiply(sellUnitPrice(player, selection.price()), count);
        if (!NumismaticsEconomy.canGive(maximumPayout)) {
            sendSellState(
                    player,
                    trader,
                    Component.translatable("screen.kingdoms.trader.notice.invalid_offer"),
                    false
            );
            return;
        }
        int removed = rotation.transactSale(
                server,
                trader.getUUID(),
                player.getUUID(),
                selection.id(),
                selection.dailyLimit(),
                count,
                allowed -> removeUpTo(player, selection, allowed)
        );
        if (removed <= 0) {
            sendSellState(
                    player,
                    trader,
                    Component.translatable("screen.kingdoms.trader.notice.nothing_to_sell"),
                    false
            );
            return;
        }
        count = removed;

        FactionManager manager = FactionManager.get(player.serverLevel());
        UUID factionId = manager.getFactionIdForMember(player.getUUID()).orElse(null);
        long spurs = PriceMath.saturatedMultiply(sellUnitPrice(player, selection.price()), count);
        NumismaticsEconomy.give(player, spurs);
        player.inventoryMenu.broadcastChanges();

        long influenceGained = 0L;
        if (factionId != null) {
            influenceGained = manager.recordSellEarnings(
                    factionId,
                    spurs,
                    ModConfigSpec.INFLUENCE_SPURS_PER_ECON.getAsLong()
            );
            if (influenceGained > 0L) {
                PacketDistributor.sendToPlayer(
                        player,
                        new com.geydev.kalfactions.net.FactionPayloads.S2CInfluenceGain(
                                com.geydev.kalfactions.faction.InfluenceType.ECONOMIC.id(),
                                influenceGained
                        )
                );
            }
        }

        Component notice = influenceGained > 0L
                ? Component.translatable(
                        "screen.kingdoms.trader.notice.sold_influence",
                        count,
                        new ItemStack(selection.displayItem()).getHoverName(),
                        NumismaticsEconomy.format(spurs),
                        influenceGained
                )
                : Component.translatable(
                        "screen.kingdoms.trader.notice.sold",
                        count,
                        new ItemStack(selection.displayItem()).getHoverName(),
                        NumismaticsEconomy.format(spurs)
                );
        sendSellState(player, trader, notice, true);
    }

    private static int countItems(ServerPlayer player, SellSelection selection) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && selection.matches(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int removeUpTo(ServerPlayer player, SellSelection selection, int limit) {
        int removed = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (removed >= limit) {
                break;
            }
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && selection.matches(stack)) {
                int taken = Math.min(stack.getCount(), limit - removed);
                removed += taken;
                stack.shrink(taken);
                if (stack.isEmpty()) {
                    player.getInventory().setItem(slot, ItemStack.EMPTY);
                }
            }
        }
        return removed;
    }

    private static java.util.Optional<SellSelection> selectionFor(SellerTraderEntity trader, String offerId) {
        if (trader.traderRole() == SellerTraderRole.PERMANENT) {
            java.util.Optional<SellSelection> permanent = catalogSelection(
                    TraderCatalogRole.PERMANENT, offerId, true, null
            );
            if (permanent.isPresent()) {
                return permanent;
            }
            return SellerOfferRotation.get(trader.getServer()).current(trader.getServer(), trader.getUUID())
                    .offer(offerId)
                    .flatMap(offer -> catalogSelection(TraderCatalogRole.ROTATING, offer.id(), false, null));
        }
        if (trader.traderRole() == SellerTraderRole.CONTRABAND) {
            return catalogSelection(TraderCatalogRole.CONTRABAND, offerId, false, null);
        }
        UUID factionId = trader.targetFactionId().orElse(null);
        if (factionId == null) {
            return java.util.Optional.empty();
        }
        TraderWorldData.RolledOffer rolled = TraderWorldData.get(trader.getServer())
                .wandering(factionId)
                .stream()
                .flatMap(event -> event.offers().stream())
                .filter(offer -> offer.id().equals(offerId))
                .findFirst()
                .orElse(null);
        return rolled == null
                ? java.util.Optional.empty()
                : catalogSelection(TraderCatalogRole.WANDERING, offerId, false, rolled.price());
    }

    private static java.util.Optional<SellSelection> catalogSelection(
            TraderCatalogRole role,
            String offerId,
            boolean permanent,
            @Nullable Long rolledPrice
    ) {
        return TraderCatalogManager.offer(role, offerId).flatMap(offer -> {
            Item item = displayItem(offer).orElse(null);
            if (item == null) {
                return java.util.Optional.empty();
            }
            TagKey<Item> tag = offer.itemTag() == null
                    ? null
                    : TagKey.create(Registries.ITEM, offer.itemTag());
            return java.util.Optional.of(new SellSelection(
                    offer.id(), item, tag, rolledPrice == null ? offer.minimumPrice() : rolledPrice,
                    offer.dailyLimit(), permanent
            ));
        });
    }

    private static java.util.Optional<Item> displayItem(TraderCatalogOffer offer) {
        if (offer.itemId() != null) {
            return java.util.Optional.of(BuiltInRegistries.ITEM.get(offer.itemId()));
        }
        TagKey<Item> tag = TagKey.create(Registries.ITEM, offer.itemTag());
        return BuiltInRegistries.ITEM.getTag(tag)
                .flatMap(values -> values.stream().findFirst())
                .map(net.minecraft.core.Holder::value);
    }

    private static int researchLevels(ServerPlayer player, String tag) {
        return FactionManager.get(player.serverLevel())
                .getFactionForMember(player.getUUID())
                .map(faction -> faction.researchBonusCount(tag))
                .orElse(0);
    }

    static long sellUnitPrice(ServerPlayer player, long basePrice) {
        long price = basePrice;
        int levels = researchLevels(player, "BUY_RATE");
        if (levels > 0 && price > 0L) {
            price = PriceMath.increaseByPercentCeil(price, 0.10D * levels);
        }
        if (FactionAccess.hasAnyBonus(player, FactionBonus.MERCHANTS) && price > 0L) {
            price = PriceMath.increaseByPercentCeil(
                    price,
                    ModConfigSpec.MERCHANT_SELL_BONUS_PERCENT.getAsDouble()
            );
        }
        return price;
    }

    static long buyUnitPrice(ServerPlayer player, long basePrice) {
        int levels = researchLevels(player, "OUTPOST_DISCOUNT");
        if (levels <= 0 || basePrice <= 0L) {
            return basePrice;
        }
        double factor = 1.0D - Math.min(0.90D, 0.10D * levels);
        return (long) Math.ceil(basePrice * factor);
    }

    private static long buyUnitPrice(ServerPlayer player, TraderOffer offer) {
        return offer.shop() == TraderOffer.Shop.KINGDOMS
                ? buyUnitPrice(player, offer.price())
                : offer.price();
    }

    private static void sendBuyState(
            ServerPlayer player,
            UUID traderId,
            TraderOffer.Shop shop,
            Component notice,
            boolean successful
    ) {
        List<TraderPayloads.OfferInfo> offers = TraderOffer.forShop(shop).stream()
                .map(offer -> new TraderPayloads.OfferInfo(
                        offer.id(),
                        BuiltInRegistries.ITEM.getKey(offer.item()).toString(),
                        1,
                        buyUnitPrice(player, offer),
                        0,
                        false
                ))
                .toList();
        PacketDistributor.sendToPlayer(
                player,
                new TraderPayloads.S2CShopState(
                        traderId,
                        TradeSessionManager.snapshot(player, traderId).sessionId(),
                        TradeSessionManager.snapshot(player, traderId).acknowledgedSequence(),
                        titleKey(shop),
                        offers,
                        List.of(),
                        notice,
                        successful,
                        0L
                )
        );
    }

    private static String titleKey(TraderOffer.Shop shop) {
        return shop == TraderOffer.Shop.BANKER
                ? "screen.kingdoms.banker.title"
                : "screen.kingdoms.trader.title";
    }

    private static void sendSellState(
            ServerPlayer player,
            SellerTraderEntity trader,
            Component notice,
            boolean successful
    ) {
        MinecraftServer server = player.serverLevel().getServer();
        SellerOfferRotation rotation = SellerOfferRotation.get(server);
        List<TraderPayloads.OfferInfo> sellOffers = new ArrayList<>();
        long refreshAt = trader.expiresAtMillis();
        String titleKey = "screen.kingdoms.seller.title";
        if (trader.traderRole() == SellerTraderRole.PERMANENT) {
            SellerOfferRotation.Window window = rotation.current(server, trader.getUUID());
            TraderCatalogManager.offers(TraderCatalogRole.PERMANENT).stream()
                    .map(offer -> sellInfo(player, trader, offer, offer.minimumPrice(), true))
                    .filter(java.util.Objects::nonNull)
                    .forEach(sellOffers::add);
            window.offers().stream()
                    .map(offer -> sellInfo(player, trader, offer, offer.minimumPrice(), false))
                    .filter(java.util.Objects::nonNull)
                    .forEach(sellOffers::add);
            refreshAt = window.nextRefreshEpochMillis();
        } else if (trader.traderRole() == SellerTraderRole.CONTRABAND) {
            titleKey = "screen.kingdoms.contraband.title";
            TraderCatalogManager.offers(TraderCatalogRole.CONTRABAND).stream()
                    .map(offer -> sellInfo(player, trader, offer, offer.minimumPrice(), false))
                    .filter(java.util.Objects::nonNull)
                    .forEach(sellOffers::add);
        } else {
            titleKey = "screen.kingdoms.wandering.title";
            UUID factionId = trader.targetFactionId().orElse(null);
            if (factionId != null) {
                TraderWorldData.get(server).wandering(factionId).stream()
                        .flatMap(event -> event.offers().stream())
                        .map(rolled -> TraderCatalogManager.offer(TraderCatalogRole.WANDERING, rolled.id())
                                .map(offer -> sellInfo(player, trader, offer, rolled.price(), false))
                                .orElse(null))
                        .filter(java.util.Objects::nonNull)
                        .forEach(sellOffers::add);
            }
        }
        PacketDistributor.sendToPlayer(
                player,
                new TraderPayloads.S2CShopState(
                        trader.getUUID(),
                        TradeSessionManager.snapshot(player, trader.getUUID()).sessionId(),
                        TradeSessionManager.snapshot(player, trader.getUUID()).acknowledgedSequence(),
                        titleKey,
                        List.of(),
                        sellOffers,
                        notice,
                        successful,
                        refreshAt
                )
        );
    }

    private static TraderPayloads.OfferInfo sellInfo(
            ServerPlayer player,
            SellerTraderEntity trader,
            TraderCatalogOffer offer,
            long price,
            boolean permanent
    ) {
        Item item = displayItem(offer).orElse(null);
        if (item == null) {
            return null;
        }
        MinecraftServer server = player.getServer();
        return new TraderPayloads.OfferInfo(
                offer.id(),
                BuiltInRegistries.ITEM.getKey(item).toString(),
                offer.count(),
                sellUnitPrice(player, price),
                SellerOfferRotation.get(server).remainingLimit(
                        server, trader.getUUID(), player.getUUID(), offer.id(), offer.dailyLimit()
                ),
                permanent
        );
    }

    private static void sendSellUnavailable(ServerPlayer player, UUID traderId, Component notice) {
        PacketDistributor.sendToPlayer(
                player,
                new TraderPayloads.S2CShopState(
                        traderId,
                        TradeSessionManager.snapshot(player, traderId).sessionId(),
                        TradeSessionManager.snapshot(player, traderId).acknowledgedSequence(),
                        "screen.kingdoms.seller.title",
                        List.of(),
                        List.of(),
                        notice,
                        false,
                        0L
                )
        );
    }

    private TraderService() {
    }

    private record SellSelection(
            String id,
            Item displayItem,
            @Nullable TagKey<Item> tag,
            long price,
            int dailyLimit,
            boolean permanent
    ) {
        private boolean matches(ItemStack stack) {
            return tag == null ? stack.is(displayItem) : stack.is(tag);
        }
    }
}
