package com.geydev.kalfactions.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class KeyForgeBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final KeyForgeType forgeType;

    protected KeyForgeBlock(KeyForgeType forgeType, BlockBehaviour.Properties properties) {
        super(properties);
        this.forgeType = forgeType;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public final KeyForgeType forgeType() {
        return forgeType;
    }

    @Override
    protected abstract MapCodec<? extends BaseEntityBlock> codec();

    @Override
    protected final RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public final BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new KeyForgeBlockEntity(forgeType, pos, state);
    }

    @Nullable
    @Override
    public final <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, forgeType.blockEntityType(), KeyForgeBlockEntity::serverTick);
    }

    @Override
    protected final InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (level.isClientSide()) {
            return InteractionResult.sidedSuccess(true);
        }
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof KeyForgeBlockEntity forge
                && forge.forgeType() == forgeType) {
            serverPlayer.openMenu(forge, buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(false);
    }

    @Override
    protected final void onRemove(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState newState,
            boolean movedByPiston
    ) {
        if (!state.is(newState.getBlock())
                && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof KeyForgeBlockEntity forge) {
            forge.dropContents(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public final BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected final VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return shapeFor(state.getValue(FACING));
    }

    @Override
    protected final VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return shapeFor(state.getValue(FACING));
    }

    @Override
    protected final BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected final BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected final void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    protected abstract VoxelShape shapeFor(Direction facing);

    protected static VoxelShape join(VoxelShape first, VoxelShape second) {
        return Shapes.joinUnoptimized(first, second, BooleanOp.OR).optimize();
    }

    protected static VoxelShape rotateShape(VoxelShape source, Direction facing) {
        VoxelShape[] result = {Shapes.empty()};
        source.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> result[0] = Shapes.or(
                result[0],
                switch (facing) {
                    case SOUTH -> Shapes.box(
                            1.0D - maxX, minY, 1.0D - maxZ,
                            1.0D - minX, maxY, 1.0D - minZ
                    );
                    case EAST -> Shapes.box(
                            1.0D - maxZ, minY, minX,
                            1.0D - minZ, maxY, maxX
                    );
                    case WEST -> Shapes.box(
                            minZ, minY, 1.0D - maxX,
                            maxZ, maxY, 1.0D - minX
                    );
                    default -> Shapes.box(minX, minY, minZ, maxX, maxY, maxZ);
                }
        ));
        return result[0].optimize();
    }
}
