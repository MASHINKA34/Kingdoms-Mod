package com.geydev.kalfactions.block;

import com.geydev.kalfactions.faith.FaithService;
import com.geydev.kalfactions.registry.ModBlocks;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class StoneGodStatueBlock extends Block implements EntityBlock {
    public static final int HEIGHT = 8;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty SEGMENT = IntegerProperty.create("segment", 0, HEIGHT - 1);

    private final Map<Direction, List<StoneGodStatueCollisionModel.Cell>> collisionCellsByDirection;

    public StoneGodStatueBlock(
            BlockBehaviour.Properties properties,
            String modelName,
            @Nullable Float centerXOverride,
            @Nullable Float centerZOverride
    ) {
        super(properties);
        this.collisionCellsByDirection = StoneGodStatueCollisionModel.load(
                modelName,
                centerXOverride,
                centerZOverride
        );
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
        for (int segment = 0; segment < HEIGHT; segment++) {
            if (!level.getBlockState(base.above(segment)).canBeReplaced()) {
                return null;
            }
        }
        for (StoneGodStatueCollisionModel.Cell cell : collisionCells(facing)) {
            if (cell.offsetX() == 0 && cell.offsetZ() == 0) {
                continue;
            }
            if (!level.getBlockState(base.offset(cell.offsetX(), cell.segment(), cell.offsetZ())).canBeReplaced()) {
                return null;
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
        List<StoneGodStatueCollisionModel.Cell> cells = collisionCells(state.getValue(FACING));
        removeStaleCollisionCells(level, pos, cells);
        for (StoneGodStatueCollisionModel.Cell cell : cells) {
            if (cell.offsetX() == 0 && cell.offsetZ() == 0) {
                continue;
            }
            BlockPos collisionPos = pos.offset(cell.offsetX(), cell.segment(), cell.offsetZ());
            BlockState existingState = level.getBlockState(collisionPos);
            if (isMatchingCollisionSegment(existingState, cell)) {
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
                                .setValue(StoneGodStatueCollisionBlock.SEGMENT, cell.segment()),
                        UPDATE_ALL
                );
            }
        }
    }

    private static void removeStaleCollisionCells(
            Level level,
            BlockPos base,
            List<StoneGodStatueCollisionModel.Cell> cells
    ) {
        Set<CellPosition> expected = new HashSet<>();
        for (StoneGodStatueCollisionModel.Cell cell : cells) {
            if (cell.offsetX() != 0 || cell.offsetZ() != 0) {
                expected.add(new CellPosition(cell.offsetX(), cell.segment(), cell.offsetZ()));
            }
        }
        for (int offsetX = -StoneGodStatueCollisionBlock.maxOffset();
                offsetX <= StoneGodStatueCollisionBlock.maxOffset();
                offsetX++) {
            for (int offsetZ = -StoneGodStatueCollisionBlock.maxOffset();
                    offsetZ <= StoneGodStatueCollisionBlock.maxOffset();
                    offsetZ++) {
                if (offsetX == 0 && offsetZ == 0) {
                    continue;
                }
                for (int segment = 0; segment < HEIGHT; segment++) {
                    BlockPos candidate = base.offset(offsetX, segment, offsetZ);
                    BlockState candidateState = level.getBlockState(candidate);
                    if (candidateState.getBlock() instanceof StoneGodStatueCollisionBlock
                            && StoneGodStatueCollisionBlock.anchorOf(candidate, candidateState).equals(base)
                            && !expected.contains(new CellPosition(offsetX, segment, offsetZ))) {
                        StoneGodStatueCollisionBlock.removeWithoutDestroyingAnchor(level, candidate);
                    }
                }
            }
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getCollisionSlice(state, 0, state.getValue(SEGMENT), 0);
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
            for (int segment = 0; segment < HEIGHT; segment++) {
                BlockPos segmentPos = base.above(segment);
                if (segmentPos.equals(pos)) {
                    continue;
                }
                BlockState segmentState = level.getBlockState(segmentPos);
                if (isMatchingSegment(segmentState, facing, segment)) {
                    level.setBlock(
                            segmentPos,
                            Blocks.AIR.defaultBlockState(),
                            UPDATE_ALL | UPDATE_SUPPRESS_DROPS
                    );
                }
            }
            for (int offsetX = -StoneGodStatueCollisionBlock.maxOffset();
                    offsetX <= StoneGodStatueCollisionBlock.maxOffset();
                    offsetX++) {
                for (int offsetZ = -StoneGodStatueCollisionBlock.maxOffset();
                        offsetZ <= StoneGodStatueCollisionBlock.maxOffset();
                        offsetZ++) {
                    if (offsetX == 0 && offsetZ == 0) {
                        continue;
                    }
                    for (int segment = 0; segment < HEIGHT; segment++) {
                        BlockPos collisionPos = base.offset(offsetX, segment, offsetZ);
                        BlockState collisionState = level.getBlockState(collisionPos);
                        if (collisionState.getBlock() instanceof StoneGodStatueCollisionBlock
                                && StoneGodStatueCollisionBlock.anchorOf(collisionPos, collisionState).equals(base)) {
                            StoneGodStatueCollisionBlock.removeWithoutDestroyingAnchor(level, collisionPos);
                        }
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

    VoxelShape getCollisionSlice(BlockState state, int offsetX, int segment, int offsetZ) {
        for (StoneGodStatueCollisionModel.Cell cell : collisionCells(state.getValue(FACING))) {
            if (cell.offsetX() == offsetX
                    && cell.segment() == segment
                    && cell.offsetZ() == offsetZ) {
                return cell.shape();
            }
        }
        return Shapes.empty();
    }

    private List<StoneGodStatueCollisionModel.Cell> collisionCells(Direction facing) {
        return collisionCellsByDirection.get(facing);
    }

    private static boolean isMatchingCollisionSegment(
            BlockState state,
            StoneGodStatueCollisionModel.Cell cell
    ) {
        return state.getBlock() instanceof StoneGodStatueCollisionBlock
                && StoneGodStatueCollisionBlock.decodeOffset(
                        state.getValue(StoneGodStatueCollisionBlock.OFFSET_X)
                ) == cell.offsetX()
                && StoneGodStatueCollisionBlock.decodeOffset(
                        state.getValue(StoneGodStatueCollisionBlock.OFFSET_Z)
                ) == cell.offsetZ()
                && state.getValue(StoneGodStatueCollisionBlock.SEGMENT) == cell.segment();
    }

    private record CellPosition(int offsetX, int segment, int offsetZ) {
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
