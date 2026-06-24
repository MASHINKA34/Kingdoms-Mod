package com.geydev.kalfactions.war;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

public record WarRecord(
        UUID id,
        WarType type,
        String reason,
        String attackerLeadName,
        List<String> attackerAllies,
        String defenderLeadName,
        List<String> defenderAllies,
        Outcome outcome,
        String winnerName,
        String loserName,
        long attackerPoints,
        long defenderPoints,
        long militaryInfluenceReward,
        boolean lootSpoilsAvailable,
        long startedAtMillis,
        long endedAtMillis
) {
    private static final String TAG_ID = "id";
    private static final String TAG_TYPE = "type";
    private static final String TAG_REASON = "reason";
    private static final String TAG_ATTACKER = "attacker";
    private static final String TAG_ATTACKER_ALLIES = "attackerAllies";
    private static final String TAG_DEFENDER = "defender";
    private static final String TAG_DEFENDER_ALLIES = "defenderAllies";
    private static final String TAG_OUTCOME = "outcome";
    private static final String TAG_WINNER = "winner";
    private static final String TAG_LOSER = "loser";
    private static final String TAG_ATTACKER_POINTS = "attackerPoints";
    private static final String TAG_DEFENDER_POINTS = "defenderPoints";
    private static final String TAG_MILITARY_REWARD = "militaryReward";
    private static final String TAG_LOOT_SPOILS = "lootSpoils";
    private static final String TAG_STARTED_AT = "startedAt";
    private static final String TAG_ENDED_AT = "endedAt";
    private static final int MAX_NAME_LENGTH = 32;
    private static final int MAX_REASON_LENGTH = War.MAX_REASON_LENGTH;
    private static final int MAX_SIDE_NAMES = 64;

    public WarRecord {
        id = id == null ? UUID.randomUUID() : id;
        type = type == null ? WarType.DEFAULT : type;
        reason = limit(reason, MAX_REASON_LENGTH);
        attackerLeadName = limit(attackerLeadName, MAX_NAME_LENGTH);
        attackerAllies = sanitizeNames(attackerAllies);
        defenderLeadName = limit(defenderLeadName, MAX_NAME_LENGTH);
        defenderAllies = sanitizeNames(defenderAllies);
        outcome = outcome == null ? Outcome.DRAW : outcome;
        winnerName = limit(winnerName, MAX_NAME_LENGTH);
        loserName = limit(loserName, MAX_NAME_LENGTH);
        attackerPoints = Math.max(0L, attackerPoints);
        defenderPoints = Math.max(0L, defenderPoints);
        militaryInfluenceReward = Math.max(0L, militaryInfluenceReward);
        startedAtMillis = Math.max(0L, startedAtMillis);
        endedAtMillis = Math.max(startedAtMillis, endedAtMillis);
    }

    public long durationMillis() {
        return Math.max(0L, endedAtMillis - startedAtMillis);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_ID, id);
        tag.putString(TAG_TYPE, type.id());
        tag.putString(TAG_REASON, reason);
        tag.putString(TAG_ATTACKER, attackerLeadName);
        tag.put(TAG_ATTACKER_ALLIES, saveNames(attackerAllies));
        tag.putString(TAG_DEFENDER, defenderLeadName);
        tag.put(TAG_DEFENDER_ALLIES, saveNames(defenderAllies));
        tag.putString(TAG_OUTCOME, outcome.name());
        tag.putString(TAG_WINNER, winnerName);
        tag.putString(TAG_LOSER, loserName);
        tag.putLong(TAG_ATTACKER_POINTS, attackerPoints);
        tag.putLong(TAG_DEFENDER_POINTS, defenderPoints);
        tag.putLong(TAG_MILITARY_REWARD, militaryInfluenceReward);
        tag.putBoolean(TAG_LOOT_SPOILS, lootSpoilsAvailable);
        tag.putLong(TAG_STARTED_AT, startedAtMillis);
        tag.putLong(TAG_ENDED_AT, endedAtMillis);
        return tag;
    }

    public static Optional<WarRecord> load(CompoundTag tag) {
        if (!tag.hasUUID(TAG_ID)) {
            return Optional.empty();
        }
        return Optional.of(new WarRecord(
                tag.getUUID(TAG_ID),
                WarType.fromIdOrDefault(tag.getString(TAG_TYPE)),
                tag.getString(TAG_REASON),
                tag.getString(TAG_ATTACKER),
                loadNames(tag.getList(TAG_ATTACKER_ALLIES, Tag.TAG_STRING)),
                tag.getString(TAG_DEFENDER),
                loadNames(tag.getList(TAG_DEFENDER_ALLIES, Tag.TAG_STRING)),
                parseOutcome(tag.getString(TAG_OUTCOME)),
                tag.getString(TAG_WINNER),
                tag.getString(TAG_LOSER),
                tag.getLong(TAG_ATTACKER_POINTS),
                tag.getLong(TAG_DEFENDER_POINTS),
                tag.getLong(TAG_MILITARY_REWARD),
                tag.getBoolean(TAG_LOOT_SPOILS),
                tag.getLong(TAG_STARTED_AT),
                tag.getLong(TAG_ENDED_AT)
        ));
    }

    private static ListTag saveNames(List<String> names) {
        ListTag tag = new ListTag();
        for (String name : sanitizeNames(names)) {
            tag.add(StringTag.valueOf(name));
        }
        return tag;
    }

    private static List<String> loadNames(ListTag tag) {
        List<String> names = new ArrayList<>(Math.min(tag.size(), MAX_SIDE_NAMES));
        int size = Math.min(tag.size(), MAX_SIDE_NAMES);
        for (int index = 0; index < size; index++) {
            names.add(limit(tag.getString(index), MAX_NAME_LENGTH));
        }
        return List.copyOf(names);
    }

    private static List<String> sanitizeNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        List<String> sanitized = new ArrayList<>(Math.min(names.size(), MAX_SIDE_NAMES));
        for (String name : names) {
            if (sanitized.size() >= MAX_SIDE_NAMES) {
                break;
            }
            String trimmed = limit(name, MAX_NAME_LENGTH);
            if (!trimmed.isBlank()) {
                sanitized.add(trimmed);
            }
        }
        return List.copyOf(sanitized);
    }

    private static String limit(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.strip();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private static Outcome parseOutcome(String value) {
        if (value == null || value.isBlank()) {
            return Outcome.DRAW;
        }
        try {
            return Outcome.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return Outcome.DRAW;
        }
    }

    public enum Outcome {
        VICTORY,
        DRAW
    }
}
