package com.geydev.kalfactions.outpost.trader;

import com.geydev.kalfactions.registry.ModItems;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;

public final class TraderPointBroadcast {
    private static final Set<UUID> RECEIVERS = new HashSet<>();

    public static void tick(MinecraftServer server) {
        TraderWorldData data = TraderWorldData.get(server);
        UUID activePoint = data.contraband().map(TraderWorldData.ActiveContraband::pointId).orElse(null);
        Set<UUID> holding = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.hasPermissions(2) || !isHoldingTool(player)) {
                continue;
            }
            holding.add(player.getUUID());
            PacketDistributor.sendToPlayer(player, new TraderPayloads.S2CTraderPoints(entries(data, activePoint)));
        }
        RECEIVERS.removeIf(id -> {
            if (holding.contains(id)) {
                return false;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                PacketDistributor.sendToPlayer(player, new TraderPayloads.S2CTraderPoints(List.of()));
            }
            return true;
        });
        RECEIVERS.addAll(holding);
    }

    public static void clear() {
        RECEIVERS.clear();
    }

    private static List<TraderPayloads.PointEntry> entries(TraderWorldData data, UUID activePoint) {
        List<TraderWorldData.SpawnPoint> points = data.points();
        List<TraderPayloads.PointEntry> entries = new ArrayList<>(points.size());
        int index = 0;
        for (TraderWorldData.SpawnPoint point : points) {
            index++;
            if (entries.size() >= TraderPayloads.MAX_POINT_ENTRIES) {
                break;
            }
            entries.add(new TraderPayloads.PointEntry(
                    index,
                    point.dimension().location().toString(),
                    point.pos(),
                    point.id().equals(activePoint)
            ));
        }
        return entries;
    }

    private static boolean isHoldingTool(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).is(ModItems.TRADER_POINT_TOOL.get())) {
                return true;
            }
        }
        return false;
    }

    private TraderPointBroadcast() {
    }
}
