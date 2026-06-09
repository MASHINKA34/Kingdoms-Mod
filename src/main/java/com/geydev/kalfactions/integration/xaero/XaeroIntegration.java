package com.geydev.kalfactions.integration.xaero;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.client.ClientClaimStore;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import xaero.common.XaeroMinimapSession;
import xaero.common.minimap.MinimapProcessor;
import xaero.common.minimap.highlight.AbstractHighlighter;
import xaero.common.minimap.highlight.DimensionHighlighterHandler;
import xaero.common.minimap.highlight.HighlighterRegistry;
import xaero.common.minimap.write.MinimapWriter;

final class XaeroIntegration {
    private static final KingdomsHighlighter HIGHLIGHTER = new KingdomsHighlighter();

    private static Field registryField;
    private static Field highlightersField;
    private static HighlighterRegistry hookedRegistry;
    private static long appliedRevision = Long.MIN_VALUE;
    private static boolean failureLogged;

    static void tick() {
        try {
            XaeroMinimapSession session = XaeroMinimapSession.getCurrentSession();
            if (session == null) {
                hookedRegistry = null;
                return;
            }
            MinimapProcessor processor = session.getMinimapProcessor();
            if (processor == null) {
                return;
            }
            MinimapWriter writer = processor.getMinimapWriter();
            if (writer == null) {
                return;
            }

            HighlighterRegistry registry = registry(writer);
            if (registry == null) {
                return;
            }
            if (registry != hookedRegistry) {
                ensureRegistered(registry);
                hookedRegistry = registry;
                appliedRevision = Long.MIN_VALUE;
            }

            long revision = ClientClaimStore.revision();
            if (revision != appliedRevision) {
                appliedRevision = revision;
                DimensionHighlighterHandler handler = writer.getDimensionHighlightHandler();
                if (handler != null) {
                    handler.requestRefresh();
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            if (!failureLogged) {
                failureLogged = true;
                KalFactions.LOGGER.warn("Could not hook Xaero claim highlighting", exception);
            }
        }
    }

    private static HighlighterRegistry registry(MinimapWriter writer) throws ReflectiveOperationException {
        if (registryField == null) {
            registryField = MinimapWriter.class.getDeclaredField("highlighterRegistry");
            registryField.setAccessible(true);
        }
        return (HighlighterRegistry) registryField.get(writer);
    }

    private static void ensureRegistered(HighlighterRegistry registry) throws ReflectiveOperationException {
        List<AbstractHighlighter> current = registry.getHighlighters();
        if (current.contains(HIGHLIGHTER)) {
            return;
        }
        if (highlightersField == null) {
            highlightersField = HighlighterRegistry.class.getDeclaredField("highlighters");
            highlightersField.setAccessible(true);
        }
        List<AbstractHighlighter> updated = new ArrayList<>(current);
        updated.add(HIGHLIGHTER);
        highlightersField.set(registry, Collections.unmodifiableList(updated));
    }

    private XaeroIntegration() {
    }
}
