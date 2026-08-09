package com.geydev.kalfactions.block;

import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.menu.DrillMenu;
import com.geydev.kalfactions.outpost.cluster.DrillService;
import com.geydev.kalfactions.outpost.cluster.DrillTerritory;
import com.geydev.kalfactions.outpost.cluster.ResourceClusterManager;
import com.geydev.kalfactions.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class DrillBlockEntity extends BlockEntity implements Container, MenuProvider {
    private static final String TAG_LAST = "LastProduceMillis";
    private static final String TAG_TARGET = "TargetClusterChunk";
    private static final String TAG_TARGET_LOST = "TargetLost";
    private static final int SLOTS = 18;
    private static final int BASE_OUTPUT = 32;
    private static final int HOUR_TICKS = 3600 * 20;
    private static final int INTERVAL_FLOOR_TICKS = 4 * HOUR_TICKS;
    private static final int CHECK_INTERVAL_TICKS = 20;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> interval;
                case 2 -> depositRemaining;
                case 3 -> depositOriginal;
                case 4 -> targetClusterChunk == null ? 0 : 1;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) {
                progress = Math.max(0, value);
            }
        }

        @Override
        public int getCount() {
            return 5;
        }
    };
    private int progress;
    private int interval = baseIntervalTicks();
    private long lastProduceMillis;
    private int sinceCheck = CHECK_INTERVAL_TICKS - 1;
    private int depositRemaining = -1;
    private int depositOriginal = -1;
    private Long targetClusterChunk;
    private boolean targetLost;

    public DrillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DRILL.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DrillBlockEntity drill) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (++drill.sinceCheck < CHECK_INTERVAL_TICKS) {
            return;
        }
        drill.sinceCheck = 0;
        drill.runCheck(serverLevel, pos);
    }

    public void runCheck(ServerLevel serverLevel, BlockPos pos) {
        recomputeInterval(serverLevel, pos);
        if (targetClusterChunk == null) {
            depositRemaining = -1;
            depositOriginal = -1;
            resetIdleClock();
            return;
        }
        if (!hasValidTarget(serverLevel, pos)) {
            releaseTarget(serverLevel);
            DrillService.notifyDrillChanged(serverLevel, this);
            return;
        }
        refreshReserveReadout(serverLevel);
        long now = System.currentTimeMillis();
        if (lastProduceMillis == 0L) {
            lastProduceMillis = now;
            setChanged();
        }
        long intervalMillis = Math.max(1L, (long) interval * 50L);
        int batches = 0;
        while (now - lastProduceMillis >= intervalMillis && batches < SLOTS) {
            ProduceResult result = produce(serverLevel, pos);
            if (result == ProduceResult.PRODUCED) {
                lastProduceMillis += intervalMillis;
                batches++;
                continue;
            }
            if (result == ProduceResult.FULL || result == ProduceResult.DEPLETED) {
                lastProduceMillis = now - intervalMillis;
            } else {
                lastProduceMillis = now;
            }
            break;
        }
        long elapsedTicks = (now - lastProduceMillis) / 50L;
        progress = (int) Math.clamp(elapsedTicks, 0L, (long) interval);
        if (batches > 0) {
            setChanged();
        }
    }

    private ProduceResult produce(ServerLevel level, BlockPos pos) {
        ChunkPos chunk = new ChunkPos(pos);
        ClaimKey key = ClaimKey.of(level, chunk);
        FactionManager manager = FactionManager.get(level);
        Faction faction = manager.getFactionAt(key).orElse(null);
        if (!DrillTerritory.owns(faction, key) || targetClusterChunk == null) {
            return ProduceResult.INVALID;
        }
        ResourceClusterManager clusters = ResourceClusterManager.get(level);
        ChunkPos targetChunk = new ChunkPos(targetClusterChunk);
        ClaimKey targetKey = ClaimKey.of(level, targetChunk);
        if (!DrillTerritory.reaches(faction, key, targetKey)) {
            releaseTarget(level);
            return ProduceResult.INVALID;
        }
        ResourceClusterManager.ClusterView cluster = clusters.clusterAt(targetChunk).orElse(null);
        if (cluster == null) {
            releaseTarget(level);
            return ProduceResult.INVALID;
        }
        if (!clusters.isBoundDrill(targetChunk, pos) && !clusters.bindDrill(targetChunk, pos)) {
            return ProduceResult.INVALID;
        }
        ResourceClusterManager.ReserveView reserve = clusters.reserveAt(targetChunk).orElse(null);
        if (reserve == null) {
            releaseTarget(level);
            return ProduceResult.INVALID;
        }
        int amount = BASE_OUTPUT + 16 * faction.researchBonusCount("DRILL_OUTPUT");
        int batch = Math.min(amount, reserve.remaining());
        if (batch <= 0) {
            return ProduceResult.DEPLETED;
        }
        ItemStack output = new ItemStack(cluster.type().displayItem(), batch);
        if (!canFit(output)) {
            return ProduceResult.FULL;
        }
        int granted = clusters.consume(level, targetChunk, batch);
        if (granted <= 0) {
            return ProduceResult.DEPLETED;
        }
        output.setCount(granted);
        insert(output);
        refreshReserveReadout(level);
        setChanged();
        DrillService.notifyClusterChanged(level, targetChunk);
        return ProduceResult.PRODUCED;
    }

    private void refreshReserveReadout(ServerLevel level) {
        if (targetClusterChunk == null) {
            depositRemaining = -1;
            depositOriginal = -1;
            return;
        }
        ResourceClusterManager.ReserveView reserve = ResourceClusterManager.get(level)
                .reserveAt(new ChunkPos(targetClusterChunk))
                .orElse(null);
        depositRemaining = reserve == null ? -1 : reserve.remaining();
        depositOriginal = reserve == null ? -1 : reserve.limit();
    }

    private boolean hasValidTarget(ServerLevel level, BlockPos pos) {
        if (targetClusterChunk == null) {
            return false;
        }
        ClaimKey drillClaim = ClaimKey.of(level, new ChunkPos(pos));
        Faction faction = FactionManager.get(level).getFactionAt(drillClaim).orElse(null);
        ChunkPos targetChunk = new ChunkPos(targetClusterChunk);
        ResourceClusterManager clusters = ResourceClusterManager.get(level);
        return DrillTerritory.reaches(faction, drillClaim, ClaimKey.of(level, targetChunk))
                && clusters.clusterAt(targetChunk).isPresent()
                && (clusters.isBoundDrill(targetChunk, worldPosition)
                        || clusters.bindDrill(targetChunk, worldPosition));
    }

    private boolean canFit(ItemStack stack) {
        int remaining = stack.getCount();
        for (int slot = 0; slot < SLOTS && remaining > 0; slot++) {
            ItemStack existing = items.get(slot);
            if (existing.isEmpty()) {
                remaining -= stack.getMaxStackSize();
            } else if (ItemStack.isSameItemSameComponents(existing, stack)) {
                remaining -= existing.getMaxStackSize() - existing.getCount();
            }
        }
        return remaining <= 0;
    }

    private enum ProduceResult {
        PRODUCED,
        FULL,
        DEPLETED,
        INVALID
    }

    private void insert(ItemStack stack) {
        for (int slot = 0; slot < SLOTS && !stack.isEmpty(); slot++) {
            ItemStack existing = items.get(slot);
            if (existing.isEmpty()) {
                items.set(slot, stack.copy());
                stack.setCount(0);
                return;
            }
            if (ItemStack.isSameItemSameComponents(existing, stack)) {
                int room = existing.getMaxStackSize() - existing.getCount();
                int moved = Math.min(room, stack.getCount());
                existing.grow(moved);
                stack.shrink(moved);
            }
        }
    }

    public void dropContents(ServerLevel level, BlockPos pos) {
        Containers.dropContents(level, pos, this);
    }

    public Long targetClusterChunk() {
        return targetClusterChunk;
    }

    public boolean targetLost() {
        return targetLost;
    }

    public boolean selectTarget(ServerLevel level, long targetChunk) {
        ResourceClusterManager clusters = ResourceClusterManager.get(level);
        if (targetClusterChunk != null && targetClusterChunk == targetChunk
                && clusters.isBoundDrill(new ChunkPos(targetChunk), worldPosition)) {
            return true;
        }
        if (!clusters.bindDrill(new ChunkPos(targetChunk), worldPosition)) {
            return false;
        }
        if (targetClusterChunk != null) {
            clusters.unbindDrill(new ChunkPos(targetClusterChunk), worldPosition);
        }
        targetClusterChunk = targetChunk;
        targetLost = false;
        lastProduceMillis = System.currentTimeMillis();
        progress = 0;
        setChanged();
        return true;
    }

    public void releaseTarget(ServerLevel level) {
        boolean released = false;
        if (targetClusterChunk != null) {
            ResourceClusterManager.get(level).unbindDrill(new ChunkPos(targetClusterChunk), worldPosition);
            targetClusterChunk = null;
            targetLost = true;
            released = true;
        }
        depositRemaining = -1;
        depositOriginal = -1;
        resetIdleClock();
        if (released) {
            setChanged();
        }
    }

    public int progressTicks() {
        return progress;
    }

    public long lastProduceMillis() {
        return lastProduceMillis;
    }

    private void resetIdleClock() {
        if (lastProduceMillis != 0L || progress != 0) {
            lastProduceMillis = 0L;
            progress = 0;
            setChanged();
        }
    }

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > stack.getMaxStackSize()) {
            stack.setCount(stack.getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null
                && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.kingdoms.drill");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new DrillMenu(containerId, playerInventory, this, dataAccess);
    }

    private static int baseIntervalTicks() {
        return Math.max(1, ModConfigSpec.OUTPOST_DRILL_INTERVAL_SECONDS.getAsInt() * 20);
    }

    private void recomputeInterval(ServerLevel level, BlockPos pos) {
        int base = baseIntervalTicks();
        int levels = FactionManager.get(level)
                .getFactionAt(ClaimKey.of(level, new ChunkPos(pos)))
                .map(faction -> faction.researchBonusCount("DRILL_INTERVAL"))
                .orElse(0);
        int floor = Math.min(base, INTERVAL_FLOOR_TICKS);
        interval = Math.max(floor, base - levels * 2 * HOUR_TICKS);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        lastProduceMillis = tag.getLong(TAG_LAST);
        targetLost = tag.getBoolean(TAG_TARGET_LOST);
        targetClusterChunk = tag.contains(TAG_TARGET) ? tag.getLong(TAG_TARGET) : null;
        if (targetClusterChunk == null) {
            lastProduceMillis = 0L;
            progress = 0;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putLong(TAG_LAST, lastProduceMillis);
        tag.putBoolean(TAG_TARGET_LOST, targetLost);
        if (targetClusterChunk != null) {
            tag.putLong(TAG_TARGET, targetClusterChunk);
        }
    }
}
