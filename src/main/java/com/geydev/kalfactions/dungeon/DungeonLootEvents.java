package com.geydev.kalfactions.dungeon;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class DungeonLootEvents {
    private static final int SCAN_INTERVAL_TICKS = 20;
    private static final int MAX_SCANS_PER_PASS = 8;
    private static final Set<PendingScan> PENDING = new LinkedHashSet<>();
    private static int ticksUntilScan;

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        DungeonManager manager = DungeonManager.get(level);
        if (manager.isEmpty()) {
            return;
        }
        ChunkPos chunk = event.getChunk().getPos();
        if (manager.isDungeon(new ClaimKey(level.dimension(), chunk))) {
            PENDING.add(new PendingScan(level.dimension(), chunk.toLong()));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty() || --ticksUntilScan > 0) {
            return;
        }
        ticksUntilScan = SCAN_INTERVAL_TICKS;
        drain(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PENDING.clear();
        ticksUntilScan = 0;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled()
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (player.isSecondaryUseActive() && !player.getItemInHand(event.getHand()).isEmpty()) {
            return;
        }
        DungeonLoot.onOpen(player, level, event.getPos());
    }

    private static void drain(MinecraftServer server) {
        int scanned = 0;
        for (PendingScan pending : Set.copyOf(PENDING)) {
            if (scanned >= MAX_SCANS_PER_PASS) {
                return;
            }
            PENDING.remove(pending);
            scanned++;
            ServerLevel level = server.getLevel(pending.dimension());
            ChunkPos chunk = new ChunkPos(pending.chunk());
            if (level == null || !level.hasChunk(chunk.x, chunk.z)) {
                continue;
            }
            LevelChunk loaded = level.getChunkSource().getChunkNow(chunk.x, chunk.z);
            if (loaded == null) {
                continue;
            }
            for (BlockPos pos : Set.copyOf(loaded.getBlockEntitiesPos())) {
                DungeonLoot.registerAutomatically(level, pos);
            }
        }
    }

    private record PendingScan(ResourceKey<Level> dimension, long chunk) {
    }

    private DungeonLootEvents() {
    }
}
