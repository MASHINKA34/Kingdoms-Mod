package com.geydev.kalfactions.protection;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.chest.ChestAccessMode;
import com.geydev.kalfactions.faction.FactionManager;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.IBlockCapabilityProvider;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Best-effort wrapper for item capabilities registered before Kingdoms.
 *
 * <p>NeoForge has no public global capability interception event. This wraps
 * the providers visible during registration; providers added by later mods and
 * direct vanilla {@code Container} access still require a dedicated mixin.</p>
 */
@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class AutomationBoundaryHandler {
    private static boolean installed;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void wrapItemHandlers(RegisterCapabilitiesEvent event) {
        if (installed) {
            return;
        }
        installed = true;

        try {
            Field providersField = BlockCapability.class.getDeclaredField("providers");
            providersField.setAccessible(true);
            Map<Block, List<IBlockCapabilityProvider<IItemHandler, Direction>>> providers =
                    (Map) providersField.get(Capabilities.ItemHandler.BLOCK);

            Map<Block, List<IBlockCapabilityProvider<IItemHandler, Direction>>> snapshot =
                    new IdentityHashMap<>(providers);
            for (Map.Entry<Block, List<IBlockCapabilityProvider<IItemHandler, Direction>>> entry
                    : snapshot.entrySet()) {
                List<IBlockCapabilityProvider<IItemHandler, Direction>> liveProviders = entry.getValue();
                if (liveProviders.isEmpty()) {
                    continue;
                }
                List<IBlockCapabilityProvider<IItemHandler, Direction>> delegates =
                        List.copyOf(new ArrayList<>(liveProviders));
                liveProviders.clear();
                liveProviders.add((level, pos, state, blockEntity, side) -> {
                    IItemHandler delegate = firstAvailable(
                            delegates,
                            level,
                            pos,
                            state,
                            blockEntity,
                            side
                    );
                    return delegate == null
                            ? null
                            : new BoundaryItemHandler(delegate, level, pos.immutable(), side);
                });
            }
            KalFactions.LOGGER.info(
                    "Installed claim-boundary wrappers for {} item capability providers.",
                    snapshot.size()
            );
        } catch (ReflectiveOperationException | RuntimeException exception) {
            KalFactions.LOGGER.warn(
                    "Could not install item capability boundary wrappers; automation protection is partial.",
                    exception
            );
        }
    }

    private static IItemHandler firstAvailable(
            List<IBlockCapabilityProvider<IItemHandler, Direction>> providers,
            Level level,
            BlockPos pos,
            BlockState state,
            BlockEntity blockEntity,
            Direction side
    ) {
        for (IBlockCapabilityProvider<IItemHandler, Direction> provider : providers) {
            IItemHandler handler = provider.getCapability(level, pos, state, blockEntity, side);
            if (handler != null) {
                return handler;
            }
        }
        return null;
    }

    private static boolean crossesProtectedBoundary(Level level, BlockPos targetPos, Direction targetSide) {
        if (!(level instanceof ServerLevel serverLevel) || targetSide == null) {
            return false;
        }

        BlockPos automationPos = targetPos.relative(targetSide);
        Optional<FactionAccess.FactionRef> targetFaction = FactionAccess.factionAt(serverLevel, targetPos);
        Optional<FactionAccess.FactionRef> automationFaction =
                FactionAccess.factionAt(serverLevel, automationPos);
        if (targetFaction.isEmpty() && automationFaction.isEmpty()) {
            return false;
        }
        if (targetFaction.isPresent()
                && automationFaction.isPresent()
                && targetFaction.get().key().equals(automationFaction.get().key())) {
            return false;
        }

        return FactionManager.get(serverLevel)
                .getChestAccess(serverLevel, targetPos)
                .map(access -> access.mode() != ChestAccessMode.PUBLIC)
                .orElse(true);
    }

    private static final class BoundaryItemHandler implements IItemHandler {
        private final IItemHandler delegate;
        private final Level level;
        private final BlockPos targetPos;
        private final Direction targetSide;

        private BoundaryItemHandler(
                IItemHandler delegate,
                Level level,
                BlockPos targetPos,
                Direction targetSide
        ) {
            this.delegate = delegate;
            this.level = level;
            this.targetPos = targetPos;
            this.targetSide = targetSide;
        }

        @Override
        public int getSlots() {
            return delegate.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return delegate.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return crossesProtectedBoundary(level, targetPos, targetSide)
                    ? stack
                    : delegate.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return crossesProtectedBoundary(level, targetPos, targetSide)
                    ? ItemStack.EMPTY
                    : delegate.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return delegate.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return !crossesProtectedBoundary(level, targetPos, targetSide)
                    && delegate.isItemValid(slot, stack);
        }
    }

    private AutomationBoundaryHandler() {
    }
}
