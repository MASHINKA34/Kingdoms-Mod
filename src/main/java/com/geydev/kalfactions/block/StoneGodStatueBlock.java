package com.geydev.kalfactions.block;

import com.geydev.kalfactions.faith.FaithService;
import com.geydev.kalfactions.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class StoneGodStatueBlock extends Block implements EntityBlock {
    public static final int HEIGHT = 8;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty SEGMENT = IntegerProperty.create("segment", 0, HEIGHT - 1);

    private final VoxelShape northShape;
    private final VoxelShape eastShape;
    private final List<CollisionCell> northCollisionCells;
    private final List<CollisionCell> eastCollisionCells;

    public StoneGodStatueBlock(
            BlockBehaviour.Properties properties,
            VoxelShape northShape,
            VoxelShape eastShape
    ) {
        super(properties);
        this.northShape = northShape;
        this.eastShape = eastShape;
        this.northCollisionCells = createCollisionCells(northShape);
        this.eastCollisionCells = createCollisionCells(eastShape);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(SEGMENT, 0));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(SEGMENT) == 0 ? new StoneGodStatueBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos base = context.getClickedPos();
        Level level = context.getLevel();
        Direction facing = context.getHorizontalDirection().getOpposite();
        if (base.getY() > level.getMaxBuildHeight() - HEIGHT) {
            return null;
        }
        for (CollisionCell cell : collisionCells(facing)) {
            for (int segment = 0; segment < HEIGHT; segment++) {
                if (!level.getBlockState(base.offset(cell.offsetX(), segment, cell.offsetZ())).canBeReplaced()) {
                    return null;
                }
            }
        }
        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(SEGMENT, 0);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || state.getValue(SEGMENT) != 0) {
            return;
        }
        for (int segment = 1; segment < HEIGHT; segment++) {
            level.setBlock(pos.above(segment), state.setValue(SEGMENT, segment), UPDATE_ALL);
        }
        ensureCollisionField(level, pos, state);
    }

    void ensureCollisionField(Level level, BlockPos pos, BlockState state) {
        BlockState collisionState = ModBlocks.STONE_GOD_STATUE_COLLISION.get().defaultBlockState();
        for (CollisionCell cell : collisionCells(state.getValue(FACING))) {
            if (cell.offsetX() == 0 && cell.offsetZ() == 0) {
                continue;
            }
            for (int segment = 0; segment < HEIGHT; segment++) {
                BlockPos collisionPos = pos.offset(cell.offsetX(), segment, cell.offsetZ());
                BlockState existingState = level.getBlockState(collisionPos);
                if (isMatchingCollisionSegment(existingState, cell, segment)) {
                    continue;
                }
                if (existingState.canBeReplaced()) {
                    level.setBlock(
                            collisionPos,
                            collisionState
                                    .setValue(
                                            StoneGodStatueCollisionBlock.OFFSET_X,
                                            StoneGodStatueCollisionBlock.encodeOffset(cell.offsetX())
                                    )
                                    .setValue(
                                            StoneGodStatueCollisionBlock.OFFSET_Z,
                                            StoneGodStatueCollisionBlock.encodeOffset(cell.offsetZ())
                                    )
                                    .setValue(StoneGodStatueCollisionBlock.SEGMENT, segment),
                            UPDATE_ALL
                    );
                }
            }
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? eastShape : northShape;
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
        if (!level.isClientSide() && state.getValue(SEGMENT) != 0) {
            BlockPos base = pos.below(state.getValue(SEGMENT));
            BlockState baseState = level.getBlockState(base);
            if (isMatchingSegment(baseState, state.getValue(FACING), 0)) {
                level.destroyBlock(base, !player.isCreative(), player);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()) {
            int removedSegment = state.getValue(SEGMENT);
            BlockPos base = pos.below(removedSegment);
            Direction facing = state.getValue(FACING);
            for (CollisionCell cell : collisionCells(facing)) {
                for (int segment = 0; segment < HEIGHT; segment++) {
                    BlockPos segmentPos = base.offset(cell.offsetX(), segment, cell.offsetZ());
                    if (segmentPos.equals(pos)) {
                        continue;
                    }
                    BlockState segmentState = level.getBlockState(segmentPos);
                    if ((cell.offsetX() == 0 && cell.offsetZ() == 0
                            && isMatchingSegment(segmentState, facing, segment))
                            || isMatchingCollisionSegment(segmentState, cell, segment)) {
                        level.setBlock(
                                segmentPos,
                                Blocks.AIR.defaultBlockState(),
                                UPDATE_ALL | UPDATE_SUPPRESS_DROPS
                        );
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
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
        builder.add(FACING, SEGMENT);
    }

    private boolean isMatchingSegment(BlockState state, Direction facing, int segment) {
        return state.is(this)
                && state.getValue(FACING) == facing
                && state.getValue(SEGMENT) == segment;
    }

    VoxelShape getCollisionSlice(BlockState state, int offsetX, int offsetZ) {
        for (CollisionCell cell : collisionCells(state.getValue(FACING))) {
            if (cell.offsetX() == offsetX && cell.offsetZ() == offsetZ) {
                return cell.shape();
            }
        }
        return Shapes.block();
    }

    private List<CollisionCell> collisionCells(Direction facing) {
        return facing.getAxis() == Direction.Axis.X ? eastCollisionCells : northCollisionCells;
    }

    private static List<CollisionCell> createCollisionCells(VoxelShape shape) {
        AABB bounds = shape.bounds();
        int minX = Mth.floor(bounds.minX);
        int maxX = Mth.ceil(bounds.maxX) - 1;
        int minZ = Mth.floor(bounds.minZ);
        int maxZ = Mth.ceil(bounds.maxZ) - 1;
        List<CollisionCell> cells = new ArrayList<>((maxX - minX + 1) * (maxZ - minZ + 1));
        for (int offsetX = minX; offsetX <= maxX; offsetX++) {
            for (int offsetZ = minZ; offsetZ <= maxZ; offsetZ++) {
                VoxelShape slice = Shapes.join(
                        shape.move(-offsetX, 0.0D, -offsetZ),
                        Shapes.block(),
                        BooleanOp.AND
                ).optimize();
                if (!slice.isEmpty()) {
                    cells.add(new CollisionCell(offsetX, offsetZ, slice));
                }
            }
        }
        return List.copyOf(cells);
    }

    private static boolean isMatchingCollisionSegment(BlockState state, CollisionCell cell, int segment) {
        return state.getBlock() instanceof StoneGodStatueCollisionBlock
                && StoneGodStatueCollisionBlock.decodeOffset(
                        state.getValue(StoneGodStatueCollisionBlock.OFFSET_X)
                ) == cell.offsetX()
                && StoneGodStatueCollisionBlock.decodeOffset(
                        state.getValue(StoneGodStatueCollisionBlock.OFFSET_Z)
                ) == cell.offsetZ()
                && state.getValue(StoneGodStatueCollisionBlock.SEGMENT) == segment;
    }

    private record CollisionCell(int offsetX, int offsetZ, VoxelShape shape) {
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
