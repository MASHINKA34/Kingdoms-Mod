package com.geydev.kalfactions.integration.xaero;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.client.ClientClaimStore;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import xaero.map.MapProcessor;
import xaero.map.WorldMapSession;
import xaero.map.highlight.AbstractHighlighter;
import xaero.map.highlight.HighlighterRegistry;
import xaero.map.world.MapDimension;
import xaero.map.world.MapWorld;

final class XaeroWorldMapIntegration {
    private static final KingdomsWorldMapHighlighter HIGHLIGHTER = new KingdomsWorldMapHighlighter();

    private static Field highlightersField;
    private static HighlighterRegistry hookedRegistry;
    private static long appliedRevision = Long.MIN_VALUE;
    private static boolean failureLogged;

    static void tick() {
        try {
            WorldMapSession session = WorldMapSession.getCurrentSession();
            if (session == null) {
                hookedRegistry = null;
                return;
            }
            MapProcessor processor = session.getMapProcessor();
            if (processor == null) {
                return;
            }
            HighlighterRegistry registry = processor.getHighlighterRegistry();
            if (registry == null) {
                return;
            }
            if (registry != hookedRegistry) {
                hookedRegistry = registry;
                appliedRevision = Long.MIN_VALUE;
            }
            if (ensureRegistered(registry)) {
                appliedRevision = Long.MIN_VALUE;
            }

            long revision = ClientClaimStore.revision();
            if (revision != appliedRevision) {
                appliedRevision = revision;
                refreshHighlights(processor);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            if (!failureLogged) {
                failureLogged = true;
                KalFactions.LOGGER.warn("Could not hook Xaero world map claim highlighting", exception);
            }
        }
    }

    private static void refreshHighlights(MapProcessor processor) {
        MapWorld mapWorld = processor.getMapWorld();
        if (mapWorld == null) {
            return;
        }
        mapWorld.clearAllCachedHighlightHashes();
        List<MapDimension> dimensions = new ArrayList<>();
        mapWorld.getDimensions(dimensions);
        for (MapDimension dimension : dimensions) {
            dimension.onClearCachedHighlightHashes();
        }
    }

    private static boolean ensureRegistered(HighlighterRegistry registry) throws ReflectiveOperationException {
        List<AbstractHighlighter> current = registry.getHighlighters();
        if (current.contains(HIGHLIGHTER)) {
            return false;
        }
        if (highlightersField == null) {
            highlightersField = HighlighterRegistry.class.getDeclaredField("highlighters");
            highlightersField.setAccessible(true);
        }
        List<AbstractHighlighter> updated = new ArrayList<>(current);
        updated.add(HIGHLIGHTER);
        highlightersField.set(registry, Collections.unmodifiableList(updated));
        return true;
    }

    private XaeroWorldMapIntegration() {
    }
}
