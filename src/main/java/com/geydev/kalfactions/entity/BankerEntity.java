package com.geydev.kalfactions.entity;

import com.geydev.kalfactions.outpost.trader.TraderService;
import com.geydev.kalfactions.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class BankerEntity extends PathfinderMob {
    public BankerEntity(EntityType<? extends BankerEntity> type, Level level) {
        super(type, level);
        setNoAi(true);
        setPersistenceRequired();
        setCustomName(Component.translatable("entity.kingdoms.banker"));
        setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D);
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return super.isInvulnerableTo(source) || !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide()) {
            return InteractionResult.sidedSuccess(true);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (serverPlayer.getItemInHand(hand).is(ModItems.TRADER_REMOVER.get())) {
                if (!serverPlayer.hasPermissions(2)) {
                    serverPlayer.displayClientMessage(Component.translatable("kingdoms.remover.no_permission"), true);
                    return InteractionResult.FAIL;
                }
                discard();
                return InteractionResult.SUCCESS;
            }
            TraderService.openBanker(serverPlayer, this);
        }
        return InteractionResult.SUCCESS;
    }
}
