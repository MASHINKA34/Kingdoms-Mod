package com.geydev.kalfactions.protection;

import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionBonus;
import com.geydev.kalfactions.faction.FactionManager;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
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

    public static Optional<FactionBonus> bonusOf(ServerPlayer player) {
        return backend.bonusOf(player);
    }

    public static boolean hasAnyBonus(ServerPlayer player, FactionBonus... bonuses) {
        return bonusOf(player).filter(Arrays.asList(bonuses)::contains).isPresent();
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

        default Optional<FactionBonus> bonusOf(ServerPlayer player) {
            return Optional.empty();
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
        public Optional<FactionRef> factionAt(ServerLevel level, BlockPos pos) {
            return FactionManager.get(level)
                    .getFactionAt(ClaimKey.of(level, pos))
                    .map(ManagerBackend::reference);
        }

        @Override
        public Optional<FactionBonus> bonusOf(ServerPlayer player) {
            return FactionManager.get(player.serverLevel())
                    .getFactionForMember(player.getUUID())
                    .map(Faction::bonus);
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
