package com.geydev.kalfactions.entity;

import com.geydev.kalfactions.ClientBridge;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.net.FactionServerHooks;
import com.geydev.kalfactions.registry.ModItems;
import com.geydev.kalfactions.scout.ScoutManager;
import com.geydev.kalfactions.scout.ScoutOrder;
import com.geydev.kalfactions.scout.ScoutService;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
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

public final class MapScoutEntity extends PathfinderMob {
    public MapScoutEntity(EntityType<? extends MapScoutEntity> type, Level level) {
        super(type, level);
        setNoAi(true);
        setPersistenceRequired();
        setCustomName(Component.translatable("entity.kingdoms.map_scout"));
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
    public boolean isInvisibleTo(Player player) {
        return hiddenFor(player) || super.isInvisibleTo(player);
    }

    @Override
    public boolean isPickable() {
        return !(level().isClientSide() && ClientBridge.scoutBusy());
    }

    @Override
    public boolean canBeCollidedWith() {
        return isPickable() && super.canBeCollidedWith();
    }

    private boolean hiddenFor(Player player) {
        if (level().isClientSide()) {
            return ClientBridge.scoutBusy();
        }
        MinecraftServer server = level().getServer();
        if (server == null) {
            return false;
        }
        UUID factionId = FactionManager.get(server).getFactionIdForMember(player.getUUID()).orElse(null);
        return factionId != null && ScoutManager.get(server).hasActiveOrder(factionId);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && level() instanceof ServerLevel serverLevel && serverLevel.getServer() != null) {
            ScoutManager.get(serverLevel.getServer()).removePost(getUUID());
        }
        super.remove(reason);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide()) {
            return ClientBridge.scoutBusy() ? InteractionResult.PASS : InteractionResult.sidedSuccess(true);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (serverPlayer.getItemInHand(hand).is(ModItems.TRADER_REMOVER.get())) {
            if (!serverPlayer.hasPermissions(2)) {
                serverPlayer.displayClientMessage(Component.translatable("kingdoms.remover.no_permission"), true);
                return InteractionResult.FAIL;
            }
            discard();
            return InteractionResult.SUCCESS;
        }
        if (!serverPlayer.getItemInHand(hand).isEmpty()) {
            return InteractionResult.PASS;
        }
        MinecraftServer server = serverPlayer.getServer();
        if (server != null) {
            UUID factionId = FactionManager.get(server).getFactionIdForMember(serverPlayer.getUUID()).orElse(null);
            ScoutOrder active = factionId == null
                    ? null
                    : ScoutManager.get(server).activeOrder(factionId).orElse(null);
            if (active != null) {
                FactionServerHooks.sendNotice(serverPlayer, ScoutService.busyNotice(active), false);
                ScoutService.pushState(serverPlayer);
                return InteractionResult.SUCCESS;
            }
        }
        ScoutService.openOrderScreen(serverPlayer, this);
        return InteractionResult.SUCCESS;
    }
}
