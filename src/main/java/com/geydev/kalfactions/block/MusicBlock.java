package com.geydev.kalfactions.block;

import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.music.MusicManager;
import com.geydev.kalfactions.music.MusicRadius;
import com.geydev.kalfactions.music.MusicService;
import com.geydev.kalfactions.music.MusicSpeaker;
import com.geydev.kalfactions.registry.ModBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class MusicBlock extends Block implements EntityBlock {
    public MusicBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MusicBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(
            Level level,
            BlockPos pos,
            BlockState state,
            @Nullable LivingEntity placer,
            ItemStack stack
    ) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel) {
            MusicSpeaker speaker = MusicService.defaultSpeaker(serverLevel, pos);
            MusicManager.get(serverLevel).putSpeaker(speaker);
            if (serverLevel.getBlockEntity(pos) instanceof MusicBlockEntity music) {
                music.applySpeaker(speaker);
            }
            MusicRadius.refreshAll(serverLevel.getServer());
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            MusicManager.get(serverLevel).removeSpeaker(serverLevel.dimension(), pos);
            MusicRadius.refreshAll(serverLevel.getServer());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block block,
            BlockPos fromPos,
            boolean movedByPiston
    ) {
        super.neighborChanged(state, level, pos, block, fromPos, movedByPiston);
        if (!(level instanceof ServerLevel serverLevel) || !ModConfigSpec.MUSIC_REDSTONE_CONTROL.get()) {
            return;
        }
        MusicManager manager = MusicManager.get(serverLevel);
        MusicSpeaker speaker = manager.speaker(serverLevel.dimension(), pos).orElse(null);
        if (speaker == null || !speaker.redstone() || !speaker.hasTrack()) {
            return;
        }
        boolean signal = serverLevel.hasNeighborSignal(pos);
        if (signal == speaker.playing()) {
            return;
        }
        MusicSpeaker updated = speaker.withPlaying(signal);
        manager.putSpeaker(updated);
        if (serverLevel.getBlockEntity(pos) instanceof MusicBlockEntity music) {
            music.applySpeaker(updated);
        }
        MusicRadius.refreshAll(serverLevel.getServer());
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        return open(level, pos, player);
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
        open(level, pos, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    private static InteractionResult open(Level level, BlockPos pos, Player player) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            MusicService.openScreen(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
