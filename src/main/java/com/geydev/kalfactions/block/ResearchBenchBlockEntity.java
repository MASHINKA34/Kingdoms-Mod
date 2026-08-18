package com.geydev.kalfactions.block;

import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.faction.ScienceIncome;
import com.geydev.kalfactions.faction.ScienceLedger;
import com.geydev.kalfactions.menu.ResearchBenchMenu;
import com.geydev.kalfactions.registry.ModBlockEntities;
import com.geydev.kalfactions.science.ResearchBenchStatus;
import com.geydev.kalfactions.science.ResearchBenchTicker;
import com.geydev.kalfactions.science.ScienceInputs;
import java.util.UUID;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class ResearchBenchBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int SLOTS = 9;
    public static final int DATA_SIZE = 6;

    private static final String TAG_LAST = "LastProduceMillis";
    private static final int MAX_CATCH_UP = 1024;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progressTicks();
                case 1 -> intervalTicks;
                case 2 -> status.ordinal();
                case 3 -> clampToInt(scienceToday);
                case 4 -> clampToInt(dailyCap);
                case 5 -> clampToInt(currentScience);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_SIZE;
        }
    };

    private long lastProduceMillis;
    private long intervalMillis = ScienceInputs.defaultSeconds() * 1000L;
    private int intervalTicks = (int) (intervalMillis / 50L);
    private long currentScience;
    private long scienceToday;
    private long dailyCap = ModConfigSpec.SCIENCE_DAILY_CAP.getAsLong();
    private ResearchBenchStatus status = ResearchBenchStatus.NO_MATERIALS;

    public ResearchBenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RESEARCH_BENCH.get(), pos, state);
    }

    public void runCheck(ServerLevel level) {
        FactionManager factions = FactionManager.get(level);
        ScienceLedger ledger = ScienceLedger.get(level);
        UUID factionId = factions.getFactionIdAt(ClaimKey.of(level, worldPosition)).orElse(null);
        long now = System.currentTimeMillis();
        dailyCap = ModConfigSpec.SCIENCE_DAILY_CAP.getAsLong();
        scienceToday = factionId == null ? 0L : ledger.grantedToday(factionId, now);
        if (factionId == null) {
            idle(ResearchBenchStatus.OFF_TERRITORY);
            return;
        }
        int processed = 0;
        while (true) {
            int slot = nextSlot();
            if (slot < 0) {
                idle(ResearchBenchStatus.NO_MATERIALS);
                break;
            }
            ScienceInputs.Entry entry = ScienceInputs.entry(items.get(slot));
            intervalMillis = Math.max(1L, entry.intervalMillis());
            intervalTicks = (int) Math.clamp(intervalMillis / 50L, 1L, Integer.MAX_VALUE);
            currentScience = entry.science();
            if (lastProduceMillis == 0L) {
                lastProduceMillis = now;
                setChanged();
            }
            if (!canPay(ledger, factionId, entry.science(), now)) {
                holdCapped(now);
                break;
            }
            if (now - lastProduceMillis < intervalMillis || processed >= MAX_CATCH_UP) {
                status = ResearchBenchStatus.WORKING;
                break;
            }
            long granted = ScienceIncome.grantDailyCapped(factions, ledger, factionId, entry.science(), null);
            if (granted <= 0L) {
                holdCapped(now);
                break;
            }
            items.get(slot).shrink(1);
            if (items.get(slot).isEmpty()) {
                items.set(slot, ItemStack.EMPTY);
            }
            lastProduceMillis += intervalMillis;
            scienceToday = ledger.grantedToday(factionId, now);
            processed++;
        }
        if (processed > 0) {
            setChanged();
        }
    }

    private boolean canPay(ScienceLedger ledger, UUID factionId, long science, long now) {
        if (dailyCap <= 0L) {
            return true;
        }
        long remaining = ledger.remainingToday(factionId, now, dailyCap);
        if (remaining <= 0L) {
            return false;
        }
        return remaining >= science || science > dailyCap;
    }

    private void holdCapped(long now) {
        status = ResearchBenchStatus.DAILY_CAP;
        if (lastProduceMillis != 0L && now - lastProduceMillis > intervalMillis) {
            lastProduceMillis = now - intervalMillis;
            setChanged();
        }
    }

    private void idle(ResearchBenchStatus idleStatus) {
        status = idleStatus;
        currentScience = 0L;
        intervalMillis = Math.max(1L, ScienceInputs.defaultSeconds() * 1000L);
        intervalTicks = (int) (intervalMillis / 50L);
        if (lastProduceMillis != 0L) {
            lastProduceMillis = 0L;
            setChanged();
        }
    }

    private int nextSlot() {
        for (int slot = 0; slot < SLOTS; slot++) {
            if (ScienceInputs.accepts(items.get(slot))) {
                return slot;
            }
        }
        return -1;
    }

    public int progressTicks() {
        if (status == ResearchBenchStatus.DAILY_CAP) {
            return intervalTicks;
        }
        if (status != ResearchBenchStatus.WORKING || lastProduceMillis == 0L) {
            return 0;
        }
        long elapsed = (System.currentTimeMillis() - lastProduceMillis) / 50L;
        return (int) Math.clamp(elapsed, 0L, intervalTicks);
    }

    public ResearchBenchStatus status() {
        return status;
    }

    public long lastProduceMillis() {
        return lastProduceMillis;
    }

    public long scienceToday() {
        return scienceToday;
    }

    public long currentScience() {
        return currentScience;
    }

    public int intervalTicks() {
        return intervalTicks;
    }

    public boolean canOpen(UUID playerId) {
        return level instanceof ServerLevel serverLevel
                && FactionManager.get(serverLevel).canAccessContainer(playerId, serverLevel, worldPosition);
    }

    public void dropContents(ServerLevel level, BlockPos pos) {
        Containers.dropContents(level, pos, this);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (level instanceof ServerLevel serverLevel) {
            ResearchBenchTicker.index(serverLevel, this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        ResearchBenchTicker.forget(this);
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
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return ScienceInputs.accepts(stack);
    }

    @Override
    public boolean stillValid(Player player) {
        if (level == null || level.getBlockEntity(worldPosition) != this) {
            return false;
        }
        if (player.distanceToSqr(
                worldPosition.getX() + 0.5D,
                worldPosition.getY() + 0.5D,
                worldPosition.getZ() + 0.5D) > 64.0D) {
            return false;
        }
        return player.hasPermissions(2) || canOpen(player.getUUID());
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.kingdoms.research_bench");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ResearchBenchMenu(containerId, playerInventory, this, dataAccess);
    }

    private static int clampToInt(long value) {
        return (int) Math.clamp(value, 0L, Integer.MAX_VALUE);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        lastProduceMillis = tag.getLong(TAG_LAST);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putLong(TAG_LAST, lastProduceMillis);
    }
}
