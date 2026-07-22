package com.geydev.kalfactions.integration.xaero.archive;

import java.util.regex.Pattern;

public record ArchiveRegionDescriptor(
        String name,
        long compressedSize,
        long uncompressedSize,
        int tileCount,
        String checksum
) {
    private static final Pattern NAME_PATTERN = Pattern.compile("-?\\d+_-?\\d+\\.zip");

    public ArchiveRegionDescriptor {
        if (!isSafeName(name)) {
            throw new IllegalArgumentException("Invalid Xaero region name");
        }
        if (compressedSize < 0 || compressedSize > XaeroArchiveLimits.MAX_REGION_COMPRESSED_SIZE) {
            throw new IllegalArgumentException("Invalid compressed region size");
        }
        if (uncompressedSize < 0 || uncompressedSize > XaeroArchiveLimits.MAX_REGION_UNCOMPRESSED_SIZE) {
            throw new IllegalArgumentException("Invalid uncompressed region size");
        }
        if (tileCount <= 0 || tileCount > 1024) {
            throw new IllegalArgumentException("Invalid Xaero tile count");
        }
        if (!ArchiveHashing.isSha256(checksum)) {
            throw new IllegalArgumentException("Invalid region checksum");
        }
    }

    public static boolean isSafeName(String value) {
        return value != null
                && value.length() <= 48
                && NAME_PATTERN.matcher(value).matches()
                && !value.contains("..")
                && !value.contains("/")
                && !value.contains("\\");
    }
}
