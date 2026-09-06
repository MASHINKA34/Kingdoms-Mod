package com.geydev.kalfactions.client;

import java.io.IOException;
import net.minecraft.util.PngInfo;

public final class EmblemImageLimits {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public static final int MAX_DIMENSION = 1024;

    public static void validate(byte[] data) throws IOException {
        if (data == null || data.length > MAX_BYTES) {
            throw new IOException("Emblem file too large");
        }
        PngInfo info = PngInfo.fromBytes(data);
        if (info.width() <= 0 || info.height() <= 0
                || info.width() > MAX_DIMENSION || info.height() > MAX_DIMENSION) {
            throw new IOException("Emblem image dimensions outside the allowed range");
        }
    }

    private EmblemImageLimits() {
    }
}
