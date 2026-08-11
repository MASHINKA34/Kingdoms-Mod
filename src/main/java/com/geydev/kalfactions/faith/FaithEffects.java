package com.geydev.kalfactions.faith;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.registry.ModEffects;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
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
            FaithManager manager = FaithManager.get(server);
            manager.pruneForfeits(System.currentTimeMillis());
            manager.pruneMissing(com.geydev.kalfactions.faction.FactionManager.get(server).factions().stream()
                    .map(com.geydev.kalfactions.faction.Faction::id)
                    .toList());
        }
    }

    public static void refresh(ServerPlayer player) {
        FaithBonuses.Active war = FaithBonuses.active(player, FaithGod.WAR);
        FaithBonuses.Active science = FaithBonuses.active(player, FaithGod.SCIENCE);
        FaithBonuses.Active economy = FaithBonuses.active(player, FaithGod.ECONOMY);
        applyBlessingIcon(player, FaithGod.SCIENCE, science);
        applyBlessingIcon(player, FaithGod.WAR, war);
        applyBlessingIcon(player, FaithGod.ECONOMY, economy);
        applyWarHealth(player, FaithBonuses.warBonusHealth(war.level()));
        boolean highlight = FaithBonuses.economyHighlight(economy.level());
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

    private static void applyBlessingIcon(ServerPlayer player, FaithGod god, FaithBonuses.Active active) {
        Holder<MobEffect> effect = ModEffects.forGod(god);
        if (active.level() <= 0) {
            if (player.hasEffect(effect)) {
                player.removeEffect(effect);
            }
            return;
        }
        int ticks = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, active.remainingMillis() / 50L));
        MobEffectInstance current = player.getEffect(effect);
        int amplifier = active.level() - 1;
        if (current != null
                && current.getAmplifier() == amplifier
                && Math.abs(current.getDuration() - ticks) <= REFRESH_INTERVAL_TICKS) {
            return;
        }
        player.addEffect(new MobEffectInstance(effect, ticks, amplifier, true, false, true));
    }

    public static void applyWarHealth(LivingEntity player, double bonus) {
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

    private static void clampHealth(LivingEntity player) {
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
        FaithService.forgetPlayer(player.getUUID());
        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute != null) {
            attribute.removeModifier(WAR_HEALTH_MODIFIER_ID);
        }
    }

    private FaithEffects() {
    }
}
