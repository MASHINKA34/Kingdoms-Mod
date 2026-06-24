package com.geydev.kalfactions.war;

import com.geydev.kalfactions.claim.ClaimKey;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
 * A single war between two sides plus the lazily captured chunk snapshots taken while it was active.
 * Each side has a lead faction (the original belligerent) and may gain extra factions when the
 * defender's allies voluntarily join the defending side. War points are tracked per side, not per
 * faction. A faction may take part in at most one war at a time.
 */
public final class War {
    private static final String TAG_ID = "id";
    private static final String TAG_ATTACKER = "attacker";
    private static final String TAG_DEFENDER = "defender";
    private static final String TAG_ATTACKER_SIDE = "attackerSide";
    private static final String TAG_DEFENDER_SIDE = "defenderSide";
    private static final String TAG_STATE = "state";
    private static final String TAG_TYPE = "type";
    private static final String TAG_REASON = "reason";
    private static final String TAG_START_TIME = "startGameTime";
    private static final String TAG_START_EPOCH = "startEpochMillis";
    public static final int MAX_REASON_LENGTH = 120;
    private static final String TAG_ATTACKER_POINTS = "attackerPoints";
    private static final String TAG_DEFENDER_POINTS = "defenderPoints";
    private static final String TAG_SNAPSHOTS = "snapshots";
    private static final String TAG_SNAPSHOT_KEY = "key";
    private static final String TAG_SNAPSHOT_DATA = "data";

    private final UUID id;
    private final UUID attackerFactionId;
    private final UUID defenderFactionId;
    private final Set<UUID> attackerSide;
    private final Set<UUID> defenderSide;
    private final WarType type;
    private final String reason;
    private final long startGameTime;
    private final long startEpochMillis;
    private final Map<ClaimKey, WarChunkSnapshot> snapshots;
    private State state;
    private long attackerPoints;
    private long defenderPoints;

    public War(
        UUID id,
        UUID attackerFactionId,
        UUID defenderFactionId,
        WarType type,
        String reason,
        State state,
        long startGameTime
    ) {
        this(id, attackerFactionId, defenderFactionId, type, reason, state, startGameTime, System.currentTimeMillis());
    }

    public War(
        UUID id,
        UUID attackerFactionId,
        UUID defenderFactionId,
        WarType type,
        String reason,
        State state,
        long startGameTime,
        long startEpochMillis
    ) {
        this(id, attackerFactionId, defenderFactionId, singletonSide(attackerFactionId), singletonSide(defenderFactionId),
            type, reason, state, startGameTime, startEpochMillis, new LinkedHashMap<>());
    }

    private War(
        UUID id,
        UUID attackerFactionId,
        UUID defenderFactionId,
        Set<UUID> attackerSide,
        Set<UUID> defenderSide,
        WarType type,
        String reason,
        State state,
        long startGameTime,
        long startEpochMillis,
        Map<ClaimKey, WarChunkSnapshot> snapshots
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.attackerFactionId = Objects.requireNonNull(attackerFactionId, "attackerFactionId");
        this.defenderFactionId = Objects.requireNonNull(defenderFactionId, "defenderFactionId");
        this.attackerSide = attackerSide;
        this.defenderSide = defenderSide;
        this.attackerSide.add(attackerFactionId);
        this.defenderSide.add(defenderFactionId);
        this.type = type == null ? WarType.DEFAULT : type;
        this.reason = sanitizeReason(reason);
        this.state = Objects.requireNonNull(state, "state");
        this.startGameTime = startGameTime;
        this.startEpochMillis = Math.max(0L, startEpochMillis);
        this.snapshots = snapshots;
    }

    private static String sanitizeReason(String reason) {
        if (reason == null) {
            return "";
        }
        String trimmed = reason.strip();
        return trimmed.length() <= MAX_REASON_LENGTH ? trimmed : trimmed.substring(0, MAX_REASON_LENGTH);
    }

    private static Set<UUID> singletonSide(UUID lead) {
        Set<UUID> side = new LinkedHashSet<>();
        side.add(lead);
        return side;
    }

    public UUID id() {
        return id;
    }

    /** Lead faction of the attacking side (the faction that declared the war). */
    public UUID attackerFactionId() {
        return attackerFactionId;
    }

    /** Lead faction of the defending side (the faction the war was declared on). */
    public UUID defenderFactionId() {
        return defenderFactionId;
    }

    public Set<UUID> attackerSide() {
        return Set.copyOf(attackerSide);
    }

    public Set<UUID> defenderSide() {
        return Set.copyOf(defenderSide);
    }

    /** Every faction taking part in the war, both sides combined. */
    public Set<UUID> participants() {
        Set<UUID> all = new LinkedHashSet<>(attackerSide);
        all.addAll(defenderSide);
        return all;
    }

    public WarType type() {
        return type;
    }

