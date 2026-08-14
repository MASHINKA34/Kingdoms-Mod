package com.geydev.kalfactions.bonus;

import com.geydev.kalfactions.faction.FactionBonus;
import com.geydev.kalfactions.protection.FactionAccess;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class NomadRiding {
    private static final Set<UUID> CLIENT_RIDERS = ConcurrentHashMap.newKeySet();

    public static boolean canRideUnsaddled(Player player) {
        if (player == null) {
            return false;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            return FactionAccess.hasLegacyMastery(serverPlayer, FactionBonus.NOMADS);
        }
        return CLIENT_RIDERS.contains(player.getUUID());
    }

    public static void setClientRider(UUID playerId, boolean allowed) {
        if (playerId == null) {
            return;
        }
        if (allowed) {
            CLIENT_RIDERS.add(playerId);
        } else {
            CLIENT_RIDERS.remove(playerId);
        }
    }

    private NomadRiding() {
    }
}
