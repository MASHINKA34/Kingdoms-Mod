package com.geydev.kalfactions.dimension;

import com.geydev.kalfactions.dimension.DimensionControlManager.PortalBounds;
import com.geydev.kalfactions.dimension.DimensionControlManager.PortalCharge;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.net.FactionServerHooks;
import com.geydev.kalfactions.registry.ModBlocks;
import com.geydev.kalfactions.tax.OfflineNoticeQueue;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.PortalShape;

public final class NetherPortalIgnition {
    public static final int ANCHOR_SEARCH_RADIUS = 16;
    private static final long INTERACTION_COOLDOWN_TICKS = 10L;
    private static final DateTimeFormatter CLOSES_AT_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM HH:mm", Locale.ROOT).withZone(NetherSchedulePolicy.MOSCOW);
    private static final ConcurrentHashMap<UUID, Long> LAST_INTERACTION_TICK = new ConcurrentHashMap<>();

    public enum Failure {
        RATE_LIMITED("kingdoms.error.action_rate_limited"),
        OVERWORLD_ONLY("commands.kingdoms.nether.portal.overworld_only"),
        NO_ANCHOR("kingdoms.nether.portal.anchor.missing"),
        NO_FRAME("kingdoms.nether.portal.anchor.no_frame"),
        TOO_LARGE("commands.kingdoms.nether.portal.invalid"),
        ALREADY_LIT("kingdoms.nether.portal.already_lit");

        private final String key;

        Failure(String key) {
            this.key = key;
        }

        public Component message() {
            return Component.translatable(key);
        }
    }

    public record Result(PortalCharge charge, Failure failure) {
        public boolean ignited() {
            return charge != null;
        }
    }

    public static Result ignite(ServerLevel level, BlockPos anchor, String ignitedBy, Instant now) {
        MinecraftServer server = level.getServer();
        if (!Level.OVERWORLD.equals(level.dimension())) {
            return new Result(null, Failure.OVERWORLD_ONLY);
        }
        if (!level.getBlockState(anchor).is(ModBlocks.NETHER_PORTAL_ANCHOR.get())) {
            return new Result(null, Failure.NO_ANCHOR);
        }
        DimensionControlManager control = DimensionControlManager.get(server);
        if (control.isNetherPortalCharged(now)) {
            return new Result(null, Failure.ALREADY_LIT);
        }
        BlockPos anchorPos = anchor.immutable();
        PortalBounds bounds = NetherPortalRegistration.findConnectedPortal(level, anchorPos).orElse(null);
        if (bounds == null) {
            if (!lightFrame(level, anchorPos)) {
                return new Result(null, Failure.NO_FRAME);
            }
            bounds = NetherPortalRegistration.findConnectedPortal(level, anchorPos).orElse(null);
            if (bounds == null) {
                NetherPortalRegistration.clearConnectedPortal(level, anchorPos);
                return new Result(null, Failure.TOO_LARGE);
            }
        }
        control.setNetherPortal(bounds);
        PortalCharge charge = control.igniteNetherPortal(
                now, control.rules().portalLifetime(), ignitedBy, anchorPos
        );
        playIgnitionEffects(level, anchorPos);
        announceIgnition(server, charge);
        return new Result(charge, null);
    }

    public static void igniteWithItem(ServerPlayer player, BlockPos anchor, ItemStack stack) {
        if (rateLimited(player)) {
            FactionServerHooks.sendNotice(player, Failure.RATE_LIMITED.message(), false);
            return;
        }
        Result result = ignite(
                player.serverLevel(), anchor, player.getGameProfile().getName(), Instant.now()
        );
        if (!result.ignited()) {
            FactionServerHooks.sendNotice(player, result.failure().message(), false);
            return;
        }
        stack.shrink(1);
    }

    public static void showStatus(ServerPlayer player) {
        if (rateLimited(player)) {
            return;
        }
        player.displayClientMessage(statusMessage(player.serverLevel().getServer(), Instant.now()), false);
    }

    public static Component statusMessage(MinecraftServer server, Instant now) {
        PortalCharge charge = DimensionControlManager.get(server).netherPortalCharge()
                .filter(candidate -> candidate.expiresAt().isAfter(now))
                .orElse(null);
        if (charge == null) {
            return Component.translatable("kingdoms.nether.portal.status.unlit");
        }
        return Component.translatable(
                "kingdoms.nether.portal.status.lit",
                NetherSchedulePolicy.formatRemaining(Duration.between(now, charge.expiresAt())),
                charge.ignitedBy()
        );
    }

