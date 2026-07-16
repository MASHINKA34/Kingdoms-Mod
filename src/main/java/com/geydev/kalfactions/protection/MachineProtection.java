package com.geydev.kalfactions.protection;

import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.sanctuary.SanctuaryManager;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public final class MachineProtection {
    private static final class ProjectileContext {
        private boolean active;
        private Entity shooter;
    }

    private static final ThreadLocal<ProjectileContext> PROJECTILE_CONTEXT =
        ThreadLocal.withInitial(ProjectileContext::new);

    public static void beginProjectileContext(Entity shooter) {
        ProjectileContext context = PROJECTILE_CONTEXT.get();
        context.active = true;
        context.shooter = shooter;
    }

    public static void endProjectileContext() {
        ProjectileContext context = PROJECTILE_CONTEXT.get();
        context.active = false;
        context.shooter = null;
    }

    public static boolean blocksProjectileGrief(Level level, BlockPos target) {
        ProjectileContext context = PROJECTILE_CONTEXT.get();
        if (!context.active) {
            return false;
        }
        return !canProjectileBreak(level, target, context.shooter);
    }

    public static boolean canProjectileBreak(Level level, BlockPos target, Entity shooter) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        if (SanctuaryManager.get(serverLevel).isSanctuary(serverLevel, target)) {
            return false;
        }
        FactionManager factions = FactionManager.get(serverLevel);
        UUID owner = factions.getFactionIdAt(ClaimKey.of(level, target)).orElse(null);
        if (owner == null) {
            return true;
        }
        if (shooter instanceof ServerPlayer player) {
            UUID shooterFaction = factions.getFactionIdForMember(player.getUUID()).orElse(null);
            return owner.equals(shooterFaction);
        }
        return false;
    }

    public static boolean canContraptionBreak(Level level, BlockPos target, BlockPos anchor) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        if (SanctuaryManager.get(serverLevel).isSanctuary(serverLevel, target)) {
            return false;
        }
        FactionManager factions = FactionManager.get(serverLevel);
        UUID owner = factions.getFactionIdAt(ClaimKey.of(level, target)).orElse(null);
        if (owner == null) {
            return true;
        }
        if (anchor == null) {
            return false;
        }
        UUID anchorFaction = factions.getFactionIdAt(ClaimKey.of(level, anchor)).orElse(null);
        return owner.equals(anchorFaction);
    }

    public static boolean canPlayerMine(Level level, BlockPos target, ServerPlayer player) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        if (SanctuaryManager.get(serverLevel).isSanctuary(serverLevel, target)) {
            return player.hasPermissions(2);
        }
        return FactionAccess.canBuild(player, serverLevel, target);
    }

    private MachineProtection() {
    }
}
