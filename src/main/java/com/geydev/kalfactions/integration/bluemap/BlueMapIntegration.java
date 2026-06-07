package com.geydev.kalfactions.integration.bluemap;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.integration.IntegrationManager.FactionMapData;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

public final class BlueMapIntegration implements AutoCloseable {
    private static final String MOD_ID = "bluemap";
    private static final String API_CLASS = "de.bluecolored.bluemap.api.BlueMapAPI";
    private static final String MARKER_SET_ID = "kingdoms-factions";
    private static final float SHAPE_Y = 64.0F;

    private final Supplier<List<FactionMapData>> snapshotSupplier;
    private final Consumer<Object> enableListener = this::onEnabled;
    private final Consumer<Object> disableListener = ignored -> onDisabled();

    private volatile Object api;
    private boolean listenersRegistered;
    private boolean failureLogged;

    public BlueMapIntegration(Supplier<List<FactionMapData>> snapshotSupplier) {
        this.snapshotSupplier = snapshotSupplier;
    }

    public synchronized void start() {
        if (listenersRegistered || !ModList.get().isLoaded(MOD_ID)) {
            return;
        }
        try {
            Class<?> apiClass = optionalClass(API_CLASS);
            apiClass.getMethod("onEnable", Consumer.class).invoke(null, enableListener);
            apiClass.getMethod("onDisable", Consumer.class).invoke(null, disableListener);
            listenersRegistered = true;
            KalFactions.LOGGER.info("BlueMap faction markers integration registered");
        } catch (ReflectiveOperationException | LinkageError exception) {
            logFailure("Could not register the BlueMap API lifecycle", exception);
        }
    }

    public void refresh() {
        Object currentApi = api;
        if (currentApi == null) {
            return;
        }
        try {
            new Access(currentApi.getClass().getClassLoader()).replaceMarkers(currentApi, snapshotSupplier.get());
            failureLogged = false;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            logFailure("Could not update BlueMap faction markers", exception);
        }
    }

    @Override
    public synchronized void close() {
        Object currentApi = api;
        api = null;
        if (currentApi != null) {
            try {
                new Access(currentApi.getClass().getClassLoader()).removeMarkerSets(currentApi);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                logFailure("Could not remove BlueMap faction markers", exception);
            }
        }
        if (!listenersRegistered) {
            return;
        }
        try {
            Class<?> apiClass = optionalClass(API_CLASS);
            Method unregister = apiClass.getMethod("unregisterListener", Consumer.class);
            unregister.invoke(null, enableListener);
            unregister.invoke(null, disableListener);
        } catch (ReflectiveOperationException | LinkageError exception) {
            logFailure("Could not unregister the BlueMap API lifecycle", exception);
        } finally {
            listenersRegistered = false;
        }
    }

    private void onEnabled(Object enabledApi) {
        api = enabledApi;
        refresh();
    }

    private void onDisabled() {
        api = null;
    }

    private synchronized void logFailure(String message, Throwable exception) {
        if (!failureLogged) {
            failureLogged = true;
            KalFactions.LOGGER.warn("{}; BlueMap integration is disabled until the next refresh", message, exception);
        }
    }

    private static Class<?> optionalClass(String name) throws ClassNotFoundException {
        return Class.forName(name, true, BlueMapIntegration.class.getClassLoader());
    }

    private static final class Access {
        private final Class<?> markerSetClass;
        private final Class<?> shapeClass;
        private final Class<?> shapeMarkerClass;
        private final Class<?> colorClass;
        private final Class<?> vector2dClass;
        private final Method apiGetMaps;
        private final Method apiGetWorld;
        private final Method worldGetMaps;
        private final Method mapGetMarkerSets;
        private final Constructor<?> markerSetConstructor;
        private final Constructor<?> shapeConstructor;
        private final Constructor<?> shapeMarkerConstructor;
        private final Constructor<?> colorConstructor;
        private final Constructor<?> vector2dConstructor;

        private Access(ClassLoader loader) throws ReflectiveOperationException {
            Class<?> apiClass = Class.forName(API_CLASS, false, loader);
            Class<?> worldClass = Class.forName("de.bluecolored.bluemap.api.BlueMapWorld", false, loader);
            Class<?> mapClass = Class.forName("de.bluecolored.bluemap.api.BlueMapMap", false, loader);
            markerSetClass = Class.forName("de.bluecolored.bluemap.api.markers.MarkerSet", false, loader);
            shapeClass = Class.forName("de.bluecolored.bluemap.api.math.Shape", false, loader);
            shapeMarkerClass = Class.forName("de.bluecolored.bluemap.api.markers.ShapeMarker", false, loader);
            colorClass = Class.forName("de.bluecolored.bluemap.api.math.Color", false, loader);
            vector2dClass = Class.forName("com.flowpowered.math.vector.Vector2d", false, loader);
            apiGetMaps = apiClass.getMethod("getMaps");
            apiGetWorld = apiClass.getMethod("getWorld", Object.class);
            worldGetMaps = worldClass.getMethod("getMaps");
            mapGetMarkerSets = mapClass.getMethod("getMarkerSets");
            markerSetConstructor = markerSetClass.getConstructor(String.class);
            shapeConstructor = shapeClass.getConstructor(Collection.class);
            shapeMarkerConstructor = shapeMarkerClass.getConstructor(String.class, shapeClass, float.class);
            colorConstructor = colorClass.getConstructor(int.class, float.class);
            vector2dConstructor = vector2dClass.getConstructor(double.class, double.class);
        }

