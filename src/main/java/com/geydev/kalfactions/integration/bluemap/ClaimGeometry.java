package com.geydev.kalfactions.integration.bluemap;

import com.geydev.kalfactions.claim.ClaimKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ClaimGeometry {
    private static final int CHUNK_SIZE = 16;

    static List<Area> trace(Set<ClaimKey> claims) {
        return traceChunkCoordinates(claims.stream()
                .map(claim -> pack(claim.x(), claim.z()))
                .toList());
    }

    static List<Area> traceChunkCoordinates(Collection<Long> packedClaims) {
        Set<Long> claimedChunks = new HashSet<>(packedClaims);

        Set<Edge> unused = new LinkedHashSet<>();
        packedClaims.stream()
                .sorted()
                .forEach(packed -> addBoundaryEdges(
                        unused,
                        claimedChunks,
                        unpackX(packed),
                        unpackZ(packed)
                ));

        Map<Point, List<Edge>> outgoing = new HashMap<>();
        for (Edge edge : unused) {
            outgoing.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge);
        }

        List<Loop> loops = new ArrayList<>();
        while (!unused.isEmpty()) {
            Edge first = unused.iterator().next();
            List<Point> points = new ArrayList<>();
            Edge current = first;
            while (true) {
                points.add(current.from());
                unused.remove(current);
                if (current.to().equals(first.from())) {
                    break;
                }
                Edge next = chooseNext(current, outgoing.getOrDefault(current.to(), List.of()), unused);
                if (next == null) {
                    points.clear();
                    break;
                }
                current = next;
            }
            points = simplify(points);
            if (points.size() >= 3) {
                loops.add(new Loop(points, signedArea(points)));
            }
        }

        List<Loop> outers = loops.stream()
                .filter(loop -> loop.signedArea() > 0.0D)
                .sorted(Comparator.comparingInt((Loop loop) -> loop.bounds().minX())
                        .thenComparingInt(loop -> loop.bounds().minZ())
                        .thenComparingDouble(loop -> -loop.signedArea()))
                .toList();
        Map<Loop, List<List<Point>>> holes = new HashMap<>();
        outers.forEach(outer -> holes.put(outer, new ArrayList<>()));

        for (Loop hole : loops) {
            if (hole.signedArea() >= 0.0D) {
                continue;
            }
            Sample sample = interiorSample(hole.points());
            Loop owner = outers.stream()
                    .filter(outer -> contains(outer.points(), sample.x(), sample.z()))
                    .min(Comparator.comparingDouble(Loop::signedArea))
                    .orElse(null);
            if (owner != null) {
                holes.get(owner).add(hole.points());
            }
        }

        return outers.stream()
                .map(outer -> new Area(outer.points(), List.copyOf(holes.get(outer))))
                .toList();
    }

    private static void addBoundaryEdges(Set<Edge> edges, Set<Long> claims, int chunkX, int chunkZ) {
        int minX = chunkX * CHUNK_SIZE;
        int minZ = chunkZ * CHUNK_SIZE;
        int maxX = minX + CHUNK_SIZE;
        int maxZ = minZ + CHUNK_SIZE;

        if (!claims.contains(pack(chunkX, chunkZ - 1))) {
            edges.add(new Edge(new Point(minX, minZ), new Point(maxX, minZ), 0));
        }
        if (!claims.contains(pack(chunkX + 1, chunkZ))) {
            edges.add(new Edge(new Point(maxX, minZ), new Point(maxX, maxZ), 1));
        }
        if (!claims.contains(pack(chunkX, chunkZ + 1))) {
            edges.add(new Edge(new Point(maxX, maxZ), new Point(minX, maxZ), 2));
        }
        if (!claims.contains(pack(chunkX - 1, chunkZ))) {
            edges.add(new Edge(new Point(minX, maxZ), new Point(minX, minZ), 3));
        }
    }

    private static long pack(int x, int z) {
        return (long) x & 0xFFFFFFFFL | ((long) z & 0xFFFFFFFFL) << 32;
    }

    private static int unpackX(long packed) {
        return (int) packed;
    }

    private static int unpackZ(long packed) {
        return (int) (packed >>> 32);
    }

    private static Edge chooseNext(Edge current, List<Edge> candidates, Set<Edge> unused) {
        Edge selected = null;
        int selectedPriority = Integer.MAX_VALUE;
        for (Edge candidate : candidates) {
            if (!unused.contains(candidate)) {
                continue;
            }
            int turn = Math.floorMod(candidate.direction() - current.direction(), 4);
            int priority = switch (turn) {
                case 1 -> 0;
                case 0 -> 1;
                case 3 -> 2;
                default -> 3;
            };
            if (priority < selectedPriority) {
                selected = candidate;
                selectedPriority = priority;
            }
        }
        return selected;
    }

    private static List<Point> simplify(List<Point> points) {
        if (points.size() < 4) {
            return points;
        }
        List<Point> simplified = new ArrayList<>();
        for (int index = 0; index < points.size(); index++) {
            Point previous = points.get(Math.floorMod(index - 1, points.size()));
            Point current = points.get(index);
            Point next = points.get((index + 1) % points.size());
            boolean collinear = (previous.x() == current.x() && current.x() == next.x())
                    || (previous.z() == current.z() && current.z() == next.z());
            if (!collinear) {
                simplified.add(current);
            }
        }
        return simplified;
    }

    private static double signedArea(List<Point> points) {
        long twiceArea = 0L;
        for (int index = 0; index < points.size(); index++) {
            Point current = points.get(index);
            Point next = points.get((index + 1) % points.size());
            twiceArea += (long) current.x() * next.z() - (long) next.x() * current.z();
        }
        return twiceArea / 2.0D;
    }

    private static Sample interiorSample(List<Point> points) {
        Point first = points.get(0);
        Point second = points.get(1);
        double dx = second.x() - first.x();
        double dz = second.z() - first.z();
        double length = Math.max(1.0D, Math.hypot(dx, dz));
        return new Sample(
                (first.x() + second.x()) * 0.5D - dz / length * 0.25D,
                (first.z() + second.z()) * 0.5D + dx / length * 0.25D
        );
    }

    private static boolean contains(List<Point> polygon, double x, double z) {
        boolean inside = false;
        for (int current = 0, previous = polygon.size() - 1; current < polygon.size(); previous = current++) {
            Point a = polygon.get(current);
            Point b = polygon.get(previous);
            boolean crosses = (a.z() > z) != (b.z() > z)
                    && x < (double) (b.x() - a.x()) * (z - a.z()) / (b.z() - a.z()) + a.x();
            if (crosses) {
                inside = !inside;
            }
        }
        return inside;
    }

    record Area(List<Point> outline, List<List<Point>> holes) {
    }

    record Point(int x, int z) {
    }

    private record Edge(Point from, Point to, int direction) {
    }

    private record Loop(List<Point> points, double signedArea) {
        Bounds bounds() {
            int minX = points.stream().mapToInt(Point::x).min().orElse(0);
            int minZ = points.stream().mapToInt(Point::z).min().orElse(0);
            return new Bounds(minX, minZ);
        }
    }

    private record Bounds(int minX, int minZ) {
    }

    private record Sample(double x, double z) {
    }

    private ClaimGeometry() {
    }
}
