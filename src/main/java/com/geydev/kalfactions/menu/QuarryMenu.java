package com.geydev.kalfactions.menu;

import com.geydev.kalfactions.quarry.QuarryManager;
import com.geydev.kalfactions.registry.ModBlocks;
import com.geydev.kalfactions.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class QuarryMenu extends AbstractContainerMenu {
    public static final double MAX_DISTANCE_SQUARED = 64.0D;
    private final BlockPos core;

    public QuarryMenu(int containerId, Inventory inventory, BlockPos core) {
        super(ModMenuTypes.QUARRY.get(), containerId);
        this.core = core.immutable();
    }

    @Override
    public boolean stillValid(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return true;
        }
        return level.dimension().equals(Level.OVERWORLD)
                && player.distanceToSqr(
                        core.getX() + 0.5D,
                        core.getY() + 0.5D,
                        core.getZ() + 0.5D
                ) <= MAX_DISTANCE_SQUARED
                && level.getBlockState(core).is(ModBlocks.QUARRY_CORE.get())
                && QuarryManager.get(level).byCore(core).isPresent();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    public BlockPos core() {
        return core;
    }
}