        private void replaceMarkers(Object api, List<FactionMapData> snapshots) throws ReflectiveOperationException {
            removeMarkerSets(api);
            Map<ResourceKey<Level>, List<FactionMapData>> byDimension = snapshots.stream()
                    .flatMap(data -> data.claims().stream().map(ClaimKey::dimension).distinct()
                            .map(dimension -> Map.entry(dimension, data)))
                    .collect(Collectors.groupingBy(Map.Entry::getKey,
                            Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

            for (Map.Entry<ResourceKey<Level>, List<FactionMapData>> entry : byDimension.entrySet()) {
                Optional<?> world = (Optional<?>) apiGetWorld.invoke(api, entry.getKey());
                if (world.isEmpty()) {
                    KalFactions.LOGGER.debug("BlueMap has no map for dimension {}", entry.getKey().location());
                    continue;
                }
                Object markerSet = createMarkerSet(entry.getKey(), entry.getValue());
                Collection<?> maps = (Collection<?>) worldGetMaps.invoke(world.get());
                for (Object map : maps) {
                    markerSets(map).put(MARKER_SET_ID, markerSet);
                }
            }
        }

        private void removeMarkerSets(Object api) throws ReflectiveOperationException {
            Collection<?> maps = (Collection<?>) apiGetMaps.invoke(api);
            for (Object map : maps) {
                markerSets(map).remove(MARKER_SET_ID);
            }
        }

        private Object createMarkerSet(ResourceKey<Level> dimension, List<FactionMapData> snapshots)
                throws ReflectiveOperationException {
            Object markerSet = markerSetConstructor.newInstance("Kingdoms - Factions");
            Map<Object, Object> markers = markers(markerSet);
            for (FactionMapData data : snapshots) {
                Set<ClaimKey> dimensionClaims = data.claims().stream()
                        .filter(claim -> claim.dimension().equals(dimension))
                        .collect(Collectors.toSet());
                List<ClaimGeometry.Area> areas = ClaimGeometry.trace(dimensionClaims);
                for (int index = 0; index < areas.size(); index++) {
                    markers.put(
                            "faction-" + data.factionId() + "-" + index,
                            createShapeMarker(data, areas.get(index))
                    );
                }
            }
            return markerSet;
        }

        private Object createShapeMarker(FactionMapData data, ClaimGeometry.Area area)
                throws ReflectiveOperationException {
            Object shape = createShape(area.outline());
            Object marker = shapeMarkerConstructor.newInstance(escapeHtml(data.name()), shape, SHAPE_Y);
            Method getHoles = shapeMarkerClass.getMethod("getHoles");
            @SuppressWarnings("unchecked")
            Collection<Object> holes = (Collection<Object>) getHoles.invoke(marker);
            for (List<ClaimGeometry.Point> hole : area.holes()) {
                holes.add(createShape(hole));
            }
            shapeMarkerClass.getMethod("setDepthTestEnabled", boolean.class).invoke(marker, false);
            shapeMarkerClass.getMethod("setLineWidth", int.class).invoke(marker, 2);
            shapeMarkerClass.getMethod("setLineColor", colorClass)
                    .invoke(marker, colorConstructor.newInstance(data.color(), 0.90F));
            shapeMarkerClass.getMethod("setFillColor", colorClass)
                    .invoke(marker, colorConstructor.newInstance(data.color(), 0.25F));
            shapeMarkerClass.getMethod("centerPosition").invoke(marker);
            return marker;
        }

        private Object createShape(List<ClaimGeometry.Point> points) throws ReflectiveOperationException {
            List<Object> vectors = new ArrayList<>(points.size());
            for (ClaimGeometry.Point point : points) {
                vectors.add(vector2dConstructor.newInstance((double) point.x(), (double) point.z()));
            }
            return shapeConstructor.newInstance(vectors);
        }

        @SuppressWarnings("unchecked")
        private Map<Object, Object> markerSets(Object map) throws ReflectiveOperationException {
            return (Map<Object, Object>) mapGetMarkerSets.invoke(map);
        }

        @SuppressWarnings("unchecked")
        private Map<Object, Object> markers(Object markerSet) throws ReflectiveOperationException {
            return (Map<Object, Object>) markerSetClass.getMethod("getMarkers").invoke(markerSet);
        }

        private static String escapeHtml(String value) {
            return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
        }
    }
}
