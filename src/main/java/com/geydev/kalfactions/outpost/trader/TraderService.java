package com.geydev.kalfactions.outpost.trader;

import com.geydev.kalfactions.command.NumismaticsEconomy;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class TraderService {
    public static final String MARKER_KEY = "kingdoms_outpost_trader";
    private static final double MAX_DISTANCE_SQUARED = 64.0D;

    public static void configure(WanderingTrader trader) {
        trader.getPersistentData().putBoolean(MARKER_KEY, true);
        trader.setNoAi(true);
        trader.setInvulnerable(true);
        trader.setPersistenceRequired();
        trader.setDespawnDelay(0);
        trader.setCustomName(Component.translatable("entity.kingdoms.outpost_trader"));
        trader.setCustomNameVisible(true);
    }

    public static boolean isMarked(Entity entity) {
        return entity instanceof WanderingTrader
                && entity.getPersistentData().getBoolean(MARKER_KEY);
    }

    public static void open(ServerPlayer player, WanderingTrader trader) {
        if (!isAvailable(player, trader)) {
            sendState(
                    player,
                    trader.getUUID(),
                    Component.translatable("screen.kingdoms.trader.notice.unavailable"),
                    false
            );
            return;
        }
        sendState(player, trader.getUUID(), Component.empty(), true);
    }

    public static void buy(ServerPlayer player, UUID traderId, String offerId) {
        Entity entity = player.serverLevel().getEntity(traderId);
        if (!(entity instanceof WanderingTrader trader) || !isMarked(trader)) {
            sendState(
                    player,
                    traderId,
                    Component.translatable("screen.kingdoms.trader.notice.unavailable"),
                    false
            );
            return;
        }
        if (!isAvailable(player, trader)) {
            sendState(
                    player,
                    traderId,
                    Component.translatable("screen.kingdoms.trader.notice.too_far"),
                    false
            );
            return;
        }

        TraderOffer offer = TraderOffer.byId(offerId).orElse(null);
        if (offer == null) {
            sendState(
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
            sendState(
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
                sendState(
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
                sendState(
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
        sendState(
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

    private static boolean isAvailable(ServerPlayer player, WanderingTrader trader) {
        return isMarked(trader)
                && trader.isAlive()
                && !trader.isRemoved()
                && trader.level() == player.level()
                && player.distanceToSqr(trader) <= MAX_DISTANCE_SQUARED;
    }

    private static boolean hasInventorySpace(ServerPlayer player, ItemStack product) {
        return player.getInventory().getSlotWithRemainingSpace(product) >= 0
                || player.getInventory().getFreeSlot() >= 0;
    }

    private static void sendState(
            ServerPlayer player,
            UUID traderId,
            Component notice,
            boolean successful
    ) {
        List<TraderPayloads.OfferInfo> offers = Arrays.stream(TraderOffer.values())
                .map(offer -> new TraderPayloads.OfferInfo(offer.id(), offer.price()))
                .toList();
        PacketDistributor.sendToPlayer(
                player,
                new TraderPayloads.S2CShopState(traderId, offers, notice, successful)
        );
    }

    private TraderService() {
    }
}
