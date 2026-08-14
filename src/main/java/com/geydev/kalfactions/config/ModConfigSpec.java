package com.geydev.kalfactions.config;

import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;
import net.neoforged.neoforge.common.ModConfigSpec.DoubleValue;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;
import net.neoforged.neoforge.common.ModConfigSpec.LongValue;

public final class ModConfigSpec {
    public static final net.neoforged.neoforge.common.ModConfigSpec SPEC;

    public static final IntValue STARTER_CLAIM_SIZE;
    public static final IntValue FREE_CLAIMS;
    public static final BooleanValue REQUIRE_ADJACENT;
    public static final BooleanValue PROTECT_EXPLOSIONS;
    public static final LongValue EXPANSION_BASE_COST;
    public static final DoubleValue EXPANSION_GROWTH;
    public static final DoubleValue UNCLAIM_REFUND_PERCENT;
    public static final DoubleValue ORE_BONUS_CHANCE;
    public static final DoubleValue MINER_MINING_SPEED_BONUS;
    public static final DoubleValue HARVEST_BONUS_CHANCE;
    public static final DoubleValue FARMER_BREEDING_TWIN_CHANCE;
    public static final DoubleValue BUILDER_DISCOUNT;
    public static final IntValue BUILDER_OUTPOST_SIZE;
    public static final DoubleValue ASSASSIN_BACK_DAMAGE_MULTIPLIER;
    public static final DoubleValue HOOKAH_ARMOR_BONUS;
    public static final DoubleValue HOOKAH_SPEED_BONUS;
    public static final DoubleValue HOOKAH_DAMAGE_MULTIPLIER;
    public static final DoubleValue MERCHANT_SELL_BONUS_PERCENT;
    public static final IntValue MERCHANT_TREASURY_INCOME_INTERVAL_HOURS;
    public static final DoubleValue MERCHANT_TREASURY_INCOME_PERCENT;
    public static final DoubleValue NOMAD_MOUNT_SPEED_BONUS;
    public static final DoubleValue RESEARCHER_RESEARCH_SPEED_BONUS;
    public static final IntValue ENCHANTER_ANVIL_MAX_COST;
    public static final IntValue ENCHANTER_PRIOR_WORK_MAX_COST;
    public static final IntValue ENCHANTER_LEVEL_COST_CAP;
    public static final IntValue WAR_ROLLBACK_CHUNKS_PER_TICK;
    public static final LongValue WAR_AUTO_END_TICKS;
    public static final IntValue CLAIM_SYNC_RADIUS_CHUNKS;
    public static final IntValue RAID_GRACE_PERIOD_HOURS;
    public static final IntValue RAID_CHANCE_PERCENT;
    public static final IntValue RAID_ROLL_INTERVAL_HOURS;
    public static final IntValue RAID_WARNING_SECONDS;
    public static final IntValue RAID_TOAST_SECONDS;
    public static final IntValue RAID_COMBAT_MINUTES;
    public static final LongValue RAID_REWARD_PER_RAIDER_MIN;
    public static final LongValue RAID_REWARD_PER_RAIDER_MAX;
    public static final DoubleValue RAID_TREASURY_STEAL_PERCENT;
    public static final LongValue RAID_TREASURY_STEAL_MIN;
    public static final LongValue RAID_INVENTORY_STEAL_MAX;
    public static final LongValue OUTPOST_CHARTER_COST;
    public static final LongValue OUTPOST_DRILL_COST;
    public static final LongValue FACTION_TABLE_COST;
    public static final LongValue ACCESS_TOOL_COST;
    public static final IntValue OUTPOST_DRILL_INTERVAL_SECONDS;
    public static final DoubleValue INFLUENCE_DECAY_PERCENT;
    public static final IntValue INFLUENCE_DECAY_INTERVAL_HOURS;
    public static final LongValue INFLUENCE_BASELINE_PER_NODE;
    public static final LongValue INFLUENCE_CRYSTAL_TO_INFLUENCE;
    public static final LongValue INFLUENCE_KILL_INFLUENCE;
    public static final IntValue INFLUENCE_KILL_CAP_PER_VICTIM;
    public static final IntValue INFLUENCE_KILL_CAP_HOURS;
    public static final LongValue INFLUENCE_WAR_WIN_INFLUENCE;
    public static final LongValue INFLUENCE_WAR_JOIN_REWARD;
    public static final LongValue INFLUENCE_CRAFT_PER_ITEM;
    public static final IntValue INFLUENCE_CRAFT_CAP_PER_ITEM;
    public static final IntValue INFLUENCE_CRAFT_CAP_HOURS;
    public static final LongValue INFLUENCE_VILLAGER_TRADE;
    public static final LongValue INFLUENCE_SELL_PER_THRESHOLD;
    public static final IntValue INFLUENCE_MOB_KILLS_PER_AWARD;
    public static final LongValue INFLUENCE_MOB_KILL_INFLUENCE;
    public static final LongValue INFLUENCE_MOB_DAILY_CAP;
    public static final LongValue INFLUENCE_SPURS_PER_ECON;
    public static final LongValue WAR_POINTS_GOAL;
    public static final IntValue WAR_BLOCK_BREAK_POINTS;
    public static final IntValue WAR_KILL_POINTS;
    public static final IntValue WAR_BLOCK_POINT_CAP_PER_MINUTE;
    public static final IntValue WAR_KILL_POINT_CAP_PER_MINUTE;
    public static final IntValue WAR_DECLARE_COOLDOWN_HOURS;
    public static final IntValue FORCE_LOAD_SLOTS;
    public static final DoubleValue MARKET_BUYBACK_PERCENT;
    public static final BooleanValue SANCTUARY_DISABLE_PVP;
    public static final BooleanValue SANCTUARY_DISABLE_MOB_SPAWNS;
    public static final BooleanValue SANCTUARY_EXPLOSION_IMMUNITY;
    public static final BooleanValue SANCTUARY_DISABLE_FIRE_SPREAD;
    public static final BooleanValue SANCTUARY_DISABLE_LIGHTNING;
    public static final LongValue QUARRY_LEVEL_2_COST;
    public static final LongValue QUARRY_LEVEL_3_COST;
    public static final LongValue QUARRY_LEVEL_4_COST;
    public static final LongValue QUARRY_LEVEL_5_COST;
    public static final DoubleValue LAGTAX_QUOTA_MS;
    public static final DoubleValue LAGTAX_TIER1_LIMIT_MS;
    public static final DoubleValue LAGTAX_TIER2_LIMIT_MS;
    public static final LongValue LAGTAX_TIER1_PRICE;
    public static final LongValue LAGTAX_TIER2_PRICE;
    public static final LongValue LAGTAX_TIER3_PRICE;
    public static final IntValue LAGTAX_INTERVAL_HOURS;
    public static final IntValue LAGTAX_WARNING_MINUTES;
    public static final LongValue LAGTAX_MIN_BILL;
    public static final DoubleValue LAGTAX_HARD_CAP_MS;
    public static final IntValue LAGTAX_SAMPLE_INTERVAL_TICKS;
    public static final IntValue LAGTAX_EMA_SECONDS;
    public static final LongValue CHUNK_LOAD_PRICE_8H;
    public static final IntValue CHUNK_LOAD_MAX_DAYS;
    public static final LongValue FACTION_METER_COST;
    public static final IntValue NEWS_MAX_ARTICLES_PER_FACTION;
    public static final IntValue NEWS_PUBLISH_COOLDOWN_MINUTES;
    public static final IntValue SCOUT_SMALL_SIZE;
    public static final IntValue SCOUT_SMALL_MINUTES;
    public static final LongValue SCOUT_SMALL_PRICE;
    public static final IntValue SCOUT_MEDIUM_SIZE;
    public static final IntValue SCOUT_MEDIUM_MINUTES;
    public static final LongValue SCOUT_MEDIUM_PRICE;
    public static final IntValue SCOUT_LARGE_SIZE;
    public static final IntValue SCOUT_LARGE_MINUTES;
    public static final LongValue SCOUT_LARGE_PRICE;
    public static final IntValue SCOUT_CHUNKS_PER_TICK;
    public static final IntValue SCOUT_MAX_CHUNKS_PER_ORDER;
    public static final ConfigValue<java.util.List<? extends String>> SCOUT_ALLOWED_DIMENSIONS;
    public static final IntValue RESOURCE_WORLD_BORDER_SIZE;
    public static final IntValue RESOURCE_BLUE_RADIUS;
    public static final IntValue RESOURCE_YELLOW_RADIUS;
    public static final IntValue RESOURCE_RED_RADIUS;
    public static final DoubleValue RESOURCE_BLUE_SIZE_MULTIPLIER;
    public static final IntValue RESOURCE_YELLOW_MIN_VEIN_SIZE;
    public static final IntValue RESOURCE_YELLOW_MAX_VEIN_SIZE;
    public static final IntValue RESOURCE_RED_MIN_VEIN_SIZE;
    public static final IntValue RESOURCE_RED_MAX_VEIN_SIZE;
    public static final DoubleValue RESOURCE_BLACK_SIZE_MULTIPLIER;
    public static final IntValue RESOURCE_YELLOW_CLUSTER_SPACING_CHUNKS;
    public static final IntValue RESOURCE_RED_CLUSTER_SPACING_CHUNKS;
    public static final IntValue RESEARCH_CRYSTAL_COST_TIER_1;
    public static final IntValue RESEARCH_CRYSTAL_COST_TIER_2;
    public static final IntValue RESEARCH_CRYSTAL_COST_TIER_3;
    public static final IntValue RESEARCH_CRYSTAL_COST_TIER_4;
    public static final IntValue RESEARCH_CRYSTAL_COST_TIER_5;
    public static final IntValue RESEARCH_CRYSTAL_COST_TIER_6_PLUS;
    public static final IntValue CONTRABAND_LIFETIME_MINUTES;
    public static final IntValue CONTRABAND_COOLDOWN_MINUTES;
    public static final IntValue CONTRABAND_OFFER_COUNT_MIN;
    public static final IntValue CONTRABAND_OFFER_COUNT_MAX;
    public static final IntValue CONTRABAND_MAX_SPAWN_DISTANCE;
    public static final IntValue CONTRABAND_SPAWN_ATTEMPTS;
    public static final IntValue WANDERING_ROLL_INTERVAL_MINUTES;
    public static final IntValue WANDERING_CHANCE_PERCENT;
    public static final IntValue WANDERING_LIFETIME_MINUTES;
    public static final IntValue WANDERING_COOLDOWN_MINUTES;
    public static final IntValue WANDERING_OFFER_COUNT_MIN;
    public static final IntValue WANDERING_OFFER_COUNT_MAX;
    public static final IntValue WANDERING_SPAWN_ATTEMPTS;
    public static final IntValue TRADER_WORLD_BORDER_MARGIN;
    public static final IntValue NETHER_SESSION_DURATION_MINUTES;
    public static final BooleanValue NETHER_REQUIRE_FULL_SESSION_BEFORE_CLOSE;
    public static final IntValue NETHER_LANDING_MIN_RADIUS;
    public static final IntValue NETHER_LANDING_MAX_RADIUS;
    public static final IntValue NETHER_LANDING_ATTEMPTS;
    public static final IntValue NETHER_LANDING_MINIMUM_SEPARATION;
    public static final IntValue CLUSTER_UNITS_RICHNESS_1;
    public static final IntValue CLUSTER_UNITS_RICHNESS_2;
    public static final IntValue CLUSTER_UNITS_RICHNESS_3;
    public static final IntValue CLUSTER_RESET_DAYS;
    public static final IntValue CLUSTER_GENERATION;
    public static final IntValue CLUSTER_MAINTENANCE_CHUNKS_PER_TICK;
    public static final LongValue LEGACY_INFLUENCE_COST_LEVEL_1;
    public static final LongValue LEGACY_INFLUENCE_COST_LEVEL_2;
    public static final LongValue LEGACY_INFLUENCE_COST_LEVEL_3;
    public static final LongValue LEGACY_INFLUENCE_COST_LEVEL_4;
    public static final LongValue LEGACY_INFLUENCE_COST_LEVEL_5;
    public static final IntValue LEGACY_CRYSTAL_COST_LEVEL_1;
    public static final IntValue LEGACY_CRYSTAL_COST_LEVEL_2;
    public static final IntValue LEGACY_CRYSTAL_COST_LEVEL_3;
    public static final IntValue LEGACY_CRYSTAL_COST_LEVEL_4;
    public static final IntValue LEGACY_CRYSTAL_COST_LEVEL_5;
    public static final BooleanValue SCORCHED_DISABLE_MOB_GUNS_IN_YELLOW_ZONE;
    public static final ConfigValue<java.util.List<? extends String>> SCORCHED_MOB_GUN_FREE_ZONES;
    public static final BooleanValue BLACK_ZONE_ENABLED;
    public static final BooleanValue BLACK_ZONE_EXEMPT_OPERATORS;
    public static final IntValue BLACK_ZONE_RELEASE_MINUTES;
    public static final IntValue BLACK_ZONE_ACTIONBAR_INTERVAL_SECONDS;
    public static final IntValue BLACK_ZONE_STAGE_ENTRY_MINUTES;
    public static final IntValue BLACK_ZONE_STAGE_HUNGER_1_MINUTES;
    public static final IntValue BLACK_ZONE_STAGE_HEALTH_2_MINUTES;
    public static final IntValue BLACK_ZONE_STAGE_HEALTH_4_MINUTES;
    public static final IntValue BLACK_ZONE_STAGE_WEAKNESS_MINUTES;
    public static final IntValue BLACK_ZONE_STAGE_HEALTH_6_MINUTES;
    public static final IntValue BLACK_ZONE_STAGE_SLOWNESS_MINUTES;
    public static final IntValue BLACK_ZONE_STAGE_HUNGER_2_MINUTES;
    public static final IntValue BLACK_ZONE_STAGE_MINING_FATIGUE_MINUTES;
    public static final IntValue BLACK_ZONE_STAGE_KOLYVAN_MINUTES;
    public static final IntValue BLACK_ZONE_STAGE_POISON_MINUTES;
    public static final IntValue BLACK_ZONE_STAGE_WITHER_MINUTES;
    public static final IntValue BLACK_ZONE_HEALTH_PENALTY_ENTRY;
    public static final IntValue BLACK_ZONE_HEALTH_PENALTY_SECOND;
    public static final IntValue BLACK_ZONE_HEALTH_PENALTY_THIRD;
    public static final IntValue BLACK_ZONE_HEALTH_PENALTY_FOURTH;
    public static final IntValue BLACK_ZONE_KOLYVAN_INTERVAL_MINUTES;
    public static final IntValue BLACK_ZONE_KOLYVAN_MIN_DISTANCE;
    public static final IntValue BLACK_ZONE_KOLYVAN_MAX_DISTANCE;
    public static final IntValue BLACK_ZONE_KOLYVAN_LIFETIME_SECONDS;
    public static final IntValue BLACK_ZONE_KOLYVAN_BLINDNESS_SECONDS;
    public static final DoubleValue BLACK_ZONE_KOLYVAN_REACH_DISTANCE;
    public static final IntValue FAITH_CRYSTAL_COST_LEVEL_2;
    public static final IntValue FAITH_CRYSTAL_COST_LEVEL_3;
    public static final IntValue FAITH_CRYSTAL_COST_LEVEL_4;
    public static final IntValue FAITH_CRYSTAL_COST_LEVEL_5;
    public static final IntValue FAITH_CRYSTAL_COST_LEVEL_6;
    public static final IntValue FAITH_CRYSTAL_COST_LEVEL_7;
    public static final IntValue FAITH_CRYSTAL_COST_LEVEL_8;
    public static final IntValue FAITH_CRYSTAL_COST_LEVEL_9;
    public static final IntValue FAITH_CRYSTAL_COST_LEVEL_10;
    public static final IntValue FAITH_SCIENCE_MIN_ENTRIES;
    public static final IntValue FAITH_SCIENCE_MAX_ENTRIES;
    public static final IntValue[] FAITH_SCIENCE_COUNT_MIN = new IntValue[9];
    public static final IntValue[] FAITH_SCIENCE_COUNT_MAX = new IntValue[9];
    public static final IntValue[] FAITH_ECONOMY_COUNT_MIN = new IntValue[9];
    public static final IntValue[] FAITH_ECONOMY_COUNT_MAX = new IntValue[9];
    public static final IntValue FAITH_ECONOMY_LATE_MIN_LEVEL;
    public static final IntValue FAITH_SCIENCE_SPECIAL_COUNT;
    public static final IntValue FAITH_WAR_KILLS_LEVEL_2;
    public static final IntValue FAITH_WAR_KILLS_LEVEL_3;
    public static final IntValue FAITH_WAR_KILLS_LEVEL_4;
    public static final IntValue FAITH_WAR_KILLS_LEVEL_5;
    public static final IntValue FAITH_WAR_KILLS_LEVEL_6;
    public static final IntValue FAITH_WAR_KILLS_LEVEL_7;
    public static final IntValue FAITH_WAR_KILLS_LEVEL_8;
    public static final IntValue FAITH_WAR_KILLS_LEVEL_9;
    public static final IntValue FAITH_WAR_KILLS_LEVEL_10;
    public static final LongValue FAITH_ECONOMY_SPURS_LEVEL_2;
    public static final LongValue FAITH_ECONOMY_SPURS_LEVEL_3;
    public static final LongValue FAITH_ECONOMY_SPURS_LEVEL_4;
    public static final LongValue FAITH_ECONOMY_SPURS_LEVEL_5;
    public static final LongValue FAITH_ECONOMY_SPURS_LEVEL_6;
    public static final LongValue FAITH_ECONOMY_SPURS_LEVEL_7;
    public static final LongValue FAITH_ECONOMY_SPURS_LEVEL_8;
    public static final LongValue FAITH_ECONOMY_SPURS_LEVEL_9;
    public static final LongValue FAITH_ECONOMY_SPURS_LEVEL_10;
    public static final IntValue FAITH_ECONOMY_GEM_ENTRIES;
    public static final IntValue FAITH_BUFF_CRYSTAL_COST;
    public static final IntValue FAITH_BUFF_DURATION_MINUTES;
    public static final DoubleValue FAITH_SCIENCE_CRAFT_CHANCE_PER_LEVEL;
    public static final DoubleValue FAITH_SCIENCE_XP_PER_LEVEL;
    public static final IntValue FAITH_SCIENCE_XP_MIN_LEVEL;
    public static final DoubleValue FAITH_WAR_DAMAGE_PER_LEVEL;
    public static final DoubleValue FAITH_WAR_HEALTH_PER_LEVEL;
    public static final IntValue FAITH_WAR_HEALTH_MIN_LEVEL;
    public static final DoubleValue FAITH_ECONOMY_SELL_PERCENT_PER_LEVEL;
    public static final DoubleValue FAITH_ECONOMY_DROP_CHANCE_PER_LEVEL;
    public static final IntValue FAITH_ECONOMY_DROP_MIN_LEVEL;
    public static final IntValue FAITH_ECONOMY_HIGHLIGHT_LEVEL;
    public static final IntValue FAITH_ECONOMY_HIGHLIGHT_RADIUS;
    public static final IntValue FAITH_ECONOMY_HIGHLIGHT_MAX_BLOCKS;
    public static final IntValue FAITH_ECONOMY_HIGHLIGHT_SCAN_TICKS;
    public static final IntValue DUNGEON_COLOR;
    public static final IntValue DUNGEON_LOOT_COOLDOWN_HOURS;
    public static final BooleanValue DUNGEON_DISABLE_MOB_SPAWNS;
    public static final BooleanValue DUNGEON_ALLOW_SPAWNER_MOBS;
    public static final IntValue MUSIC_DEFAULT_RADIUS;
    public static final IntValue MUSIC_MAX_RADIUS;
    public static final IntValue MUSIC_MAX_TRACK_BYTES;
    public static final IntValue MUSIC_MAX_TRACKS;
    public static final IntValue MUSIC_MAX_STORAGE_MEGABYTES;
    public static final IntValue MUSIC_DOWNLOAD_KILOBYTES_PER_SECOND;
    public static final BooleanValue MUSIC_ALLOW_FACTION_UPLOAD;
    public static final BooleanValue MUSIC_REDSTONE_CONTROL;

