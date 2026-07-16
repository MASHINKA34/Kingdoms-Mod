package com.geydev.kalfactions.block;

import com.geydev.kalfactions.ClientBridge;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.net.FactionServerHooks;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class NewsBoardBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public NewsBoardBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return state;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return null;
        }
        if (player.hasPermissions(2)) {
            return state;
        }
        FactionManager manager = FactionManager.get(serverLevel);
        UUID owner = manager.getFactionIdAt(ClaimKey.of(serverLevel, context.getClickedPos())).orElse(null);
        UUID playerFaction = manager.getFactionIdForMember(player.getUUID()).orElse(null);
        if (owner != null && owner.equals(playerFaction)) {
            return state;
        }
        FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.error.news_board_territory"), false);
        return null;
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return BlockShapes.newsBoard(state.getValue(FACING));
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        return open(level);
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        if (player.isSecondaryUseActive()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        open(level);
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    private static InteractionResult open(Level level) {
        if (level.isClientSide()) {
            ClientBridge.openNews();
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }
}
