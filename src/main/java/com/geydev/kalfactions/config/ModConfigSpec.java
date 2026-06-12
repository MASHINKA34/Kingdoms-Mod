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
    public static final IntValue WAR_ROLLBACK_CHUNKS_PER_TICK;
    public static final LongValue WAR_AUTO_END_TICKS;
    public static final IntValue CLAIM_SYNC_RADIUS_CHUNKS;

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
        builder.pop();

        builder.push("war");
        WAR_ROLLBACK_CHUNKS_PER_TICK = builder
            .comment("How many snapshotted chunks to roll back per server tick when a war ends.")
            .defineInRange("rollbackChunksPerTick", 8, 1, 4096);
        WAR_AUTO_END_TICKS = builder
            .comment("Game-time ticks after which an active war ends automatically (0 disables auto-end).")
            .defineInRange("autoEndTicks", 0L, 0L, Long.MAX_VALUE);
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
