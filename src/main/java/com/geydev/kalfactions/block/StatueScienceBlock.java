package com.geydev.kalfactions.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

public final class StatueScienceBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final float CRYSTAL_BOB_AMPLITUDE = 0.09F;
    public static final float CRYSTAL_BOB_SPEED = 0.05F;

    private static final DustParticleOptions CRYSTAL_DUST =
            new DustParticleOptions(new Vector3f(0.18F, 0.58F, 1.0F), 0.85F);
    private static final VoxelShape LOWER_PLATFORM_SHAPE = Shapes.or(
            Block.box(1.0D, 0.0D, 1.0D, 15.0D, 2.0D, 15.0D),
            Block.box(2.0D, 2.0D, 2.0D, 14.0D, 4.0D, 14.0D),
            Block.box(3.0D, 4.0D, 3.0D, 13.0D, 5.0D, 13.0D)
    );
    private static final VoxelShape LOWER_CRYSTAL_OUTLINE_SHAPE = Shapes.or(
            Block.box(5.0D, 6.0D, 5.0D, 11.0D, 16.0D, 11.0D)
    );
    private static final VoxelShape UPPER_CRYSTAL_OUTLINE_SHAPE = Shapes.or(
            Block.box(3.5D, 0.0D, 5.0D, 12.5D, 5.0D, 11.0D),
            Block.box(4.5D, 5.0D, 4.5D, 11.5D, 9.0D, 11.5D),
            Block.box(5.5D, 9.0D, 5.5D, 10.5D, 13.0D, 10.5D),
            Block.box(6.25D, 13.0D, 6.5D, 9.0D, 15.5D, 9.5D)
    );

    public StatueScienceBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER
                ? new StatueScienceBlockEntity(pos, state)
                : null;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1
                || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), UPDATE_ALL);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        double bob = crystalBobOffset(level);
        VoxelShape crystal = state.getValue(HALF) == DoubleBlockHalf.UPPER
                ? UPPER_CRYSTAL_OUTLINE_SHAPE
                : LOWER_CRYSTAL_OUTLINE_SHAPE;
        crystal = crystal.move(0.0D, bob, 0.0D);
        return state.getValue(HALF) == DoubleBlockHalf.UPPER
                ? crystal
                : Shapes.or(LOWER_PLATFORM_SHAPE, crystal);
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER
                ? Shapes.empty()
                : LOWER_PLATFORM_SHAPE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(HALF) != DoubleBlockHalf.UPPER || random.nextInt(2) != 0) {
            return;
        }

        double angle = random.nextDouble() * Mth.TWO_PI;
        double radius = 0.18D + random.nextDouble() * 0.30D;
        double x = pos.getX() + 0.5D + Mth.cos((float) angle) * radius;
        double y = pos.getY() - 0.45D + random.nextDouble() * 1.35D
                + crystalBobOffset(level.getGameTime(), 0.0F);
        double z = pos.getZ() + 0.5D + Mth.sin((float) angle) * radius;
        double outwardSpeed = 0.006D + random.nextDouble() * 0.014D;
        double velocityX = Mth.cos((float) angle) * outwardSpeed;
        double velocityY = 0.004D + random.nextDouble() * 0.012D;
        double velocityZ = Mth.sin((float) angle) * outwardSpeed;

        level.addParticle(CRYSTAL_DUST, x, y, z, velocityX, velocityY, velocityZ);
        if (random.nextInt(12) == 0) {
            level.addParticle(ParticleTypes.END_ROD, x, y, z,
                    velocityX * 0.35D, velocityY * 0.5D, velocityZ * 0.35D);
        }
    }

    public static float crystalBobOffset(long gameTime, float partialTick) {
        float time = gameTime % 24000L + partialTick;
        return Mth.sin(time * CRYSTAL_BOB_SPEED) * CRYSTAL_BOB_AMPLITUDE;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos lower = pos.below();
            BlockState lowerState = level.getBlockState(lower);
            if (isMatchingHalf(lowerState, state.getValue(FACING), DoubleBlockHalf.LOWER)) {
                level.destroyBlock(lower, !player.isCreative(), player);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()) {
            Direction facing = state.getValue(FACING);
            BlockPos other = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos.above();
            DoubleBlockHalf otherHalf = state.getValue(HALF) == DoubleBlockHalf.UPPER
                    ? DoubleBlockHalf.LOWER
                    : DoubleBlockHalf.UPPER;
            if (isMatchingHalf(level.getBlockState(other), facing, otherHalf)) {
                level.setBlock(other, Blocks.AIR.defaultBlockState(), UPDATE_ALL | UPDATE_SUPPRESS_DROPS);
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
        builder.add(FACING, HALF);
    }

    private boolean isMatchingHalf(BlockState state, Direction facing, DoubleBlockHalf half) {
        return state.is(this) && state.getValue(FACING) == facing && state.getValue(HALF) == half;
    }

    private static float crystalBobOffset(BlockGetter level) {
        return level instanceof Level concreteLevel
                ? crystalBobOffset(concreteLevel.getGameTime(), 0.0F)
                : 0.0F;
    }
}