    static {
        Builder builder = new Builder();
        builder.push("factions");
        STARTER_CLAIM_SIZE = builder.defineInRange("starterClaimSize", 3, 1, 15);
        FREE_CLAIMS = builder.defineInRange("freeClaims", 9, 1, 225);
        REQUIRE_ADJACENT = builder.define("requireAdjacent", true);
        PROTECT_EXPLOSIONS = builder.define("protectExplosions", true);
        EXPANSION_BASE_COST = builder.defineInRange("expansionBaseCost", 100L, 0L, Long.MAX_VALUE);
        EXPANSION_GROWTH = builder.defineInRange("expansionGrowth", 0.15D, 0D, 100D);
        UNCLAIM_REFUND_PERCENT = builder.defineInRange("unclaimRefundPercent", 0.5D, 0D, 1D);
        ORE_BONUS_CHANCE = builder.defineInRange("oreBonusChance", 0.1D, 0D, 1D);
        MINER_MINING_SPEED_BONUS = builder.defineInRange("minerMiningSpeedBonus", 0.05D, 0D, 10D);
        HARVEST_BONUS_CHANCE = builder.defineInRange("harvestBonusChance", 0.15D, 0D, 1D);
        FARMER_BREEDING_TWIN_CHANCE = builder.defineInRange("farmerBreedingTwinChance", 0.20D, 0D, 1D);
        BUILDER_DISCOUNT = builder.defineInRange("builderDiscount", 0.2D, 0D, 1D);
        BUILDER_OUTPOST_SIZE = builder.defineInRange("builderOutpostSize", 3, 1, 15);
        ASSASSIN_BACK_DAMAGE_MULTIPLIER = builder.defineInRange("assassinBackDamageMultiplier", 1.25D, 1D, 100D);
        HOOKAH_ARMOR_BONUS = builder.defineInRange("hookahArmorBonus", 2.0D, 0D, 100D);
        HOOKAH_SPEED_BONUS = builder.defineInRange("hookahSpeedBonus", 0.08D, 0D, 10D);
        HOOKAH_DAMAGE_MULTIPLIER = builder.defineInRange("hookahDamageMultiplier", 1.15D, 1D, 100D);
        MERCHANT_SELL_BONUS_PERCENT = builder.defineInRange("merchantSellBonusPercent", 0.10D, 0D, 100D);
        MERCHANT_TREASURY_INCOME_INTERVAL_HOURS = builder.defineInRange("merchantTreasuryIncomeIntervalHours", 50, 1, 8760);
        MERCHANT_TREASURY_INCOME_PERCENT = builder.defineInRange("merchantTreasuryIncomePercent", 0.15D, 0D, 100D);
        NOMAD_MOUNT_SPEED_BONUS = builder.defineInRange("nomadMountSpeedBonus", 0.15D, 0D, 10D);
        RESEARCHER_RESEARCH_SPEED_BONUS = builder.defineInRange("researcherResearchSpeedBonus", 0.20D, 0D, 10D);
        ENCHANTER_ANVIL_MAX_COST = builder.defineInRange("enchanterAnvilMaxCost", 60, 1, Integer.MAX_VALUE);
        ENCHANTER_PRIOR_WORK_MAX_COST = builder.defineInRange("enchanterPriorWorkMaxCost", 12, 0, Integer.MAX_VALUE);
        ENCHANTER_LEVEL_COST_CAP = builder.defineInRange("enchanterLevelCostCap", 12, 1, Integer.MAX_VALUE);
        builder.pop();

        builder.push("research");
        RESEARCH_CRYSTAL_COST_TIER_1 = builder.defineInRange("crystalCostTier1", 0, 0, 4096);
        RESEARCH_CRYSTAL_COST_TIER_2 = builder.defineInRange("crystalCostTier2", 240, 0, 4096);
        RESEARCH_CRYSTAL_COST_TIER_3 = builder.defineInRange("crystalCostTier3", 360, 0, 4096);
        RESEARCH_CRYSTAL_COST_TIER_4 = builder.defineInRange("crystalCostTier4", 480, 0, 4096);
        RESEARCH_CRYSTAL_COST_TIER_5 = builder.defineInRange("crystalCostTier5", 640, 0, 4096);
        RESEARCH_CRYSTAL_COST_TIER_6_PLUS = builder.defineInRange("crystalCostTier6Plus", 960, 0, 4096);
        builder.push("legacy");
        LEGACY_INFLUENCE_COST_LEVEL_1 = builder.defineInRange("influencePerTypeLevel1", 150L, 0L, Long.MAX_VALUE);
        LEGACY_INFLUENCE_COST_LEVEL_2 = builder.defineInRange("influencePerTypeLevel2", 250L, 0L, Long.MAX_VALUE);
        LEGACY_INFLUENCE_COST_LEVEL_3 = builder.defineInRange("influencePerTypeLevel3", 350L, 0L, Long.MAX_VALUE);
        LEGACY_INFLUENCE_COST_LEVEL_4 = builder.defineInRange("influencePerTypeLevel4", 450L, 0L, Long.MAX_VALUE);
        LEGACY_INFLUENCE_COST_LEVEL_5 = builder.defineInRange("influencePerTypeLevel5", 550L, 0L, Long.MAX_VALUE);
        LEGACY_CRYSTAL_COST_LEVEL_1 = builder.defineInRange("crystalPerTypeLevel1", 64, 0, 4096);
        LEGACY_CRYSTAL_COST_LEVEL_2 = builder.defineInRange("crystalPerTypeLevel2", 128, 0, 4096);
        LEGACY_CRYSTAL_COST_LEVEL_3 = builder.defineInRange("crystalPerTypeLevel3", 256, 0, 4096);
        LEGACY_CRYSTAL_COST_LEVEL_4 = builder.defineInRange("crystalPerTypeLevel4", 512, 0, 4096);
        LEGACY_CRYSTAL_COST_LEVEL_5 = builder.defineInRange("crystalPerTypeLevel5", 1024, 0, 4096);
        builder.pop();
        builder.pop();

        builder.push("traderEvents");
        CONTRABAND_LIFETIME_MINUTES = builder.defineInRange("contrabandLifetimeMinutes", 60, 1, 10080);
        CONTRABAND_COOLDOWN_MINUTES = builder.defineInRange("contrabandCooldownMinutes", 10, 0, 43200);
        CONTRABAND_OFFER_COUNT_MIN = builder.defineInRange("contrabandOfferCountMin", 3, 1, 16);
        CONTRABAND_OFFER_COUNT_MAX = builder.defineInRange("contrabandOfferCountMax", 4, 1, 16);
        CONTRABAND_MAX_SPAWN_DISTANCE = builder.defineInRange("contrabandMaxSpawnDistanceFromSpawn", 512, 16, 10000);
        CONTRABAND_SPAWN_ATTEMPTS = builder.defineInRange("contrabandSpawnAttempts", 8, 1, 128);
        WANDERING_ROLL_INTERVAL_MINUTES = builder.defineInRange("wanderingRollIntervalMinutes", 60, 1, 10080);
        WANDERING_CHANCE_PERCENT = builder.defineInRange("wanderingChancePercent", 10, 0, 100);
        WANDERING_LIFETIME_MINUTES = builder.defineInRange("wanderingLifetimeMinutes", 45, 1, 10080);
        WANDERING_COOLDOWN_MINUTES = builder.defineInRange("wanderingCooldownMinutes", 360, 1, 43200);
        WANDERING_OFFER_COUNT_MIN = builder.defineInRange("wanderingOfferCountMin", 3, 1, 16);
        WANDERING_OFFER_COUNT_MAX = builder.defineInRange("wanderingOfferCountMax", 6, 1, 16);
        WANDERING_SPAWN_ATTEMPTS = builder.defineInRange("wanderingSpawnAttempts", 8, 1, 128);
        TRADER_WORLD_BORDER_MARGIN = builder.defineInRange("traderWorldBorderMargin", 16, 1, 512);
        builder.pop();

        builder.push("netherSessions");
        NETHER_SESSION_DURATION_MINUTES = builder.defineInRange("sessionDurationMinutes", 90, 1, 300);
        NETHER_REQUIRE_FULL_SESSION_BEFORE_CLOSE = builder
                .comment("Deprecated compatibility setting. Late Nether entries are always allowed until 23:00 Moscow time.")
                .define("requireFullSessionBeforeClose", false);
        NETHER_LANDING_MIN_RADIUS = builder.defineInRange("landingMinRadius", 1000, 0, 100000);
        NETHER_LANDING_MAX_RADIUS = builder.defineInRange("landingMaxRadius", 5000, 1, 100000);
        NETHER_LANDING_ATTEMPTS = builder.defineInRange("landingAttempts", 8, 1, 8);
        NETHER_LANDING_MINIMUM_SEPARATION = builder.defineInRange("landingMinimumSeparation", 512, 0, 100000);
        builder.pop();

        builder.push("resourceDeposits");
        RESOURCE_WORLD_BORDER_SIZE = builder
            .comment("One-time overworld border size in blocks; centered on the shared spawn.")
            .defineInRange("worldBorderSize", 20_000, 1_000, 60_000_000);
        RESOURCE_BLUE_RADIUS = builder.defineInRange("safeZoneRadius", 200, 0, 1000000);
        RESOURCE_YELLOW_RADIUS = builder.defineInRange("yellowZoneRadius", 5000, 1, 1000000);
        RESOURCE_RED_RADIUS = builder.defineInRange("redRadius", 8000, 1, 1000000);
        builder.push("zoneProfiles");
        RESOURCE_BLUE_SIZE_MULTIPLIER = builder
            .comment("Ore vein size multiplier inside the spawn zone; 0 disables ore entirely.")
            .defineInRange("blueOreSizeMultiplier", 0.00D, 0.0D, 10.0D);
        RESOURCE_YELLOW_MIN_VEIN_SIZE = builder
            .comment("Ore vein size range in the yellow zone; every vein rolls between these values.")
            .defineInRange("yellowOreMinVeinSize", 1, 0, 64);
        RESOURCE_YELLOW_MAX_VEIN_SIZE = builder.defineInRange("yellowOreMaxVeinSize", 4, 0, 64);
        RESOURCE_RED_MIN_VEIN_SIZE = builder
            .comment("Ore vein size range in the red zone.")
            .defineInRange("redOreMinVeinSize", 5, 0, 64);
        RESOURCE_RED_MAX_VEIN_SIZE = builder.defineInRange("redOreMaxVeinSize", 7, 0, 64);
        RESOURCE_BLACK_SIZE_MULTIPLIER = builder
            .comment("Black zone keeps vanilla-shaped veins scaled by this multiplier.")
            .defineInRange("blackOreSizeMultiplier", 1.20D, 0.0D, 10.0D);
        builder.pop();
        RESOURCE_YELLOW_CLUSTER_SPACING_CHUNKS =
                builder.defineInRange("yellowSurfaceClusterSpacingChunks", 9, 1, 64);
        RESOURCE_RED_CLUSTER_SPACING_CHUNKS =
                builder.defineInRange("redSurfaceClusterSpacingChunks", 6, 1, 64);
        builder.pop();

        builder.push("raids");
        RAID_GRACE_PERIOD_HOURS = builder
            .comment("Real-time hours after a faction is founded before it can be raided.")
            .defineInRange("gracePeriodHours", 48, 0, 8760);
        RAID_CHANCE_PERCENT = builder
            .comment("Percent chance a raid triggers on each roll (only when at least one member is online).")
            .defineInRange("chancePercent", 15, 0, 100);
        RAID_ROLL_INTERVAL_HOURS = builder
            .comment("Real-time hours between raid rolls for a faction.")
            .defineInRange("rollIntervalHours", 4, 1, 8760);
        RAID_WARNING_SECONDS = builder
            .comment("Seconds of warning before raiders spawn.")
            .defineInRange("warningSeconds", 300, 5, 7200);
        RAID_TOAST_SECONDS = builder
            .comment("Seconds a faction notice toast (raid warning, alliance, etc.) stays on screen.")
            .defineInRange("toastSeconds", 6, 1, 60);
        RAID_COMBAT_MINUTES = builder
            .comment("Minutes the defenders have to kill every raider before the raid is lost.")
            .defineInRange("combatMinutes", 10, 1, 240);
        RAID_REWARD_PER_RAIDER_MIN = builder
            .comment("Minimum treasury reward per slain raider on victory.")
            .defineInRange("rewardPerRaiderMin", 25L, 0L, Long.MAX_VALUE);
        RAID_REWARD_PER_RAIDER_MAX = builder
            .comment("Maximum treasury reward per slain raider on victory.")
            .defineInRange("rewardPerRaiderMax", 100L, 0L, Long.MAX_VALUE);
        RAID_TREASURY_STEAL_PERCENT = builder
            .comment("Fraction of the treasury stolen when a raid on the main territory is lost.")
            .defineInRange("treasuryStealPercent", 0.10D, 0D, 1D);
        RAID_TREASURY_STEAL_MIN = builder
            .comment("Minimum spurs stolen when a raid on the main territory is lost.")
            .defineInRange("treasuryStealMin", 100L, 0L, Long.MAX_VALUE);
        RAID_INVENTORY_STEAL_MAX = builder
            .comment("Maximum spurs taken from online members' inventories when the treasury is empty.")
            .defineInRange("inventoryStealMax", 500L, 0L, Long.MAX_VALUE);
        builder.pop();

        builder.push("outposts");
        OUTPOST_CHARTER_COST = builder
            .comment("Spurs charged by the spawn trader for an outpost charter.")
            .defineInRange("charterCost", 6000L, 0L, Long.MAX_VALUE);
        OUTPOST_DRILL_COST = builder
            .comment("Spurs charged by the spawn trader for a drill.")
            .defineInRange("drillCost", 3500L, 0L, Long.MAX_VALUE);
        FACTION_TABLE_COST = builder.defineInRange("factionTableCost", 800L, 0L, Long.MAX_VALUE);
        ACCESS_TOOL_COST = builder.defineInRange("accessToolCost", 400L, 0L, Long.MAX_VALUE);
        OUTPOST_DRILL_INTERVAL_SECONDS = builder
            .comment("Seconds between drill resource outputs (base production 12h = 43200).")
            .defineInRange("drillIntervalSeconds", 43200, 1, 86400);
        builder.pop();

        builder.push("influence");
        INFLUENCE_DECAY_PERCENT = builder
            .comment("Fraction of the influence above the safe baseline that decays each interval.")
            .defineInRange("decayPercent", 0.10D, 0D, 1D);
        INFLUENCE_DECAY_INTERVAL_HOURS = builder
            .comment("Real-time hours between influence decay passes.")
            .defineInRange("decayIntervalHours", 12, 1, 8760);
        INFLUENCE_BASELINE_PER_NODE = builder
            .comment("Safe baseline added to an influence type when a research node of that type completes.")
            .defineInRange("baselinePerNode", 25L, 0L, Long.MAX_VALUE);
        INFLUENCE_CRYSTAL_TO_INFLUENCE = builder
            .comment("Influence granted per crystal turned in at the faction table.")
            .defineInRange("crystalToInfluence", 10L, 0L, Long.MAX_VALUE);
        INFLUENCE_KILL_INFLUENCE = builder
            .comment("Military influence awarded for killing an enemy faction player.")
            .defineInRange("killInfluence", 15L, 0L, Long.MAX_VALUE);
        INFLUENCE_KILL_CAP_PER_VICTIM = builder
            .comment("Maximum kill-influence awards per killer/victim pair within the cap window.")
            .defineInRange("killCapPerVictim", 5, 0, Integer.MAX_VALUE);
        INFLUENCE_KILL_CAP_HOURS = builder
            .comment("Real-time hours the kill-influence anti-farm cap window lasts.")
            .defineInRange("killCapHours", 24, 1, 8760);
        INFLUENCE_WAR_WIN_INFLUENCE = builder
            .comment("Military influence awarded to a faction that wins a war.")
            .defineInRange("warWinInfluence", 200L, 0L, Long.MAX_VALUE);
        INFLUENCE_WAR_JOIN_REWARD = builder
            .comment("Secondary influence awarded to an ally that joined the winning side; randomly economic or science.")
            .defineInRange("warJoinReward", 100L, 0L, Long.MAX_VALUE);
        INFLUENCE_CRAFT_PER_ITEM = builder
            .comment("Science influence granted per crafted modded (non-vanilla) item.")
            .defineInRange("craftInfluencePerItem", 5L, 0L, Long.MAX_VALUE);
        INFLUENCE_CRAFT_CAP_PER_ITEM = builder
            .comment("Per-player crafts of one item type that grant influence within the cap window; further crafts give nothing.")
            .defineInRange("craftInfluenceCapPerItem", 64, 0, Integer.MAX_VALUE);
        INFLUENCE_CRAFT_CAP_HOURS = builder
            .comment("Real-time hours of the per-item craft-influence anti-farm window (wall-clock, runs while offline).")
            .defineInRange("craftInfluenceCapHours", 24, 1, 8760);
        INFLUENCE_VILLAGER_TRADE = builder
            .comment("Economic influence granted per trade with an ordinary villager.")
            .defineInRange("villagerTradeInfluence", 2L, 0L, Long.MAX_VALUE);
        INFLUENCE_SELL_PER_THRESHOLD = builder
            .comment("Economic influence granted for each full spursPerEconInfluence chunk of trader earnings.")
            .defineInRange("sellInfluencePerThreshold", 5L, 0L, Long.MAX_VALUE);
        INFLUENCE_MOB_KILLS_PER_AWARD = builder
            .comment("Hostile mobs a member must kill to earn one military influence award.")
            .defineInRange("mobKillsPerAward", 5, 1, Integer.MAX_VALUE);
        INFLUENCE_MOB_KILL_INFLUENCE = builder
            .comment("Military influence per mob-kill award.")
            .defineInRange("mobKillInfluence", 7L, 0L, Long.MAX_VALUE);
        INFLUENCE_MOB_DAILY_CAP = builder
            .comment("Maximum military influence a member can earn from mob kills per 24 real-time hours.")
            .defineInRange("mobKillDailyCap", 210L, 0L, Long.MAX_VALUE);
        INFLUENCE_SPURS_PER_ECON = builder
            .comment("Spurs a member must earn selling to the trader to grant one sell-influence award.")
            .defineInRange("spursPerEconInfluence", 50L, 1L, Long.MAX_VALUE);
        builder.pop();

        builder.push("war");
        WAR_ROLLBACK_CHUNKS_PER_TICK = builder
            .comment("How many snapshotted chunks to roll back per server tick when a war ends.")
            .defineInRange("rollbackChunksPerTick", 8, 1, 4096);
        WAR_AUTO_END_TICKS = builder
            .comment("Game-time ticks after which an active war ends automatically (0 disables auto-end).")
            .defineInRange("autoEndTicks", 0L, 0L, Long.MAX_VALUE);
        WAR_POINTS_GOAL = builder
            .comment("War points a side needs to win the war outright.")
            .defineInRange("warPointsGoal", 1000L, 1L, Long.MAX_VALUE);
        WAR_KILL_POINTS = builder
            .comment("War points awarded for killing an enemy faction player.")
            .defineInRange("killPoints", 25, 0, Integer.MAX_VALUE);
        WAR_BLOCK_BREAK_POINTS = builder
            .comment("War points awarded for destroying an enemy-placed block in their claims (by hand or explosion).")
            .defineInRange("blockBreakPoints", 2, 0, Integer.MAX_VALUE);
        WAR_BLOCK_POINT_CAP_PER_MINUTE = builder
            .comment("Maximum block/explosion war points a faction can earn per minute.")
            .defineInRange("blockPointCapPerMinute", 40, 0, Integer.MAX_VALUE);
        WAR_KILL_POINT_CAP_PER_MINUTE = builder
            .comment("Maximum kill war points a faction can earn per minute (anti-snowball; 0 disables the cap).")
            .defineInRange("killPointCapPerMinute", 100, 0, Integer.MAX_VALUE);
        WAR_DECLARE_COOLDOWN_HOURS = builder
            .comment("Real-time hours an attacker faction must wait after its war ends before it can declare a new war.")
            .defineInRange("declareCooldownHours", 48, 0, 8760);
        builder.pop();

        builder.push("chunkloader");
        FORCE_LOAD_SLOTS = builder
            .comment("Base number of chunks a faction may force-load (research adds more).")
            .defineInRange("forceLoadSlots", 15, 0, 4096);
        builder.pop();

        builder.push("integration");
        CLAIM_SYNC_RADIUS_CHUNKS = builder
            .comment("Radius in chunks around each player for which faction claims are streamed to client minimap mods (Xaero).")
            .defineInRange("claimSyncRadiusChunks", 64, 8, 512);
        builder.pop();

        builder.push("market");
        MARKET_BUYBACK_PERCENT = builder
            .comment("Fraction of the base price refunded when a market plot is sold back to the server.")
            .defineInRange("buybackPercent", 0.5D, 0D, 1D);
        builder.pop();

        builder.push("sanctuary");
        SANCTUARY_DISABLE_PVP = builder
            .comment("Cancel all player-versus-player damage inside spawn sanctuary chunks.")
            .define("disablePvp", true);
        SANCTUARY_DISABLE_MOB_SPAWNS = builder
            .comment("Prevent hostile mobs from spawning inside spawn sanctuary chunks.")
            .define("disableMobSpawns", true);
        SANCTUARY_EXPLOSION_IMMUNITY = builder
            .comment("Make spawn sanctuary chunks immune to all explosions (creeper, TNT, etc.).")
            .define("explosionImmunity", true);
        SANCTUARY_DISABLE_FIRE_SPREAD = builder
            .comment("Extinguish fire inside spawn sanctuary chunks and stop it from spreading into or within them.")
            .define("disableFireSpread", true);
        SANCTUARY_DISABLE_LIGHTNING = builder
            .comment("Stop lightning bolts from striking inside spawn sanctuary chunks (weather, lightning rods, tridents and /summon alike).")
            .define("disableLightning", true);
        builder.pop();

        builder.push("quarry");
        QUARRY_LEVEL_2_COST = builder
            .comment("Treasury cost to upgrade an activated quarry to level 2.")
            .defineInRange("level2Cost", 10_000L, 0L, Long.MAX_VALUE);
        QUARRY_LEVEL_3_COST = builder
            .comment("Treasury cost to upgrade a quarry to level 3.")
            .defineInRange("level3Cost", 25_000L, 0L, Long.MAX_VALUE);
        QUARRY_LEVEL_4_COST = builder
            .comment("Treasury cost to upgrade a quarry to level 4.")
            .defineInRange("level4Cost", 50_000L, 0L, Long.MAX_VALUE);
        QUARRY_LEVEL_5_COST = builder
            .comment("Treasury cost to upgrade a quarry to level 5.")
            .defineInRange("level5Cost", 100_000L, 0L, Long.MAX_VALUE);
        builder.pop();

        builder.push("lagtax");
        LAGTAX_QUOTA_MS = builder
            .comment("Free ms/tick quota per faction summed over all its chunks.")
            .defineInRange("quotaMs", 2.0D, 0D, 50D);
        LAGTAX_TIER1_LIMIT_MS = builder
            .comment("Excess above the quota (ms/tick) charged at the tier 1 price.")
            .defineInRange("tier1LimitMs", 2.0D, 0D, 50D);
        LAGTAX_TIER2_LIMIT_MS = builder
            .comment("Excess above the quota (ms/tick) up to which the tier 2 price applies; above it tier 3.")
            .defineInRange("tier2LimitMs", 5.0D, 0D, 50D);
        LAGTAX_TIER1_PRICE = builder
            .comment("Spurs per 0.1 ms/tick of tier 1 excess per game day.")
            .defineInRange("tier1Price", 100L, 0L, Long.MAX_VALUE);
        LAGTAX_TIER2_PRICE = builder
            .comment("Spurs per 0.1 ms/tick of tier 2 excess per game day.")
            .defineInRange("tier2Price", 250L, 0L, Long.MAX_VALUE);
        LAGTAX_TIER3_PRICE = builder
            .comment("Spurs per 0.1 ms/tick of tier 3 excess per game day.")
            .defineInRange("tier3Price", 600L, 0L, Long.MAX_VALUE);
        LAGTAX_INTERVAL_HOURS = builder
            .comment("Real-time hours between tax collections; tier prices cover one fully ticked period.")
            .defineInRange("taxIntervalHours", 12, 1, 720);
        LAGTAX_WARNING_MINUTES = builder
            .comment("Minutes before a collection at which officers are warned about the upcoming charge.")
            .defineInRange("taxWarningMinutes", 60, 1, 720);
        LAGTAX_MIN_BILL = builder
            .comment("Bills below this many spurs are forgiven at collection time.")
            .defineInRange("minBill", 10L, 0L, Long.MAX_VALUE);
        LAGTAX_HARD_CAP_MS = builder
            .comment("Absolute ms/tick ceiling per faction regardless of money (0 disables).")
            .defineInRange("hardCapMs", 0.0D, 0D, 50D);
        LAGTAX_SAMPLE_INTERVAL_TICKS = builder
            .comment("Profiler samples block entity ticks every Nth server tick.")
            .defineInRange("sampleIntervalTicks", 4, 1, 20);
        LAGTAX_EMA_SECONDS = builder
            .comment("Smoothing window in seconds for the per-chunk load moving average.")
            .defineInRange("emaSeconds", 300, 10, 3600);
        CHUNK_LOAD_PRICE_8H = builder
            .comment("Spurs per chunk per 8-hour block of paid chunk loading.")
            .defineInRange("chunkLoadPrice8h", 500L, 0L, Long.MAX_VALUE);
        CHUNK_LOAD_MAX_DAYS = builder
            .comment("Maximum days ahead a chunk load timer may be extended to.")
            .defineInRange("chunkLoadMaxDays", 7, 1, 365);
        FACTION_METER_COST = builder
            .comment("Spurs charged by the kingdom trader for the faction load meter.")
            .defineInRange("factionMeterCost", 300L, 0L, Long.MAX_VALUE);
        builder.pop();

        builder.push("news");
        NEWS_MAX_ARTICLES_PER_FACTION = builder
            .comment("Maximum stored news articles per faction; publishing beyond evicts the oldest.")
            .defineInRange("maxArticlesPerFaction", 20, 1, 200);
        NEWS_PUBLISH_COOLDOWN_MINUTES = builder
            .comment("Real-time minutes a faction must wait between news publications (0 disables).")
            .defineInRange("publishCooldownMinutes", 10, 0, 10080);
        builder.pop();

        builder.push("scout");
        SCOUT_SMALL_SIZE = builder
            .comment("Side of the small scouting package, in chunks.")
            .defineInRange("smallSizeChunks", 5, 1, 64);
        SCOUT_SMALL_MINUTES = builder
            .comment("Real-time minutes the small scouting package takes.")
            .defineInRange("smallMinutes", 30, 1, 10080);
        SCOUT_SMALL_PRICE = builder
            .comment("Spurs charged from the faction treasury for the small scouting package.")
            .defineInRange("smallPrice", 600L, 0L, Long.MAX_VALUE);
        SCOUT_MEDIUM_SIZE = builder
            .comment("Side of the medium scouting package, in chunks.")
            .defineInRange("mediumSizeChunks", 10, 1, 64);
        SCOUT_MEDIUM_MINUTES = builder
            .comment("Real-time minutes the medium scouting package takes.")
            .defineInRange("mediumMinutes", 50, 1, 10080);
        SCOUT_MEDIUM_PRICE = builder
            .comment("Spurs charged from the faction treasury for the medium scouting package.")
            .defineInRange("mediumPrice", 1300L, 0L, Long.MAX_VALUE);
        SCOUT_LARGE_SIZE = builder
            .comment("Side of the large scouting package, in chunks.")
            .defineInRange("largeSizeChunks", 13, 1, 64);
        SCOUT_LARGE_MINUTES = builder
            .comment("Real-time minutes the large scouting package takes.")
            .defineInRange("largeMinutes", 70, 1, 10080);
        SCOUT_LARGE_PRICE = builder
            .comment("Spurs charged from the faction treasury for the large scouting package.")
            .defineInRange("largePrice", 1500L, 0L, Long.MAX_VALUE);
        SCOUT_CHUNKS_PER_TICK = builder
            .comment("Chunks the scout may load or generate per server tick across all orders.")
            .defineInRange("chunksPerTick", 1, 1, 64);
        SCOUT_MAX_CHUNKS_PER_ORDER = builder
            .comment("Hard cap on the chunks a single scouting order may cover.")
            .defineInRange("maxChunksPerOrder", 400, 1, 4096);
        SCOUT_ALLOWED_DIMENSIONS = builder
            .comment("Dimensions the map scout is allowed to survey.")
            .defineList("allowedDimensions", java.util.List.of("minecraft:overworld"),
                value -> value instanceof String text && !text.isBlank());
        builder.pop();

        builder.push("resourceClusters");
        CLUSTER_UNITS_RICHNESS_1 = builder
            .comment("Resource units a richness 1 surface cluster holds before it runs dry.")
            .defineInRange("richness1Units", 300, 1, 1_000_000);
        CLUSTER_UNITS_RICHNESS_2 = builder
            .comment("Resource units a richness 2 surface cluster holds before it runs dry.")
            .defineInRange("richness2Units", 650, 1, 1_000_000);
        CLUSTER_UNITS_RICHNESS_3 = builder
            .comment("Resource units a richness 3 surface cluster holds before it runs dry.")
            .defineInRange("richness3Units", 1200, 1, 1_000_000);
        CLUSTER_RESET_DAYS = builder
            .comment("Real-time days from the first extraction until a cluster refills completely; 0 refills instantly.")
            .defineInRange("clusterResetDays", 7, 0, 3650);
        CLUSTER_GENERATION = builder
            .comment("Base generation counter mixed into cluster placement; /kingdoms cluster regenerate all adds to it.")
            .defineInRange("clusterGeneration", 0, 0, 1_000_000);
        CLUSTER_MAINTENANCE_CHUNKS_PER_TICK = builder
            .comment("Chunks processed per server tick by /kingdoms cluster reset and regenerate.")
            .defineInRange("maintenanceChunksPerTick", 16, 1, 4096);
        builder.pop();

        builder.push("scorched");
        SCORCHED_DISABLE_MOB_GUNS_IN_YELLOW_ZONE = builder
            .comment("Keep Scorched Guns firearms away from hostile mobs inside the listed resource zones.")
            .define("disableMobGunsInYellowZone", true);
        SCORCHED_MOB_GUN_FREE_ZONES = builder
            .comment("Overworld resource zones cleared of mob firearms: BLUE, YELLOW, RED, BLACK.")
            .defineList("mobGunFreeZones", java.util.List.of("YELLOW"),
                value -> value instanceof String text && !text.isBlank());
        builder.pop();

        builder.push("blackZone");
        BLACK_ZONE_ENABLED = builder
            .comment("Make the black zone slowly kill players who stay in it.")
            .define("enabled", true);
        BLACK_ZONE_EXEMPT_OPERATORS = builder
            .comment("Players with permission level 2 or higher never accumulate the black zone toll.")
            .define("exemptOperators", true);
        BLACK_ZONE_RELEASE_MINUTES = builder
            .comment("Real-time minutes outside the black zone after which the toll and every penalty are dropped.")
            .defineInRange("releaseMinutes", 10, 1, 1440);
        BLACK_ZONE_ACTIONBAR_INTERVAL_SECONDS = builder
            .comment("Seconds between action bar reminders of the accumulated toll when Xaero's minimap is absent.")
            .defineInRange("actionBarIntervalSeconds", 30, 1, 3600);
        BLACK_ZONE_STAGE_ENTRY_MINUTES = builder
            .comment("Minutes of accumulated black zone time at which each stage starts.")
            .defineInRange("stageEntryMinutes", 0, 0, 10080);
        BLACK_ZONE_STAGE_HUNGER_1_MINUTES = builder.defineInRange("stageHunger1Minutes", 20, 0, 10080);
        BLACK_ZONE_STAGE_HEALTH_2_MINUTES = builder.defineInRange("stageHealth2Minutes", 40, 0, 10080);
        BLACK_ZONE_STAGE_HEALTH_4_MINUTES = builder.defineInRange("stageHealth4Minutes", 60, 0, 10080);
        BLACK_ZONE_STAGE_WEAKNESS_MINUTES = builder.defineInRange("stageWeaknessMinutes", 80, 0, 10080);
        BLACK_ZONE_STAGE_HEALTH_6_MINUTES = builder.defineInRange("stageHealth6Minutes", 100, 0, 10080);
        BLACK_ZONE_STAGE_SLOWNESS_MINUTES = builder.defineInRange("stageSlownessMinutes", 120, 0, 10080);
        BLACK_ZONE_STAGE_HUNGER_2_MINUTES = builder.defineInRange("stageHunger2Minutes", 140, 0, 10080);
        BLACK_ZONE_STAGE_MINING_FATIGUE_MINUTES = builder.defineInRange("stageMiningFatigueMinutes", 150, 0, 10080);
        BLACK_ZONE_STAGE_KOLYVAN_MINUTES = builder.defineInRange("stageKolyvanMinutes", 160, 0, 10080);
        BLACK_ZONE_STAGE_POISON_MINUTES = builder.defineInRange("stagePoisonMinutes", 180, 0, 10080);
        BLACK_ZONE_STAGE_WITHER_MINUTES = builder.defineInRange("stageWitherMinutes", 240, 0, 10080);
        BLACK_ZONE_HEALTH_PENALTY_ENTRY = builder
            .comment("Maximum health taken away at each health stage, in half hearts (a full bar is 20).")
            .defineInRange("healthPenaltyEntry", 1, 0, 19);
        BLACK_ZONE_HEALTH_PENALTY_SECOND = builder.defineInRange("healthPenaltySecond", 2, 0, 19);
        BLACK_ZONE_HEALTH_PENALTY_THIRD = builder.defineInRange("healthPenaltyThird", 4, 0, 19);
        BLACK_ZONE_HEALTH_PENALTY_FOURTH = builder.defineInRange("healthPenaltyFourth", 6, 0, 19);
        BLACK_ZONE_KOLYVAN_INTERVAL_MINUTES = builder
            .comment("Real-time minutes between Kolyvan visits while the player stays in the black zone.")
            .defineInRange("kolyvanIntervalMinutes", 5, 1, 1440);
        BLACK_ZONE_KOLYVAN_MIN_DISTANCE = builder
            .comment("Blocks from the player within which Kolyvan appears.")
            .defineInRange("kolyvanMinDistance", 20, 1, 128);
        BLACK_ZONE_KOLYVAN_MAX_DISTANCE = builder.defineInRange("kolyvanMaxDistance", 30, 1, 128);
        BLACK_ZONE_KOLYVAN_LIFETIME_SECONDS = builder
            .comment("Seconds Kolyvan chases the player before he vanishes silently.")
            .defineInRange("kolyvanLifetimeSeconds", 30, 1, 600);
        BLACK_ZONE_KOLYVAN_BLINDNESS_SECONDS = builder
            .comment("Seconds of blindness Kolyvan leaves behind when he reaches the player.")
            .defineInRange("kolyvanBlindnessSeconds", 10, 1, 600);
        BLACK_ZONE_KOLYVAN_REACH_DISTANCE = builder
            .comment("Distance in blocks at which Kolyvan is considered to have caught the player.")
            .defineInRange("kolyvanReachDistance", 2.0D, 0.5D, 16.0D);
        builder.pop();

        builder.push("statues");
        FAITH_CRYSTAL_COST_LEVEL_2 = builder
            .comment("Crystals of the god's own type each faith level costs.")
            .defineInRange("crystalCostLevel2", 32, 0, 100_000);
        FAITH_CRYSTAL_COST_LEVEL_3 = builder.defineInRange("crystalCostLevel3", 96, 0, 100_000);
        FAITH_CRYSTAL_COST_LEVEL_4 = builder.defineInRange("crystalCostLevel4", 192, 0, 100_000);
        FAITH_CRYSTAL_COST_LEVEL_5 = builder.defineInRange("crystalCostLevel5", 384, 0, 100_000);
        FAITH_CRYSTAL_COST_LEVEL_6 = builder.defineInRange("crystalCostLevel6", 768, 0, 100_000);
        FAITH_CRYSTAL_COST_LEVEL_7 = builder.defineInRange("crystalCostLevel7", 960, 0, 100_000);
        FAITH_CRYSTAL_COST_LEVEL_8 = builder.defineInRange("crystalCostLevel8", 1152, 0, 100_000);
        FAITH_CRYSTAL_COST_LEVEL_9 = builder.defineInRange("crystalCostLevel9", 1344, 0, 100_000);
        FAITH_CRYSTAL_COST_LEVEL_10 = builder.defineInRange("crystalCostLevel10", 1536, 0, 100_000);
        FAITH_SCIENCE_MIN_ENTRIES = builder
            .comment("Smallest number of natural offerings the science quest rolls for one level.")
            .defineInRange("scienceMinEntries", 2, 1, 8);
        FAITH_SCIENCE_MAX_ENTRIES = builder
            .comment("Largest number of natural offerings the science quest rolls for one level.")
            .defineInRange("scienceMaxEntries", 5, 1, 8);
        int[] scienceMin = {32, 32, 32, 32, 64, 64, 8, 8, 50};
        int[] scienceMax = {128, 128, 128, 128, 320, 320, 16, 16, 100};
        builder.comment("Amount range rolled per science offering, one pair per faith level.");
        for (int level = 2; level <= 10; level++) {
            FAITH_SCIENCE_COUNT_MIN[level - 2] =
                builder.defineInRange("scienceMinCountLevel" + level, scienceMin[level - 2], 1, 10_000);
            FAITH_SCIENCE_COUNT_MAX[level - 2] =
                builder.defineInRange("scienceMaxCountLevel" + level, scienceMax[level - 2], 1, 10_000);
        }
        FAITH_SCIENCE_SPECIAL_COUNT = builder
            .comment("Offerings of the next tier up the science quest adds at levels 5, 7 and 9.")
            .defineInRange("scienceSpecialCount", 1, 0, 10_000);
        FAITH_WAR_KILLS_LEVEL_2 = builder
            .comment("Kills of players outside the faction the war quest asks for at each faith level.")
            .defineInRange("warKillsLevel2", 2, 0, 10_000);
        FAITH_WAR_KILLS_LEVEL_3 = builder.defineInRange("warKillsLevel3", 4, 0, 10_000);
        FAITH_WAR_KILLS_LEVEL_4 = builder.defineInRange("warKillsLevel4", 6, 0, 10_000);
        FAITH_WAR_KILLS_LEVEL_5 = builder.defineInRange("warKillsLevel5", 9, 0, 10_000);
        FAITH_WAR_KILLS_LEVEL_6 = builder.defineInRange("warKillsLevel6", 12, 0, 10_000);
        FAITH_WAR_KILLS_LEVEL_7 = builder.defineInRange("warKillsLevel7", 15, 0, 10_000);
        FAITH_WAR_KILLS_LEVEL_8 = builder.defineInRange("warKillsLevel8", 19, 0, 10_000);
        FAITH_WAR_KILLS_LEVEL_9 = builder.defineInRange("warKillsLevel9", 23, 0, 10_000);
        FAITH_WAR_KILLS_LEVEL_10 = builder.defineInRange("warKillsLevel10", 27, 0, 10_000);
        FAITH_ECONOMY_SPURS_LEVEL_2 = builder
            .comment("Spurs taken from the faction treasury at each faith level.")
            .defineInRange("economySpursLevel2", 4000L, 0L, 1_000_000_000L);
        FAITH_ECONOMY_SPURS_LEVEL_3 = builder.defineInRange("economySpursLevel3", 8000L, 0L, 1_000_000_000L);
        FAITH_ECONOMY_SPURS_LEVEL_4 = builder.defineInRange("economySpursLevel4", 12_000L, 0L, 1_000_000_000L);
        FAITH_ECONOMY_SPURS_LEVEL_5 = builder.defineInRange("economySpursLevel5", 14_000L, 0L, 1_000_000_000L);
        FAITH_ECONOMY_SPURS_LEVEL_6 = builder.defineInRange("economySpursLevel6", 18_000L, 0L, 1_000_000_000L);
        FAITH_ECONOMY_SPURS_LEVEL_7 = builder.defineInRange("economySpursLevel7", 20_000L, 0L, 1_000_000_000L);
        FAITH_ECONOMY_SPURS_LEVEL_8 = builder.defineInRange("economySpursLevel8", 24_000L, 0L, 1_000_000_000L);
        FAITH_ECONOMY_SPURS_LEVEL_9 = builder.defineInRange("economySpursLevel9", 28_000L, 0L, 1_000_000_000L);
        FAITH_ECONOMY_SPURS_LEVEL_10 = builder.defineInRange("economySpursLevel10", 30_000L, 0L, 1_000_000_000L);
        FAITH_ECONOMY_GEM_ENTRIES = builder
            .comment("Number of jewellery kinds the economy quest rolls from kingdoms:economy_offerings.")
            .defineInRange("economyGemEntries", 2, 1, 8);
        FAITH_ECONOMY_LATE_MIN_LEVEL = builder
            .comment("Faith level from which kingdoms:economy_offerings_late may be rolled at all.")
            .defineInRange("economyLateMinLevel", 6, 1, 10);
        int[] economyMin = {16, 32, 64, 80, 96, 112, 128, 144, 160};
        int[] economyMax = {32, 64, 80, 96, 112, 128, 144, 160, 176};
        builder.comment("Amount range rolled per jewellery kind, one pair per faith level.");
        for (int level = 2; level <= 10; level++) {
            FAITH_ECONOMY_COUNT_MIN[level - 2] =
                builder.defineInRange("economyMinCountLevel" + level, economyMin[level - 2], 1, 10_000);
            FAITH_ECONOMY_COUNT_MAX[level - 2] =
                builder.defineInRange("economyMaxCountLevel" + level, economyMax[level - 2], 1, 10_000);
        }
        FAITH_BUFF_CRYSTAL_COST = builder
            .comment("Crystals of the god's own type a small statue takes to light the faction buff.")
            .defineInRange("buffCrystalCost", 4, 0, 10_000);
        FAITH_BUFF_DURATION_MINUTES = builder
            .comment("Minutes a faction buff stays lit; the same span doubles as its cooldown.")
            .defineInRange("buffDurationMinutes", 30, 1, 10_080);
        FAITH_SCIENCE_CRAFT_CHANCE_PER_LEVEL = builder
            .comment("Chance per faith level of a second copy of anything crafted while the science buff burns.")
            .defineInRange("scienceCraftChancePerLevel", 0.05D, 0.0D, 1.0D);
        FAITH_SCIENCE_XP_PER_LEVEL = builder
            .comment("Extra mob experience per faith level above scienceXpMinLevel minus one.")
            .defineInRange("scienceXpPerLevel", 0.05D, 0.0D, 10.0D);
        FAITH_SCIENCE_XP_MIN_LEVEL = builder
            .comment("Faith level at which the science buff starts paying extra mob experience.")
            .defineInRange("scienceXpMinLevel", 5, 1, 10);
        FAITH_WAR_DAMAGE_PER_LEVEL = builder
            .comment("Flat damage in half hearts added per faith level to everything a believer hits.")
            .defineInRange("warDamagePerLevel", 0.5D, 0.0D, 100.0D);
        FAITH_WAR_HEALTH_PER_LEVEL = builder
            .comment("Extra maximum health in half hearts per faith level above warHealthMinLevel minus one.")
            .defineInRange("warHealthPerLevel", 1.0D, 0.0D, 100.0D);
        FAITH_WAR_HEALTH_MIN_LEVEL = builder
            .comment("Faith level at which the war buff starts adding maximum health.")
            .defineInRange("warHealthMinLevel", 5, 1, 10);
        FAITH_ECONOMY_SELL_PERCENT_PER_LEVEL = builder
            .comment("Sell price bonus per faith level at every mod trader while the economy buff burns.")
            .defineInRange("economySellPercentPerLevel", 0.035D, 0.0D, 10.0D);
        FAITH_ECONOMY_DROP_CHANCE_PER_LEVEL = builder
            .comment("Extra drop chance per faith level above economyDropMinLevel minus one.")
            .defineInRange("economyDropChancePerLevel", 0.05D, 0.0D, 1.0D);
        FAITH_ECONOMY_DROP_MIN_LEVEL = builder
            .comment("Faith level at which the economy buff starts rolling extra ore, mob and fishing drops.")
            .defineInRange("economyDropMinLevel", 5, 1, 10);
        FAITH_ECONOMY_HIGHLIGHT_LEVEL = builder
            .comment("Faith level at which the economy buff starts outlining nearby ores.")
            .defineInRange("economyHighlightLevel", 10, 1, 10);
        FAITH_ECONOMY_HIGHLIGHT_RADIUS = builder
            .comment("Radius in blocks scanned for kingdoms:highlighted_ores while the outline is lit.")
            .defineInRange("economyHighlightRadius", 12, 1, 64);
        FAITH_ECONOMY_HIGHLIGHT_MAX_BLOCKS = builder
            .comment("Hard cap on outlined ore blocks; the nearest ones win.")
            .defineInRange("economyHighlightMaxBlocks", 256, 1, 4096);
        FAITH_ECONOMY_HIGHLIGHT_SCAN_TICKS = builder
            .comment("Client ticks between ore outline rescans.")
            .defineInRange("economyHighlightScanTicks", 20, 1, 600);
        builder.pop();

        builder.push("dungeon");
        DUNGEON_COLOR = builder
            .comment("RRGGBB colour used for dungeon chunks on the Xaero map.")
            .defineInRange("mapColor", 0x9B30FF, 0x000000, 0xFFFFFF);
        DUNGEON_LOOT_COOLDOWN_HOURS = builder
            .comment("Real-time hours between loot refills of a registered dungeon container.")
            .defineInRange("lootCooldownHours", 4, 0, 8760);
        DUNGEON_DISABLE_MOB_SPAWNS = builder
            .comment("Prevent every natural mob spawn (hostile and passive alike) inside dungeon chunks.")
            .define("disableMobSpawns", true);
        DUNGEON_ALLOW_SPAWNER_MOBS = builder
            .comment("Let vanilla spawner blocks keep working inside dungeon chunks while natural spawning is off.")
            .define("allowSpawnerMobs", true);
        builder.pop();

        builder.push("music");
        MUSIC_DEFAULT_RADIUS = builder
            .comment("Blocks a freshly placed music speaker is heard within.")
            .defineInRange("defaultRadius", 500, 1, 2000);
        MUSIC_MAX_RADIUS = builder
            .comment("Hard cap on the radius slider of a music speaker.")
            .defineInRange("maxRadius", 500, 1, 2000);
        MUSIC_MAX_TRACK_BYTES = builder
            .comment("Largest OGG file accepted by the music upload, in bytes.")
            .defineInRange("maxTrackBytes", 8 * 1024 * 1024, 1024, 64 * 1024 * 1024);
        MUSIC_MAX_TRACKS = builder
            .comment("Largest number of tracks the server keeps at once.")
            .defineInRange("maxTracks", 64, 1, 4096);
        MUSIC_MAX_STORAGE_MEGABYTES = builder
            .comment("Total megabytes of music the server stores; uploads past it are refused.")
            .defineInRange("maxStorageMegabytes", 128, 1, 65536);
        MUSIC_DOWNLOAD_KILOBYTES_PER_SECOND = builder
            .comment("Per player kilobytes per second the server spends handing out music files.")
            .defineInRange("downloadKilobytesPerSecond", 256, 8, 65536);
        MUSIC_ALLOW_FACTION_UPLOAD = builder
            .comment("Let faction members place speakers and upload music on their own claims; sanctuary stays operator only.")
            .define("allowFactionUpload", false);
        MUSIC_REDSTONE_CONTROL = builder
            .comment("Let a redstone signal start and stop a music speaker.")
            .define("redstoneControl", true);
        builder.pop();
        SPEC = builder.build();
    }

    private ModConfigSpec() {
    }

    private static boolean isTimezone(String id) {
        try {
            java.time.ZoneId.of(id);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
