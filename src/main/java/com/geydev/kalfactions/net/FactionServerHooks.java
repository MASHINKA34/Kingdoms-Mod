package com.geydev.kalfactions.net;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.block.FactionTableBlockEntity;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FactionServerHooks {
    public static final int MAX_NAME_LENGTH = 32;
    private static final double MAX_TABLE_DISTANCE_SQR = 64.0D;
    private static final long ACTION_COOLDOWN_TICKS = 2L;
    private static final ConcurrentHashMap<UUID, Long> LAST_ACTION_TICK = new ConcurrentHashMap<>();
    private static volatile Service service = new FactionManagerService();

    public static void install(Service newService) {
        service = Objects.requireNonNull(newService, "newService");
    }

    public static Service service() {
        return service;
    }

    public static void openFor(ServerPlayer player, BlockPos tablePos) {
        Validation validation = validateTable(player, tablePos, false);
        if (!validation.allowed) {
            send(player, fallbackSnapshot(tablePos), false, false, validation.message);
            return;
        }

        try {
            FactionSnapshot snapshot = sanitizeSnapshot(tablePos, service.view(player, tablePos));
            send(player, snapshot, true, true, Component.empty());
        } catch (RuntimeException exception) {
            KalFactions.LOGGER.error("Failed to open faction table at {} for {}", tablePos, player.getGameProfile().getName(), exception);
            send(
                    player,
                    fallbackSnapshot(tablePos),
                    false,
                    false,
                    Component.translatable("kingdoms.error.faction_data_unavailable")
            );
        }
    }

    public static void create(ServerPlayer player, BlockPos tablePos, String requestedName, int requestedColor) {
        Validation validation = validateTable(player, tablePos, true);
        if (!validation.allowed) {
            reject(player, tablePos, validation.message);
            return;
        }

        String name = normalizeName(requestedName);
        if (name.length() < 3) {
            reject(player, tablePos, Component.translatable("kingdoms.error.name_too_short"));
            return;
        }

        FactionSnapshot before = safeView(player, tablePos);
        if (before.hasFaction()) {
            send(
                    player,
                    before,
                    false,
                    true,
                    Component.translatable("kingdoms.error.already_in_faction")
            );
            return;
        }

        perform(player, tablePos, () -> service.create(player, tablePos, name, requestedColor & 0xFFFFFF));
    }

    public static void update(ServerPlayer player, BlockPos tablePos, String requestedName, int requestedColor) {
        Validation validation = validateTable(player, tablePos, true);
        if (!validation.allowed) {
            reject(player, tablePos, validation.message);
            return;
        }

        String name = normalizeName(requestedName);
        if (name.length() < 3) {
            reject(player, tablePos, Component.translatable("kingdoms.error.name_too_short"));
            return;
        }

        FactionSnapshot before = safeView(player, tablePos);
        if (!before.canManage()) {
            send(
                    player,
                    before,
                    false,
                    true,
                    Component.translatable("kingdoms.error.no_manage_permission")
            );
            return;
        }

        perform(player, tablePos, () -> service.update(player, tablePos, name, requestedColor & 0xFFFFFF));
    }

    public static void setClaim(
            ServerPlayer player,
            BlockPos tablePos,
            int chunkX,
            int chunkZ,
            boolean claimed
    ) {
        Validation validation = validateTable(player, tablePos, true);
        if (!validation.allowed) {
            reject(player, tablePos, validation.message);
            return;
        }

        FactionSnapshot before = safeView(player, tablePos);
        if (!before.canClaim()) {
            send(
                    player,
                    before,
                    false,
                    true,
                    Component.translatable("kingdoms.error.no_claim_permission")
            );
            return;
        }

        int maxDelta = before.mapRadius();
        if (Math.abs(chunkX - before.centerChunkX()) > maxDelta
                || Math.abs(chunkZ - before.centerChunkZ()) > maxDelta) {
            send(
                    player,
                    before,
                    false,
                    true,
                    Component.translatable("kingdoms.error.claim_outside_map")
            );
            return;
        }

        perform(player, tablePos, () -> service.setClaim(player, tablePos, new ChunkPos(chunkX, chunkZ), claimed));
    }

    public static void deposit(ServerPlayer player, BlockPos tablePos, long amount) {
        Validation validation = validateTable(player, tablePos, true);
        if (!validation.allowed) {
            reject(player, tablePos, validation.message);
            return;
        }
        perform(player, tablePos, () -> service.deposit(player, tablePos, amount));
    }

    public static void withdraw(ServerPlayer player, BlockPos tablePos, long amount) {
        Validation validation = validateTable(player, tablePos, true);
        if (!validation.allowed) {
            reject(player, tablePos, validation.message);
            return;
        }
        perform(player, tablePos, () -> service.withdraw(player, tablePos, amount));
    }

    public static void kickMember(ServerPlayer player, BlockPos tablePos, UUID targetId) {
        Validation validation = validateTable(player, tablePos, true);
        if (!validation.allowed) {
            reject(player, tablePos, validation.message);
            return;
        }
        perform(player, tablePos, () -> service.kickMember(player, tablePos, targetId));
    }

    public static void setMemberRole(ServerPlayer player, BlockPos tablePos, UUID targetId, String role) {
        Validation validation = validateTable(player, tablePos, true);
        if (!validation.allowed) {
            reject(player, tablePos, validation.message);
            return;
        }
        perform(player, tablePos, () -> service.setMemberRole(player, tablePos, targetId, role));
    }

    public static void setPvp(ServerPlayer player, BlockPos tablePos, boolean enabled) {
        Validation validation = validateTable(player, tablePos, true);
        if (!validation.allowed) {
            reject(player, tablePos, validation.message);
            return;
        }
        perform(player, tablePos, () -> service.setPvp(player, tablePos, enabled));
    }

    public static void leave(ServerPlayer player, BlockPos tablePos) {
        Validation validation = validateTable(player, tablePos, true);
        if (!validation.allowed) {
            reject(player, tablePos, validation.message);
            return;
        }
        perform(player, tablePos, () -> service.leave(player, tablePos));
    }

    public static void disband(ServerPlayer player, BlockPos tablePos) {
        Validation validation = validateTable(player, tablePos, true);
        if (!validation.allowed) {
            reject(player, tablePos, validation.message);
            return;
        }
        perform(player, tablePos, () -> service.disband(player, tablePos));
    }

    public static void invite(ServerPlayer player, BlockPos tablePos, String targetName) {
        Validation validation = validateTable(player, tablePos, true);
        if (!validation.allowed) {
            reject(player, tablePos, validation.message);
            return;
        }
        perform(player, tablePos, () -> service.invite(player, tablePos, targetName));
    }

    public static void transfer(ServerPlayer player, BlockPos tablePos, String targetName) {
        Validation validation = validateTable(player, tablePos, true);
        if (!validation.allowed) {
            reject(player, tablePos, validation.message);
            return;
        }
        perform(player, tablePos, () -> service.transfer(player, tablePos, targetName));
    }

    public static void declareWar(ServerPlayer player, BlockPos tablePos, String targetFactionName) {
        Validation validation = validateTable(player, tablePos, true);
        if (!validation.allowed) {
            reject(player, tablePos, validation.message);
            return;
        }
        perform(player, tablePos, () -> service.declareWar(player, tablePos, targetFactionName));
    }

    public static void endWar(ServerPlayer player, BlockPos tablePos) {
        Validation validation = validateTable(player, tablePos, true);
        if (!validation.allowed) {
            reject(player, tablePos, validation.message);
            return;
        }
        perform(player, tablePos, () -> service.endWar(player, tablePos));
    }

    private static void perform(ServerPlayer player, BlockPos tablePos, Operation operation) {
        try {
            Result result = Objects.requireNonNull(operation.run(), "Faction service returned null");
            FactionSnapshot snapshot = sanitizeSnapshot(
                    tablePos,
                    result.snapshot == null ? service.view(player, tablePos) : result.snapshot
            );
            send(player, snapshot, result.successful, true, result.message);
        } catch (RuntimeException exception) {
            KalFactions.LOGGER.error("Faction operation failed at {} for {}", tablePos, player.getGameProfile().getName(), exception);
            reject(
                    player,
                    tablePos,
                    Component.translatable("kingdoms.error.faction_action_failed")
            );
        }
    }

    private static FactionSnapshot safeView(ServerPlayer player, BlockPos tablePos) {
        try {
            return sanitizeSnapshot(tablePos, service.view(player, tablePos));
        } catch (RuntimeException exception) {
            KalFactions.LOGGER.error("Failed to read faction data at {}", tablePos, exception);
            return fallbackSnapshot(tablePos);
        }
    }

    private static Validation validateTable(ServerPlayer player, BlockPos tablePos, boolean rateLimited) {
        if (!player.isAlive() || player.isSpectator()) {
            return Validation.deny(Component.translatable("kingdoms.error.table_unavailable_now"));
        }
        if (!player.level().isLoaded(tablePos)) {
            return Validation.deny(Component.translatable("kingdoms.error.table_not_loaded"));
        }
        if (player.distanceToSqr(tablePos.getX() + 0.5D, tablePos.getY() + 0.5D, tablePos.getZ() + 0.5D)
                > MAX_TABLE_DISTANCE_SQR) {
            return Validation.deny(Component.translatable("kingdoms.error.table_too_far"));
        }
        if (!(player.level().getBlockEntity(tablePos) instanceof FactionTableBlockEntity)) {
            return Validation.deny(Component.translatable("kingdoms.error.not_faction_table"));
        }
        if (rateLimited) {
            long now = player.level().getGameTime();
            Long previous = LAST_ACTION_TICK.put(player.getUUID(), now);
            if (previous != null && now - previous < ACTION_COOLDOWN_TICKS) {
                return Validation.deny(Component.translatable("kingdoms.error.action_rate_limited"));
            }
        }
        return Validation.ALLOW;
    }

    private static String normalizeName(String value) {
        StringBuilder normalized = new StringBuilder(MAX_NAME_LENGTH);
        if (value != null) {
            value.codePoints()
                    .filter(codePoint -> !Character.isISOControl(codePoint))
                    .limit(MAX_NAME_LENGTH)
                    .forEach(normalized::appendCodePoint);
        }
        return normalized.toString().trim().replaceAll("\\s+", " ");
    }

    private static FactionSnapshot sanitizeSnapshot(BlockPos tablePos, FactionSnapshot snapshot) {
        if (snapshot == null) {
            return fallbackSnapshot(tablePos);
        }
        return new FactionSnapshot(
                tablePos,
                snapshot.factionId(),
                snapshot.name(),
                snapshot.ownerName(),
                snapshot.color(),
                snapshot.canManage(),
                snapshot.canClaim(),
                snapshot.centerChunkX(),
                snapshot.centerChunkZ(),
                snapshot.mapRadius(),
                snapshot.members(),
                snapshot.claims(),
                snapshot.treasury(),
                snapshot.influence(),
                snapshot.internalPvp(),
                snapshot.viewerId(),
                snapshot.isOfficer()
        );
    }

    private static FactionSnapshot fallbackSnapshot(BlockPos tablePos) {
        ChunkPos center = new ChunkPos(tablePos);
        return FactionSnapshot.empty(tablePos, center.x, center.z);
    }

    private static void reject(ServerPlayer player, BlockPos tablePos, Component message) {
        send(player, safeView(player, tablePos), false, true, message);
    }

    private static void send(
            ServerPlayer player,
            FactionSnapshot snapshot,
            boolean successful,
            boolean openScreen,
            Component message
    ) {
        if (!message.getString().isBlank()) {
            player.sendSystemMessage(message);
        }
        PacketDistributor.sendToPlayer(
                player,
                new FactionPayloads.S2CFactionState(snapshot, successful, openScreen, "")
        );
    }

    public interface Service {
        FactionSnapshot view(ServerPlayer player, BlockPos tablePos);

        Result create(ServerPlayer player, BlockPos tablePos, String name, int color);

        Result update(ServerPlayer player, BlockPos tablePos, String name, int color);

        Result setClaim(ServerPlayer player, BlockPos tablePos, ChunkPos chunkPos, boolean claimed);

        Result deposit(ServerPlayer player, BlockPos tablePos, long amount);

        Result withdraw(ServerPlayer player, BlockPos tablePos, long amount);

        Result kickMember(ServerPlayer player, BlockPos tablePos, UUID targetId);

        Result setMemberRole(ServerPlayer player, BlockPos tablePos, UUID targetId, String role);

        Result setPvp(ServerPlayer player, BlockPos tablePos, boolean enabled);

        Result leave(ServerPlayer player, BlockPos tablePos);

        Result disband(ServerPlayer player, BlockPos tablePos);

        Result invite(ServerPlayer player, BlockPos tablePos, String targetName);

        Result transfer(ServerPlayer player, BlockPos tablePos, String targetName);

        Result declareWar(ServerPlayer player, BlockPos tablePos, String targetFactionName);

        Result endWar(ServerPlayer player, BlockPos tablePos);
    }

    public record Result(boolean successful, Component message, FactionSnapshot snapshot) {
        public static Result success(FactionSnapshot snapshot) {
            return new Result(true, Component.empty(), snapshot);
        }

        public static Result denied(Component message, FactionSnapshot snapshot) {
            return new Result(false, message, snapshot);
        }
    }

    private record Validation(boolean allowed, Component message) {
        private static final Validation ALLOW = new Validation(true, Component.empty());

        private static Validation deny(Component message) {
            return new Validation(false, message);
        }
    }

    @FunctionalInterface
    private interface Operation {
        Result run();
    }

    private static final class UnavailableService implements Service {
        @Override
        public FactionSnapshot view(ServerPlayer player, BlockPos tablePos) {
            return fallbackSnapshot(tablePos);
        }

        @Override
        public Result create(ServerPlayer player, BlockPos tablePos, String name, int color) {
            return managementUnavailable(player, tablePos);
        }

        @Override
        public Result update(ServerPlayer player, BlockPos tablePos, String name, int color) {
            return managementUnavailable(player, tablePos);
        }

        @Override
        public Result setClaim(ServerPlayer player, BlockPos tablePos, ChunkPos chunkPos, boolean claimed) {
            return Result.denied(
                    Component.translatable("kingdoms.error.claims_unavailable"),
                    view(player, tablePos)
            );
        }

        @Override
        public Result deposit(ServerPlayer player, BlockPos tablePos, long amount) {
            return managementUnavailable(player, tablePos);
        }

        @Override
        public Result withdraw(ServerPlayer player, BlockPos tablePos, long amount) {
            return managementUnavailable(player, tablePos);
        }

        @Override
        public Result kickMember(ServerPlayer player, BlockPos tablePos, UUID targetId) {
            return managementUnavailable(player, tablePos);
        }

        @Override
        public Result setMemberRole(ServerPlayer player, BlockPos tablePos, UUID targetId, String role) {
            return managementUnavailable(player, tablePos);
        }

        @Override
        public Result setPvp(ServerPlayer player, BlockPos tablePos, boolean enabled) {
            return managementUnavailable(player, tablePos);
        }

        @Override
        public Result leave(ServerPlayer player, BlockPos tablePos) {
            return managementUnavailable(player, tablePos);
        }

        @Override
        public Result disband(ServerPlayer player, BlockPos tablePos) {
            return managementUnavailable(player, tablePos);
        }

        @Override
        public Result invite(ServerPlayer player, BlockPos tablePos, String targetName) {
            return managementUnavailable(player, tablePos);
        }

        @Override
        public Result transfer(ServerPlayer player, BlockPos tablePos, String targetName) {
            return managementUnavailable(player, tablePos);
        }

        @Override
        public Result declareWar(ServerPlayer player, BlockPos tablePos, String targetFactionName) {
            return managementUnavailable(player, tablePos);
        }

        @Override
        public Result endWar(ServerPlayer player, BlockPos tablePos) {
            return managementUnavailable(player, tablePos);
        }

        private Result managementUnavailable(ServerPlayer player, BlockPos tablePos) {
            return Result.denied(
                    Component.translatable("kingdoms.error.management_unavailable"),
                    view(player, tablePos)
            );
        }
    }

    private FactionServerHooks() {
    }
}
