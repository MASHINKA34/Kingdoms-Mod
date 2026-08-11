package com.geydev.kalfactions.block;

import com.geydev.kalfactions.faith.FaithService;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class StoneGodStatueCollisionBlock extends Block {
    private static final int OFFSET_BIAS = 3;
    public static final IntegerProperty OFFSET_X = IntegerProperty.create("offset_x", 0, 6);
    public static final IntegerProperty OFFSET_Z = IntegerProperty.create("offset_z", 0, 6);
    public static final IntegerProperty SEGMENT = IntegerProperty.create("segment", 0, StoneGodStatueBlock.HEIGHT - 1);

    public StoneGodStatueCollisionBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(OFFSET_X, OFFSET_BIAS)
                .setValue(OFFSET_Z, OFFSET_BIAS)
                .setValue(SEGMENT, 0));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        BlockState anchorState = level.getBlockState(anchorPos(pos, state));
        if (anchorState.getBlock() instanceof StoneGodStatueBlock statue) {
            return statue.getCollisionSlice(
                    anchorState,
                    decodeOffset(state.getValue(OFFSET_X)),
                    decodeOffset(state.getValue(OFFSET_Z))
            );
        }
        return Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return getShape(state, level, pos, context);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            destroyAnchor(level, pos, state, player, !player.isCreative());
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()) {
            destroyAnchor(level, pos, state, null, true);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OFFSET_X, OFFSET_Z, SEGMENT);
    }

    public static BlockPos anchorOf(BlockPos pos, BlockState state) {
        return anchorPos(pos, state);
    }

    private static BlockPos anchorPos(BlockPos pos, BlockState state) {
        return pos.offset(
                -decodeOffset(state.getValue(OFFSET_X)),
                -state.getValue(SEGMENT),
                -decodeOffset(state.getValue(OFFSET_Z))
        );
    }

    static int encodeOffset(int offset) {
        return offset + OFFSET_BIAS;
    }

    static int decodeOffset(int encodedOffset) {
        return encodedOffset - OFFSET_BIAS;
    }

    private static void destroyAnchor(
            Level level,
            BlockPos pos,
            BlockState state,
            @Nullable Player player,
            boolean drop
    ) {
        BlockPos anchor = anchorPos(pos, state);
        if (level.getBlockState(anchor).getBlock() instanceof StoneGodStatueBlock) {
            level.destroyBlock(anchor, drop, player);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        return FaithService.openFromBlock(level, pos, player);
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
        FaithService.openFromBlock(level, pos, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

}
