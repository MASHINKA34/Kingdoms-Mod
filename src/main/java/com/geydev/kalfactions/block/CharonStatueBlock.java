package com.geydev.kalfactions.block;

import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A static, linked 2 x 2 x 3 statue. Each cell owns its model and bounded collision. */
public final class CharonStatueBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART_X = IntegerProperty.create("part_x", 0, 1);
    public static final IntegerProperty PART_Z = IntegerProperty.create("part_z", 0, 1);
    public static final IntegerProperty LAYER = IntegerProperty.create("layer", 0, 2);
    private static final ThreadLocal<Set<Removal>> REMOVING = ThreadLocal.withInitial(HashSet::new);

    public CharonStatueBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(PART_X, 0).setValue(PART_Z, 0).setValue(LAYER, 0));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos anchor = context.getClickedPos();
        Direction facing = context.getHorizontalDirection().getOpposite();
        CollisionContext collision = context.getPlayer() == null
                ? CollisionContext.empty() : CollisionContext.of(context.getPlayer());
        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 2; z++) {
                BlockPos floor = partPos(anchor, facing, x, 0, z).below();
                if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
                    return null;
                }
                for (int y = 0; y < 3; y++) {
                    BlockPos pos = partPos(anchor, facing, x, y, z);
                    if (level.isOutsideBuildHeight(pos)
                            || !level.getWorldBorder().isWithinBounds(pos)
                            || !level.getBlockState(pos).canBeReplaced(context)
                            || !level.isUnobstructed(partState(facing, x, y, z), pos, collision)) {
                        return null;
                    }
                }
            }
        }
        return partState(facing, 0, 0, 0);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !isAnchor(state)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        // These writes participate in NeoForge's placement snapshots, so the existing
        // multi-place protection handler checks all cells and can roll the whole item back.
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 2; z++) {
                    if (x != 0 || y != 0 || z != 0) {
                        level.setBlock(partPos(pos, facing, x, y, z), partState(facing, x, y, z), UPDATE_ALL);
                    }
                }
            }
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CharonStatueShapes.get(state.getValue(FACING), state.getValue(PART_X),
                state.getValue(LAYER), state.getValue(PART_Z));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement,
            boolean movedByPiston) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide() && !level.restoringBlockSnapshots) {
            BlockPos anchor = anchorPos(pos, state);
            Removal removal = new Removal(level, anchor);
            Set<Removal> active = REMOVING.get();
            if (active.add(removal)) {
                try {
                    Direction facing = state.getValue(FACING);
                    for (int x = 0; x < 2; x++) {
                        for (int y = 0; y < 3; y++) {
                            for (int z = 0; z < 2; z++) {
                                BlockPos other = partPos(anchor, facing, x, y, z);
                                if (!other.equals(pos) && level.getBlockState(other).equals(partState(facing, x, y, z))) {
                                    // Only the originally mined/exploded cell runs its loot table.
                                    level.setBlock(other, Blocks.AIR.defaultBlockState(), UPDATE_ALL | UPDATE_SUPPRESS_DROPS);
                                }
                            }
                        }
                    }
                } finally {
                    active.remove(removal);
                    if (active.isEmpty()) {
                        REMOVING.remove();
                    }
                }
            }
        }
        super.onRemove(state, level, pos, replacement, movedByPiston);
    }

    public BlockState partState(Direction facing, int x, int y, int z) {
        return defaultBlockState().setValue(FACING, facing).setValue(PART_X, x)
                .setValue(LAYER, y).setValue(PART_Z, z);
    }

    public static BlockPos partPos(BlockPos anchor, Direction facing, int x, int y, int z) {
        return anchor.relative(facing.getClockWise(), x).relative(facing.getOpposite(), z).above(y);
    }

    public static BlockPos anchorPos(BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        return pos.relative(facing.getCounterClockWise(), state.getValue(PART_X))
                .relative(facing, state.getValue(PART_Z)).below(state.getValue(LAYER));
    }

    private static boolean isAnchor(BlockState state) {
        return state.getValue(PART_X) == 0 && state.getValue(PART_Z) == 0 && state.getValue(LAYER) == 0;
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
        builder.add(FACING, PART_X, PART_Z, LAYER);
    }

    private record Removal(Level level, BlockPos anchor) {
    }
}
