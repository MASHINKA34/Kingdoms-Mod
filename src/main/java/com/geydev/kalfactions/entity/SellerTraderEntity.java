package com.geydev.kalfactions.entity;

import com.geydev.kalfactions.outpost.trader.SellerOfferRotation;
import com.geydev.kalfactions.outpost.trader.SellerTraderRole;
import com.geydev.kalfactions.outpost.trader.TraderService;
import com.geydev.kalfactions.outpost.trader.TraderLifecycle;
import com.geydev.kalfactions.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
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

public final class SellerTraderEntity extends PathfinderMob {
    private static final int DATA_VERSION = 1;
    private SellerTraderRole traderRole = SellerTraderRole.PERMANENT;
    private java.util.UUID eventId;
    private java.util.UUID targetFactionId;
    private long expiresAtMillis;

    public SellerTraderEntity(EntityType<? extends SellerTraderEntity> type, Level level) {
        super(type, level);
        setNoAi(true);
        setPersistenceRequired();
        setCustomName(Component.translatable("entity.kingdoms.seller_trader"));
        setCustomNameVisible(true);
    }

    public SellerTraderRole traderRole() {
        return traderRole;
    }

    public void setTraderRole(SellerTraderRole role) {
        traderRole = role == null ? SellerTraderRole.PERMANENT : role;
        setCustomName(Component.translatable("entity.kingdoms.seller_trader." + traderRole.id()));
    }

    public java.util.Optional<java.util.UUID> eventId() {
        return java.util.Optional.ofNullable(eventId);
    }

    public void setEventId(java.util.UUID value) {
        eventId = value;
    }

    public java.util.Optional<java.util.UUID> targetFactionId() {
        return java.util.Optional.ofNullable(targetFactionId);
    }

    public void setTargetFactionId(java.util.UUID value) {
        targetFactionId = value;
    }

    public long expiresAtMillis() {
        return expiresAtMillis;
    }

    public void setExpiresAtMillis(long value) {
        expiresAtMillis = Math.max(0L, value);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("kingdomsTraderVersion", DATA_VERSION);
        tag.putString("kingdomsTraderRole", traderRole.id());
        if (eventId != null) {
            tag.putUUID("kingdomsEventId", eventId);
        }
        if (targetFactionId != null) {
            tag.putUUID("kingdomsTargetFaction", targetFactionId);
        }
        tag.putLong("kingdomsExpiresAt", expiresAtMillis);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setTraderRole(SellerTraderRole.parse(tag.getString("kingdomsTraderRole"))
                .orElse(SellerTraderRole.PERMANENT));
        eventId = tag.hasUUID("kingdomsEventId") ? tag.getUUID("kingdomsEventId") : null;
        targetFactionId = tag.hasUUID("kingdomsTargetFaction") ? tag.getUUID("kingdomsTargetFaction") : null;
        expiresAtMillis = Math.max(0L, tag.getLong("kingdomsExpiresAt"));
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
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && level() instanceof ServerLevel serverLevel) {
            TraderLifecycle.onRemoved(this, serverLevel);
            SellerOfferRotation.get(serverLevel.getServer()).removeShop(getUUID());
        }
        super.remove(reason);
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
            TraderService.openSeller(serverPlayer, this);
        }
        return InteractionResult.SUCCESS;
    }
}
