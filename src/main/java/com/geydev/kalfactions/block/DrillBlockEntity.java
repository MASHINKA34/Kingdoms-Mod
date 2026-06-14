package com.geydev.kalfactions.block;

import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.outpost.cluster.ResourceClusterManager;
import com.geydev.kalfactions.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class DrillBlockEntity extends BlockEntity {
    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_PROGRESS = "Progress";
    private static final int SLOTS = 12;

    private final ItemStackHandler inventory = new ItemStackHandler(SLOTS);
    private int progress;

    public DrillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DRILL.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DrillBlockEntity drill) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        int intervalTicks = Math.max(1, ModConfigSpec.OUTPOST_DRILL_INTERVAL_SECONDS.getAsInt() * 20);
        if (++drill.progress < intervalTicks) {
            return;
        }
        drill.progress = 0;
        drill.produce(serverLevel, pos);
    }

    private void produce(ServerLevel level, BlockPos pos) {
        ChunkPos chunk = new ChunkPos(pos);
        ClaimKey key = ClaimKey.of(level, chunk);
        FactionManager manager = FactionManager.get(level);
        Faction faction = manager.getFactionAt(key).orElse(null);
        if (faction == null || !faction.isOutpostChunk(key)) {
            return;
        }
        ResourceClusterManager.ClusterView cluster = ResourceClusterManager.get(level).clusterAt(chunk).orElse(null);
        if (cluster == null) {
            return;
        }
        ItemStack produced = new ItemStack(cluster.type().displayItem(), cluster.richness());
        ItemStack remaining = produced;
        for (int slot = 0; slot < inventory.getSlots() && !remaining.isEmpty(); slot++) {
            remaining = inventory.insertItem(slot, remaining, false);
        }
        setChanged();
    }

    public void collectInto(Player player) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.extractItem(slot, Integer.MAX_VALUE, false);
            if (stack.isEmpty()) {
                continue;
            }
            if (!player.getInventory().add(stack) && !stack.isEmpty()) {
                player.drop(stack, false);
            }
        }
        setChanged();
    }

    public boolean isEmpty() {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (!inventory.getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public void dropContents(ServerLevel level, BlockPos pos) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        progress = tag.getInt(TAG_PROGRESS);
        if (tag.contains(TAG_INVENTORY)) {
            inventory.deserializeNBT(registries, tag.getCompound(TAG_INVENTORY));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_PROGRESS, progress);
        tag.put(TAG_INVENTORY, inventory.serializeNBT(registries));
    }
}
