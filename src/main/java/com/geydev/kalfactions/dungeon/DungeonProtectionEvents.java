package com.geydev.kalfactions.dungeon;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class DungeonProtectionEvents {
    private static final int GRIEF_RADIUS_BLOCKS = 3;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player)
                || player.hasPermissions(2)) {
            return;
        }
        if (level.getBlockState(event.getPos()).is(ModBlocks.DUNGEON_CORE.get())) {
            event.setCanceled(true);
            deny(player, "kingdoms.dungeon.not_operator");
            return;
        }
        if (DungeonProtection.isDungeon(level, event.getPos())) {
            event.setCanceled(true);
            deny(player, "kingdoms.dungeon.protected");
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Entity entity = event.getEntity();
        ServerPlayer player = entity instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        boolean operator = player != null && player.hasPermissions(2);

        if (event.getPlacedBlock().is(ModBlocks.DUNGEON_CORE.get())) {
            if (player == null) {
                event.setCanceled(true);
                return;
            }
            if (!DungeonService.canPlaceCore(player, level, event.getPos())) {
                event.setCanceled(true);
                return;
            }
            DungeonService.ensure(level, event.getPos().immutable());
            return;
        }

        if (operator) {
            return;
        }
        boolean insideDungeon = event instanceof BlockEvent.EntityMultiPlaceEvent multiPlace
                ? multiPlace.getReplacedBlockSnapshots().stream()
                        .map(BlockSnapshot::getPos)
                        .anyMatch(pos -> DungeonProtection.isDungeon(level, pos))
                : DungeonProtection.isDungeon(level, event.getPos());
        if (!insideDungeon) {
            return;
        }
        event.setCanceled(true);
        if (player != null) {
            deny(player, "kingdoms.dungeon.protected");
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || DungeonManager.get(level).isEmpty()) {
            return;
        }
        event.getAffectedBlocks().removeIf(pos -> DungeonProtection.isDungeon(level, pos));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPiston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level) || DungeonManager.get(level).isEmpty()) {
            return;
        }
        BlockPos base = event.getPos();
        if (DungeonProtection.isDungeon(level, base)) {
            event.setCanceled(true);
            return;
        }
        PistonStructureResolver resolver = event.getStructureHelper();
        if (resolver == null || !resolver.resolve()) {
            if (DungeonProtection.isDungeon(level, base.relative(event.getDirection()))) {
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
        if (affected.stream().anyMatch(pos -> DungeonProtection.isDungeon(level, pos))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMobGriefing(EntityMobGriefingEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof ServerPlayer player && player.hasPermissions(2)) {
            return;
        }
        if (!(entity.level() instanceof ServerLevel level) || DungeonManager.get(level).isEmpty()) {
            return;
        }
        if (DungeonProtection.nearDungeon(level, entity.blockPosition(), GRIEF_RADIUS_BLOCKS)) {
            event.setCanGrief(false);
        }
    }

    private static void deny(ServerPlayer player, String translationKey) {
        player.displayClientMessage(Component.translatable(translationKey), true);
    }

    private DungeonProtectionEvents() {
    }
}
