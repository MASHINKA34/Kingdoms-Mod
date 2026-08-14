package com.geydev.kalfactions.faction;

import com.geydev.kalfactions.config.ModConfigSpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class LegacyResearch {
    public static final int MAX_SLOTS = 2;
    public static final int MAX_LEVEL = 5;

    public static List<FactionBonus> slots(Collection<FactionBonus> bonuses) {
        if (bonuses == null || bonuses.isEmpty()) {
            return List.of();
        }
        List<FactionBonus> ordered = new ArrayList<>();
        bonuses.stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingInt(FactionBonus::ordinal))
                .limit(MAX_SLOTS)
                .forEach(ordered::add);
        return List.copyOf(ordered);
    }

    public static Optional<FactionBonus> bonusAt(Collection<FactionBonus> bonuses, int slot) {
        List<FactionBonus> ordered = slots(bonuses);
        return slot < 0 || slot >= ordered.size() ? Optional.empty() : Optional.of(ordered.get(slot));
    }

    public static int slotOf(Collection<FactionBonus> bonuses, FactionBonus bonus) {
        return bonus == null ? -1 : slots(bonuses).indexOf(bonus);
    }

    public static long influenceCostPerType(int level) {
        return switch (Math.clamp(level, 1, MAX_LEVEL)) {
            case 1 -> Math.max(0L, ModConfigSpec.LEGACY_INFLUENCE_COST_LEVEL_1.get());
            case 2 -> Math.max(0L, ModConfigSpec.LEGACY_INFLUENCE_COST_LEVEL_2.get());
            case 3 -> Math.max(0L, ModConfigSpec.LEGACY_INFLUENCE_COST_LEVEL_3.get());
            case 4 -> Math.max(0L, ModConfigSpec.LEGACY_INFLUENCE_COST_LEVEL_4.get());
            default -> Math.max(0L, ModConfigSpec.LEGACY_INFLUENCE_COST_LEVEL_5.get());
        };
    }

    public static long influenceCost(int level) {
        return influenceCostPerType(level) * InfluenceType.VALUES.length;
    }

    public static int crystalCostPerType(int level) {
        return switch (Math.clamp(level, 1, MAX_LEVEL)) {
            case 1 -> Math.max(0, ModConfigSpec.LEGACY_CRYSTAL_COST_LEVEL_1.getAsInt());
            case 2 -> Math.max(0, ModConfigSpec.LEGACY_CRYSTAL_COST_LEVEL_2.getAsInt());
            case 3 -> Math.max(0, ModConfigSpec.LEGACY_CRYSTAL_COST_LEVEL_3.getAsInt());
            case 4 -> Math.max(0, ModConfigSpec.LEGACY_CRYSTAL_COST_LEVEL_4.getAsInt());
            default -> Math.max(0, ModConfigSpec.LEGACY_CRYSTAL_COST_LEVEL_5.getAsInt());
        };
    }

    public static int crystalCost(int level) {
        return crystalCostPerType(level) * InfluenceType.VALUES.length;
    }

    public static long share(long total, int parts) {
        if (parts <= 1) {
            return Math.max(0L, total);
        }
        return Math.max(0L, (total + parts - 1) / parts);
    }

    public static int share(int total, int parts) {
        if (parts <= 1) {
            return Math.max(0, total);
        }
        return Math.max(0, (total + parts - 1) / parts);
    }

    private LegacyResearch() {
    }
}
