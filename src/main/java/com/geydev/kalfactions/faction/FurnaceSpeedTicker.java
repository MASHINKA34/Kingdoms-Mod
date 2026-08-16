package com.geydev.kalfactions.faction;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.config.ModConfigSpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class FurnaceSpeedTicker {
    private static final Map<ResourceKey<Level>, Map<Long, List<AbstractFurnaceBlockEntity>>> INDEX = new HashMap<>();

    private static List<ClaimKey> scanOrder = List.of();
    private static int scanCursor;
    private static int ticksUntilRefresh;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (--ticksUntilRefresh <= 0) {
            ticksUntilRefresh = ModConfigSpec.SMELT_BOOST_REFRESH_TICKS.getAsInt();
            refreshClaims(server);
        }
        rescan(server);
        applyBoost();
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Map<Long, List<AbstractFurnaceBlockEntity>> chunks = INDEX.get(level.dimension());
        if (chunks != null) {
            chunks.remove(event.getChunk().getPos().toLong());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        INDEX.clear();
        scanOrder = List.of();
        scanCursor = 0;
        ticksUntilRefresh = 0;
    }

    private static void refreshClaims(MinecraftServer server) {
        FactionManager manager = FactionManager.get(server);
        Map<ResourceKey<Level>, Set<Long>> boosted = new HashMap<>();
        List<ClaimKey> order = new ArrayList<>();
        for (Faction faction : manager.factions()) {
            if (faction.researchBonusCount("SMELT_SPEED") <= 0) {
                continue;
            }
            for (ClaimKey claim : faction.claims()) {
                Set<Long> chunks = boosted.computeIfAbsent(claim.dimension(), dimension -> new HashSet<>());
                if (chunks.add(claim.chunk().toLong())) {
                    order.add(claim);
                }
            }
        }
        INDEX.keySet().retainAll(boosted.keySet());
        INDEX.forEach((dimension, chunks) -> chunks.keySet().retainAll(boosted.get(dimension)));
        scanOrder = List.copyOf(order);
        if (scanCursor >= scanOrder.size()) {
            scanCursor = 0;
        }
    }

    private static void rescan(MinecraftServer server) {
        if (scanOrder.isEmpty()) {
            return;
        }
        int budget = Math.min(ModConfigSpec.SMELT_BOOST_CHUNKS_PER_TICK.getAsInt(), scanOrder.size());
        for (int visited = 0; visited < budget; visited++) {
            if (scanCursor >= scanOrder.size()) {
                scanCursor = 0;
            }
            rescanChunk(server, scanOrder.get(scanCursor++));
        }
    }

    private static void rescanChunk(MinecraftServer server, ClaimKey claim) {
        Map<Long, List<AbstractFurnaceBlockEntity>> chunks = INDEX.get(claim.dimension());
        long key = claim.chunk().toLong();
        ServerLevel level = server.getLevel(claim.dimension());
        LevelChunk loaded = level == null ? null : level.getChunkSource().getChunkNow(claim.x(), claim.z());
        List<AbstractFurnaceBlockEntity> furnaces = null;
        if (loaded != null) {
            for (BlockEntity blockEntity : loaded.getBlockEntities().values()) {
                if (blockEntity instanceof AbstractFurnaceBlockEntity furnace) {
                    if (furnaces == null) {
                        furnaces = new ArrayList<>();
                    }
                    furnaces.add(furnace);
                }
            }
        }
        if (furnaces == null) {
            if (chunks != null) {
                chunks.remove(key);
            }
            return;
        }
        INDEX.computeIfAbsent(claim.dimension(), dimension -> new HashMap<>()).put(key, List.copyOf(furnaces));
    }

    private static void applyBoost() {
        for (Map<Long, List<AbstractFurnaceBlockEntity>> chunks : INDEX.values()) {
            for (List<AbstractFurnaceBlockEntity> furnaces : chunks.values()) {
                for (int index = 0; index < furnaces.size(); index++) {
                    boost(furnaces.get(index));
                }
            }
        }
    }

    private static void boost(AbstractFurnaceBlockEntity furnace) {
        if (furnace.isRemoved()) {
            return;
        }
        if (!furnace.getBlockState().hasProperty(BlockStateProperties.LIT)
                || !furnace.getBlockState().getValue(BlockStateProperties.LIT)) {
            return;
        }
        furnace.cookingProgress = boostedProgress(furnace.cookingProgress, furnace.cookingTotalTime);
    }

    static int boostedProgress(int cookingProgress, int cookingTotalTime) {
        if (cookingProgress <= 0 || cookingProgress >= cookingTotalTime - 1) {
            return cookingProgress;
        }
        return cookingProgress + 1;
    }

    private FurnaceSpeedTicker() {
    }
}
