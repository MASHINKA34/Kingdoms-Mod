package com.geydev.kalfactions.entity;

import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.registry.ModSounds;
import com.geydev.kalfactions.territory.WorldZonePolicy;
import java.util.UUID;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class KolyvanEntity extends PathfinderMob {
    private static final double CHASE_SPEED = 1.0D;
    private static final int REPATH_INTERVAL_TICKS = 10;
    private static final int ZONE_CHECK_INTERVAL_TICKS = 20;

    private UUID targetPlayerId;
    private long despawnAtGameTime;

    public KolyvanEntity(EntityType<? extends KolyvanEntity> type, Level level) {
        super(type, level);
        setSilent(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.42D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.ATTACK_DAMAGE, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    public void hunt(ServerPlayer player, int lifetimeSeconds) {
        targetPlayerId = player.getUUID();
        despawnAtGameTime = level().getGameTime() + Math.max(1, lifetimeSeconds) * 20L;
        getLookControl().setLookAt(player, 30.0F, 30.0F);
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    protected void customServerAiStep() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Player target = targetPlayerId == null ? null : serverLevel.getPlayerByUUID(targetPlayerId);
        if (target == null || !target.isAlive() || serverLevel.getGameTime() >= despawnAtGameTime) {
            discard();
            return;
        }
        if (tickCount % ZONE_CHECK_INTERVAL_TICKS == 0
                && !WorldZonePolicy.isBlack(serverLevel, target.blockPosition())) {
            discard();
            return;
        }
        getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (tickCount % REPATH_INTERVAL_TICKS == 0) {
            getNavigation().moveTo(target, CHASE_SPEED);
        }
        double reach = ModConfigSpec.BLACK_ZONE_KOLYVAN_REACH_DISTANCE.get();
        if (distanceToSqr(target) <= reach * reach) {
            seize(serverLevel, target);
        }
    }

    private void seize(ServerLevel level, Player target) {
        level.playSound(
                null,
                getX(),
                getY(),
                getZ(),
                ModSounds.KOLYVAN.get(),
                SoundSource.HOSTILE,
                1.0F,
                1.0F
        );
        target.addEffect(new MobEffectInstance(
                MobEffects.BLINDNESS,
                Math.max(1, ModConfigSpec.BLACK_ZONE_KOLYVAN_BLINDNESS_SECONDS.getAsInt()) * 20,
                0,
                false,
                true,
                true
        ));
        discard();
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return super.isInvulnerableTo(source) || !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return true;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    @Override
    public boolean isPersistenceRequired() {
        return false;
    }
}
