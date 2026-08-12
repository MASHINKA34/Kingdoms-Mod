package com.geydev.kalfactions.faith;

import com.geydev.kalfactions.config.ModConfigSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class FaithQuests {
    public static int crystalCost(int level) {
        return switch (level) {
            case 2 -> ModConfigSpec.FAITH_CRYSTAL_COST_LEVEL_2.getAsInt();
            case 3 -> ModConfigSpec.FAITH_CRYSTAL_COST_LEVEL_3.getAsInt();
            case 4 -> ModConfigSpec.FAITH_CRYSTAL_COST_LEVEL_4.getAsInt();
            case 5 -> ModConfigSpec.FAITH_CRYSTAL_COST_LEVEL_5.getAsInt();
            case 6 -> ModConfigSpec.FAITH_CRYSTAL_COST_LEVEL_6.getAsInt();
            case 7 -> ModConfigSpec.FAITH_CRYSTAL_COST_LEVEL_7.getAsInt();
            case 8 -> ModConfigSpec.FAITH_CRYSTAL_COST_LEVEL_8.getAsInt();
            case 9 -> ModConfigSpec.FAITH_CRYSTAL_COST_LEVEL_9.getAsInt();
            case 10 -> ModConfigSpec.FAITH_CRYSTAL_COST_LEVEL_10.getAsInt();
            default -> 0;
        };
    }

    public static int trophyTier(int level) {
        if (level >= 9) {
            return 3;
        }
        return level >= 6 ? 2 : 1;
    }

    public static boolean requiresKillsAndTrophy(int level) {
        return level == 3 || level == 6 || level == 9;
    }

    public static boolean hasSpecialOffering(int level) {
        return level == 5 || level == 7 || level == 9;
    }

    public static int warKills(int level) {
        return switch (level) {
            case 2 -> ModConfigSpec.FAITH_WAR_KILLS_LEVEL_2.getAsInt();
            case 3 -> ModConfigSpec.FAITH_WAR_KILLS_LEVEL_3.getAsInt();
            case 4 -> ModConfigSpec.FAITH_WAR_KILLS_LEVEL_4.getAsInt();
            case 5 -> ModConfigSpec.FAITH_WAR_KILLS_LEVEL_5.getAsInt();
            case 6 -> ModConfigSpec.FAITH_WAR_KILLS_LEVEL_6.getAsInt();
            case 7 -> ModConfigSpec.FAITH_WAR_KILLS_LEVEL_7.getAsInt();
            case 8 -> ModConfigSpec.FAITH_WAR_KILLS_LEVEL_8.getAsInt();
            case 9 -> ModConfigSpec.FAITH_WAR_KILLS_LEVEL_9.getAsInt();
            case 10 -> ModConfigSpec.FAITH_WAR_KILLS_LEVEL_10.getAsInt();
            default -> 0;
        };
    }

    public static long economySpurs(int level) {
        return switch (level) {
            case 2 -> ModConfigSpec.FAITH_ECONOMY_SPURS_LEVEL_2.get();
            case 3 -> ModConfigSpec.FAITH_ECONOMY_SPURS_LEVEL_3.get();
            case 4 -> ModConfigSpec.FAITH_ECONOMY_SPURS_LEVEL_4.get();
            case 5 -> ModConfigSpec.FAITH_ECONOMY_SPURS_LEVEL_5.get();
            case 6 -> ModConfigSpec.FAITH_ECONOMY_SPURS_LEVEL_6.get();
            case 7 -> ModConfigSpec.FAITH_ECONOMY_SPURS_LEVEL_7.get();
            case 8 -> ModConfigSpec.FAITH_ECONOMY_SPURS_LEVEL_8.get();
            case 9 -> ModConfigSpec.FAITH_ECONOMY_SPURS_LEVEL_9.get();
            case 10 -> ModConfigSpec.FAITH_ECONOMY_SPURS_LEVEL_10.get();
            default -> 0L;
        };
    }

    public static FaithQuest build(UUID factionId, FaithGod god, int level, int nonce) {
        int target = Math.clamp(level, FaithGod.MIN_LEVEL + 1, FaithGod.MAX_LEVEL);
        Random random = new Random(seed(factionId, god, target, nonce));
        List<FaithRequirement> requirements = new ArrayList<>();
        requirements.add(FaithRequirement.ofItem(god.crystal(), crystalCost(target)));

        long spurs = 0L;
        int kills = 0;
        boolean killsOrTrophy = false;
        switch (god) {
            case SCIENCE -> {
                int minEntries = ModConfigSpec.FAITH_SCIENCE_MIN_ENTRIES.getAsInt();
                int maxEntries = Math.max(minEntries, ModConfigSpec.FAITH_SCIENCE_MAX_ENTRIES.getAsInt());
                int entries = minEntries + random.nextInt(maxEntries - minEntries + 1);
                int minCount = scienceMinCount(target);
                int maxCount = Math.max(minCount, scienceMaxCount(target));
                List<Item> rolled = pick(FaithTags.scienceOfferings(target), entries, random);
                for (Item item : rolled) {
                    requirements.add(FaithRequirement.ofItem(
                            item,
                            minCount + random.nextInt(maxCount - minCount + 1)
                    ));
                }
                int specialCount = ModConfigSpec.FAITH_SCIENCE_SPECIAL_COUNT.getAsInt();
                if (hasSpecialOffering(target) && specialCount > 0) {
                    int specialLevel = Math.min(FaithGod.MAX_LEVEL, target + 1);
                    for (Item item : pick(FaithTags.scienceOfferings(specialLevel), 1, random, rolled)) {
                        requirements.add(FaithRequirement.ofItem(item, specialCount));
                    }
                }
            }
            case ECONOMY -> {
                spurs = economySpurs(target);
                int minCount = economyMinCount(target);
                int maxCount = Math.max(minCount, economyMaxCount(target));
                int entries = ModConfigSpec.FAITH_ECONOMY_GEM_ENTRIES.getAsInt();
                List<Item> banned = target < ModConfigSpec.FAITH_ECONOMY_LATE_MIN_LEVEL.getAsInt()
                        ? FaithTags.sortedItems(FaithTags.ECONOMY_OFFERINGS_LATE)
                        : List.of();
                for (Item item : pick(FaithTags.ECONOMY_OFFERINGS, entries, random, banned)) {
                    requirements.add(FaithRequirement.ofItem(
                            item,
                            minCount + random.nextInt(maxCount - minCount + 1)
                    ));
                }
            }
            case WAR -> {
                kills = warKills(target);
                killsOrTrophy = !requiresKillsAndTrophy(target);
                TagKey<Item> trophies = FaithTags.bossTrophies(trophyTier(target));
                requirements.add(FaithRequirement.ofTag(trophies, FaithTags.tagLabelKey(trophies), 1));
            }
        }
        return new FaithQuest(god, target, requirements, spurs, kills, killsOrTrophy);
    }

    public static int scienceMinCount(int level) {
        return ModConfigSpec.FAITH_SCIENCE_COUNT_MIN[levelIndex(level)].getAsInt();
    }

    public static int scienceMaxCount(int level) {
        return ModConfigSpec.FAITH_SCIENCE_COUNT_MAX[levelIndex(level)].getAsInt();
    }

    public static int economyMinCount(int level) {
        return ModConfigSpec.FAITH_ECONOMY_COUNT_MIN[levelIndex(level)].getAsInt();
    }

    public static int economyMaxCount(int level) {
        return ModConfigSpec.FAITH_ECONOMY_COUNT_MAX[levelIndex(level)].getAsInt();
    }

    private static int levelIndex(int level) {
        return Math.clamp(level, FaithGod.MIN_LEVEL + 1, FaithGod.MAX_LEVEL) - FaithGod.MIN_LEVEL - 1;
    }

    private static List<Item> pick(TagKey<Item> tag, int wanted, Random random) {
        return pick(tag, wanted, random, List.of());
    }

    private static List<Item> pick(TagKey<Item> tag, int wanted, Random random, List<Item> exclude) {
        List<Item> pool = new ArrayList<>(FaithTags.sortedItems(tag));
        pool.removeAll(exclude);
        if (pool.isEmpty()) {
            return List.of();
        }
        List<Item> picked = new ArrayList<>(Math.min(wanted, pool.size()));
        for (int index = 0; index < wanted && !pool.isEmpty(); index++) {
            picked.add(pool.remove(random.nextInt(pool.size())));
        }
        return picked;
    }

    private static long seed(UUID factionId, FaithGod god, int level, int nonce) {
        long seed = factionId == null ? 0L : factionId.getMostSignificantBits() * 31L
                + factionId.getLeastSignificantBits();
        seed = seed * 1_000_003L + god.ordinal();
        seed = seed * 1_000_003L + level;
        seed = seed * 1_000_003L + nonce;
        return seed;
    }

    private FaithQuests() {
    }
}
