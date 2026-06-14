package com.geydev.kalfactions.protection;

import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionBonus;
import com.geydev.kalfactions.faction.FactionManager;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Narrow boundary between event handlers and the faction implementation.
 */
public final class FactionAccess {
    private static final Backend DEFAULT_BACKEND = new ManagerBackend();
    private static volatile Backend backend = DEFAULT_BACKEND;

    public static void installBackend(Backend newBackend) {
        backend = Objects.requireNonNull(newBackend, "newBackend");
    }

    public static void resetBackend() {
        backend = DEFAULT_BACKEND;
    }

    public static Optional<FactionRef> factionOf(ServerPlayer player) {
        return backend.factionOf(player);
    }

    public static Optional<FactionRef> factionAt(ServerLevel level, BlockPos pos) {
        return backend.factionAt(level, pos);
    }

    public static boolean isClaimed(ServerLevel level, BlockPos pos) {
        return factionAt(level, pos).isPresent();
    }

    public static boolean canBuild(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return player.hasPermissions(2) || backend.canBuild(player, level, pos);
    }

    public static boolean sameFaction(ServerPlayer first, ServerPlayer second) {
        Optional<FactionRef> firstFaction = factionOf(first);
        Optional<FactionRef> secondFaction = factionOf(second);
        return firstFaction.isPresent()
                && secondFaction.isPresent()
                && firstFaction.get().key().equals(secondFaction.get().key());
    }

    public static Set<FactionBonus> bonusesOf(ServerPlayer player) {
        return backend.bonusesOf(player);
    }

    public static boolean hasAnyBonus(ServerPlayer player, FactionBonus... bonuses) {
        Set<FactionBonus> owned = bonusesOf(player);
        return Arrays.stream(bonuses).anyMatch(owned::contains);
    }

    public static boolean internalPvpEnabled(ServerPlayer player) {
        return backend.internalPvpEnabled(player);
    }

    public record FactionRef(Object key, String displayName, Object value) {
        public FactionRef {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(value, "value");
        }
    }

    public interface Backend {
        Optional<FactionRef> factionOf(ServerPlayer player);

        Optional<FactionRef> factionAt(ServerLevel level, BlockPos pos);

        default boolean canBuild(ServerPlayer player, ServerLevel level, BlockPos pos) {
            Optional<FactionRef> owner = factionAt(level, pos);
            if (owner.isEmpty()) {
                return true;
            }
            return factionOf(player).map(faction -> faction.key().equals(owner.get().key())).orElse(false);
        }

        default Set<FactionBonus> bonusesOf(ServerPlayer player) {
            return Set.of();
        }

        default boolean internalPvpEnabled(ServerPlayer player) {
            return false;
        }
    }

    private static final class ManagerBackend implements Backend {
        @Override
        public Optional<FactionRef> factionOf(ServerPlayer player) {
            return FactionManager.get(player.serverLevel())
                    .getFactionForMember(player.getUUID())
                    .map(ManagerBackend::reference);
        }

        @Override
        public boolean canBuild(ServerPlayer player, ServerLevel level, BlockPos pos) {
            FactionManager manager = FactionManager.get(level);
            java.util.UUID owner = manager.getFactionIdAt(ClaimKey.of(level, pos)).orElse(null);
            if (owner == null) {
                return !com.geydev.kalfactions.outpost.RogueOutpostManager.get(level)
                        .isRogueChunk(ClaimKey.of(level, pos));
            }
            java.util.UUID playerFaction = manager.getFactionIdForMember(player.getUUID()).orElse(null);
            return owner.equals(playerFaction) || manager.areAllied(owner, playerFaction);
        }

        @Override
        public Optional<FactionRef> factionAt(ServerLevel level, BlockPos pos) {
            return FactionManager.get(level)
                    .getFactionAt(ClaimKey.of(level, pos))
                    .map(ManagerBackend::reference);
        }

        @Override
        public Set<FactionBonus> bonusesOf(ServerPlayer player) {
            return FactionManager.get(player.serverLevel())
                    .getFactionForMember(player.getUUID())
                    .map(Faction::bonuses)
                    .orElse(Set.of());
        }

        @Override
        public boolean internalPvpEnabled(ServerPlayer player) {
            return FactionManager.get(player.serverLevel())
                    .getFactionForMember(player.getUUID())
                    .map(Faction::internalPvp)
                    .orElse(false);
        }

        private static FactionRef reference(Faction faction) {
            return new FactionRef(faction.id(), faction.name(), faction);
        }
    }

    private FactionAccess() {
    }
}
