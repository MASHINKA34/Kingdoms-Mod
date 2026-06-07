package com.geydev.kalfactions.integration.xaero;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.integration.IntegrationManager.FactionMapData;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.function.IntBinaryOperator;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

public final class XaeroIntegration {
    private static final String MINIMAP_MOD_ID = "xaerominimap";
    private static final String OWNER_KEY = KalFactions.MOD_ID + ":factions";
    private static final int[][] XAERO_COLORS = {
        {0x000000, 0}, {0x0000AA, 1}, {0x00AA00, 2}, {0x00AAAA, 3},
        {0xAA0000, 4}, {0xAA00AA, 5}, {0xFFAA00, 6}, {0xAAAAAA, 7},
        {0x555555, 8}, {0x5555FF, 9}, {0x55FF55, 10}, {0x55FFFF, 11},
        {0xFF5555, 12}, {0xFF55FF, 13}, {0xFFFF55, 14}, {0xFFFFFF, 15}
    };

    private static Object lastSession;
    private static long lastRevision = Long.MIN_VALUE;
    private static ResourceKey<Level> lastDimension;
    private static boolean failureLogged;

    public static boolean isAvailable() {
        return ModList.get().isLoaded(MINIMAP_MOD_ID);
    }

    public static void refresh(
            ResourceKey<Level> dimension,
            long revision,
            Collection<FactionMapData> snapshots,
            IntBinaryOperator surfaceY
    ) {
        if (!isAvailable()) {
            return;
        }
        try {
            Access access = new Access();
            Object session = access.currentSession();
            if (session == null) {
                clear();
                return;
            }
            if (session == lastSession && revision == lastRevision && dimension.equals(lastDimension)) {
                return;
            }

            Hashtable<Object, Object> waypoints = access.customWaypoints();
            waypoints.clear();
            Set<Integer> usedIds = new HashSet<>();
            for (FactionMapData snapshot : snapshots) {
                List<Set<ClaimKey>> groups = connectedGroups(snapshot.claims(), dimension);
                for (int index = 0; index < groups.size(); index++) {
                    Center center = center(groups.get(index));
                    String name = groups.size() == 1 ? snapshot.name() : snapshot.name() + " #" + (index + 1);
                    Object waypoint = access.createWaypoint(
                            center.x(),
                            surfaceY.applyAsInt(center.x(), center.z()),
                            center.z(),
                            name,
                            initials(snapshot.name()),
                            nearestColor(snapshot.color())
                    );
                    int id = stableId(snapshot, dimension, index);
                    while (!usedIds.add(id)) {
                        id++;
                    }
                    waypoints.put(id, waypoint);
                }
            }

            lastSession = session;
            lastRevision = revision;
            lastDimension = dimension;
            failureLogged = false;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            if (!failureLogged) {
                failureLogged = true;
                KalFactions.LOGGER.warn("Could not update Xaero faction waypoints", exception);
            }
        }
    }

    public static void clear() {
        if (!isAvailable()) {
            resetState();
            return;
        }
        try {
            new Access().customWaypoints().clear();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            if (!failureLogged) {
                failureLogged = true;
                KalFactions.LOGGER.warn("Could not clear Xaero faction waypoints", exception);
            }
        } finally {
            resetState();
        }
    }

    private static void resetState() {
        lastSession = null;
        lastRevision = Long.MIN_VALUE;
        lastDimension = null;
    }

    private static List<Set<ClaimKey>> connectedGroups(Set<ClaimKey> allClaims, ResourceKey<Level> dimension) {
        Set<ClaimKey> remaining = allClaims.stream()
                .filter(claim -> claim.dimension().equals(dimension))
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        List<Set<ClaimKey>> groups = new ArrayList<>();
        while (!remaining.isEmpty()) {
            ClaimKey first = remaining.iterator().next();
            Set<ClaimKey> group = new HashSet<>();
            Deque<ClaimKey> pending = new ArrayDeque<>();
            pending.add(first);
            while (!pending.isEmpty()) {
                ClaimKey current = pending.removeFirst();
                if (!remaining.remove(current)) {
                    continue;
                }
                group.add(current);
                current.cardinalNeighbors().stream()
                        .filter(remaining::contains)
                        .forEach(pending::addLast);
            }
            groups.add(group);
        }
        groups.sort(Comparator.comparingInt((Set<ClaimKey> group) -> center(group).x())
                .thenComparingInt(group -> center(group).z()));
        return groups;
    }

    private static Center center(Set<ClaimKey> claims) {
        long x = 0L;
        long z = 0L;
        for (ClaimKey claim : claims) {
            x += (long) claim.x() * 16L + 8L;
            z += (long) claim.z() * 16L + 8L;
        }
        int count = Math.max(1, claims.size());
        return new Center((int) Math.round((double) x / count), (int) Math.round((double) z / count));
    }

    private static int stableId(FactionMapData snapshot, ResourceKey<Level> dimension, int groupIndex) {
        int hash = snapshot.factionId().hashCode();
        hash = 31 * hash + dimension.location().hashCode();
        return 31 * hash + groupIndex;
    }

    private static String initials(String name) {
        StringBuilder result = new StringBuilder(2);
        name.codePoints()
                .filter(Character::isLetterOrDigit)
                .limit(2)
                .forEach(result::appendCodePoint);
        return result.isEmpty() ? "KF" : result.toString().toUpperCase(java.util.Locale.ROOT);
    }

    private static int nearestColor(int rgb) {
        int red = rgb >> 16 & 0xFF;
        int green = rgb >> 8 & 0xFF;
        int blue = rgb & 0xFF;
        int bestIndex = 15;
        long bestDistance = Long.MAX_VALUE;
        for (int[] candidate : XAERO_COLORS) {
            int value = candidate[0];
            long dr = red - (value >> 16 & 0xFF);
            long dg = green - (value >> 8 & 0xFF);
            long db = blue - (value & 0xFF);
            long distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = candidate[1];
            }
        }
        return bestIndex;
    }

    private static final class Access {
        private final Method currentSession;
        private final Method getCustomWaypoints;
        private final Constructor<?> waypointConstructor;

        private Access() throws ReflectiveOperationException {
            ClassLoader loader = XaeroIntegration.class.getClassLoader();
            Class<?> sessionClass = Class.forName("xaero.common.XaeroMinimapSession", false, loader);
            Class<?> managerClass = Class.forName(
                    "xaero.common.minimap.waypoints.WaypointsManager",
                    false,
                    loader
            );
            Class<?> waypointClass = Class.forName("xaero.common.minimap.waypoints.Waypoint", false, loader);
            currentSession = sessionClass.getMethod("getCurrentSession");
            getCustomWaypoints = managerClass.getMethod("getCustomWaypoints", String.class);
            waypointConstructor = waypointClass.getConstructor(
                    int.class,
                    int.class,
                    int.class,
                    String.class,
                    String.class,
                    int.class
            );
        }

        private Object currentSession() throws ReflectiveOperationException {
            return currentSession.invoke(null);
        }

        @SuppressWarnings("unchecked")
        private Hashtable<Object, Object> customWaypoints() throws ReflectiveOperationException {
            return (Hashtable<Object, Object>) getCustomWaypoints.invoke(null, OWNER_KEY);
        }

        private Object createWaypoint(int x, int y, int z, String name, String initials, int color)
                throws ReflectiveOperationException {
            return waypointConstructor.newInstance(x, y, z, name, initials, color);
        }
    }

    private record Center(int x, int z) {
    }

    private XaeroIntegration() {
    }
}
