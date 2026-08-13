package com.geydev.kalfactions.block;

import com.geydev.kalfactions.dungeon.DungeonService;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class DungeonChestBlock extends BaseEntityBlock {
    public static final MapCodec<DungeonChestBlock> CODEC = simpleCodec(DungeonChestBlock::new);

    public DungeonChestBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DungeonChestBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof DungeonChestBlockEntity chest)) {
            return InteractionResult.PASS;
        }
        if (player.isSecondaryUseActive() && serverPlayer.hasPermissions(2)) {
            DungeonService.openChest(serverPlayer, pos);
            return InteractionResult.CONSUME;
        }
        announce(serverPlayer, chest);
        serverPlayer.openMenu(chest);
        return InteractionResult.CONSUME;
    }

    private static void announce(ServerPlayer player, DungeonChestBlockEntity chest) {
        if (chest.refillIfDue()) {
            player.displayClientMessage(Component.translatable("kingdoms.dungeon.loot_refreshed"), true);
            return;
        }
        if (!chest.configured()) {
            if (player.hasPermissions(2)) {
                player.displayClientMessage(Component.translatable("kingdoms.dungeon.chest_unconfigured"), true);
            }
            return;
        }
        if (chest.looksEmpty()) {
            player.displayClientMessage(
                    Component.translatable(
                            "kingdoms.dungeon.loot_cooldown",
                            com.geydev.kalfactions.dungeon.DungeonLoot.formatRemaining(chest.remainingMillis())
                    ),
                    true
            );
            return;
        }
        player.displayClientMessage(Component.translatable("kingdoms.dungeon.loot_warning"), true);
    }

    @Override
    protected void onRemove(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState newState,
            boolean movedByPiston
    ) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof DungeonChestBlockEntity chest) {
            Containers.dropContents(level, pos, chest);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
