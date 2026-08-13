package com.geydev.kalfactions.dungeon;

import com.geydev.kalfactions.block.DungeonChestBlockEntity;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.net.ClaimSyncManager;
import com.geydev.kalfactions.net.FactionServerHooks;
import com.geydev.kalfactions.registry.ModBlocks;
import com.geydev.kalfactions.territory.WorldZonePolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DungeonService {
    private static final double MAX_CORE_DISTANCE_SQR = 64.0D;
    private static final long ACTION_COOLDOWN_TICKS = 2L;
    private static final long EDIT_COOLDOWN_TICKS = 1L;
    private static final ConcurrentHashMap<UUID, Long> LAST_ACTION_TICK = new ConcurrentHashMap<>();

    public static void clearRateLimit(UUID playerId) {
        LAST_ACTION_TICK.remove(playerId);
    }

    public static void open(ServerPlayer player, BlockPos corePos) {
        if (!validateCore(player, corePos)) {
            return;
        }
        DungeonManager.DungeonView dungeon = ensure(player.serverLevel(), corePos);
        if (dungeon == null) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.dungeon.not_black"), false);
            return;
        }
        sendState(player, dungeon, Component.empty(), true);
    }

    public static void rename(ServerPlayer player, int dungeonId, String requestedName) {
        if (!validatePlayer(player) || !rateLimit(player)) {
            return;
        }
        DungeonManager manager = DungeonManager.get(player.serverLevel());
        DungeonManager.DungeonView dungeon = manager.byId(dungeonId).orElse(null);
        if (dungeon == null) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.dungeon.not_found"), false);
            return;
        }
        DungeonManager.Reason reason = manager.rename(dungeonId, requestedName);
        DungeonManager.DungeonView updated = manager.byId(dungeonId).orElse(dungeon);
        if (reason != DungeonManager.Reason.OK) {
            sendState(player, updated, reasonMessage(reason), false);
            return;
        }
        ClaimSyncManager.resyncAll(player.getServer());
        sendState(
                player,
                updated,
                Component.translatable("kingdoms.dungeon.renamed", updated.name()),
                true
        );
    }

    public static void remove(ServerPlayer player, int dungeonId) {
        if (!validatePlayer(player) || !rateLimit(player)) {
            return;
        }
        DungeonManager manager = DungeonManager.get(player.serverLevel());
        DungeonManager.DungeonView dungeon = manager.byId(dungeonId).orElse(null);
        if (dungeon == null || !manager.remove(dungeonId)) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.dungeon.not_found"), false);
            return;
        }
        ClaimSyncManager.resyncAll(player.getServer());
        FactionServerHooks.sendNotice(
                player,
                Component.translatable("kingdoms.dungeon.removed", dungeon.name()),
                true
        );
    }

    public static void setClaim(
            ServerPlayer player,
            int dungeonId,
            BlockPos corePos,
            int chunkX,
            int chunkZ,
            boolean marked
    ) {
        if (!validateCore(player, corePos) || !rateLimit(player)) {
            return;
        }
        DungeonManager manager = DungeonManager.get(player.serverLevel());
        DungeonManager.DungeonView dungeon = manager.byId(dungeonId).orElse(null);
        if (dungeon == null) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.dungeon.not_found"), false);
            return;
        }
        ChunkPos center = new ChunkPos(corePos);
        if (Math.abs(chunkX - center.x) > DungeonPayloads.MAP_RADIUS
                || Math.abs(chunkZ - center.z) > DungeonPayloads.MAP_RADIUS) {
            sendState(player, dungeon, Component.translatable("kingdoms.error.claim_outside_map"), false);
            return;
        }
        applyMarks(
                player,
                dungeon,
                List.of(new ClaimKey(player.serverLevel().dimension(), chunkX, chunkZ)),
                marked,
                true
        );
    }

    public static void mapSet(ServerPlayer player, int dungeonId, boolean marked, List<Long> packedChunks) {
        if (!validatePlayer(player) || packedChunks.isEmpty() || !rateLimit(player)) {
            return;
        }
        DungeonManager manager = DungeonManager.get(player.serverLevel());
        DungeonManager.DungeonView dungeon = manager.byId(dungeonId).orElse(null);
        if (dungeon == null) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.dungeon.not_found"), false);
            return;
        }
        ServerLevel level = player.serverLevel();
        List<ClaimKey> keys = new ArrayList<>(packedChunks.size());
        for (Long packed : packedChunks.subList(0, Math.min(packedChunks.size(), DungeonPayloads.MAX_BATCH_CHUNKS))) {
            keys.add(new ClaimKey(level.dimension(), new ChunkPos(packed)));
        }
        applyMarks(player, dungeon, keys, marked, false);
    }

    private static void applyMarks(
            ServerPlayer player,
            DungeonManager.DungeonView dungeon,
            List<ClaimKey> keys,
            boolean marked,
            boolean withScreen
    ) {
        ServerLevel level = player.serverLevel();
        DungeonManager manager = DungeonManager.get(level);
        DungeonManager.MarkResult result = manager.setClaims(level, dungeon.id(), keys, marked);
        if (result.changed() > 0) {
            ClaimSyncManager.resyncAll(player.getServer());
        }
        Component message = result.blocked() > 0 && result.reason() != DungeonManager.Reason.OK
                ? reasonMessage(result.reason())
                : Component.translatable(
                        marked ? "kingdoms.dungeon.map_marked" : "kingdoms.dungeon.map_unmarked",
                        result.changed()
                );
        boolean successful = result.changed() > 0;
        DungeonManager.DungeonView updated = manager.byId(dungeon.id()).orElse(dungeon);
        if (withScreen) {
            sendState(player, updated, message, successful);
        } else {
            FactionServerHooks.sendNotice(player, message, successful);
        }
    }

    public static DungeonManager.DungeonView ensure(ServerLevel level, BlockPos corePos) {
        DungeonManager manager = DungeonManager.get(level);
        DungeonManager.DungeonView existing = manager.byCore(level.dimension(), corePos).orElse(null);
        if (existing != null) {
            return existing;
        }
        DungeonManager.DungeonView here = manager.dungeonAt(ClaimKey.of(level, corePos)).orElse(null);
        if (here != null) {
            manager.bindCore(here.id(), corePos);
            return manager.byId(here.id()).orElse(here);
        }
        DungeonManager.CreateResult result = manager.create(level, corePos, manager.suggestName());
        if (!result.successful()) {
            return null;
        }
        ClaimSyncManager.resyncAll(level.getServer());
        return result.dungeon();
    }

    public static void openChestEditor(ServerPlayer player, BlockPos pos) {
        if (!validateChest(player, pos)) {
            return;
        }
        player.openMenu(
                new net.minecraft.world.SimpleMenuProvider(
                        (containerId, inventory, opener) ->
                                new com.geydev.kalfactions.menu.DungeonLootMenu(containerId, inventory, pos),
                        Component.translatable("screen.kingdoms.dungeon_chest.title")
                ),
                buffer -> buffer.writeBlockPos(pos)
        );
    }

    public static void chestEntry(ServerPlayer player, DungeonPayloads.C2SDungeonChestEntry payload) {
        if (!validateChest(player, payload.pos()) || !rateLimit(player, EDIT_COOLDOWN_TICKS)) {
            return;
        }
        DungeonChestBlockEntity chest =
                (DungeonChestBlockEntity) player.serverLevel().getBlockEntity(payload.pos());
        if (payload.slot() >= 0) {
            chest.setEntry(payload.slot(), payload.chance(), payload.min(), payload.max());
        }
        chest.setCooldownHours(payload.cooldownHours());
        if (payload.announce()) {
            FactionServerHooks.sendNotice(
                    player,
                    Component.translatable("kingdoms.dungeon.chest_saved"),
                    true
            );
        }
    }

    public static void chestRefill(ServerPlayer player, BlockPos pos) {
        if (!validateChest(player, pos) || !rateLimit(player)) {
            return;
        }
        DungeonChestBlockEntity chest = (DungeonChestBlockEntity) player.serverLevel().getBlockEntity(pos);
        chest.resetCooldown();
        chest.refill();
        FactionServerHooks.sendNotice(
                player,
                Component.translatable("kingdoms.dungeon.chest_refreshed"),
                true
        );
    }

    private static boolean validateChest(ServerPlayer player, BlockPos pos) {
        if (!validatePlayer(player)) {
            return false;
        }
        if (!player.level().isLoaded(pos)) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.error.table_not_loaded"), false);
            return false;
        }
        if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)
                > MAX_CORE_DISTANCE_SQR) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.error.table_too_far"), false);
            return false;
        }
        if (!(player.serverLevel().getBlockEntity(pos) instanceof DungeonChestBlockEntity)) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.dungeon.not_chest"), false);
            return false;
        }
        return true;
    }

    public static boolean canPlaceCore(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!player.hasPermissions(2)) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.dungeon.not_operator"), false);
            return false;
        }
        if (!WorldZonePolicy.isBlack(level, pos)) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.dungeon.not_black"), false);
            return false;
        }
        return true;
    }

    public static void sendState(
            ServerPlayer player,
            DungeonManager.DungeonView dungeon,
            Component message,
            boolean successful
    ) {
        ServerLevel level = player.serverLevel();
        ChunkPos center = new ChunkPos(dungeon.corePos());
        List<Long> chunks = new ArrayList<>();
        for (ClaimKey key : dungeon.chunks()) {
            if (Math.abs(key.x() - center.x) <= DungeonPayloads.MAP_RADIUS
                    && Math.abs(key.z() - center.z) <= DungeonPayloads.MAP_RADIUS) {
                chunks.add(ChunkPos.asLong(key.x(), key.z()));
            }
        }
        PacketDistributor.sendToPlayer(player, new DungeonPayloads.S2COpenDungeon(
                dungeon.id(),
                dungeon.name(),
                dungeon.corePos(),
                level.dimension().location(),
                dungeon.chunks().size(),
                dungeon.containerCount(),
                center.x,
                center.z,
                DungeonPayloads.MAP_RADIUS,
                chunks,
                message,
                successful
        ));
    }

    public static Component reasonMessage(DungeonManager.Reason reason) {
        return switch (reason) {
            case OK -> Component.empty();
            case NOT_FOUND -> Component.translatable("kingdoms.dungeon.not_found");
            case NOT_BLACK -> Component.translatable("kingdoms.dungeon.not_black");
            case SANCTUARY -> Component.translatable("kingdoms.dungeon.chunk_sanctuary");
            case CLAIMED -> Component.translatable("kingdoms.dungeon.chunk_claimed");
            case OTHER_DUNGEON -> Component.translatable("kingdoms.dungeon.chunk_other_dungeon");
            case NAME_TAKEN -> Component.translatable("kingdoms.dungeon.name_taken");
            case NAME_EMPTY -> Component.translatable("kingdoms.dungeon.name_empty");
            case TOO_MANY -> Component.translatable("kingdoms.dungeon.too_many");
            case TOO_MANY_CHUNKS -> Component.translatable("kingdoms.dungeon.too_many_chunks");
        };
    }

    private static boolean validatePlayer(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) {
            return false;
        }
        if (!player.hasPermissions(2)) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.dungeon.not_operator"), false);
            return false;
        }
        return true;
    }

    private static boolean validateCore(ServerPlayer player, BlockPos corePos) {
        if (!validatePlayer(player)) {
            return false;
        }
        if (!player.level().isLoaded(corePos)) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.error.table_not_loaded"), false);
            return false;
        }
        if (player.distanceToSqr(corePos.getX() + 0.5D, corePos.getY() + 0.5D, corePos.getZ() + 0.5D)
                > MAX_CORE_DISTANCE_SQR) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.error.table_too_far"), false);
            return false;
        }
        if (!player.level().getBlockState(corePos).is(ModBlocks.DUNGEON_CORE.get())) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.dungeon.not_core"), false);
            return false;
        }
        return true;
    }

    private static boolean rateLimit(ServerPlayer player) {
        return rateLimit(player, ACTION_COOLDOWN_TICKS);
    }

    private static boolean rateLimit(ServerPlayer player, long cooldownTicks) {
        long now = player.level().getGameTime();
        Long previous = LAST_ACTION_TICK.put(player.getUUID(), now);
        if (previous != null && now - previous < cooldownTicks) {
            FactionServerHooks.sendNotice(
                    player,
                    Component.translatable("kingdoms.error.action_rate_limited"),
                    false
            );
            return false;
        }
        return true;
    }

    private DungeonService() {
    }
}
