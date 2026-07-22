package com.geydev.kalfactions.integration.xaero.archive;

public final class XaeroArchiveLimits {
    public static final int FORMAT_VERSION = 1;
    public static final String PROTOCOL_VERSION = "1";
    public static final String XAERO_WORLD_MAP_VERSION = "1.43.0";
    public static final String XAERO_MINIMAP_VERSION = "26.3.0";
    public static final int PART_SIZE = 24 * 1024;
    public static final int MAX_REGIONS = 2048;
    public static final long MAX_REGION_COMPRESSED_SIZE = 32L * 1024L * 1024L;
    public static final long MAX_REGION_UNCOMPRESSED_SIZE = 32L * 1024L * 1024L;
    public static final long MAX_SESSION_COMPRESSED_SIZE = 256L * 1024L * 1024L;
    public static final long MAX_SESSION_UNCOMPRESSED_SIZE = 768L * 1024L * 1024L;
    public static final int MAX_PARTS = 131_072;
    public static final int MAX_PLAYER_SESSIONS = 2;
    public static final int MAX_GLOBAL_SESSIONS = 16;
    public static final long SESSION_TIMEOUT_MILLIS = 120_000L;
    public static final int PARTS_PER_TICK = 4;
    public static final long UPLOAD_BYTES_PER_SECOND = 4L * 1024L * 1024L;
    public static final double MAX_ANCHOR_DISTANCE_SQUARED = 64.0D;

    private XaeroArchiveLimits() {
    }
}
