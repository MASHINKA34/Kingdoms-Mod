package com.geydev.kalfactions.quarry;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.net.FactionServerHooks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;

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

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPiston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        QuarryManager manager = QuarryManager.get(level);
        BlockPos base = event.getPos();
        if (manager.isQuarry(level, base)) {
            event.setCanceled(true);
            return;
        }
        PistonStructureResolver resolver = event.getStructureHelper();
        if (resolver == null || !resolver.resolve()) {
            BlockPos head = base.relative(event.getDirection());
            if (manager.isQuarry(level, head)) {
                event.setCanceled(true);
            }
            return;
        }
        Direction moveDirection = event.getPistonMoveType() == PistonEvent.PistonMoveType.EXTEND
                ? event.getDirection()
                : event.getDirection().getOpposite();
        List<BlockPos> affected = new ArrayList<>();
        affected.add(base.relative(event.getDirection()));
        for (BlockPos pos : resolver.getToPush()) {
            affected.add(pos);
            affected.add(pos.relative(moveDirection));
        }
        affected.addAll(resolver.getToDestroy());
        if (affected.stream().anyMatch(pos -> manager.isQuarry(level, pos))) {
            event.setCanceled(true);
        }
    }

    private QuarryProtectionEvents() {
    }
}
