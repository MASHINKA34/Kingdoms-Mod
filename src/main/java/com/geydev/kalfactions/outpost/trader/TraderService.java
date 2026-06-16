package com.geydev.kalfactions.outpost.trader;

import com.geydev.kalfactions.command.NumismaticsEconomy;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.entity.OutpostTraderEntity;
import com.geydev.kalfactions.entity.SellerTraderEntity;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.faction.ResearchBonus;
import com.geydev.kalfactions.registry.ModEntities;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.chat.Component;
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

        long price = offer.price();
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

    public static void sell(ServerPlayer player, UUID traderId, String offerId) {
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

        SellOffer offer = SellOffer.byId(offerId).orElse(null);
        if (offer == null) {
            sendSellState(
                    player,
                    traderId,
                    Component.translatable("screen.kingdoms.trader.notice.invalid_offer"),
                    false
            );
            return;
        }

        int count = removeAll(player, offer.item());
        if (count <= 0) {
            sendSellState(
                    player,
                    traderId,
                    Component.translatable("screen.kingdoms.trader.notice.nothing_to_sell"),
                    false
            );
            return;
        }

        FactionManager manager = FactionManager.get(player.serverLevel());
        UUID factionId = manager.getFactionIdForMember(player.getUUID()).orElse(null);
        long base = offer.price() * count;
        long spurs = base;
        if (factionId != null) {
            Faction faction = manager.getFactionById(factionId).orElse(null);
            if (faction != null && faction.hasResearchBonus(ResearchBonus.BUY_RATE)) {
                spurs = base + Math.round(base * 0.10D);
            }
        }
        NumismaticsEconomy.give(player, spurs);
        player.inventoryMenu.broadcastChanges();

        long influenceGained = 0L;
        if (factionId != null) {
            influenceGained = manager.recordSellEarnings(
                    factionId,
                    spurs,
                    ModConfigSpec.INFLUENCE_SPURS_PER_ECON.getAsLong()
            );
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

    private static int removeAll(ServerPlayer player, net.minecraft.world.item.Item item) {
        int removed = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                removed += stack.getCount();
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }
        return removed;
    }

    private static void sendBuyState(
            ServerPlayer player,
            UUID traderId,
            Component notice,
            boolean successful
    ) {
        List<TraderPayloads.OfferInfo> offers = Arrays.stream(TraderOffer.values())
                .map(offer -> new TraderPayloads.OfferInfo(offer.id(), offer.price()))
                .toList();
        List<TraderPayloads.OfferInfo> sellOffers = Arrays.stream(SellOffer.values())
                .map(offer -> new TraderPayloads.OfferInfo(offer.id(), offer.price()))
                .toList();
        PacketDistributor.sendToPlayer(
                player,
                new TraderPayloads.S2CShopState(traderId, offers, List.of(), notice, successful)
        );
    }

    private static void sendSellState(
            ServerPlayer player,
            UUID traderId,
            Component notice,
            boolean successful
    ) {
        List<TraderPayloads.OfferInfo> sellOffers = Arrays.stream(SellOffer.values())
                .map(offer -> new TraderPayloads.OfferInfo(offer.id(), offer.price()))
                .toList();
        PacketDistributor.sendToPlayer(
                player,
                new TraderPayloads.S2CShopState(traderId, List.of(), sellOffers, notice, successful)
        );
    }

    private TraderService() {
    }
}
