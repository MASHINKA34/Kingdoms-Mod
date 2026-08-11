package com.geydev.kalfactions.pvp;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.faith.FaithService;
import com.geydev.kalfactions.protection.FactionAccess;
import com.geydev.kalfactions.registry.ModItems;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class WarTrophyDrops {
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim) || victim instanceof FakePlayer) {
            return;
        }
        ServerPlayer killer = killerOf(victim, event.getSource());
        if (killer != null) {
            FaithService.recordPlayerKill(killer, victim);
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim) || victim instanceof FakePlayer) {
            return;
        }
        ServerPlayer killer = killerOf(victim, event.getSource());
        if (killer == null || FactionAccess.sameFaction(killer, victim)) {
            return;
        }
        ItemEntity drop = new ItemEntity(
                victim.level(),
                victim.getX(),
                victim.getY() + 0.5D,
                victim.getZ(),
                new ItemStack(ModItems.WAR_TROPHY.get())
        );
        drop.setDefaultPickUpDelay();
        event.getDrops().add(drop);
    }

    @Nullable
    private static ServerPlayer killerOf(ServerPlayer victim, DamageSource source) {
        ServerPlayer killer = null;
        if (source.getEntity() instanceof ServerPlayer direct) {
            killer = direct;
        } else if (victim.getKillCredit() instanceof ServerPlayer credited) {
            killer = credited;
        }
        if (killer == null || killer instanceof FakePlayer || killer.getUUID().equals(victim.getUUID())) {
            return null;
        }
        return killer;
    }

    private WarTrophyDrops() {
    }
}
