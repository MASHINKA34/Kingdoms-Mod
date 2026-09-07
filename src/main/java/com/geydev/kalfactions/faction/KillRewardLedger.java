package com.geydev.kalfactions.faction;

import com.geydev.kalfactions.data.SavedDataFormat;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class KillRewardLedger extends SavedData {
    public static final String DATA_NAME = "kingdoms_kill_rewards";
    public static final Factory<KillRewardLedger> FACTORY = new Factory<>(KillRewardLedger::new, KillRewardLedger::load);
    public static final long DAY_MILLIS = 86_400_000L;
    private static final SavedDataFormat FORMAT = new SavedDataFormat(1);
    private final Map<KillPair, Deque<Long>> playerAwards = new LinkedHashMap<>();
    private final Map<UUID, MobRewards> mobRewards = new LinkedHashMap<>();
    private long nextCleanupMillis;

    public static KillRewardLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public synchronized long awardPlayerKill(
            UUID killer, UUID victim, long now, long window, int cap, LongSupplier grant
    ) {
        cleanup(now, window);
        KillPair pair = new KillPair(killer, victim);
        Deque<Long> awards = playerAwards.computeIfAbsent(pair, ignored -> new ArrayDeque<>());
        if (awards.removeIf(time -> expired(time, now, window))) {
            setDirty();
        }
        if (cap > 0 && awards.size() >= cap) {
            return 0L;
        }
        long granted = grant.getAsLong();
        if (granted > 0L && cap > 0) {
            awards.addLast(now);
            setDirty();
        }
        if (awards.isEmpty()) {
            playerAwards.remove(pair);
        }
        return granted;
    }

    public synchronized long awardMobKill(
            UUID player, long now, int killsPerAward, long reward, long dailyCap, LongUnaryOperator grant
    ) {
        if (killsPerAward <= 0 || reward <= 0L) {
            return 0L;
        }
        MobRewards rewards = mobRewards.computeIfAbsent(player, ignored -> new MobRewards());
        rewards.awards.removeIf(award -> expired(award.time(), now, DAY_MILLIS));
        rewards.lastKill = now;
        rewards.progress = Math.min(rewards.progress, killsPerAward - 1) + 1;
        setDirty();
        if (rewards.progress < killsPerAward) {
            return 0L;
        }
        rewards.progress = 0;
        long remaining = dailyCap <= 0L ? Long.MAX_VALUE : dailyCap;
        for (MobAward award : rewards.awards) {
            remaining = Math.max(0L, remaining - Math.min(remaining, award.amount()));
        }
        long requested = Math.min(reward, remaining);
        if (requested == 0L) {
            return 0L;
        }
        long granted = grant.applyAsLong(requested);
        if (granted > 0L && dailyCap > 0L) {
            rewards.awards.addLast(new MobAward(now, granted));
        }
        return granted;
    }

    public synchronized void cleanup(long now, long playerWindow) {
        if (now < nextCleanupMillis) {
            return;
        }
        nextCleanupMillis = now + 60_000L;
        boolean changed = false;
        for (Deque<Long> awards : playerAwards.values()) {
            changed |= awards.removeIf(time -> expired(time, now, playerWindow));
        }
        changed |= playerAwards.values().removeIf(Deque::isEmpty);
        for (MobRewards rewards : mobRewards.values()) {
            changed |= rewards.awards.removeIf(award -> expired(award.time(), now, DAY_MILLIS));
        }
        changed |= mobRewards.values().removeIf(rewards ->
                rewards.awards.isEmpty() && expired(rewards.lastKill, now, DAY_MILLIS));
        if (changed) {
            setDirty();
        }
    }

    private static boolean expired(long time, long now, long window) {
        return now >= time && now - time >= window;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        FORMAT.stamp(tag);
        ListTag pairs = new ListTag();
        playerAwards.forEach((pair, awards) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("killer", pair.killer());
            entry.putUUID("victim", pair.victim());
            entry.putLongArray("times", awards.stream().mapToLong(Long::longValue).toArray());
            pairs.add(entry);
        });
        tag.put("playerAwards", pairs);
        ListTag mobs = new ListTag();
        mobRewards.forEach((player, rewards) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", player);
            entry.putInt("progress", rewards.progress);
            entry.putLong("lastKill", rewards.lastKill);
            ListTag awards = new ListTag();
            for (MobAward award : rewards.awards) {
                CompoundTag record = new CompoundTag();
                record.putLong("time", award.time());
                record.putLong("amount", award.amount());
                awards.add(record);
            }
            entry.put("awards", awards);
            mobs.add(entry);
        });
        tag.put("mobRewards", mobs);
        return tag;
    }

    private static KillRewardLedger load(CompoundTag saved, HolderLookup.Provider registries) {
        CompoundTag tag = FORMAT.upgrade(saved);
        KillRewardLedger ledger = new KillRewardLedger();
        for (Tag value : tag.getList("playerAwards", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            if (!entry.hasUUID("killer") || !entry.hasUUID("victim")) {
                continue;
            }
            Deque<Long> awards = new ArrayDeque<>();
            for (long time : entry.getLongArray("times")) {
                if (time >= 0L) {
                    awards.addLast(time);
                }
            }
            ledger.playerAwards.put(new KillPair(entry.getUUID("killer"), entry.getUUID("victim")), awards);
        }
        for (Tag value : tag.getList("mobRewards", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            if (!entry.hasUUID("player")) {
                continue;
            }
            MobRewards rewards = new MobRewards();
            rewards.progress = Math.max(0, entry.getInt("progress"));
            rewards.lastKill = Math.max(0L, entry.getLong("lastKill"));
            for (Tag awardValue : entry.getList("awards", Tag.TAG_COMPOUND)) {
                CompoundTag award = (CompoundTag) awardValue;
                long time = award.getLong("time");
                long amount = award.getLong("amount");
                if (time >= 0L && amount > 0L) {
                    rewards.awards.addLast(new MobAward(time, amount));
                }
            }
            ledger.mobRewards.put(entry.getUUID("player"), rewards);
        }
        if (FORMAT.outdated(saved)) {
            ledger.setDirty();
        }
        return ledger;
    }

    private record KillPair(UUID killer, UUID victim) {
    }

    private record MobAward(long time, long amount) {
    }

    private static final class MobRewards {
        private int progress;
        private long lastKill;
        private final Deque<MobAward> awards = new ArrayDeque<>();
    }
}
