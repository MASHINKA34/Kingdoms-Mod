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
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class SellerTraderEntity extends PathfinderMob {
    private static final int DATA_VERSION = 1;
    private static final double WANDERING_SPEED = 0.32D;
    private static final int WANDERING_LEASH_RADIUS = 7;
    private static final EntityDataAccessor<Integer> DATA_ROLE =
            SynchedEntityData.defineId(SellerTraderEntity.class, EntityDataSerializers.INT);
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

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ROLE, SellerTraderRole.PERMANENT.ordinal());
    }

    public SellerTraderRole traderRole() {
        return SellerTraderRole.byOrdinal(entityData.get(DATA_ROLE));
    }

    public void setTraderRole(SellerTraderRole role) {
        SellerTraderRole value = role == null ? SellerTraderRole.PERMANENT : role;
        entityData.set(DATA_ROLE, value.ordinal());
        setCustomName(Component.translatable("entity.kingdoms.seller_trader." + value.id()));
        applyRoleBehaviour(value);
    }

    private void applyRoleBehaviour(SellerTraderRole role) {
        boolean roaming = role == SellerTraderRole.WANDERING;
        setNoAi(!roaming);
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(roaming ? WANDERING_SPEED : 0.0D);
        }
        if (roaming) {
            restrictTo(blockPosition(), WANDERING_LEASH_RADIUS);
        } else {
            clearRestriction();
        }
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
        tag.putString("kingdomsTraderRole", traderRole().id());
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
        goalSelector.addGoal(1, new MoveTowardsRestrictionGoal(this, 0.4D));
        goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.35D, 0.9F));
        goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && traderRole() == SellerTraderRole.WANDERING && !hasRestriction()) {
            restrictTo(blockPosition(), WANDERING_LEASH_RADIUS);
        }
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
