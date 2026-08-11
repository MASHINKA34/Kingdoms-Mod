package com.geydev.kalfactions.faith;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.config.ModConfigSpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class FaithEffects {
    public static final ResourceLocation WAR_HEALTH_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "faith_war_health");

    private static final int REFRESH_INTERVAL_TICKS = 20;
    private static final Map<UUID, Boolean> HIGHLIGHT_STATE = new HashMap<>();

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % REFRESH_INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            refresh(player);
        }
        if (server.getTickCount() % (REFRESH_INTERVAL_TICKS * 60) == 0) {
            FaithManager.get(server).pruneForfeits(System.currentTimeMillis());
        }
    }

    public static void refresh(ServerPlayer player) {
        applyWarHealth(player, FaithBonuses.warBonusHealth(FaithBonuses.activeLevel(player, FaithGod.WAR)));
        boolean highlight = FaithBonuses.economyHighlight(FaithBonuses.activeLevel(player, FaithGod.ECONOMY));
        Boolean previous = HIGHLIGHT_STATE.get(player.getUUID());
        if (previous == null || previous != highlight) {
            HIGHLIGHT_STATE.put(player.getUUID(), highlight);
            PacketDistributor.sendToPlayer(player, new FaithPayloads.S2COreHighlight(
                    highlight,
                    Math.max(1, ModConfigSpec.FAITH_ECONOMY_HIGHLIGHT_RADIUS.getAsInt()),
                    Math.max(1, ModConfigSpec.FAITH_ECONOMY_HIGHLIGHT_MAX_BLOCKS.getAsInt()),
                    Math.max(1, ModConfigSpec.FAITH_ECONOMY_HIGHLIGHT_SCAN_TICKS.getAsInt())
            ));
        }
    }

    private static void applyWarHealth(ServerPlayer player, double bonus) {
        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) {
            return;
        }
        AttributeModifier existing = attribute.getModifier(WAR_HEALTH_MODIFIER_ID);
        if (bonus <= 0.0D) {
            if (existing != null) {
                attribute.removeModifier(WAR_HEALTH_MODIFIER_ID);
                clampHealth(player);
            }
            return;
        }
        if (existing != null && existing.amount() == bonus) {
            return;
        }
        attribute.addOrUpdateTransientModifier(new AttributeModifier(
                WAR_HEALTH_MODIFIER_ID,
                bonus,
                AttributeModifier.Operation.ADD_VALUE
        ));
    }

    private static void clampHealth(ServerPlayer player) {
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.isCanceled() || !(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        double bonus = FaithBonuses.warBonusDamage(FaithBonuses.activeLevel(attacker, FaithGod.WAR));
        if (bonus > 0.0D) {
            event.setAmount(event.getAmount() + (float) bonus);
        }
    }

    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        if (!(event.getAttackingPlayer() instanceof ServerPlayer player) || event.getDroppedExperience() <= 0) {
            return;
        }
        double bonus = FaithBonuses.scienceExperienceBonus(FaithBonuses.activeLevel(player, FaithGod.SCIENCE));
        if (bonus > 0.0D) {
            event.setDroppedExperience((int) Math.round(event.getDroppedExperience() * (1.0D + bonus)));
        }
    }

    @SubscribeEvent
    public static void onMobDrops(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim instanceof ServerPlayer || event.getDrops().isEmpty()) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) {
            return;
        }
        double chance = FaithBonuses.economyDropChance(FaithBonuses.activeLevel(player, FaithGod.ECONOMY));
        if (chance <= 0.0D || player.getRandom().nextDouble() >= chance) {
            return;
        }
        List<ItemEntity> extra = new ArrayList<>(event.getDrops().size());
        for (ItemEntity drop : event.getDrops()) {
            ItemStack copy = drop.getItem().copy();
            if (copy.isEmpty()) {
                continue;
            }
            ItemEntity bonus = new ItemEntity(victim.level(), drop.getX(), drop.getY(), drop.getZ(), copy);
            bonus.setDefaultPickUpDelay();
            extra.add(bonus);
        }
        event.getDrops().addAll(extra);
    }

    @SubscribeEvent
    public static void onFished(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getDrops().isEmpty()) {
            return;
        }
        double chance = FaithBonuses.economyDropChance(FaithBonuses.activeLevel(player, FaithGod.ECONOMY));
        if (chance <= 0.0D || player.getRandom().nextDouble() >= chance) {
            return;
        }
        List<ItemStack> extra = new ArrayList<>(event.getDrops().size());
        for (ItemStack stack : event.getDrops()) {
            if (!stack.isEmpty()) {
                extra.add(stack.copy());
            }
        }
        event.getDrops().addAll(extra);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            FaithService.forfeitBuffs(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HIGHLIGHT_STATE.remove(player.getUUID());
            refresh(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HIGHLIGHT_STATE.remove(player.getUUID());
            refresh(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        HIGHLIGHT_STATE.remove(player.getUUID());
        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute != null) {
            attribute.removeModifier(WAR_HEALTH_MODIFIER_ID);
        }
    }

    private FaithEffects() {
    }
}
