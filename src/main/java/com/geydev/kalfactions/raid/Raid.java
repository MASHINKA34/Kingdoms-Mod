package com.geydev.kalfactions.raid;

import com.geydev.kalfactions.claim.ClaimKey;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

public final class Raid {
    private static final String TAG_ID = "id";
    private static final String TAG_FACTION = "faction";
    private static final String TAG_TARGET_TYPE = "targetType";
    private static final String TAG_TARGET_CLAIM = "targetClaim";
    private static final String TAG_TARGET_POS = "targetPos";
    private static final String TAG_OUTPOST_ID = "outpostId";
    private static final String TAG_STATE = "state";
    private static final String TAG_WARNING_ENDS_AT = "warningEndsAt";
    private static final String TAG_ACTIVE_ENDS_AT = "activeEndsAt";
    private static final String TAG_ORIGINAL_RAIDERS = "originalRaiders";
    private static final String TAG_RAIDERS = "raiders";
    private static final String TAG_LAST_NOTICE = "lastNotice";

    private final UUID id;
    private final UUID factionId;
    private final TargetType targetType;
    private final ClaimKey targetClaim;
    private final BlockPos targetPos;
    private final UUID outpostId;
    private final Set<UUID> raiderIds;
    private State state;
    private long warningEndsAtEpochMillis;
    private long activeEndsAtEpochMillis;
    private int originalRaiderCount;
    private int lastNotifiedRemainingSeconds;

    Raid(
        UUID id,
        UUID factionId,
        TargetType targetType,
        ClaimKey targetClaim,
        BlockPos targetPos,
        UUID outpostId,
        State state,
        long warningEndsAtEpochMillis,
        long activeEndsAtEpochMillis,
        int originalRaiderCount,
        Set<UUID> raiderIds,
        int lastNotifiedRemainingSeconds
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.factionId = Objects.requireNonNull(factionId, "factionId");
        this.targetType = Objects.requireNonNull(targetType, "targetType");
        this.targetClaim = Objects.requireNonNull(targetClaim, "targetClaim");
        this.targetPos = Objects.requireNonNull(targetPos, "targetPos").immutable();
        this.outpostId = outpostId;
        this.state = Objects.requireNonNull(state, "state");
        this.warningEndsAtEpochMillis = Math.max(0L, warningEndsAtEpochMillis);
        this.activeEndsAtEpochMillis = Math.max(0L, activeEndsAtEpochMillis);
        this.originalRaiderCount = Math.max(0, originalRaiderCount);
        this.raiderIds = new LinkedHashSet<>(Objects.requireNonNull(raiderIds, "raiderIds"));
        this.lastNotifiedRemainingSeconds = lastNotifiedRemainingSeconds;
    }

    static Raid warning(
        UUID id,
        UUID factionId,
        TargetType targetType,
        ClaimKey targetClaim,
        BlockPos targetPos,
        UUID outpostId,
        long warningEndsAtEpochMillis
    ) {
        return new Raid(
            id,
            factionId,
            targetType,
            targetClaim,
            targetPos,
            outpostId,
            State.WARNING,
            warningEndsAtEpochMillis,
            0L,
            0,
            Set.of(),
            -1
        );
    }

    public UUID id() {
        return id;
    }

    public UUID factionId() {
        return factionId;
    }

    public TargetType targetType() {
        return targetType;
    }

    public ClaimKey targetClaim() {
        return targetClaim;
    }

    public BlockPos targetPos() {
        return targetPos;
    }

    public UUID outpostId() {
        return outpostId;
    }

    public State state() {
        return state;
    }

    public long warningEndsAtEpochMillis() {
        return warningEndsAtEpochMillis;
    }

    public long activeEndsAtEpochMillis() {
        return activeEndsAtEpochMillis;
    }

    public int originalRaiderCount() {
        return originalRaiderCount;
    }

    public Set<UUID> raiderIds() {
        return Set.copyOf(raiderIds);
    }

    public int remainingRaiderCount() {
        return raiderIds.size();
    }

    public int lastNotifiedRemainingSeconds() {
        return lastNotifiedRemainingSeconds;
    }

    void addRaider(UUID entityId) {
        raiderIds.add(Objects.requireNonNull(entityId, "entityId"));
    }

