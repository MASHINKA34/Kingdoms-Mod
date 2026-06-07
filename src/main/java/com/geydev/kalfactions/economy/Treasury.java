package com.geydev.kalfactions.economy;

import net.minecraft.nbt.CompoundTag;

public final class Treasury {
    private static final String TAG_BALANCE = "balance";

    private long balance;

    public Treasury() {
        this(0L);
    }

    public Treasury(long balance) {
        if (balance < 0L) {
            throw new IllegalArgumentException("Treasury balance cannot be negative");
        }
        this.balance = balance;
    }

    public long balance() {
        return balance;
    }

    public boolean canDeposit(long amount) {
        return amount >= 0L && Long.MAX_VALUE - balance >= amount;
    }

    public boolean deposit(long amount) {
        if (!canDeposit(amount)) {
            return false;
        }
        balance += amount;
        return true;
    }

    public boolean canWithdraw(long amount) {
        return amount >= 0L && balance >= amount;
    }

    public boolean withdraw(long amount) {
        if (!canWithdraw(amount)) {
            return false;
        }
        balance -= amount;
        return true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong(TAG_BALANCE, balance);
        return tag;
    }

    public static Treasury load(CompoundTag tag) {
        return new Treasury(Math.max(0L, tag.getLong(TAG_BALANCE)));
    }
}
