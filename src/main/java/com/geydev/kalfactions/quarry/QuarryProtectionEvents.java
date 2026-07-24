package com.geydev.kalfactions.quarry;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.net.FactionServerHooks;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class QuarryProtectionEvents {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player)
                || player.hasPermissions(2)
                || !QuarryManager.get(level).isQuarry(level, event.getPos())) {
            return;
        }
        event.setCanceled(true);
        FactionServerHooks.sendNotice(
                player,
                Component.translatable("kingdoms.quarry.protected"),
                false
        );
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer player)
                || player.hasPermissions(2)) {
            return;
        }
        QuarryManager manager = QuarryManager.get(level);
        boolean protectedPosition = event instanceof BlockEvent.EntityMultiPlaceEvent multiPlace
                ? multiPlace.getReplacedBlockSnapshots().stream()
                        .anyMatch(snapshot -> manager.isQuarry(level, snapshot.getPos()))
                : manager.isQuarry(level, event.getPos());
        if (!protectedPosition) {
            return;
        }
        event.setCanceled(true);
        FactionServerHooks.sendNotice(
                player,
                Component.translatable("kingdoms.quarry.protected"),
                false
        );
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) {
            QuarryManager manager = QuarryManager.get(level);
            event.getAffectedBlocks().removeIf(pos -> manager.isQuarry(level, pos));
        }
    }

    private QuarryProtectionEvents() {
    }
}
