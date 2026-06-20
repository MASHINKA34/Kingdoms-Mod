package com.geydev.kalfactions.command;

import dev.ithundxr.createnumismatics.Numismatics;
import dev.ithundxr.createnumismatics.content.backend.BankAccount;
import dev.ithundxr.createnumismatics.content.backend.Coin;
import dev.ithundxr.createnumismatics.content.coins.CoinItem;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Server-side Numismatics transactions. All amounts are expressed in spurs.
 */
public final class NumismaticsEconomy {
    public static final long MAX_SINGLE_PAYOUT = 64L * 250L * Coin.SUN.value;

    private static final Coin[] HIGH_TO_LOW = Arrays.stream(Coin.values())
        .sorted(Comparator.comparingInt((Coin coin) -> coin.value).reversed())
        .toArray(Coin[]::new);
    private static final int MAX_OVERPAY = Arrays.stream(Coin.values())
        .mapToInt(coin -> coin.value)
        .max()
        .orElse(1) - 1;

    public static long balance(ServerPlayer player) {
        long total = 0L;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof CoinItem coinItem) {
                long value = (long) coinItem.coin.value * stack.getCount();
                total = saturatedAdd(total, value);
            }
        }
        return total;
    }

    public static long bankBalance(ServerPlayer player) {
        BankAccount account = Numismatics.BANK.getAccount(player.getUUID());
        return account == null ? 0L : Math.max(0L, account.getBalance());
    }

    public static long deductBank(ServerPlayer player, long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        BankAccount account = Numismatics.BANK.getAccount(player.getUUID());
        if (account == null) {
            return 0L;
        }
        long take = Math.min(amount, Math.max(0L, account.getBalance()));
        if (take <= 0L) {
            return 0L;
        }
        account.setBalance(account.getBalance() - (int) take);
        return take;
    }

    public static Payment preparePayment(ServerPlayer player, long amount) {
        if (amount <= 0L) {
            return Payment.invalid(amount);
        }

        EnumMap<Coin, Integer> available = coinCounts(player);
        long total = totalValue(available);
        if (total < amount) {
            return Payment.insufficient(amount, total);
        }

        long upperBound = Math.min(total, saturatedAdd(amount, MAX_OVERPAY));
        for (long paid = amount; paid <= upperBound; paid++) {
            EnumMap<Coin, Integer> coins = exactCoins(available, paid);
            if (coins != null) {
                return Payment.ready(amount, paid, coins);
            }
        }
        return Payment.insufficient(amount, total);
    }

    public static boolean commitPayment(ServerPlayer player, Payment payment) {
        if (!payment.ready()) {
            return false;
        }

        Map<Coin, Integer> coins = payment.coins();
        if (!CoinItem.extract(player, InteractionHand.MAIN_HAND, coins, true, false)) {
            return false;
        }

        long change = payment.change();
        var changeStacks = stacksFor(change);
        if (!CoinItem.extract(player, InteractionHand.MAIN_HAND, coins, false, false)) {
            return false;
        }
        changeStacks.forEach(player.getInventory()::placeItemBackInInventory);
        return true;
    }

    public static void give(ServerPlayer player, long amount) {
        if (amount < 0L) {
            throw new IllegalArgumentException("Coin amount cannot be negative");
        }
        if (!canGive(amount)) {
            throw new IllegalArgumentException("Coin payout is too large for one transaction");
        }
        stacksFor(amount).forEach(player.getInventory()::placeItemBackInInventory);
    }

    public static boolean canGive(long amount) {
        return amount >= 0L && amount <= MAX_SINGLE_PAYOUT;
    }

    public static Component format(long spurs) {
        long lastTwo = Math.floorMod(spurs, 100L);
        long last = Math.floorMod(spurs, 10L);
        String key;
        if (last == 1L && lastTwo != 11L) {
            key = "kingdoms.currency.spurs.one";
        } else if (last >= 2L && last <= 4L && (lastTwo < 12L || lastTwo > 14L)) {
            key = "kingdoms.currency.spurs.few";
        } else {
            key = "kingdoms.currency.spurs.many";
        }
        return Component.translatable(key, spurs);
    }

    private static EnumMap<Coin, Integer> coinCounts(ServerPlayer player) {
        EnumMap<Coin, Integer> counts = new EnumMap<>(Coin.class);
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof CoinItem coinItem) {
                counts.merge(coinItem.coin, stack.getCount(), NumismaticsEconomy::saturatedIntAdd);
            }
        }
        return counts;
    }

    private static long totalValue(Map<Coin, Integer> counts) {
        long total = 0L;
        for (Map.Entry<Coin, Integer> entry : counts.entrySet()) {
            total = saturatedAdd(total, (long) entry.getKey().value * entry.getValue());
        }
        return total;
    }

    /**
     * Greedy is exact for Numismatics' divisible denomination chain
     * (1, 8, 16, 64, 512, 4096), including bounded inventories.
     */
    private static EnumMap<Coin, Integer> exactCoins(Map<Coin, Integer> available, long target) {
        EnumMap<Coin, Integer> result = new EnumMap<>(Coin.class);
        long remaining = target;
        for (Coin coin : HIGH_TO_LOW) {
            int count = (int) Math.min(available.getOrDefault(coin, 0), remaining / coin.value);
            if (count > 0) {
                result.put(coin, count);
                remaining -= (long) count * coin.value;
            }
        }
        return remaining == 0L ? result : null;
    }

    private static java.util.List<ItemStack> stacksFor(long amount) {
        java.util.ArrayList<ItemStack> stacks = new java.util.ArrayList<>();
        long remaining = amount;
        for (Coin coin : HIGH_TO_LOW) {
            long count = remaining / coin.value;
            remaining %= coin.value;
            while (count > 0L) {
                ItemStack probe = coin.asStack();
                int stackSize = (int) Math.min(count, probe.getMaxStackSize());
                probe.setCount(stackSize);
                stacks.add(probe);
                count -= stackSize;
            }
        }
        if (remaining != 0L) {
            throw new IllegalStateException("Numismatics has no spur denomination");
        }
        return stacks;
    }

    private static long saturatedAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    private static int saturatedIntAdd(int left, int right) {
        return Integer.MAX_VALUE - left < right ? Integer.MAX_VALUE : left + right;
    }

    public record Payment(Status status, long requested, long paid, long available, Map<Coin, Integer> coins) {
        public Payment {
            coins = Map.copyOf(coins);
        }

        public boolean ready() {
            return status == Status.READY;
        }

        public long change() {
            return ready() ? paid - requested : 0L;
        }

        private static Payment ready(long requested, long paid, Map<Coin, Integer> coins) {
            return new Payment(Status.READY, requested, paid, paid, coins);
        }

        private static Payment insufficient(long requested, long available) {
            return new Payment(Status.INSUFFICIENT_FUNDS, requested, 0L, available, Map.of());
        }

        private static Payment invalid(long requested) {
            return new Payment(Status.INVALID_AMOUNT, requested, 0L, 0L, Map.of());
        }
    }

    public enum Status {
        READY,
        INVALID_AMOUNT,
        INSUFFICIENT_FUNDS
    }

    private NumismaticsEconomy() {
    }
}
