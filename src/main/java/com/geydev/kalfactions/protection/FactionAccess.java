package com.geydev.kalfactions.protection;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/**
 * Narrow boundary between event handlers and the faction implementation.
 *
 * <p>The reflection backend keeps the handlers usable while the faction module is
 * being built in parallel. The faction module should install an exact backend as
 * soon as its API is stable.</p>
 */
public final class FactionAccess {
    private static final Backend REFLECTION_BACKEND = new ReflectionBackend();
    private static final Backend MANAGER_BACKEND = new ManagerBackend();
    private static volatile Backend backend = MANAGER_BACKEND;

    public static void installBackend(Backend newBackend) {
        backend = Objects.requireNonNull(newBackend, "newBackend");
    }

    public static void resetBackend() {
        backend = MANAGER_BACKEND;
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

    public static boolean canAccess(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return player.hasPermissions(2) || backend.canAccess(player, level, pos);
    }

    public static boolean sameFaction(ServerPlayer first, ServerPlayer second) {
        Optional<FactionRef> firstFaction = factionOf(first);
        Optional<FactionRef> secondFaction = factionOf(second);
        return firstFaction.isPresent()
                && secondFaction.isPresent()
                && firstFaction.get().key().equals(secondFaction.get().key());
    }

    public static Optional<String> roleOf(ServerPlayer player) {
        return backend.roleOf(player).map(role -> role.toUpperCase(Locale.ROOT));
    }

    public static boolean hasAnyRole(ServerPlayer player, String... roles) {
        Set<String> expected = Arrays.stream(roles)
                .map(role -> role.toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return roleOf(player).filter(expected::contains).isPresent();
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

        default boolean canAccess(ServerPlayer player, ServerLevel level, BlockPos pos) {
            return canBuild(player, level, pos);
        }

        default Optional<String> roleOf(ServerPlayer player) {
            return Optional.empty();
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
        public Optional<String> roleOf(ServerPlayer player) {
            return FactionManager.get(player.serverLevel())
                    .getFactionForMember(player.getUUID())
                    .flatMap(faction -> faction.roleOf(player.getUUID()))
                    .map(Enum::name);
        }

        private static FactionRef reference(Faction faction) {
            return new FactionRef(faction.id(), faction.name(), faction);
        }
    }

    private static final class ReflectionBackend implements Backend {
        private static final List<String> MANAGER_CLASSES = List.of(
                "com.geydev.kalfactions.faction.FactionManager",
                "com.geydev.kalfactions.faction.manager.FactionManager"
        );
        private static final Set<String> MANAGER_FACTORIES = Set.of(
                "get", "getInstance", "instance", "getManager", "forLevel"
        );
        private static final Set<String> PLAYER_FACTION_METHODS = Set.of(
                "getFaction", "findFaction", "getFactionByPlayer", "getPlayerFaction"
        );
        private static final Set<String> CLAIM_FACTION_METHODS = Set.of(
                "getFactionAt", "findFactionAt", "getFactionByChunk", "getClaimOwner", "getOwnerAt"
        );
        private static final Set<String> ROLE_METHODS = Set.of(
                "getRole", "findRole", "getMemberRole", "roleOf"
        );
        private static volatile Class<?> managerClass;
        private static volatile boolean managerClassResolved;
        private static volatile boolean unsupportedApiLogged;

        @Override
        public Optional<FactionRef> factionOf(ServerPlayer player) {
            Optional<Object> manager = manager(player.serverLevel());
            if (manager.isEmpty()) {
                return Optional.empty();
            }

            Optional<Object> value = invokeNamed(
                    manager.get(),
                    PLAYER_FACTION_METHODS,
                    new Object[] { player },
                    new Object[] { player.getUUID() },
                    new Object[] { player.getGameProfile().getName() }
            );
            logUnsupportedApiIfNeeded(value);
            return value.flatMap(this::toFactionRef);
        }

        @Override
        public Optional<FactionRef> factionAt(ServerLevel level, BlockPos pos) {
            Optional<Object> manager = manager(level);
            if (manager.isEmpty()) {
                return Optional.empty();
            }

            ChunkPos chunkPos = new ChunkPos(pos);
            Optional<Object> value = invokeNamed(
                    manager.get(),
                    CLAIM_FACTION_METHODS,
                    new Object[] { level, pos },
                    new Object[] { pos },
                    new Object[] { level, chunkPos },
                    new Object[] { chunkPos },
                    new Object[] { level.dimension(), chunkPos },
                    new Object[] { chunkPos.toLong() }
            ).flatMap(this::extractFactionFromClaim);
            logUnsupportedApiIfNeeded(value);
            return value.flatMap(this::toFactionRef);
        }

        @Override
        public Optional<String> roleOf(ServerPlayer player) {
            Optional<Object> manager = manager(player.serverLevel());
            if (manager.isEmpty()) {
                return Optional.empty();
            }

            Optional<Object> directRole = invokeNamed(
                    manager.get(),
                    ROLE_METHODS,
                    new Object[] { player },
                    new Object[] { player.getUUID() }
            );
            if (directRole.isPresent()) {
                return roleName(directRole.get());
            }

            Optional<FactionRef> faction = factionOf(player);
            if (faction.isEmpty()) {
                return Optional.empty();
            }

            Optional<Object> factionRole = invokeNamed(
                    faction.get().value(),
                    ROLE_METHODS,
                    new Object[] { player },
                    new Object[] { player.getUUID() }
            );
            if (factionRole.isPresent()) {
                return roleName(factionRole.get());
            }

            Optional<Object> member = invokeNamed(
                    faction.get().value(),
                    Set.of("getMember", "findMember", "member"),
                    new Object[] { player },
                    new Object[] { player.getUUID() }
            );
            return member.flatMap(value -> invokeNamed(value, ROLE_METHODS, new Object[0]))
                    .flatMap(this::roleName);
        }

        private Optional<Object> manager(ServerLevel level) {
            Class<?> type = resolveManagerClass();
            if (type == null) {
                return Optional.empty();
            }

            Optional<Object> factoryResult = invokeNamed(
                    type,
                    MANAGER_FACTORIES,
                    new Object[] { level },
                    new Object[] { level.getServer() },
                    new Object[0]
            );
            if (factoryResult.isPresent()) {
                return factoryResult;
            }

            for (String fieldName : List.of("INSTANCE", "instance")) {
                try {
                    Field field = type.getDeclaredField(fieldName);
                    if (!Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    field.setAccessible(true);
                    return unwrap(field.get(null));
                } catch (ReflectiveOperationException ignored) {
                    // Try the next common singleton shape.
                }
            }
            return Optional.empty();
        }

        private Class<?> resolveManagerClass() {
            if (managerClassResolved) {
                return managerClass;
            }
            synchronized (ReflectionBackend.class) {
                if (managerClassResolved) {
                    return managerClass;
                }
                for (String className : MANAGER_CLASSES) {
                    try {
                        managerClass = Class.forName(className, false, FactionAccess.class.getClassLoader());
                        break;
                    } catch (ClassNotFoundException ignored) {
                        // The faction module may install an explicit backend instead.
                    }
                }
                managerClassResolved = true;
                return managerClass;
            }
        }

        private Optional<Object> extractFactionFromClaim(Object value) {
            Optional<Object> unwrapped = unwrap(value);
            if (unwrapped.isEmpty()) {
                return Optional.empty();
            }
            Object claimOrFaction = unwrapped.get();
            Optional<Object> nested = invokeNamed(
                    claimOrFaction,
                    Set.of("getFaction", "faction", "getOwner", "owner", "getFactionId"),
                    new Object[0]
            );
            return nested.isPresent() ? nested : Optional.of(claimOrFaction);
        }

        private Optional<FactionRef> toFactionRef(Object value) {
            Optional<Object> unwrapped = unwrap(value);
            if (unwrapped.isEmpty()) {
                return Optional.empty();
            }

            Object faction = unwrapped.get();
            Object key = invokeNamed(
                    faction,
                    Set.of("getId", "id", "getUuid", "getUUID", "getName", "name"),
                    new Object[0]
            ).orElse(faction);
            String name = invokeNamed(
                    faction,
                    Set.of("getDisplayName", "displayName", "getName", "name"),
                    new Object[0]
            ).map(Object::toString).orElse(key.toString());
            return Optional.of(new FactionRef(key, name, faction));
        }

        private Optional<String> roleName(Object value) {
            return unwrap(value).map(role -> role instanceof Enum<?> enumRole
                    ? enumRole.name()
                    : role.toString());
        }

        private void logUnsupportedApiIfNeeded(Optional<Object> result) {
            if (result.isPresent() || managerClass == null || unsupportedApiLogged) {
                return;
            }
            unsupportedApiLogged = true;
            KalFactions.LOGGER.warn(
                    "FactionManager was found, but no compatible query methods were detected. "
                            + "Install FactionAccess.Backend from the faction module."
            );
        }

        private static Optional<Object> invokeNamed(Object target, Set<String> names, Object[]... variants) {
            Class<?> type = target instanceof Class<?> targetClass ? targetClass : target.getClass();
            boolean requireStatic = target instanceof Class<?>;
            for (Method method : type.getMethods()) {
                if (!names.contains(method.getName()) || Modifier.isStatic(method.getModifiers()) != requireStatic) {
                    continue;
                }
                for (Object[] arguments : variants) {
                    if (!accepts(method.getParameterTypes(), arguments)) {
                        continue;
                    }
                    try {
                        return unwrap(method.invoke(requireStatic ? null : target, arguments));
                    } catch (ReflectiveOperationException | RuntimeException exception) {
                        KalFactions.LOGGER.debug("Faction adapter invocation failed: {}", method, exception);
                    }
                }
            }
            return Optional.empty();
        }

        private static boolean accepts(Class<?>[] parameters, Object[] arguments) {
            if (parameters.length != arguments.length) {
                return false;
            }
            for (int index = 0; index < parameters.length; index++) {
                Object argument = arguments[index];
                if (argument == null) {
                    if (parameters[index].isPrimitive()) {
                        return false;
                    }
                    continue;
                }
                Class<?> parameter = wrap(parameters[index]);
                if (!parameter.isInstance(argument)) {
                    return false;
                }
            }
            return true;
        }

        private static Class<?> wrap(Class<?> type) {
            if (!type.isPrimitive()) {
                return type;
            }
            if (type == boolean.class) {
                return Boolean.class;
            }
            if (type == byte.class) {
                return Byte.class;
            }
            if (type == short.class) {
                return Short.class;
            }
            if (type == int.class) {
                return Integer.class;
            }
            if (type == long.class) {
                return Long.class;
            }
            if (type == float.class) {
                return Float.class;
            }
            if (type == double.class) {
                return Double.class;
            }
            if (type == char.class) {
                return Character.class;
            }
            return type;
        }

        private static Optional<Object> unwrap(Object value) {
            Object current = value;
            while (current instanceof Optional<?> optional) {
                if (optional.isEmpty()) {
                    return Optional.empty();
                }
                current = optional.get();
            }
            return Optional.ofNullable(current);
        }
    }

    private FactionAccess() {
    }
}
