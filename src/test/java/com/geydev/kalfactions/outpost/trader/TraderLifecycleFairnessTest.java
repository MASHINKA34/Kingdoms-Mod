package com.geydev.kalfactions.outpost.trader;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

final class TraderLifecycleFairnessTest {
    @Test
    void windowsAdvanceAndWrapWithoutStarvingTail() {
        List<Integer> first = TraderLifecycle.fairWindowIndices(700, 0, 512);
        List<Integer> second = TraderLifecycle.fairWindowIndices(700, 512, 512);

        assertEquals(512, first.size());
        assertEquals(0, first.getFirst());
        assertEquals(511, first.getLast());
        assertEquals(512, second.getFirst());
        assertEquals(323, second.getLast());
        assertEquals(List.of(3, 4, 0, 1), TraderLifecycle.fairWindowIndices(5, 3, 4));
    }
}
