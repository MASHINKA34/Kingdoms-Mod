package com.geydev.kalfactions.war;

import com.geydev.kalfactions.claim.ClaimKey;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * A single war between two factions plus the lazily captured chunk snapshots taken while it was
 * active. One faction may take part in at most one war at a time (v1).
 */
public final class War {
    private static final String TAG_ID = "id";
    private static final String TAG_ATTACKER = "attacker";
    private static final String TAG_DEFENDER = "defender";
    private static final String TAG_STATE = "state";
    private static final String TAG_START_TIME = "startGameTime";
    private static final String TAG_SNAPSHOTS = "snapshots";
    private static final String TAG_SNAPSHOT_KEY = "key";
    private static final String TAG_SNAPSHOT_DATA = "data";

    private final UUID id;
    private final UUID attackerFactionId;
    private final UUID defenderFactionId;
    private final long startGameTime;
    private final Map<ClaimKey, WarChunkSnapshot> snapshots;
    private State state;

    public War(UUID id, UUID attackerFactionId, UUID defenderFactionId, State state, long startGameTime) {
        this(id, attackerFactionId, defenderFactionId, state, startGameTime, new LinkedHashMap<>());
    }

    private War(
        UUID id,
        UUID attackerFactionId,
        UUID defenderFactionId,
        State state,
        long startGameTime,
        Map<ClaimKey, WarChunkSnapshot> snapshots
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.attackerFactionId = Objects.requireNonNull(attackerFactionId, "attackerFactionId");
        this.defenderFactionId = Objects.requireNonNull(defenderFactionId, "defenderFactionId");
        this.state = Objects.requireNonNull(state, "state");
        this.startGameTime = startGameTime;
        this.snapshots = snapshots;
    }

    public UUID id() {
        return id;
    }

    public UUID attackerFactionId() {
        return attackerFactionId;
    }

    public UUID defenderFactionId() {
        return defenderFactionId;
    }

    public State state() {
        return state;
    }

    public void setState(State state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    public long startGameTime() {
        return startGameTime;
    }

    public boolean isActive() {
        return state == State.ACTIVE;
    }

    public boolean involves(UUID factionId) {
        return attackerFactionId.equals(factionId) || defenderFactionId.equals(factionId);
    }

    public UUID opponentOf(UUID factionId) {
        if (attackerFactionId.equals(factionId)) {
            return defenderFactionId;
        }
        if (defenderFactionId.equals(factionId)) {
            return attackerFactionId;
        }
        return null;
    }

    public int snapshotCount() {
        return snapshots.size();
    }

    public boolean hasSnapshot(ClaimKey key) {
        return snapshots.containsKey(key);
    }

    public void putSnapshot(ClaimKey key, WarChunkSnapshot snapshot) {
        snapshots.put(key, snapshot);
    }

    public WarChunkSnapshot removeSnapshot(ClaimKey key) {
        return snapshots.remove(key);
    }

    public boolean snapshotsEmpty() {
        return snapshots.isEmpty();
    }

    /** Snapshot keys, copied so callers can iterate while the underlying map is drained during rollback. */
    public Set<ClaimKey> snapshotKeys() {
        return Set.copyOf(snapshots.keySet());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_ID, id);
        tag.putUUID(TAG_ATTACKER, attackerFactionId);
        tag.putUUID(TAG_DEFENDER, defenderFactionId);
        tag.putString(TAG_STATE, state.name());
        tag.putLong(TAG_START_TIME, startGameTime);

        ListTag snapshotsTag = new ListTag();
        for (Map.Entry<ClaimKey, WarChunkSnapshot> entry : snapshots.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.put(TAG_SNAPSHOT_KEY, entry.getKey().save());
            entryTag.put(TAG_SNAPSHOT_DATA, entry.getValue().save());
            snapshotsTag.add(entryTag);
        }
        tag.put(TAG_SNAPSHOTS, snapshotsTag);
        return tag;
    }

    public static Optional<War> load(CompoundTag tag) {
        if (!tag.hasUUID(TAG_ID) || !tag.hasUUID(TAG_ATTACKER) || !tag.hasUUID(TAG_DEFENDER)) {
            return Optional.empty();
        }
        State state;
        try {
            state = State.valueOf(tag.getString(TAG_STATE).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }

        Map<ClaimKey, WarChunkSnapshot> snapshots = new LinkedHashMap<>();
        ListTag snapshotsTag = tag.getList(TAG_SNAPSHOTS, Tag.TAG_COMPOUND);
        for (int index = 0; index < snapshotsTag.size(); index++) {
            CompoundTag entryTag = snapshotsTag.getCompound(index);
            Optional<ClaimKey> key = ClaimKey.load(entryTag.getCompound(TAG_SNAPSHOT_KEY));
            if (key.isEmpty()) {
                return Optional.empty();
            }
            snapshots.put(key.get(), WarChunkSnapshot.load(entryTag.getCompound(TAG_SNAPSHOT_DATA)));
        }

        return Optional.of(new War(
            tag.getUUID(TAG_ID),
            tag.getUUID(TAG_ATTACKER),
            tag.getUUID(TAG_DEFENDER),
            state,
            tag.getLong(TAG_START_TIME),
            snapshots
        ));
    }

    public enum State {
        /** Reserved for a future declare/accept handshake; v1 wars start {@link #ACTIVE}. */
        DECLARED,
        /** Combat is live: belligerents may break/place in each other's claims and snapshots are taken. */
        ACTIVE,
        /** Combat is over; remaining snapshots are being rolled back a few chunks per tick. */
        ENDING,
        /** Fully rolled back; the war is about to be removed. */
        ENDED
    }
}
