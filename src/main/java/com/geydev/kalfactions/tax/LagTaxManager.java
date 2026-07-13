package com.geydev.kalfactions.tax;

import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.economy.PriceMath;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class LagTaxManager extends SavedData {
    public static final String DATA_NAME = "kingdoms_lagtax";
    public static final Factory<LagTaxManager> FACTORY = new Factory<>(LagTaxManager::new, LagTaxManager::load);

    private static final String TAG_PERIOD_TICKS = "periodTicks";
    private static final String TAG_FACTIONS = "factions";
    private static final String TAG_FACTION_ID = "id";
    private static final String TAG_ACCRUED = "accruedMicros";
    private static final String TAG_EXCESS_INTEGRAL = "excessMsTicksMicros";
    private static final String TAG_LOAD_INTEGRAL = "loadMsTicksMicros";
    private static final String TAG_UNPAID = "unpaidBill";
    private static final String TAG_FROZEN = "frozen";
    private static final String TAG_FREEZE_REASON = "freezeReason";
    private static final String TAG_AUTO_RENEW = "autoRenew";
    private static final String TAG_WARNED_75 = "warned75";
    private static final String TAG_LOADS = "chunkLoads";
    private static final String TAG_LOAD_KEY = "key";
    private static final String TAG_LOAD_EXPIRES = "expiresAt";
    private static final String TAG_LOAD_HOURS = "lastHours";
    private static final String TAG_LOAD_WARNED = "warnedExpiry";

    public enum FreezeReason {
        NONE,
        TAX,
        HARD_CAP
    }

    public static final class ChunkLoad {
        private long expiresAtMillis;
        private int lastHours;
        private boolean warnedExpiry;

        ChunkLoad(long expiresAtMillis, int lastHours, boolean warnedExpiry) {
            this.expiresAtMillis = expiresAtMillis;
            this.lastHours = lastHours;
            this.warnedExpiry = warnedExpiry;
        }

        public long expiresAtMillis() {
            return expiresAtMillis;
        }

        public int lastHours() {
            return lastHours;
        }

        public boolean warnedExpiry() {
            return warnedExpiry;
        }
    }

    public static final class FactionTaxState {
        private long accruedMicros;
        private long excessMsTicksMicros;
        private long loadMsTicksMicros;
        private long unpaidBill;
        private FreezeReason freezeReason = FreezeReason.NONE;
        private boolean autoRenew;
        private boolean warned75;
        private final Map<ClaimKey, ChunkLoad> chunkLoads = new LinkedHashMap<>();

        public long accruedMicros() {
            return accruedMicros;
        }

        public long excessMsTicksMicros() {
            return excessMsTicksMicros;
        }

        public long loadMsTicksMicros() {
            return loadMsTicksMicros;
        }

        public long unpaidBill() {
            return unpaidBill;
        }

        public boolean frozen() {
            return freezeReason != FreezeReason.NONE;
        }

        public FreezeReason freezeReason() {
            return freezeReason;
        }

        public boolean autoRenew() {
            return autoRenew;
        }

        public boolean warned75() {
            return warned75;
        }

        public Map<ClaimKey, ChunkLoad> chunkLoads() {
            return Map.copyOf(chunkLoads);
        }
    }

    private final Map<UUID, FactionTaxState> states = new HashMap<>();
    private long periodTicks;

    public static LagTaxManager get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public synchronized FactionTaxState state(UUID factionId) {
        FactionTaxState state = states.get(factionId);
        return state == null ? new FactionTaxState() : state;
    }

    public synchronized Optional<FactionTaxState> existingState(UUID factionId) {
        return Optional.ofNullable(states.get(factionId));
    }

    public synchronized Set<UUID> frozenFactionIds() {
        Set<UUID> frozen = new java.util.LinkedHashSet<>();
        for (Map.Entry<UUID, FactionTaxState> entry : states.entrySet()) {
            if (entry.getValue().frozen()) {
                frozen.add(entry.getKey());
            }
        }
        return frozen;
    }

    public synchronized long periodTicks() {
        return periodTicks;
    }

    public synchronized void accrue(UUID factionId, long costMicros, long excessMsTicksMicros, long loadMsTicksMicros) {
        FactionTaxState state = states.computeIfAbsent(factionId, ignored -> new FactionTaxState());
        state.accruedMicros = PriceMath.saturatedAdd(state.accruedMicros, costMicros);
        state.excessMsTicksMicros = PriceMath.saturatedAdd(state.excessMsTicksMicros, excessMsTicksMicros);
        state.loadMsTicksMicros = PriceMath.saturatedAdd(state.loadMsTicksMicros, loadMsTicksMicros);
        setDirty();
    }

    public synchronized void advancePeriod(long ticks) {
        periodTicks += ticks;
        setDirty();
    }

    public synchronized void resetPeriod() {
        periodTicks = 0L;
        for (FactionTaxState state : states.values()) {
            state.accruedMicros = 0L;
            state.excessMsTicksMicros = 0L;
            state.loadMsTicksMicros = 0L;
            state.warned75 = false;
        }
        setDirty();
    }

    public synchronized void setUnpaidBill(UUID factionId, long bill) {
        FactionTaxState state = states.computeIfAbsent(factionId, ignored -> new FactionTaxState());
        state.unpaidBill = Math.max(0L, bill);
        setDirty();
    }

    public synchronized void setFreezeReason(UUID factionId, FreezeReason reason) {
        FactionTaxState state = states.computeIfAbsent(factionId, ignored -> new FactionTaxState());
        state.freezeReason = Objects.requireNonNull(reason, "reason");
        setDirty();
    }

    public synchronized void setAutoRenew(UUID factionId, boolean autoRenew) {
        FactionTaxState state = states.computeIfAbsent(factionId, ignored -> new FactionTaxState());
        state.autoRenew = autoRenew;
        setDirty();
    }

    public synchronized boolean markWarned75(UUID factionId) {
        FactionTaxState state = states.computeIfAbsent(factionId, ignored -> new FactionTaxState());
        if (state.warned75) {
            return false;
        }
        state.warned75 = true;
        setDirty();
        return true;
    }

    public synchronized void putChunkLoad(UUID factionId, ClaimKey key, long expiresAtMillis, int lastHours) {
        FactionTaxState state = states.computeIfAbsent(factionId, ignored -> new FactionTaxState());
        state.chunkLoads.put(key, new ChunkLoad(expiresAtMillis, lastHours, false));
        setDirty();
    }

    public synchronized void removeChunkLoad(UUID factionId, ClaimKey key) {
        FactionTaxState state = states.get(factionId);
        if (state != null && state.chunkLoads.remove(key) != null) {
            setDirty();
        }
    }

    public synchronized void markLoadWarned(UUID factionId, ClaimKey key) {
        FactionTaxState state = states.get(factionId);
        if (state == null) {
            return;
        }
        ChunkLoad load = state.chunkLoads.get(key);
        if (load != null && !load.warnedExpiry) {
            load.warnedExpiry = true;
            setDirty();
        }
    }

    public synchronized void removeFaction(UUID factionId) {
        if (states.remove(factionId) != null) {
            setDirty();
        }
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong(TAG_PERIOD_TICKS, periodTicks);
        ListTag factionsTag = new ListTag();
        states.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
            .forEach(entry -> {
                FactionTaxState state = entry.getValue();
                CompoundTag stateTag = new CompoundTag();
                stateTag.putUUID(TAG_FACTION_ID, entry.getKey());
                stateTag.putLong(TAG_ACCRUED, state.accruedMicros);
                stateTag.putLong(TAG_EXCESS_INTEGRAL, state.excessMsTicksMicros);
                stateTag.putLong(TAG_LOAD_INTEGRAL, state.loadMsTicksMicros);
                stateTag.putLong(TAG_UNPAID, state.unpaidBill);
                stateTag.putBoolean(TAG_FROZEN, state.frozen());
                stateTag.putString(TAG_FREEZE_REASON, state.freezeReason.name());
                stateTag.putBoolean(TAG_AUTO_RENEW, state.autoRenew);
                stateTag.putBoolean(TAG_WARNED_75, state.warned75);
                ListTag loadsTag = new ListTag();
                state.chunkLoads.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(loadEntry -> {
                        CompoundTag loadTag = new CompoundTag();
                        loadTag.put(TAG_LOAD_KEY, loadEntry.getKey().save());
                        loadTag.putLong(TAG_LOAD_EXPIRES, loadEntry.getValue().expiresAtMillis);
                        loadTag.putInt(TAG_LOAD_HOURS, loadEntry.getValue().lastHours);
                        loadTag.putBoolean(TAG_LOAD_WARNED, loadEntry.getValue().warnedExpiry);
                        loadsTag.add(loadTag);
                    });
                stateTag.put(TAG_LOADS, loadsTag);
                factionsTag.add(stateTag);
            });
        tag.put(TAG_FACTIONS, factionsTag);
        return tag;
    }

    private static LagTaxManager load(CompoundTag tag, HolderLookup.Provider registries) {
        LagTaxManager manager = new LagTaxManager();
        manager.periodTicks = Math.max(0L, tag.getLong(TAG_PERIOD_TICKS));
        ListTag factionsTag = tag.getList(TAG_FACTIONS, Tag.TAG_COMPOUND);
        for (int index = 0; index < factionsTag.size(); index++) {
            CompoundTag stateTag = factionsTag.getCompound(index);
            if (!stateTag.hasUUID(TAG_FACTION_ID)) {
                continue;
            }
            FactionTaxState state = new FactionTaxState();
            state.accruedMicros = Math.max(0L, stateTag.getLong(TAG_ACCRUED));
            state.excessMsTicksMicros = Math.max(0L, stateTag.getLong(TAG_EXCESS_INTEGRAL));
            state.loadMsTicksMicros = Math.max(0L, stateTag.getLong(TAG_LOAD_INTEGRAL));
            state.unpaidBill = Math.max(0L, stateTag.getLong(TAG_UNPAID));
            FreezeReason reason = FreezeReason.NONE;
            if (stateTag.getBoolean(TAG_FROZEN)) {
                try {
                    reason = FreezeReason.valueOf(stateTag.getString(TAG_FREEZE_REASON));
                } catch (IllegalArgumentException ignored) {
                    reason = FreezeReason.TAX;
                }
            }
            state.freezeReason = reason == FreezeReason.NONE && stateTag.getBoolean(TAG_FROZEN)
                ? FreezeReason.TAX
                : reason;
            state.autoRenew = stateTag.getBoolean(TAG_AUTO_RENEW);
            state.warned75 = stateTag.getBoolean(TAG_WARNED_75);
            ListTag loadsTag = stateTag.getList(TAG_LOADS, Tag.TAG_COMPOUND);
            for (int loadIndex = 0; loadIndex < loadsTag.size(); loadIndex++) {
                CompoundTag loadTag = loadsTag.getCompound(loadIndex);
                ClaimKey.load(loadTag.getCompound(TAG_LOAD_KEY)).ifPresent(key -> state.chunkLoads.put(
                    key,
                    new ChunkLoad(
                        Math.max(0L, loadTag.getLong(TAG_LOAD_EXPIRES)),
                        Math.max(1, loadTag.getInt(TAG_LOAD_HOURS)),
                        loadTag.getBoolean(TAG_LOAD_WARNED)
                    )
                ));
            }
            manager.states.put(stateTag.getUUID(TAG_FACTION_ID), state);
        }
        return manager;
    }
}
