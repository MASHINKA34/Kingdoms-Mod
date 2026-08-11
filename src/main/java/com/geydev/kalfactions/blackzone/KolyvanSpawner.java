package com.geydev.kalfactions.blackzone;

import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.entity.KolyvanEntity;
import com.geydev.kalfactions.registry.ModEntities;
import com.geydev.kalfactions.registry.ModSounds;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class KolyvanSpawner {
    private static final int FACING_ATTEMPTS = 24;
    private static final int BLIND_ATTEMPTS = 16;
    private static final float FACING_SPREAD_DEGREES = 70.0F;
    private static final int MAX_VERTICAL_OFFSET = 12;
    private static final double SEARCH_RADIUS = 64.0D;

    public static boolean spawnFor(ServerLevel level, ServerPlayer player) {
        return !alreadyHunting(level, player) && spawnNow(level, player);
    }

    public static boolean spawnNow(ServerLevel level, ServerPlayer player) {
        int minDistance = Math.max(1, ModConfigSpec.BLACK_ZONE_KOLYVAN_MIN_DISTANCE.getAsInt());
        int maxDistance = Math.max(minDistance, ModConfigSpec.BLACK_ZONE_KOLYVAN_MAX_DISTANCE.getAsInt());
        BlockPos spot = findSpot(level, player, minDistance, maxDistance, true);
        if (spot == null) {
            spot = findSpot(level, player, minDistance, maxDistance, false);
        }
        return spot != null && spawnAt(level, player, spot);
    }

    private static boolean spawnAt(ServerLevel level, ServerPlayer player, BlockPos spot) {
        KolyvanEntity kolyvan = ModEntities.KOLYVAN.get().create(level);
        if (kolyvan == null) {
            return false;
        }
        kolyvan.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, player.getYRot() + 180.0F, 0.0F);
        kolyvan.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null);
        kolyvan.hunt(player, ModConfigSpec.BLACK_ZONE_KOLYVAN_LIFETIME_SECONDS.getAsInt());
        if (!level.addFreshEntity(kolyvan)) {
            return false;
        }
        level.playSound(
                null,
                spot.getX() + 0.5D,
                spot.getY(),
                spot.getZ() + 0.5D,
                ModSounds.KOLYVAN.get(),
                SoundSource.HOSTILE,
                1.0F,
                1.0F
        );
        return true;
    }

    private static boolean alreadyHunting(ServerLevel level, ServerPlayer player) {
        List<KolyvanEntity> nearby = level.getEntitiesOfClass(
                KolyvanEntity.class,
                player.getBoundingBox().inflate(SEARCH_RADIUS),
                kolyvan -> kolyvan.isHunting(player.getUUID())
        );
        return !nearby.isEmpty();
    }

    private static BlockPos findSpot(
            ServerLevel level,
            ServerPlayer player,
            int minDistance,
            int maxDistance,
            boolean requireSight
    ) {
        int attempts = requireSight ? FACING_ATTEMPTS : BLIND_ATTEMPTS;
        for (int attempt = 0; attempt < attempts; attempt++) {
            float angle = requireSight
                    ? player.getYRot() + (level.random.nextFloat() * 2.0F - 1.0F) * FACING_SPREAD_DEGREES
                    : level.random.nextFloat() * 360.0F;
            double distance = minDistance + level.random.nextDouble() * (maxDistance - minDistance);
            double radians = Math.toRadians(angle);
            int x = Mth.floor(player.getX() - Math.sin(radians) * distance);
            int z = Mth.floor(player.getZ() + Math.cos(radians) * distance);
            if (!level.hasChunkAt(new BlockPos(x, player.getBlockY(), z))) {
                continue;
            }
            BlockPos candidate = groundAt(level, x, z, player.getBlockY());
            if (requireSight && !hasLineOfSight(level, player, candidate)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private static BlockPos groundAt(ServerLevel level, int x, int z, int aroundY) {
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        if (Math.abs(surface.getY() - aroundY) <= MAX_VERTICAL_OFFSET) {
            return surface;
        }
        return new BlockPos(x, aroundY, z);
    }

    private static boolean hasLineOfSight(ServerLevel level, ServerPlayer player, BlockPos spot) {
        Vec3 from = new Vec3(spot.getX() + 0.5D, spot.getY() + 1.6D, spot.getZ() + 0.5D);
        Vec3 to = player.getEyePosition();
        return level.clip(new ClipContext(
                from,
                to,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        )).getType() == HitResult.Type.MISS;
    }

    private KolyvanSpawner() {
    }
}
