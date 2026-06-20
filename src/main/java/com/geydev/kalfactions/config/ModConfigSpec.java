package com.geydev.kalfactions.config;

import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
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
    public static final DoubleValue WARRIOR_DAMAGE_MULTIPLIER;
    public static final DoubleValue ORE_BONUS_CHANCE;
    public static final DoubleValue HARVEST_BONUS_CHANCE;
    public static final DoubleValue CRAFT_BONUS_CHANCE;
    public static final DoubleValue BUILDER_DISCOUNT;
    public static final LongValue INFLUENCE_PER_CHUNK_PER_DAY;
    public static final LongValue CREATION_COST;
    public static final IntValue WAR_ROLLBACK_CHUNKS_PER_TICK;
    public static final LongValue WAR_AUTO_END_TICKS;
    public static final IntValue CLAIM_SYNC_RADIUS_CHUNKS;
    public static final IntValue RAID_GRACE_PERIOD_HOURS;
    public static final IntValue RAID_CHANCE_PERCENT;
    public static final IntValue RAID_ROLL_INTERVAL_HOURS;
    public static final IntValue RAID_WARNING_SECONDS;
    public static final IntValue RAID_COMBAT_MINUTES;
    public static final LongValue RAID_REWARD_PER_RAIDER_MIN;
    public static final LongValue RAID_REWARD_PER_RAIDER_MAX;
    public static final DoubleValue RAID_TREASURY_STEAL_PERCENT;
    public static final LongValue RAID_TREASURY_STEAL_MIN;
    public static final LongValue RAID_INVENTORY_STEAL_MAX;
    public static final LongValue OUTPOST_CHARTER_COST;
    public static final LongValue OUTPOST_DRILL_COST;
    public static final IntValue OUTPOST_DRILL_INTERVAL_SECONDS;
    public static final DoubleValue INFLUENCE_DECAY_PERCENT;
    public static final IntValue INFLUENCE_DECAY_INTERVAL_HOURS;
    public static final LongValue INFLUENCE_BASELINE_PER_NODE;
    public static final LongValue INFLUENCE_CRYSTAL_TO_INFLUENCE;
    public static final LongValue INFLUENCE_KILL_INFLUENCE;
    public static final IntValue INFLUENCE_KILL_CAP_PER_VICTIM;
    public static final IntValue INFLUENCE_KILL_CAP_HOURS;
    public static final LongValue INFLUENCE_WAR_WIN_INFLUENCE;
    public static final DoubleValue INFLUENCE_CRAFT_CHANCE;
    public static final LongValue INFLUENCE_FURNACE_TICK;
    public static final LongValue INFLUENCE_SPURS_PER_ECON;
    public static final LongValue WAR_POINTS_GOAL;
    public static final IntValue WAR_BLOCK_BREAK_POINTS;
    public static final IntValue WAR_KILL_POINTS;
    public static final IntValue WAR_BLOCK_POINT_CAP_PER_MINUTE;
    public static final IntValue FORCE_LOAD_SLOTS;

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
        WARRIOR_DAMAGE_MULTIPLIER = builder.defineInRange("warriorDamageMultiplier", 1.1D, 1D, 100D);
        ORE_BONUS_CHANCE = builder.defineInRange("oreBonusChance", 0.1D, 0D, 1D);
        HARVEST_BONUS_CHANCE = builder.defineInRange("harvestBonusChance", 0.15D, 0D, 1D);
        CRAFT_BONUS_CHANCE = builder.defineInRange("craftBonusChance", 0.2D, 0D, 1D);
        BUILDER_DISCOUNT = builder.defineInRange("builderDiscount", 0.2D, 0D, 1D);
        INFLUENCE_PER_CHUNK_PER_DAY = builder.defineInRange("influencePerChunkPerDay", 1L, 0L, Long.MAX_VALUE);
        CREATION_COST = builder
            .comment("Spurs charged from a player's inventory when founding a faction.")
            .defineInRange("creationCost", 500L, 0L, Long.MAX_VALUE);
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
            .defineInRange("charterCost", 2000L, 0L, Long.MAX_VALUE);
        OUTPOST_DRILL_COST = builder
            .comment("Spurs charged by the spawn trader for a drill.")
            .defineInRange("drillCost", 1500L, 0L, Long.MAX_VALUE);
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
            .defineInRange("warWinInfluence", 100L, 0L, Long.MAX_VALUE);
        INFLUENCE_CRAFT_CHANCE = builder
            .comment("Chance a member crafting on faction territory grants 1 science influence.")
            .defineInRange("craftInfluenceChance", 0.1D, 0D, 1D);
        INFLUENCE_FURNACE_TICK = builder
            .comment("Science influence per minute for each lit furnace inside faction claims.")
            .defineInRange("furnaceTickInfluence", 1L, 0L, Long.MAX_VALUE);
        INFLUENCE_SPURS_PER_ECON = builder
            .comment("Spurs a member must earn selling to the trader to grant 1 economic influence.")
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
            .defineInRange("warPointsGoal", 100L, 1L, Long.MAX_VALUE);
        WAR_KILL_POINTS = builder
            .comment("War points awarded for killing an enemy faction player.")
            .defineInRange("killPoints", 10, 0, Integer.MAX_VALUE);
        WAR_BLOCK_BREAK_POINTS = builder
            .comment("War points awarded for breaking an enemy-placed block in their claims.")
            .defineInRange("blockBreakPoints", 1, 0, Integer.MAX_VALUE);
        WAR_BLOCK_POINT_CAP_PER_MINUTE = builder
            .comment("Maximum block-break war points a faction can earn per minute.")
            .defineInRange("blockPointCapPerMinute", 30, 0, Integer.MAX_VALUE);
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
        SPEC = builder.build();
    }

    private ModConfigSpec() {
    }
}