    public static int extinguish(MinecraftServer server, String noticeKey) {
        DimensionControlManager control = DimensionControlManager.get(server);
        ServerLevel overworld = server.overworld();
        int evacuated = DimensionControlEvents.evacuateNether(server, noticeKey);
        control.netherPortal().ifPresent(bounds -> NetherPortalRegistration.clearConnectedPortal(
                overworld, new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ())
        ));
        control.netherPortalAnchor().ifPresent(
                anchor -> NetherPortalRegistration.clearConnectedPortal(overworld, anchor)
        );
        control.clearNetherPortal();
        control.clearNetherPortalCharge();
        broadcast(server, Component.translatable(noticeKey), false);
        return evacuated;
    }

    public static void tick(MinecraftServer server, Instant now) {
        DimensionControlManager control = DimensionControlManager.get(server);
        PortalCharge charge = control.netherPortalCharge().orElse(null);
        if (charge == null) {
            return;
        }
        if (!charge.expiresAt().isAfter(now)) {
            extinguish(server, "kingdoms.nether.portal.expired");
            return;
        }
        PortalBounds registered = control.netherPortal().orElse(null);
        if (registered == null) {
            extinguish(server, "kingdoms.nether.portal.broken");
            return;
        }
        BlockPos corner = new BlockPos(registered.minX(), registered.minY(), registered.minZ());
        if (server.overworld().isLoaded(corner)
                && !NetherPortalRegistration.isIntact(server.overworld(), registered)) {
            extinguish(server, "kingdoms.nether.portal.broken");
        }
    }

    public static Optional<BlockPos> findAnchor(ServerLevel level, BlockPos around) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos candidate : BlockPos.betweenClosed(
                around.offset(-ANCHOR_SEARCH_RADIUS, -ANCHOR_SEARCH_RADIUS, -ANCHOR_SEARCH_RADIUS),
                around.offset(ANCHOR_SEARCH_RADIUS, ANCHOR_SEARCH_RADIUS, ANCHOR_SEARCH_RADIUS)
        )) {
            if (!level.getBlockState(candidate).is(ModBlocks.NETHER_PORTAL_ANCHOR.get())) {
                continue;
            }
            double distance = candidate.distSqr(around);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate.immutable();
            }
        }
        return Optional.ofNullable(best);
    }

    public static void clear() {
        LAST_INTERACTION_TICK.clear();
    }

    static void removePlayer(UUID playerId) {
        LAST_INTERACTION_TICK.remove(playerId);
    }

    private static boolean lightFrame(ServerLevel level, BlockPos anchor) {
        for (Direction direction : Direction.values()) {
            BlockPos candidate = anchor.relative(direction);
            if (!level.getBlockState(candidate).isAir()) {
                continue;
            }
            Optional<PortalShape> shape =
                    PortalShape.findEmptyPortalShape(level, candidate, Direction.Axis.X);
            if (shape.isPresent()) {
                shape.get().createPortalBlocks();
                return true;
            }
        }
        return false;
    }

    private static void playIgnitionEffects(ServerLevel level, BlockPos anchor) {
        level.playSound(
                null, anchor, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.BLOCKS, 1.0F, 0.8F
        );
        level.sendParticles(
                ParticleTypes.SOUL_FIRE_FLAME,
                anchor.getX() + 0.5D,
                anchor.getY() + 1.1D,
                anchor.getZ() + 0.5D,
                24,
                0.4D,
                0.4D,
                0.4D,
                0.02D
        );
    }

    private static void announceIgnition(MinecraftServer server, PortalCharge charge) {
        Component message = Component.translatable(
                "kingdoms.nether.portal.opened",
                charge.ignitedBy(),
                CLOSES_AT_FORMAT.format(charge.expiresAt()),
                NetherSchedulePolicy.formatRemaining(Duration.between(charge.ignitedAt(), charge.expiresAt()))
        );
        broadcast(server, message, true);
    }

    private static void broadcast(MinecraftServer server, Component message, boolean successful) {
        for (ServerPlayer online : List.copyOf(server.getPlayerList().getPlayers())) {
            online.sendSystemMessage(message);
            FactionServerHooks.sendNotice(online, message, successful);
        }
        OfflineNoticeQueue queue = OfflineNoticeQueue.get(server);
        for (Faction faction : FactionManager.get(server).factions()) {
            for (UUID member : faction.members().keySet()) {
                if (server.getPlayerList().getPlayer(member) == null) {
                    queue.enqueue(server, member, message, successful);
                }
            }
        }
    }

    private static boolean rateLimited(ServerPlayer player) {
        long now = player.level().getGameTime();
        Long previous = LAST_INTERACTION_TICK.put(player.getUUID(), now);
        return previous != null && now - previous < INTERACTION_COOLDOWN_TICKS;
    }

    private NetherPortalIgnition() {
    }
}
