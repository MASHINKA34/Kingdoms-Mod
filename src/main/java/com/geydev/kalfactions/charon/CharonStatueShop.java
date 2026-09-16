package com.geydev.kalfactions.charon;

import com.geydev.kalfactions.block.CharonStatueBlock;
import com.geydev.kalfactions.command.NumismaticsEconomy;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.faction.FactionRole;
import com.geydev.kalfactions.registry.ModBlocks;
import com.geydev.kalfactions.registry.ModItems;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CharonStatueShop {
    public static final double MAX_USE_DISTANCE_SQUARED = 64.0D;

    public static InteractionResult offer(ServerPlayer player, BlockPos anchor) {
        if (!ModConfigSpec.CHARON_STATUE_SALE_ENABLED.get()) {
            return InteractionResult.PASS;
        }
        if (CharonService.isGhost(player)) {
            return refuse(player, "ghost");
        }
        BlockState state = statueState(player, anchor);
        if (state == null) {
            return InteractionResult.FAIL;
        }
        if (tooFar(player, anchor, state)) {
            return refuse(player, "too_far");
        }
        CharonNetwork.sendStatueOffer(player, anchor.immutable(), price());
        return InteractionResult.SUCCESS;
    }

    public static boolean buy(ServerPlayer player, BlockPos anchor) {
        if (!ModConfigSpec.CHARON_STATUE_SALE_ENABLED.get()) {
            return false;
        }
        if (CharonService.isGhost(player)) {
            refuse(player, "ghost");
            return false;
        }
        BlockState state = statueState(player, anchor);
        if (state == null) {
            return false;
        }
        if (tooFar(player, anchor, state)) {
            refuse(player, "too_far");
            return false;
        }
        ItemStack token = new ItemStack(ModItems.CHARON_TOKEN.get());
        if (!hasInventorySpace(player, token)) {
            refuse(player, "inventory_full");
            return false;
        }

        long price = price();
        long coins = NumismaticsEconomy.balance(player);
        long bank = bankAvailable(player);
        Faction faction = payingFaction(player);
        long treasury = faction == null ? 0L : faction.treasuryBalance();
        Split split = plan(price, coins, bank, treasury);
        if (split == null) {
            player.displayClientMessage(Component.translatable(
                    "message.kingdoms.charon_statue.insufficient",
                    NumismaticsEconomy.format(price),
                    NumismaticsEconomy.format(saturatedAdd(saturatedAdd(coins, bank), treasury))
            ), false);
            return false;
        }
        if (!collect(player, faction, split)) {
            refuse(player, "payment_changed");
            return false;
        }

        if (!player.getInventory().add(token) || !token.isEmpty()) {
            player.getInventory().placeItemBackInInventory(token);
        }
        player.inventoryMenu.broadcastChanges();
        player.serverLevel().playSound(
                null, anchor, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 1.2F);
        player.displayClientMessage(purchaseMessage(price, split), false);
        return true;
    }

    @Nullable
    public static Split plan(long price, long coins, long bank, long treasury) {
        if (price < 0L) {
            return null;
        }
        long remaining = price;
        long fromCoins = Math.min(remaining, Math.max(0L, coins));
        remaining -= fromCoins;
        long fromBank = Math.min(remaining, Math.max(0L, bank));
        remaining -= fromBank;
        long fromTreasury = Math.min(remaining, Math.max(0L, treasury));
        remaining -= fromTreasury;
        return remaining > 0L ? null : new Split(fromCoins, fromBank, fromTreasury);
    }

    private static boolean collect(ServerPlayer player, @Nullable Faction faction, Split split) {
        long fromCoins = split.fromCoins();
        if (fromCoins > 0L) {
            NumismaticsEconomy.Payment payment = NumismaticsEconomy.preparePayment(player, fromCoins);
            if (!payment.ready() || !NumismaticsEconomy.commitPayment(player, payment)) {
                return false;
            }
        }
        long fromBank = split.fromBank();
        if (fromBank > 0L) {
            if (NumismaticsEconomy.bankBalance(player) < fromBank
                    || NumismaticsEconomy.deductBank(player, fromBank) != fromBank) {
                NumismaticsEconomy.give(player, fromCoins);
                return false;
            }
        }
        long fromTreasury = split.fromTreasury();
        if (fromTreasury > 0L) {
            MinecraftServer server = player.getServer();
            UUID factionId = faction == null ? null : faction.id();
            if (server == null
                    || factionId == null
                    || !FactionManager.get(server).withdraw(factionId, fromTreasury).successful()) {
                NumismaticsEconomy.depositBank(player, fromBank);
                NumismaticsEconomy.give(player, fromCoins);
                return false;
            }
        }
        return true;
    }

    private static Component purchaseMessage(long price, Split split) {
        int sources = (split.fromCoins() > 0L ? 1 : 0)
                + (split.fromBank() > 0L ? 1 : 0)
                + (split.fromTreasury() > 0L ? 1 : 0);
        if (sources > 1) {
            return Component.translatable(
                    "message.kingdoms.charon_statue.purchased_split",
                    NumismaticsEconomy.format(price),
                    NumismaticsEconomy.format(split.fromCoins()),
                    NumismaticsEconomy.format(split.fromBank()),
                    NumismaticsEconomy.format(split.fromTreasury())
            );
        }
        return Component.translatable(
                "message.kingdoms.charon_statue.purchased", NumismaticsEconomy.format(price));
    }

    public static long price() {
        return ModConfigSpec.CHARON_STATUE_TOKEN_COST.getAsLong();
    }

    @Nullable
    private static BlockState statueState(ServerPlayer player, BlockPos anchor) {
        if (!player.isAlive() || player.isSpectator() || !(player.level() instanceof ServerLevel level)) {
            return null;
        }
        if (!level.isLoaded(anchor)) {
            return null;
        }
        BlockState state = level.getBlockState(anchor);
        if (!state.is(ModBlocks.CHARON_STATUE.get()) || !CharonStatueBlock.isAnchor(state)) {
            return null;
        }
        return state;
    }

    private static boolean tooFar(ServerPlayer player, BlockPos anchor, BlockState state) {
        return player.distanceToSqr(center(anchor, state.getValue(CharonStatueBlock.FACING)))
                > MAX_USE_DISTANCE_SQUARED;
    }

    private static Vec3 center(BlockPos anchor, Direction facing) {
        BlockPos far = CharonStatueBlock.partPos(anchor, facing, 1, 2, 1);
        return new AABB(anchor).minmax(new AABB(far)).getCenter();
    }

    private static long bankAvailable(ServerPlayer player) {
        return ModConfigSpec.CHARON_STATUE_PAY_FROM_BANK.get() ? NumismaticsEconomy.bankBalance(player) : 0L;
    }

    @Nullable
    private static Faction payingFaction(ServerPlayer player) {
        if (!ModConfigSpec.CHARON_STATUE_PAY_FROM_TREASURY.get()) {
            return null;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return null;
        }
        Faction faction = FactionManager.get(server).getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            return null;
        }
        return faction.roleOf(player.getUUID()).filter(FactionRole::canManageTreasury).isPresent() ? faction : null;
    }

    private static boolean hasInventorySpace(ServerPlayer player, ItemStack product) {
        return player.getInventory().getSlotWithRemainingSpace(product) >= 0
                || player.getInventory().getFreeSlot() >= 0;
    }

    private static InteractionResult refuse(ServerPlayer player, String reason) {
        player.displayClientMessage(Component.translatable("message.kingdoms.charon_statue." + reason), true);
        return InteractionResult.FAIL;
    }

    private static long saturatedAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    public record Split(long fromCoins, long fromBank, long fromTreasury) {
    }

    private CharonStatueShop() {
    }
}
