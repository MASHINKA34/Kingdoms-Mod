package com.geydev.kalfactions.item;

import com.geydev.kalfactions.dungeon.DungeonManager;
import com.geydev.kalfactions.registry.ModBlocks;
import com.geydev.kalfactions.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class WarpScrollItem extends Item {
    private static final int MAX_LANDING_RADIUS = 3;
    private static final int[] LANDING_HEIGHTS = {0, 1, -1, 2, -2, -3};

    public WarpScrollItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (player == null || !level.getBlockState(pos).is(ModBlocks.WARP_ANCHOR.get())) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            context.getItemInHand().set(
                    ModDataComponents.WARP_TARGET.get(),
                    GlobalPos.of(serverLevel.dimension(), pos.immutable())
            );
            serverLevel.playSound(
                    null,
                    pos,
                    SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.BLOCKS,
                    0.8F,
                    1.4F
            );
            serverPlayer.displayClientMessage(
                    Component.translatable("message.kingdoms.warp_scroll.bound"),
                    true
            );
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel origin) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if (!DungeonManager.get(origin).isDungeon(origin, serverPlayer.blockPosition())) {
            return refuse(serverPlayer, stack, "message.kingdoms.warp_scroll.outside_dungeon");
        }
        GlobalPos target = stack.get(ModDataComponents.WARP_TARGET.get());
        if (target == null) {
            return refuse(serverPlayer, stack, "message.kingdoms.warp_scroll.not_bound");
        }
        ServerLevel destination = origin.getServer().getLevel(target.dimension());
        if (destination == null) {
            return refuse(serverPlayer, stack, "message.kingdoms.warp_scroll.anchor_missing");
        }
        BlockPos anchor = target.pos();
        ChunkPos chunk = new ChunkPos(anchor);
        destination.getChunk(chunk.x, chunk.z);
        if (!destination.getBlockState(anchor).is(ModBlocks.WARP_ANCHOR.get())) {
            return refuse(serverPlayer, stack, "message.kingdoms.warp_scroll.anchor_missing");
        }
        BlockPos landing = findLanding(destination, anchor);
        playWarpEffects(origin, serverPlayer.position());
        serverPlayer.teleportTo(
                destination,
                landing.getX() + 0.5D,
                landing.getY(),
                landing.getZ() + 0.5D,
                serverPlayer.getYRot(),
                serverPlayer.getXRot()
        );
        playWarpEffects(destination, Vec3.atBottomCenterOf(landing));
        stack.shrink(1);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        GlobalPos target = stack.get(ModDataComponents.WARP_TARGET.get());
        if (target == null) {
            tooltip.add(Component.translatable("item.kingdoms.warp_scroll.unbound")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        BlockPos pos = target.pos();
        tooltip.add(Component.translatable(
                "item.kingdoms.warp_scroll.bound",
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                dimensionName(target.dimension())
        ).withStyle(ChatFormatting.GRAY));
    }

    private static Component dimensionName(ResourceKey<Level> dimension) {
        if (dimension.equals(Level.OVERWORLD)) {
            return Component.translatable("kingdoms.dimension.name.overworld");
        }
        if (dimension.equals(Level.NETHER)) {
            return Component.translatable("kingdoms.dimension.name.nether");
        }
        if (dimension.equals(Level.END)) {
            return Component.translatable("kingdoms.dimension.name.end");
        }
        return Component.literal(dimension.location().toString());
    }

    public static BlockPos findLanding(ServerLevel level, BlockPos anchor) {
        for (int radius = 1; radius <= MAX_LANDING_RADIUS; radius++) {
            for (int offsetX = -radius; offsetX <= radius; offsetX++) {
                for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                    if (Math.abs(offsetX) != radius && Math.abs(offsetZ) != radius) {
                        continue;
                    }
                    BlockPos standing = standingSpot(level, anchor.offset(offsetX, 0, offsetZ));
                    if (standing != null) {
                        return standing;
                    }
                }
            }
        }
        return aboveAnchor(level, anchor);
    }

    private static BlockPos standingSpot(ServerLevel level, BlockPos column) {
        for (int offsetY : LANDING_HEIGHTS) {
            BlockPos feet = column.above(offsetY);
            if (isFree(level, feet) && isFree(level, feet.above()) && isStandable(level, feet.below())) {
                return feet;
            }
        }
        return null;
    }

    private static BlockPos aboveAnchor(ServerLevel level, BlockPos anchor) {
        BlockPos.MutableBlockPos cursor = anchor.mutable().move(Direction.UP);
        while (cursor.getY() < level.getMaxBuildHeight() - 1) {
            if (isFree(level, cursor) && isFree(level, cursor.above())) {
                return cursor.immutable();
            }
            cursor.move(Direction.UP);
        }
        return anchor.above().immutable();
    }

    private static boolean isFree(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getFluidState(pos).isEmpty();
    }

    private static boolean isStandable(ServerLevel level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static void playWarpEffects(ServerLevel level, Vec3 position) {
        level.sendParticles(
                ParticleTypes.PORTAL,
                position.x,
                position.y + 1.0D,
                position.z,
                48,
                0.4D,
                0.8D,
                0.4D,
                0.2D
        );
        level.playSound(
                null,
                position.x,
                position.y,
                position.z,
                SoundEvents.PLAYER_TELEPORT,
                SoundSource.PLAYERS,
                0.8F,
                1.0F
        );
    }

    private static InteractionResultHolder<ItemStack> refuse(
            ServerPlayer player,
            ItemStack stack,
            String messageKey
    ) {
        player.displayClientMessage(Component.translatable(messageKey), true);
        return InteractionResultHolder.fail(stack);
    }
}
