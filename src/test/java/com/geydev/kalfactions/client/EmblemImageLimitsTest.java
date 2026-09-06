package com.geydev.kalfactions.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class EmblemImageLimitsTest {
    @Test
    void acceptsDimensionsWithinLimit() {
        assertDoesNotThrow(() -> EmblemImageLimits.validate(header(1, 1)));
        assertDoesNotThrow(() -> EmblemImageLimits.validate(header(1024, 1024)));
    }

    @Test
    void rejectsOversizedDimensionsBeforeDecoding() {
        assertThrows(IOException.class, () -> EmblemImageLimits.validate(header(100_000, 100_000)));
        assertThrows(IOException.class, () -> EmblemImageLimits.validate(header(1025, 1)));
        assertThrows(IOException.class, () -> EmblemImageLimits.validate(header(1, 1025)));
        assertThrows(IOException.class, () -> EmblemImageLimits.validate(header(0, 1)));
        assertThrows(IOException.class, () -> EmblemImageLimits.validate(header(1, -1)));
    }

    @Test
    void rejectsMalformedAndOversizedFiles() {
        assertThrows(IOException.class, () -> EmblemImageLimits.validate(new byte[24]));
        assertThrows(IOException.class, () -> EmblemImageLimits.validate(new byte[10]));
        assertThrows(IOException.class, () -> EmblemImageLimits.validate(new byte[EmblemImageLimits.MAX_BYTES + 1]));
    }

    private static byte[] header(int width, int height) {
        return ByteBuffer.allocate(24).putLong(0x89504e470d0a1a0aL)
                .putInt(13).putInt(0x49484452).putInt(width).putInt(height).array();
    }
}
