package com.geydev.kalfactions.quarry;

import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.faction.FactionRole;
import com.geydev.kalfactions.menu.QuarryMenu;
import com.geydev.kalfactions.registry.ModBlocks;
import com.geydev.kalfactions.registry.ModItems;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public final class QuarryService {
    private static final long REQUEST_INTERVAL_NANOS = 100_000_000L;
    private static final long ACTION_INTERVAL_NANOS = 250_000_000L;
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();
    private static final Map<UUID, Long> LAST_ACTION = new HashMap<>();

    public static void open(ServerPlayer player, BlockPos core) {
        if (!validCore(player, core)) {
            return;
        }
        player.openMenu(
                new SimpleMenuProvider(
                        (containerId, inventory, ignored) -> new QuarryMenu(containerId, inventory, core),
                        Component.translatable("screen.kingdoms.quarry.title")
                ),
                buffer -> buffer.writeBlockPos(core)
        ).ifPresent(containerId -> sendState(player, containerId, core));
    }

    public static void requestState(ServerPlayer player, QuarryPayloads.C2SRequestState payload) {
        if (payload.containerId() < 0 || !validMenu(player, payload.containerId(), payload.core())) {
            closeInvalidMenu(player, payload.core());
            return;
        }
        if (!allow(LAST_REQUEST, player.getUUID(), REQUEST_INTERVAL_NANOS)) {
            return;
        }
        sendState(player, payload.containerId(), payload.core());
    }

    public static QuarryManager.ActionResult performAction(
            ServerPlayer player,
            QuarryPayloads.C2SAction payload
    ) {
        if (payload.containerId() < 0
                || payload.stateVersion() < 0L
                || payload.action() < QuarryPayloads.ACTION_ACTIVATE
                || payload.action() > QuarryPayloads.ACTION_CAPTURE) {
            return QuarryManager.ActionResult.INVALID_REQUEST;
        }
        if (!validMenu(player, payload.containerId(), payload.core())) {
            closeInvalidMenu(player, payload.core());
            return QuarryManager.ActionResult.TOO_FAR;
        }
        if (!allow(LAST_ACTION, player.getUUID(), ACTION_INTERVAL_NANOS)) {
            if (player.connection != null) {
                sendState(player, payload.containerId(), payload.core());
            }
            return QuarryManager.ActionResult.RATE_LIMITED;
        }
        QuarryManager manager = QuarryManager.get(player.serverLevel());
        QuarryManager.ActionResult result =
                manager.performAction(player, payload.core(), payload.stateVersion(), payload.action());
        broadcastState(player.getServer(), payload.core());
        return result;
    }

    public static void tickOpenMenus(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.containerMenu instanceof QuarryMenu menu)) {
                continue;
            }
            if (!menu.stillValid(player)) {
                player.closeContainer();
                continue;
            }
            sendState(player, menu.containerId, menu.core());
        }
    }

    public static void clearRateLimit(UUID playerId) {
        LAST_REQUEST.remove(playerId);
        LAST_ACTION.remove(playerId);
    }

    private static void broadcastState(MinecraftServer server, BlockPos core) {
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (viewer.containerMenu instanceof QuarryMenu menu && menu.core().equals(core) && menu.stillValid(viewer)) {
                sendState(viewer, menu.containerId, core);
            }
        }
    }

    private static void sendState(ServerPlayer player, int containerId, BlockPos core) {
        QuarryManager.QuarryView quarry =
                QuarryManager.get(player.serverLevel()).byCore(core).orElse(null);
        if (quarry == null) {
            player.closeContainer();
            return;
        }
        FactionManager factions = FactionManager.get(player.serverLevel());
        Faction owner = quarry.ownerFactionId() == null
                ? null
                : factions.getFactionById(quarry.ownerFactionId()).orElse(null);
        Faction viewer = factions.getFactionForMember(player.getUUID()).orElse(null);
        Faction attacker = quarry.attackerFactionId() == null
                ? null
                : factions.getFactionById(quarry.attackerFactionId()).orElse(null);
        Decision decision = decision(player, quarry, viewer);
        int status = quarry.ownerFactionId() == null
                ? QuarryPayloads.STATUS_NEUTRAL
                : quarry.attackerFactionId() == null
                        ? QuarryPayloads.STATUS_OWNED
                        : QuarryPayloads.STATUS_UNDER_ATTACK;
        int nextLevel = quarry.level() >= QuarryManager.MAX_LEVEL ? 0 : quarry.level() + 1;
        long nextCost = nextLevel == 0 ? 0L : QuarryManager.upgradeCost(quarry.level());
        PacketDistributor.sendToPlayer(player, new QuarryPayloads.S2CState(
                containerId,
                core,
                quarry.stateVersion(),
                status,
                owner == null ? "" : owner.name(),
                owner == null ? QuarryManager.NEUTRAL_COLOR : owner.color(),
                quarry.level(),
                nextLevel,
                nextCost,
                viewer == null ? "" : viewer.name(),
                viewer == null ? QuarryManager.NEUTRAL_COLOR : viewer.color(),
                viewer == null ? 0L : viewer.treasuryBalance(),
                attacker == null ? "" : attacker.name(),
                attacker == null ? QuarryManager.NEUTRAL_COLOR : attacker.color(),
                quarry.captureTicksRemaining(),
                quarry.capturePaused(),
                decision.action(),
                decision.enabled(),
                decision.reason()
        ));
    }

    private static Decision decision(
            ServerPlayer player,
            QuarryManager.QuarryView quarry,
            Faction viewer
    ) {
        if (quarry.ownerFactionId() == null) {
            if (viewer == null) {
                return new Decision(
                        QuarryPayloads.ACTION_ACTIVATE,
                        false,
                        QuarryPayloads.REASON_NOT_IN_FACTION
                );
            }
            if (!hasActivator(player)) {
                return new Decision(
                        QuarryPayloads.ACTION_ACTIVATE,
                        false,
                        QuarryPayloads.REASON_REQUIRES_ACTIVATOR
                );
            }
            return new Decision(QuarryPayloads.ACTION_ACTIVATE, true, QuarryPayloads.REASON_NONE);
        }
        if (quarry.attackerFactionId() != null) {
            int reason = viewer != null && quarry.attackerFactionId().equals(viewer.id())
                    ? QuarryPayloads.REASON_CAPTURE_ACTIVE
                    : QuarryPayloads.REASON_CAPTURE_BUSY;
            return new Decision(QuarryPayloads.ACTION_NONE, false, reason);
        }
        if (viewer == null) {
            return new Decision(
                    QuarryPayloads.ACTION_CAPTURE,
                    false,
                    QuarryPayloads.REASON_NOT_IN_FACTION
            );
        }
        if (quarry.ownerFactionId().equals(viewer.id())) {
            if (quarry.level() >= QuarryManager.MAX_LEVEL) {
                return new Decision(
                        QuarryPayloads.ACTION_UPGRADE,
                        false,
                        QuarryPayloads.REASON_MAX_LEVEL
                );
            }
            FactionRole role = viewer.roleOf(player.getUUID()).orElse(FactionRole.MEMBER);
            if (!role.canManageTreasury()) {
                return new Decision(
                        QuarryPayloads.ACTION_UPGRADE,
                        false,
                        QuarryPayloads.REASON_NO_PERMISSION
                );
            }
            if (viewer.treasuryBalance() < QuarryManager.upgradeCost(quarry.level())) {
                return new Decision(
                        QuarryPayloads.ACTION_UPGRADE,
                        false,
                        QuarryPayloads.REASON_INSUFFICIENT_FUNDS
                );
            }
            return new Decision(QuarryPayloads.ACTION_UPGRADE, true, QuarryPayloads.REASON_NONE);
        }
        return new Decision(QuarryPayloads.ACTION_CAPTURE, true, QuarryPayloads.REASON_NONE);
    }

    private static boolean validMenu(ServerPlayer player, int containerId, BlockPos core) {
        return player.containerMenu instanceof QuarryMenu menu
                && menu.containerId == containerId
                && menu.core().equals(core)
                && menu.stillValid(player);
    }

    private static boolean validCore(ServerPlayer player, BlockPos core) {
        return player.serverLevel().dimension().equals(Level.OVERWORLD)
                && player.distanceToSqr(
                        core.getX() + 0.5D,
                        core.getY() + 0.5D,
                        core.getZ() + 0.5D
                ) <= QuarryMenu.MAX_DISTANCE_SQUARED
                && player.serverLevel().getBlockState(core).is(ModBlocks.QUARRY_CORE.get())
                && QuarryManager.get(player.serverLevel()).byCore(core).isPresent();
    }

    private static void closeInvalidMenu(ServerPlayer player, BlockPos core) {
        if (player.containerMenu instanceof QuarryMenu menu && menu.core().equals(core)) {
            if (player.connection != null) {
                player.closeContainer();
            }
        }
    }

    private static boolean hasActivator(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ModItems.QUARRY_ACTIVATOR.get())) {
                return true;
            }
        }
        return false;
    }

    private static boolean allow(Map<UUID, Long> timestamps, UUID playerId, long intervalNanos) {
        long now = System.nanoTime();
        Long previous = timestamps.get(playerId);
        if (previous != null && now - previous < intervalNanos) {
            return false;
        }
        timestamps.put(playerId, now);
        return true;
    }

    private record Decision(int action, boolean enabled, int reason) {
    }

    private QuarryService() {
    }
}