    void removeRaider(UUID entityId) {
        raiderIds.remove(entityId);
    }

    boolean containsRaider(UUID entityId) {
        return raiderIds.contains(entityId);
    }

    void activate(long activeEndsAtEpochMillis, int originalRaiderCount, long nowEpochMillis) {
        state = State.ACTIVE;
        warningEndsAtEpochMillis = 0L;
        this.activeEndsAtEpochMillis = Math.max(0L, activeEndsAtEpochMillis);
        this.originalRaiderCount = Math.max(0, originalRaiderCount);
        lastNotifiedRemainingSeconds = secondsRemaining(nowEpochMillis);
    }

    void setLastNotifiedRemainingSeconds(int seconds) {
        lastNotifiedRemainingSeconds = seconds;
    }

    int secondsRemaining(long nowEpochMillis) {
        if (state != State.ACTIVE || activeEndsAtEpochMillis <= nowEpochMillis) {
            return 0;
        }
        long remainingMillis = activeEndsAtEpochMillis - nowEpochMillis;
        return (int) Math.min(Integer.MAX_VALUE, (remainingMillis + 999L) / 1000L);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_ID, id);
        tag.putUUID(TAG_FACTION, factionId);
        tag.putString(TAG_TARGET_TYPE, targetType.name());
        tag.put(TAG_TARGET_CLAIM, targetClaim.save());
        tag.putLong(TAG_TARGET_POS, targetPos.asLong());
        if (outpostId != null) {
            tag.putUUID(TAG_OUTPOST_ID, outpostId);
        }
        tag.putString(TAG_STATE, state.name());
        tag.putLong(TAG_WARNING_ENDS_AT, warningEndsAtEpochMillis);
        tag.putLong(TAG_ACTIVE_ENDS_AT, activeEndsAtEpochMillis);
        tag.putInt(TAG_ORIGINAL_RAIDERS, originalRaiderCount);
        tag.putInt(TAG_LAST_NOTICE, lastNotifiedRemainingSeconds);
        ListTag raidersTag = new ListTag();
        for (UUID raiderId : raiderIds) {
            raidersTag.add(NbtUtils.createUUID(raiderId));
        }
        tag.put(TAG_RAIDERS, raidersTag);
        return tag;
    }

    static Optional<Raid> load(CompoundTag tag) {
        if (!tag.hasUUID(TAG_ID) || !tag.hasUUID(TAG_FACTION) || !tag.contains(TAG_TARGET_POS, Tag.TAG_LONG)) {
            return Optional.empty();
        }
        Optional<ClaimKey> targetClaim = ClaimKey.load(tag.getCompound(TAG_TARGET_CLAIM));
        if (targetClaim.isEmpty()) {
            return Optional.empty();
        }
        TargetType targetType;
        State state;
        try {
            targetType = TargetType.valueOf(tag.getString(TAG_TARGET_TYPE).toUpperCase(Locale.ROOT));
            state = State.valueOf(tag.getString(TAG_STATE).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        Set<UUID> raiders = new LinkedHashSet<>();
        ListTag raidersTag = tag.getList(TAG_RAIDERS, Tag.TAG_INT_ARRAY);
        for (int index = 0; index < raidersTag.size(); index++) {
            try {
                raiders.add(NbtUtils.loadUUID(raidersTag.get(index)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Optional.of(new Raid(
            tag.getUUID(TAG_ID),
            tag.getUUID(TAG_FACTION),
            targetType,
            targetClaim.get(),
            BlockPos.of(tag.getLong(TAG_TARGET_POS)),
            tag.hasUUID(TAG_OUTPOST_ID) ? tag.getUUID(TAG_OUTPOST_ID) : null,
            state,
            tag.getLong(TAG_WARNING_ENDS_AT),
            tag.getLong(TAG_ACTIVE_ENDS_AT),
            tag.getInt(TAG_ORIGINAL_RAIDERS),
            raiders,
            tag.contains(TAG_LAST_NOTICE, Tag.TAG_INT) ? tag.getInt(TAG_LAST_NOTICE) : -1
        ));
    }

    public enum TargetType {
        MAIN,
        OUTPOST
    }

    public enum State {
        WARNING,
        ACTIVE
    }
}
