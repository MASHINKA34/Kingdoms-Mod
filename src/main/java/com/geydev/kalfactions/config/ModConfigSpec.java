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
    public static final IntValue RESOURCE_BLUE_RADIUS;
    public static final IntValue RESOURCE_YELLOW_RADIUS;
    public static final IntValue RESOURCE_CELL_SIZE;
    public static final DoubleValue RESOURCE_BASE_DENSITY;
    public static final BooleanValue RESOURCE_AUTO_CYCLE;
    public static final IntValue RESOURCE_CYCLE_DAYS;
    public static final IntValue RESOURCE_CYCLE_RESET_HOUR;
    public static final IntValue RESOURCE_GENERATION_BLOCKS_PER_TICK;
    public static final IntValue RESOURCE_CLEANUP_BLOCKS_PER_TICK;
    public static final IntValue RESOURCE_MIN_RESERVE;
    public static final IntValue RESOURCE_MAX_RESERVE;
    public static final IntValue RESOURCE_MAX_PHYSICAL_BLOCKS;
    public static final IntValue RESEARCH_CRYSTAL_COST_TIER_1;
    public static final IntValue RESEARCH_CRYSTAL_COST_TIER_2;
    public static final IntValue RESEARCH_CRYSTAL_COST_TIER_3;
    public static final IntValue RESEARCH_CRYSTAL_COST_TIER_4;
    public static final IntValue RESEARCH_CRYSTAL_COST_TIER_5;
    public static final IntValue RESEARCH_CRYSTAL_COST_TIER_6_PLUS;
    public static final IntValue CONTRABAND_LIFETIME_MINUTES;
    public static final IntValue CONTRABAND_COOLDOWN_MINUTES;
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
    public static final IntValue NETHER_WIPE_INTERVAL_DAYS;
    public static final IntValue NETHER_WIPE_HOUR;
    public static final ConfigValue<String> NETHER_WIPE_TIMEZONE;

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
        RESEARCH_CRYSTAL_COST_TIER_1 = builder.defineInRange("crystalCostTier1", 12, 0, 4096);
        RESEARCH_CRYSTAL_COST_TIER_2 = builder.defineInRange("crystalCostTier2", 24, 0, 4096);
        RESEARCH_CRYSTAL_COST_TIER_3 = builder.defineInRange("crystalCostTier3", 36, 0, 4096);
        RESEARCH_CRYSTAL_COST_TIER_4 = builder.defineInRange("crystalCostTier4", 48, 0, 4096);
        RESEARCH_CRYSTAL_COST_TIER_5 = builder.defineInRange("crystalCostTier5", 64, 0, 4096);
        RESEARCH_CRYSTAL_COST_TIER_6_PLUS = builder.defineInRange("crystalCostTier6Plus", 96, 0, 4096);
        builder.pop();

        builder.push("traderEvents");
        CONTRABAND_LIFETIME_MINUTES = builder.defineInRange("contrabandLifetimeMinutes", 60, 1, 10080);
        CONTRABAND_COOLDOWN_MINUTES = builder.defineInRange("contrabandCooldownMinutes", 240, 1, 43200);
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
        NETHER_REQUIRE_FULL_SESSION_BEFORE_CLOSE = builder.define("requireFullSessionBeforeClose", true);
        NETHER_LANDING_MIN_RADIUS = builder.defineInRange("landingMinRadius", 1000, 0, 100000);
        NETHER_LANDING_MAX_RADIUS = builder.defineInRange("landingMaxRadius", 5000, 1, 100000);
        NETHER_LANDING_ATTEMPTS = builder.defineInRange("landingAttempts", 8, 1, 64);
        NETHER_LANDING_MINIMUM_SEPARATION = builder.defineInRange("landingMinimumSeparation", 512, 0, 100000);
        NETHER_WIPE_INTERVAL_DAYS = builder.defineInRange("wipeIntervalDays", 7, 1, 3650);
        NETHER_WIPE_HOUR = builder.defineInRange("wipeHour", 23, 0, 23);
        NETHER_WIPE_TIMEZONE = builder.define(
                "wipeTimezone",
                "Europe/Moscow",
                value -> value instanceof String id && isTimezone(id)
        );
        builder.pop();

        builder.push("resourceDeposits");
        RESOURCE_BLUE_RADIUS = builder.defineInRange("blueRadius", 5000, 0, 1000000);
        RESOURCE_YELLOW_RADIUS = builder.defineInRange("yellowRadius", 8000, 1, 1000000);
        RESOURCE_CELL_SIZE = builder.defineInRange("cellSizeBlocks", 256, 64, 4096);
        RESOURCE_BASE_DENSITY = builder.defineInRange("baseDensity", 0.40D, 0.0D, 1.0D);
        RESOURCE_AUTO_CYCLE = builder.define("automaticCycle", true);
        RESOURCE_CYCLE_DAYS = builder.defineInRange("cycleDays", 7, 1, 365);
        RESOURCE_CYCLE_RESET_HOUR = builder.defineInRange("cycleResetHour", 0, 0, 23);
        RESOURCE_GENERATION_BLOCKS_PER_TICK = builder.defineInRange("generationBlocksPerTick", 24, 1, 4096);
        RESOURCE_CLEANUP_BLOCKS_PER_TICK = builder.defineInRange("cleanupBlocksPerTick", 24, 1, 4096);
        RESOURCE_MIN_RESERVE = builder.defineInRange("minimumBaseReserve", 100, 1, 1000000);
        RESOURCE_MAX_RESERVE = builder.defineInRange("maximumBaseReserve", 200, 1, 1000000);
        RESOURCE_MAX_PHYSICAL_BLOCKS = builder.defineInRange("maximumPhysicalBlocks", 120, 8, 4096);
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
