package com.geydev.kalfactions.outpost.cluster.distribution;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class FiniteResourceLedgerTest {
    @Test
    void extractionNeverExceedsRemainingReserve() {
        FiniteResourceLedger.Extraction extraction = FiniteResourceLedger.extract(12, 32);

        assertEquals(12, extraction.extracted());
        assertEquals(0, extraction.remaining());
    }

    @Test
    void repeatedExtractionCannotDuplicateReserve() {
        FiniteResourceLedger.Extraction first = FiniteResourceLedger.extract(40, 32);
        FiniteResourceLedger.Extraction second = FiniteResourceLedger.extract(first.remaining(), 32);
        FiniteResourceLedger.Extraction replay = FiniteResourceLedger.extract(second.remaining(), 32);

        assertEquals(40, first.extracted() + second.extracted() + replay.extracted());
        assertEquals(0, replay.remaining());
    }
}
