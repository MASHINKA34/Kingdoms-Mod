package com.geydev.kalfactions.net;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

public final class PlayerNames {
    public static String resolve(ServerPlayer viewer, UUID playerId) {
        ServerPlayer online = viewer.getServer().getPlayerList().getPlayer(playerId);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        return viewer.getServer().getProfileCache()
                .get(playerId)
                .map(profile -> profile.getName())
                .orElse(playerId.toString().substring(0, 8));
    }

    private PlayerNames() {
    }
}
