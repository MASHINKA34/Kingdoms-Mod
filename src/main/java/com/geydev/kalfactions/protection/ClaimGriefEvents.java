package com.geydev.kalfactions.protection;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.faction.FactionManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.level.PistonEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class ClaimGriefEvents {
    private static final int GRIEF_RADIUS_BLOCKS = 8;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPiston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !ModConfigSpec.PROTECT_PISTONS.get()) {
            return;
        }
        FactionManager manager = FactionManager.get(level);
        if (!manager.hasClaims()) {
            return;
        }
        BlockPos base = event.getPos();
        UUID owner = manager.getFactionIdAt(ClaimKey.of(level, base)).orElse(null);
        PistonStructureResolver resolver = event.getStructureHelper();
        if (resolver == null || !resolver.resolve()) {
            if (crosses(manager, level, owner, base.relative(event.getDirection()))) {
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
        for (BlockPos pos : affected) {
            if (crosses(manager, level, owner, pos)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMobGriefing(EntityMobGriefingEvent event) {
        if (!ModConfigSpec.PROTECT_MOB_GRIEFING.get()) {
            return;
        }
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        if (entity instanceof ServerPlayer player
                && (player.hasPermissions(2) || FactionAccess.canBuild(player, level, player.blockPosition()))) {
            return;
        }
        if (nearClaim(level, entity.blockPosition())) {
            event.setCanGrief(false);
        }
    }

    private static boolean crosses(FactionManager manager, ServerLevel level, UUID owner, BlockPos pos) {
        UUID target = manager.getFactionIdAt(ClaimKey.of(level, pos)).orElse(null);
        return !ClaimBoundary.sharesOwner(manager, owner, target);
    }

    private static boolean nearClaim(ServerLevel level, BlockPos origin) {
        FactionManager manager = FactionManager.get(level);
        if (!manager.hasClaims()) {
            return false;
        }
        for (int x = -GRIEF_RADIUS_BLOCKS; x <= GRIEF_RADIUS_BLOCKS; x += GRIEF_RADIUS_BLOCKS) {
            for (int z = -GRIEF_RADIUS_BLOCKS; z <= GRIEF_RADIUS_BLOCKS; z += GRIEF_RADIUS_BLOCKS) {
                if (manager.getFactionIdAt(ClaimKey.of(level, origin.offset(x, 0, z))).isPresent()) {
                    return true;
                }
            }
        }
        return false;
    }

    private ClaimGriefEvents() {
    }
}
