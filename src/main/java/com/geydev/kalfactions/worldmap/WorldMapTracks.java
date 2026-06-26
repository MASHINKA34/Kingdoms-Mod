package com.geydev.kalfactions.worldmap;

import com.geydev.kalfactions.KalFactions;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.GlobalRailwayManager;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.station.GlobalStation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class WorldMapTracks {
    public static final int MAX_SEGMENTS = 16_384;
    public static final int MAX_STATIONS = 512;
    private static final float[] EMPTY = new float[0];
    private static final double MARGIN = 32.0;

    public record Station(String name, float x, float z) {
    }

    private WorldMapTracks() {
    }

    public static List<Station> collectStations(ResourceKey<Level> dimension, int centerX, int centerZ, int regionBlocks) {
        if (regionBlocks <= 0) {
            return List.of();
        }
        GlobalRailwayManager railways = Create.RAILWAYS;
        Map<UUID, TrackGraph> networks = railways == null ? null : railways.trackNetworks;
        if (networks == null || networks.isEmpty()) {
            return List.of();
        }
        double minX = centerX - regionBlocks / 2.0;
        double minZ = centerZ - regionBlocks / 2.0;
        double maxX = minX + regionBlocks;
        double maxZ = minZ + regionBlocks;
        List<Station> stations = new ArrayList<>();
        try {
            for (TrackGraph graph : networks.values()) {
                for (GlobalStation station : graph.getPoints(EdgePointType.STATION)) {
                    if (station == null || !dimension.equals(station.getBlockEntityDimension())) {
                        continue;
                    }
                    BlockPos pos = station.getBlockEntityPos();
                    if (pos == null || pos.getX() < minX - MARGIN || pos.getX() > maxX + MARGIN
                            || pos.getZ() < minZ - MARGIN || pos.getZ() > maxZ + MARGIN) {
                        continue;
                    }
                    String name = station.name == null ? "" : station.name;
                    stations.add(new Station(name, pos.getX() + 0.5F, pos.getZ() + 0.5F));
                    if (stations.size() >= MAX_STATIONS) {
                        return stations;
                    }
                }
            }
        } catch (RuntimeException e) {
            KalFactions.LOGGER.error("Failed to collect Create stations", e);
        }
        return stations;
    }

    public static float[] collect(ResourceKey<Level> dimension, int centerX, int centerZ, int regionBlocks) {
        if (regionBlocks <= 0) {
            return EMPTY;
        }
        GlobalRailwayManager railways = Create.RAILWAYS;
        Map<UUID, TrackGraph> networks = railways == null ? null : railways.trackNetworks;
        if (networks == null || networks.isEmpty()) {
            return EMPTY;
        }
        double minX = centerX - regionBlocks / 2.0;
        double minZ = centerZ - regionBlocks / 2.0;
        double maxX = minX + regionBlocks;
        double maxZ = minZ + regionBlocks;
        List<float[]> segments = new ArrayList<>();
        try {
            for (TrackGraph graph : networks.values()) {
                for (TrackNodeLocation location : graph.getNodes()) {
                    if (!dimension.equals(location.getDimension())) {
                        continue;
                    }
                    TrackNode node = graph.locateNode(location);
                    if (node == null) {
                        continue;
                    }
                    Map<TrackNode, TrackEdge> connections = graph.getConnectionsFrom(node);
                    if (connections == null) {
                        continue;
                    }
                    for (TrackNode other : connections.keySet()) {
                        if (other == null || node.getNetId() >= other.getNetId()) {
                            continue;
                        }
                        TrackNodeLocation otherLocation = other.getLocation();
                        if (!dimension.equals(otherLocation.getDimension())) {
                            continue;
                        }
                        Vec3 a = location.getLocation();
                        Vec3 b = otherLocation.getLocation();
                        if (outsideRegion(a, b, minX, minZ, maxX, maxZ)) {
                            continue;
                        }
                        segments.add(new float[] {(float) a.x, (float) a.z, (float) b.x, (float) b.z});
                        if (segments.size() >= MAX_SEGMENTS) {
                            return flatten(segments);
                        }
                    }
                }
            }
        } catch (RuntimeException e) {
            KalFactions.LOGGER.error("Failed to collect Create track segments", e);
            return flatten(segments);
        }
        return flatten(segments);
    }

    private static boolean outsideRegion(Vec3 a, Vec3 b, double minX, double minZ, double maxX, double maxZ) {
        return (a.x < minX - MARGIN && b.x < minX - MARGIN)
                || (a.x > maxX + MARGIN && b.x > maxX + MARGIN)
                || (a.z < minZ - MARGIN && b.z < minZ - MARGIN)
                || (a.z > maxZ + MARGIN && b.z > maxZ + MARGIN);
    }

    private static float[] flatten(List<float[]> segments) {
        float[] data = new float[segments.size() * 4];
        for (int i = 0; i < segments.size(); i++) {
            float[] segment = segments.get(i);
            System.arraycopy(segment, 0, data, i * 4, 4);
        }
        return data;
    }
}
