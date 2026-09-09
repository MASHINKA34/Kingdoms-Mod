package com.geydev.kalfactions.charon;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class CharonGhosts {
    private static final Set<UUID> GHOSTS = ConcurrentHashMap.newKeySet();

    public static boolean isGhost(@Nullable UUID playerId) {
        return playerId != null && GHOSTS.contains(playerId);
    }

    public static boolean isGhost(@Nullable Entity entity) {
        return entity instanceof Player player && GHOSTS.contains(player.getUUID());
    }

    public static void set(UUID playerId, boolean active) {
        if (active) {
            GHOSTS.add(playerId);
        } else {
            GHOSTS.remove(playerId);
        }
    }

    public static void clear() {
        GHOSTS.clear();
    }

    private CharonGhosts() {
    }
}
