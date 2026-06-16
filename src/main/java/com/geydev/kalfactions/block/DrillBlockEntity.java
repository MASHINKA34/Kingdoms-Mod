package com.geydev.kalfactions.block;

import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.menu.DrillMenu;
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
    private static final String TAG_PROGRESS = "Progress";
    private static final int SLOTS = 18;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> intervalTicks();
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
            return 2;
        }
    };
    private int progress;

    public DrillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DRILL.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DrillBlockEntity drill) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        int intervalTicks = drill.intervalTicks();
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
        if (faction == null) {
            return;
        }
        ResourceClusterManager clusters = ResourceClusterManager.get(level);
        ResourceClusterManager.ClusterView cluster = clusters.clusterAt(chunk).orElse(null);
        if (cluster == null) {
            return;
        }
        if (!clusters.isBoundDrill(chunk, pos) && !clusters.bindDrill(chunk, pos)) {
            return;
        }
        int amount = cluster.richness();
        if (faction.hasResearchBonus(com.geydev.kalfactions.faction.ResearchBonus.DRILL_OUTPUT)) {
            amount += 1;
        }
        insert(new ItemStack(cluster.type().displayItem(), amount));
        setChanged();
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

    private int intervalTicks() {
        return Math.max(1, ModConfigSpec.OUTPOST_DRILL_INTERVAL_SECONDS.getAsInt() * 20);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        progress = tag.getInt(TAG_PROGRESS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt(TAG_PROGRESS, progress);
    }
}
