package com.geydev.kalfactions.outpost.trader;

import com.geydev.kalfactions.command.NumismaticsEconomy;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.entity.OutpostTraderEntity;
import com.geydev.kalfactions.entity.SellerTraderEntity;
import com.geydev.kalfactions.economy.PriceMath;
import com.geydev.kalfactions.faction.FactionBonus;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.protection.FactionAccess;
import com.geydev.kalfactions.registry.ModEntities;
import java.util.Arrays;
import java.util.List;
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
        SellerTraderEntity trader = ModEntities.SELLER_TRADER.get().create(level);
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

    public static boolean isMarked(Entity entity) {
        return entity instanceof OutpostTraderEntity || entity instanceof SellerTraderEntity;
    }

    public static void open(ServerPlayer player, OutpostTraderEntity trader) {
        if (!isAvailable(player, trader)) {
            sendBuyState(
                    player,
                    trader.getUUID(),
                    Component.translatable("screen.kingdoms.trader.notice.unavailable"),
                    false
            );
            return;
        }
        sendBuyState(player, trader.getUUID(), Component.empty(), true);
    }

    public static void openSeller(ServerPlayer player, SellerTraderEntity trader) {
        if (!isAvailable(player, trader)) {
            sendSellState(
                    player,
                    trader.getUUID(),
                    Component.translatable("screen.kingdoms.trader.notice.unavailable"),
                    false
            );
            return;
        }
        sendSellState(player, trader.getUUID(), Component.empty(), true);
    }

    public static void refreshSeller(ServerPlayer player, UUID traderId) {
        Entity entity = player.serverLevel().getEntity(traderId);
        if (!(entity instanceof SellerTraderEntity trader) || !isAvailable(player, trader)) {
            sendSellState(
                    player,
                    traderId,
                    Component.translatable("screen.kingdoms.trader.notice.too_far"),
                    false
            );
            return;
        }
        sendSellState(player, traderId, Component.empty(), true);
    }

    public static void buy(ServerPlayer player, UUID traderId, String offerId) {
        Entity entity = player.serverLevel().getEntity(traderId);
        if (!(entity instanceof OutpostTraderEntity trader)) {
            sendBuyState(
                    player,
                    traderId,
                    Component.translatable("screen.kingdoms.trader.notice.unavailable"),
                    false
            );
            return;
        }
        if (!isAvailable(player, trader)) {
            sendBuyState(
                    player,
                    traderId,
                    Component.translatable("screen.kingdoms.trader.notice.too_far"),
                    false
            );
            return;
        }

        TraderOffer offer = TraderOffer.byId(offerId).orElse(null);
        if (offer == null) {
            sendBuyState(
                    player,
                    traderId,
                    Component.translatable("screen.kingdoms.trader.notice.invalid_offer"),
                    false
            );
            return;
        }

        long price = buyUnitPrice(player, offer.price());
        ItemStack product = new ItemStack(offer.item());
        if (!hasInventorySpace(player, product)) {
            sendBuyState(
                    player,
                    traderId,
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
                Component.translatable(
                        "screen.kingdoms.trader.notice.purchased",
                        offer.item().getName(new ItemStack(offer.item())),
                        NumismaticsEconomy.format(price)
                ),
                true
        );
    }

    private static boolean isAvailable(ServerPlayer player, Entity trader) {
        return trader.isAlive()
                && !trader.isRemoved()
                && trader.level() == player.level()
                && player.distanceToSqr(trader) <= MAX_DISTANCE_SQUARED;
    }

    private static boolean hasInventorySpace(ServerPlayer player, ItemStack product) {
        return player.getInventory().getSlotWithRemainingSpace(product) >= 0
                || player.getInventory().getFreeSlot() >= 0;
    }

    public static void sell(ServerPlayer player, UUID traderId, String offerId, int requestedCount) {
        Entity entity = player.serverLevel().getEntity(traderId);
        if (!(entity instanceof SellerTraderEntity trader) || !isAvailable(player, trader)) {
            sendSellState(
                    player,
                    traderId,
                    Component.translatable("screen.kingdoms.trader.notice.too_far"),
                    false
            );
            return;
        }
        if (requestedCount <= 0) {
            sendSellState(
                    player,
                    traderId,
                    Component.translatable("screen.kingdoms.trader.notice.invalid_offer"),
                    false
            );
            return;
        }

        MinecraftServer server = player.serverLevel().getServer();
        SellerOfferRotation rotation = SellerOfferRotation.get(server);
        SellerOfferRotation.Window window = rotation.current(server);
        SellOffer offer = window.offer(offerId).orElse(null);
        if (offer == null) {
            sendSellState(
                    player,
                    traderId,
                    Component.translatable("screen.kingdoms.trader.notice.invalid_offer"),
                    false
            );
            return;
        }

        int owned = countItems(player, offer.item());
        if (owned <= 0) {
            sendSellState(
                    player,
                    traderId,
                    Component.translatable("screen.kingdoms.trader.notice.nothing_to_sell"),
                    false
            );
            return;
        }
        int remainingLimit = rotation.remainingLimit(server, player.getUUID(), offer);
        if (remainingLimit <= 0) {
            sendSellState(
                    player,
                    traderId,
                    Component.translatable("screen.kingdoms.trader.notice.sell_limit_reached"),
                    false
            );
            return;
        }

        int count = Math.min(Math.min(owned, remainingLimit), requestedCount);
        int removed = removeUpTo(player, offer.item(), count);
        if (removed <= 0) {
            sendSellState(
                    player,
                    traderId,
                    Component.translatable("screen.kingdoms.trader.notice.nothing_to_sell"),
                    false
            );
            return;
        }
        count = removed;

        FactionManager manager = FactionManager.get(player.serverLevel());
        UUID factionId = manager.getFactionIdForMember(player.getUUID()).orElse(null);
        long spurs = PriceMath.saturatedMultiply(sellUnitPrice(player, offer.price()), count);
        NumismaticsEconomy.give(player, spurs);
        rotation.recordSale(server, player.getUUID(), offer, count);
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
                        new ItemStack(offer.item()).getHoverName(),
                        NumismaticsEconomy.format(spurs),
                        influenceGained
                )
                : Component.translatable(
                        "screen.kingdoms.trader.notice.sold",
                        count,
                        new ItemStack(offer.item()).getHoverName(),
                        NumismaticsEconomy.format(spurs)
                );
        sendSellState(player, traderId, notice, true);
    }

    private static int countItems(ServerPlayer player, net.minecraft.world.item.Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int removeUpTo(ServerPlayer player, net.minecraft.world.item.Item item, int limit) {
        int removed = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (removed >= limit) {
                break;
            }
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) {
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

    private static void sendBuyState(
            ServerPlayer player,
            UUID traderId,
            Component notice,
            boolean successful
    ) {
        List<TraderPayloads.OfferInfo> offers = Arrays.stream(TraderOffer.values())
                .map(offer -> new TraderPayloads.OfferInfo(offer.id(), buyUnitPrice(player, offer.price()), 0))
                .toList();
        PacketDistributor.sendToPlayer(
                player,
                new TraderPayloads.S2CShopState(traderId, offers, List.of(), notice, successful, 0L)
        );
    }

    private static void sendSellState(
            ServerPlayer player,
            UUID traderId,
            Component notice,
            boolean successful
    ) {
        MinecraftServer server = player.serverLevel().getServer();
        SellerOfferRotation rotation = SellerOfferRotation.get(server);
        SellerOfferRotation.Window window = rotation.current(server);
        List<TraderPayloads.OfferInfo> sellOffers = window.offers().stream()
                .map(offer -> new TraderPayloads.OfferInfo(
                        offer.id(),
                        sellUnitPrice(player, offer.price()),
                        rotation.remainingLimit(server, player.getUUID(), offer)
                ))
                .toList();
        PacketDistributor.sendToPlayer(
                player,
                new TraderPayloads.S2CShopState(
                        traderId,
                        List.of(),
                        sellOffers,
                        notice,
                        successful,
                        window.nextRefreshEpochMillis()
                )
        );
    }

    private TraderService() {
    }
}
