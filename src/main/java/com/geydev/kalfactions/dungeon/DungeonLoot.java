package com.geydev.kalfactions.dungeon;

import com.geydev.kalfactions.config.ModConfigSpec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;

public final class DungeonLoot {
    public static RandomizableContainer containerAt(ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof com.geydev.kalfactions.block.DungeonChestBlockEntity) {
            return null;
        }
        return blockEntity instanceof RandomizableContainer container ? container : null;
    }

    public static Optional<ResourceLocation> pendingTable(RandomizableContainer container) {
        ResourceKey<LootTable> table = container.getLootTable();
        return table == null ? Optional.empty() : Optional.of(table.location());
    }

    public static boolean registerAutomatically(ServerLevel level, BlockPos pos) {
        DungeonManager manager = DungeonManager.get(level);
        if (!manager.isDungeon(level, pos) || manager.lootAt(level, pos).isPresent()) {
            return false;
        }
        RandomizableContainer container = containerAt(level, pos);
        if (container == null) {
            return false;
        }
        return pendingTable(container)
                .map(table -> manager.markLoot(level, pos, table, DungeonClock.now()))
                .orElse(false);
    }

    public static void onOpen(ServerPlayer player, ServerLevel level, BlockPos pos) {
        DungeonManager manager = DungeonManager.get(level);
        if (manager.isEmpty() || !manager.isDungeon(level, pos)) {
            return;
        }
        RandomizableContainer container = containerAt(level, pos);
        if (container == null) {
            return;
        }
        DungeonManager.LootEntry entry = manager.lootAt(level, pos).orElse(null);
        if (entry == null) {
            if (registerAutomatically(level, pos)) {
                notice(player, Component.translatable("kingdoms.dungeon.loot_warning"));
            }
            return;
        }
        if (container.getLootTable() != null) {
            notice(player, Component.translatable("kingdoms.dungeon.loot_warning"));
            return;
        }
        long now = DungeonClock.now();
        long cooldown = cooldownMillis();
        long elapsed = now - entry.lastFilled();
        if (elapsed >= cooldown) {
            refill(level, pos, container, entry, now);
            notice(player, Component.translatable("kingdoms.dungeon.loot_refreshed"));
            return;
        }
        if (!isEmpty(container)) {
            notice(player, Component.translatable("kingdoms.dungeon.loot_warning"));
        }
    }

    public static boolean refreshIfDue(ServerLevel level, BlockPos pos) {
        DungeonManager manager = DungeonManager.get(level);
        DungeonManager.LootEntry entry = manager.lootAt(level, pos).orElse(null);
        RandomizableContainer container = containerAt(level, pos);
        if (entry == null || container == null || container.getLootTable() != null) {
            return false;
        }
        long now = DungeonClock.now();
        if (now - entry.lastFilled() < cooldownMillis()) {
            return false;
        }
        refill(level, pos, container, entry, now);
        return true;
    }

    static void refill(
            ServerLevel level,
            BlockPos pos,
            RandomizableContainer container,
            DungeonManager.LootEntry entry,
            long now
    ) {
        if (container instanceof Container inventory) {
            inventory.clearContent();
        }
        container.setLootTable(
                ResourceKey.create(Registries.LOOT_TABLE, entry.lootTable()),
                level.getRandom().nextLong()
        );
        if (container instanceof BlockEntity blockEntity) {
            blockEntity.setChanged();
        }
        DungeonManager.get(level).touchLoot(level, pos, now);
    }

    public static long cooldownMillis() {
        return ModConfigSpec.DUNGEON_LOOT_COOLDOWN_HOURS.getAsInt() * 3_600_000L;
    }

    public static String formatRemaining(long millis) {
        long total = Math.max(0L, millis);
        long minutes = (total + 59_999L) / 60_000L;
        return String.format("%02d:%02d", minutes / 60L, minutes % 60L);
    }

    private static boolean isEmpty(RandomizableContainer container) {
        if (!(container instanceof Container inventory)) {
            return true;
        }
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (!inventory.getItem(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static void notice(ServerPlayer player, Component message) {
        player.displayClientMessage(message, true);
    }

    private DungeonLoot() {
    }
}
