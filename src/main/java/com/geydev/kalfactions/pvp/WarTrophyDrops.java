package com.geydev.kalfactions.pvp;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class WarTrophyDrops {
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim) || victim instanceof FakePlayer) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)
                || killer instanceof FakePlayer
                || killer.getUUID().equals(victim.getUUID())) {
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

    private WarTrophyDrops() {
    }
}
