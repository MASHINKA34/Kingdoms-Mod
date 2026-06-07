package com.geydev.kalfactions.protection;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.chest.AccessTool;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.faction.FactionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class ProtectionHandler {
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)
                || FactionAccess.canBuild(player, level, event.getPos())) {
            return;
        }
        event.setCanceled(true);
        deny(player, "You cannot break blocks in this faction claim.");
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        boolean denied;
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multiPlaceEvent) {
            denied = multiPlaceEvent.getReplacedBlockSnapshots().stream()
                    .anyMatch(snapshot -> !FactionAccess.canBuild(player, level, snapshot.getPos()));
        } else {
            denied = !FactionAccess.canBuild(player, level, event.getPos());
        }

        if (denied) {
            event.setCanceled(true);
            deny(player, "You cannot place blocks in this faction claim.");
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (isAlwaysAllowed(state)) {
            return;
        }

        boolean accessTool = player.getItemInHand(event.getHand()).getItem() instanceof AccessTool;
        if (isContainer(level, pos)) {
            if (!canAccessContainer(player, level, pos)) {
                cancelInteraction(event);
                deny(player, event.getHand(), "You cannot access this container.");
                return;
            }
            if (accessTool) {
                if (!FactionAccess.canBuild(player, level, pos)) {
                    cancelInteraction(event);
                    deny(player, event.getHand(), "You cannot edit access for this container.");
                    return;
                }
                handleAccessTool(event, player, level, pos);
            }
            return;
        }

        if (!FactionAccess.canBuild(player, level, pos)) {
            cancelInteraction(event);
            deny(player, event.getHand(), "You cannot interact with blocks in this faction claim.");
            return;
        }

        if (accessTool) {
            handleAccessTool(event, player, level, pos);
        }
    }

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        for (Slot slot : event.getContainer().slots) {
            if (slot.container instanceof BlockEntity blockEntity
                    && !canAccessContainer(player, level, blockEntity.getBlockPos())) {
                player.closeContainer();
                deny(player, "You cannot access this container.");
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!ModConfigSpec.PROTECT_EXPLOSIONS.get()
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        event.getAffectedBlocks().removeIf(pos -> FactionAccess.isClaimed(level, pos));
    }

    private static void cancelInteraction(PlayerInteractEvent.RightClickBlock event) {
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    private static void handleAccessTool(
            PlayerInteractEvent.RightClickBlock event,
            ServerPlayer player,
            ServerLevel level,
            BlockPos pos
    ) {
        AccessTool.ToolResult result = AccessTool.handleProtectedUse(player, level, pos, event.getHand());
        if (result == AccessTool.ToolResult.PASS) {
            return;
        }
        cancelInteraction(event);
        event.setCancellationResult(result == AccessTool.ToolResult.CONSUME
                ? InteractionResult.SUCCESS
                : InteractionResult.FAIL);
    }

    private static boolean isAlwaysAllowed(BlockState state) {
        return state.is(BlockTags.DOORS)
                || state.is(BlockTags.TRAPDOORS)
                || state.is(BlockTags.BUTTONS)
                || state.is(BlockTags.PRESSURE_PLATES)
                || state.is(BlockTags.FENCE_GATES)
                || state.getBlock() instanceof LeverBlock;
    }

    private static boolean isContainer(ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof Container
                || level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null) != null;
    }

    private static boolean canAccessContainer(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return player.hasPermissions(2)
                || FactionManager.get(level).canAccessContainer(player.getUUID(), level, pos);
    }

    private static void deny(ServerPlayer player, InteractionHand hand, String message) {
        if (hand == InteractionHand.MAIN_HAND) {
            deny(player, message);
        }
    }

    private static void deny(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), true);
    }

    private ProtectionHandler() {
    }
}
