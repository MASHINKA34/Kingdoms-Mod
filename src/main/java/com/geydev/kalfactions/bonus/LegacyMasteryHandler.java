package com.geydev.kalfactions.bonus;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionBonus;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.faction.LegacyEffect;
import com.geydev.kalfactions.protection.FactionAccess;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class LegacyMasteryHandler {
    public static final double ENCHANTER_XP_BONUS = 0.30D;
    public static final double MERCHANT_BUY_DISCOUNT = 0.30D;
    public static final double BUILDER_OUTPOST_DISCOUNT = 0.50D;
    private static final String MINER_VISION_KEY = "kingdoms:miner_vision";
    private static final int REFRESH_INTERVAL_TICKS = 100;

    public static void toggleMinerVision(ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (!FactionAccess.hasLegacyMastery(player, FactionBonus.MINERS)) {
            player.displayClientMessage(Component.translatable("kingdoms.legacy.miner_vision.locked"), true);
            return;
        }
        boolean enabled = !minerVisionEnabled(player);
        player.getPersistentData().putBoolean(MINER_VISION_KEY, enabled);
        if (enabled) {
            applyMinerVision(player);
        } else {
            clearMinerVision(player);
        }
        player.displayClientMessage(
                Component.translatable(enabled
                        ? "kingdoms.legacy.miner_vision.on"
                        : "kingdoms.legacy.miner_vision.off"),
                true
        );
        player.level().playSound(
                null,
                player.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS,
                0.5F,
                enabled ? 1.4F : 0.8F
        );
    }

    public static boolean minerVisionEnabled(ServerPlayer player) {
        return player.getPersistentData().getBoolean(MINER_VISION_KEY);
    }

    private static void applyMinerVision(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(
                MobEffects.NIGHT_VISION,
                MobEffectInstance.INFINITE_DURATION,
                0,
                true,
                false,
                true
        ));
        player.addEffect(new MobEffectInstance(
                MobEffects.DIG_SPEED,
                MobEffectInstance.INFINITE_DURATION,
                0,
                true,
                false,
                true
        ));
    }

    private static void clearMinerVision(ServerPlayer player) {
        player.removeEffect(MobEffects.NIGHT_VISION);
        player.removeEffect(MobEffects.DIG_SPEED);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.tickCount % REFRESH_INTERVAL_TICKS != 0
                || !minerVisionEnabled(player)) {
            return;
        }
        if (FactionAccess.hasLegacyMastery(player, FactionBonus.MINERS)) {
            applyMinerVision(player);
        } else {
            player.getPersistentData().putBoolean(MINER_VISION_KEY, false);
            clearMinerVision(player);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag original = event.getOriginal().getPersistentData();
        if (original.contains(MINER_VISION_KEY)) {
            event.getEntity().getPersistentData().putBoolean(
                    MINER_VISION_KEY,
                    original.getBoolean(MINER_VISION_KEY)
            );
        }
    }

    @SubscribeEvent
    public static void onXpChange(PlayerXpEvent.XpChange event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getAmount() <= 0
                || !FactionAccess.hasLegacyMastery(player, FactionBonus.ENCHANTERS)) {
            return;
        }
        event.setAmount((int) Math.round(event.getAmount() * (1.0D + ENCHANTER_XP_BONUS)));
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!isPiglin(event.getEntity())
                || !(event.getNewAboutToBeSetTarget() instanceof ServerPlayer target)) {
            return;
        }
        if (FactionAccess.hasLegacyMastery(target, FactionBonus.MERCHANTS)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPiglinDamage(LivingIncomingDamageEvent event) {
        if (event.isCanceled()
                || !(event.getEntity() instanceof ServerPlayer victim)
                || !isPiglin(event.getSource().getEntity())) {
            return;
        }
        if (FactionAccess.hasLegacyMastery(victim, FactionBonus.MERCHANTS)) {
            event.setCanceled(true);
        }
    }

    private static boolean isPiglin(Entity entity) {
        return entity instanceof AbstractPiglin || entity instanceof ZombifiedPiglin;
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getHand() != InteractionHand.MAIN_HAND
                || !FactionAccess.hasLegacyMastery(player, FactionBonus.NOMADS)) {
            return;
        }
        if (event.getTarget() instanceof AbstractHorse horse) {
            if (horse.isBaby() || horse.isVehicle() || !player.getItemInHand(event.getHand()).isEmpty()) {
                return;
            }
            if (!horse.isTamed()) {
                horse.tameWithName(player);
            }
            horse.setTarget(null);
            player.startRiding(horse);
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            return;
        }
        if (event.getTarget() instanceof Wolf wolf
                && !wolf.isTame()
                && !wolf.isAngry()
                && player.getItemInHand(event.getHand()).isEmpty()) {
            wolf.tame(player);
            wolf.setOrderedToSit(true);
            wolf.level().broadcastEntityEvent(wolf, (byte) 7);
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public static void onUnattendedHarvest(BlockDropsEvent event) {
        if (event.getBreaker() instanceof ServerPlayer
                || !(event.getLevel() instanceof ServerLevel level)
                || event.getDrops().isEmpty()) {
            return;
        }
        BlockState state = event.getState();
        if (!state.is(BlockTags.CROPS)) {
            return;
        }
        BlockPos pos = event.getPos();
        Faction owner = FactionManager.get(level).getFactionAt(ClaimKey.of(level, pos)).orElse(null);
        if (owner == null
                || !owner.hasBonus(FactionBonus.FARMERS)
                || !owner.hasLegacyMastery(FactionBonus.FARMERS)) {
            return;
        }
        double chance = owner.legacyValue(LegacyEffect.HARVEST);
        if (chance <= 0.0D || level.getRandom().nextDouble() >= chance) {
            return;
        }
        List<ItemEntity> extra = new ArrayList<>(event.getDrops().size());
        for (ItemEntity drop : event.getDrops()) {
            ItemStack copy = drop.getItem().copy();
            if (copy.isEmpty()) {
                continue;
            }
            ItemEntity bonus = new ItemEntity(level, drop.getX(), drop.getY(), drop.getZ(), copy);
            bonus.setDefaultPickUpDelay();
            extra.add(bonus);
        }
        event.getDrops().addAll(extra);
    }

    private LegacyMasteryHandler() {
    }
}
