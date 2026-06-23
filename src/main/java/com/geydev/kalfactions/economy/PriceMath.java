package com.geydev.kalfactions.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PriceMath {
    private static final BigDecimal LONG_MAX = BigDecimal.valueOf(Long.MAX_VALUE);

    private PriceMath() {
    }

    public static long claimPrice(
        int currentClaimCount,
        int freeClaims,
        long baseCost,
        double growth,
        double discount
    ) {
        requireNonNegative(currentClaimCount, "currentClaimCount");
        requireNonNegative(freeClaims, "freeClaims");
        requireNonNegative(baseCost, "baseCost");
        requireFiniteRange(growth, 0.0D, Double.MAX_VALUE, "growth");
        requireFiniteRange(discount, 0.0D, 1.0D, "discount");

        if (currentClaimCount < freeClaims || baseCost == 0L || discount == 1.0D) {
            return 0L;
        }

        int paidClaimIndex = currentClaimCount - freeClaims;
        BigDecimal multiplier = BigDecimal.ONE.add(
            BigDecimal.valueOf(growth).multiply(BigDecimal.valueOf(paidClaimIndex))
        );
        BigDecimal price = BigDecimal.valueOf(baseCost).multiply(multiplier);
        price = price.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(discount)));
        if (price.compareTo(LONG_MAX) >= 0) {
            return Long.MAX_VALUE;
        }
        return price.setScale(0, RoundingMode.CEILING).longValueExact();
    }

    public static long refund(long paidPrice, double refundPercent) {
        requireNonNegative(paidPrice, "paidPrice");
        requireFiniteRange(refundPercent, 0.0D, 1.0D, "refundPercent");
        return BigDecimal.valueOf(paidPrice)
            .multiply(BigDecimal.valueOf(refundPercent))
            .setScale(0, RoundingMode.FLOOR)
            .longValueExact();
    }

    public static long saturatedMultiply(long left, long right) {
        requireNonNegative(left, "left");
        requireNonNegative(right, "right");
        if (left == 0L || right == 0L) {
            return 0L;
        }
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    public static long saturatedAdd(long left, long right) {
        requireNonNegative(left, "left");
        requireNonNegative(right, "right");
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    public static long percentageCeil(long amount, double percent) {
        requireNonNegative(amount, "amount");
        requireFiniteRange(percent, 0.0D, Double.MAX_VALUE, "percent");
        if (amount == 0L || percent == 0.0D) {
            return 0L;
        }
        BigDecimal value = BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(percent));
        if (value.compareTo(LONG_MAX) >= 0) {
            return Long.MAX_VALUE;
        }
        return value.setScale(0, RoundingMode.CEILING).longValueExact();
    }

    public static long increaseByPercentCeil(long amount, double percent) {
        return saturatedAdd(amount, percentageCeil(amount, percent));
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " cannot be negative");
        }
    }

    private static void requireFiniteRange(double value, double minimum, double maximum, String name) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " is outside the allowed range");
        }
    }
}