    public String reason() {
        return reason;
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

    public long startEpochMillis() {
        return startEpochMillis;
    }

    public boolean isActive() {
        return state == State.ACTIVE;
    }

    public boolean involves(UUID factionId) {
        return factionId != null && (attackerSide.contains(factionId) || defenderSide.contains(factionId));
    }

    public boolean isLead(UUID factionId) {
        return attackerFactionId.equals(factionId) || defenderFactionId.equals(factionId);
    }

    public Side sideOf(UUID factionId) {
        if (factionId == null) {
            return null;
        }
        if (attackerSide.contains(factionId)) {
            return Side.ATTACKER;
        }
        if (defenderSide.contains(factionId)) {
            return Side.DEFENDER;
        }
        return null;
    }

    public UUID leadOf(Side side) {
        return side == Side.ATTACKER ? attackerFactionId : defenderFactionId;
    }

    /** True when both factions take part and sit on opposing sides (i.e. they are enemies). */
    public boolean opposingSides(UUID factionA, UUID factionB) {
        Side a = sideOf(factionA);
        Side b = sideOf(factionB);
        return a != null && b != null && a != b;
    }

    /** Adds an ally to the defending side. */
    public boolean joinDefenders(UUID factionId) {
        if (factionId == null || attackerSide.contains(factionId)) {
            return false;
        }
        return defenderSide.add(factionId);
    }

    /** Removes a non-lead participant from whichever side it joined. */
    public boolean removeParticipant(UUID factionId) {
        if (factionId == null || isLead(factionId)) {
            return false;
        }
        return attackerSide.remove(factionId) || defenderSide.remove(factionId);
    }

    public long attackerPoints() {
        return attackerPoints;
    }

    public long defenderPoints() {
        return defenderPoints;
    }

    public long pointsForSide(Side side) {
        return side == Side.ATTACKER ? attackerPoints : defenderPoints;
    }

    public long points(UUID factionId) {
        Side side = sideOf(factionId);
        return side == null ? 0L : pointsForSide(side);
    }

    public void addPoints(UUID factionId, long amount) {
        if (amount <= 0L) {
            return;
        }
        Side side = sideOf(factionId);
        if (side == Side.ATTACKER) {
            attackerPoints += amount;
        } else if (side == Side.DEFENDER) {
            defenderPoints += amount;
        }
    }

    void setPointsRaw(long attacker, long defender) {
        attackerPoints = Math.max(0L, attacker);
        defenderPoints = Math.max(0L, defender);
    }

    /** Lead faction of the side opposing the given faction, for display and scoring. */
    public UUID opponentOf(UUID factionId) {
        Side side = sideOf(factionId);
        if (side == Side.ATTACKER) {
            return defenderFactionId;
        }
        if (side == Side.DEFENDER) {
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
        tag.put(TAG_ATTACKER_SIDE, saveSide(attackerSide));
        tag.put(TAG_DEFENDER_SIDE, saveSide(defenderSide));
        tag.putString(TAG_TYPE, type.id());
        tag.putString(TAG_REASON, reason);
        tag.putString(TAG_STATE, state.name());
        tag.putLong(TAG_START_TIME, startGameTime);
        tag.putLong(TAG_START_EPOCH, startEpochMillis);
        tag.putLong(TAG_ATTACKER_POINTS, attackerPoints);
        tag.putLong(TAG_DEFENDER_POINTS, defenderPoints);

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

    private static ListTag saveSide(Set<UUID> side) {
        ListTag list = new ListTag();
        for (UUID factionId : side) {
            list.add(net.minecraft.nbt.NbtUtils.createUUID(factionId));
        }
        return list;
    }

    private static Set<UUID> loadSide(CompoundTag tag, String key, UUID lead) {
        Set<UUID> side = new LinkedHashSet<>();
        side.add(lead);
        ListTag list = tag.getList(key, Tag.TAG_INT_ARRAY);
        for (int index = 0; index < list.size(); index++) {
            side.add(net.minecraft.nbt.NbtUtils.loadUUID(list.get(index)));
        }
        return side;
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

        UUID attacker = tag.getUUID(TAG_ATTACKER);
        UUID defender = tag.getUUID(TAG_DEFENDER);
        War war = new War(
            tag.getUUID(TAG_ID),
            attacker,
            defender,
            loadSide(tag, TAG_ATTACKER_SIDE, attacker),
            loadSide(tag, TAG_DEFENDER_SIDE, defender),
            WarType.fromIdOrDefault(tag.getString(TAG_TYPE)),
            tag.getString(TAG_REASON),
            state,
            tag.getLong(TAG_START_TIME),
            tag.contains(TAG_START_EPOCH, Tag.TAG_LONG) ? tag.getLong(TAG_START_EPOCH) : System.currentTimeMillis(),
            snapshots
        );
        war.setPointsRaw(tag.getLong(TAG_ATTACKER_POINTS), tag.getLong(TAG_DEFENDER_POINTS));
        return Optional.of(war);
    }

    /** Which side of a war a faction belongs to. */
    public enum Side {
        ATTACKER,
        DEFENDER
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
