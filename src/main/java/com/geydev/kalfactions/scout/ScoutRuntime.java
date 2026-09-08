package com.geydev.kalfactions.scout;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.integration.xaero.archive.ArchiveRegionDescriptor;
import com.geydev.kalfactions.integration.xaero.archive.XaeroArchiveStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

final class ScoutRuntime implements AutoCloseable {
    private static final int MAX_ACTIVE_WORK = 2;
    private static final long TICK_BUDGET_NANOS = TimeUnit.MILLISECONDS.toNanos(4);
    private final MinecraftServer server;
    private final Path stagingBase;
    private final Map<UUID, Work> work = new LinkedHashMap<>();
    private final List<Map.Entry<UUID, ScoutOrder>> discarded = new ArrayList<>();
    private int nextWork;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(MAX_ACTIVE_WORK),
            Thread.ofPlatform().daemon().name("kingdoms-scout-io").factory());

    ScoutRuntime(MinecraftServer server) {
        this.server = server;
        stagingBase = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
                .resolve("kingdoms").resolve("scout_staging");
        var known = ScoutManager.get(server).activeOrders().stream()
                .map(entry -> entry.getValue().id().toString()).collect(java.util.stream.Collectors.toSet());
        executor.execute(() -> {
            if (!Files.isDirectory(stagingBase)) {
                return;
            }
            try (var paths = Files.list(stagingBase)) {
                paths.filter(Files::isDirectory).filter(path -> !known.contains(path.getFileName().toString()))
                        .filter(path -> isOrderDirectory(path.getFileName().toString()))
                        .forEach(this::deleteStaging);
            } catch (IOException exception) {
                KalFactions.LOGGER.warn("Could not prune scout staging directories", exception);
            }
        });
    }

    List<Result> tick(ScoutManager manager, int budget) {
        List<Result> results = new ArrayList<>();
        for (var iterator = work.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            Work current = entry.getValue();
            if (manager.activeOrder(entry.getKey()).orElse(null) != current.order) {
                current.cancelled = true;
                current.gate.cancel();
                current.closeJob();
            }
            if (current.future == null) {
                if (current.cancelled) {
                    iterator.remove();
                }
                continue;
            }
            if (!current.future.isDone()) {
                continue;
            }
            if (current.phase == Phase.CLEANUP) {
                iterator.remove();
                continue;
            }
            if (!current.cancelled) {
                try {
                    List<String> names = current.future.join();
                    if (current.phase == Phase.STAGING) {
                        current.order.markScanned(names);
                        manager.markDirty();
                    } else {
                        results.add(new Result(entry.getKey(), current.order, true));
                        iterator.remove();
                        continue;
                    }
                } catch (CompletionException exception) {
                    KalFactions.LOGGER.warn("Scout order {} failed", current.order.id(), exception.getCause());
                    results.add(new Result(entry.getKey(), current.order, false));
                }
            }
            if (current.phase == Phase.STAGING && !current.cancelled && current.order.scanned()) {
                iterator.remove();
            } else {
                cleanup(current);
            }
        }

        for (var iterator = discarded.iterator(); iterator.hasNext() && work.size() < MAX_ACTIVE_WORK;) {
            var entry = iterator.next();
            if (!work.containsKey(entry.getKey())) {
                Work current = new Work(entry.getValue());
                current.cancelled = true;
                work.put(entry.getKey(), current);
                cleanup(current);
                iterator.remove();
            }
        }
        long now = System.currentTimeMillis();
        for (var entry : manager.activeOrders()) {
            if (work.size() >= MAX_ACTIVE_WORK) {
                break;
            }
            ScoutOrder order = entry.getValue();
            if (work.containsKey(entry.getKey()) || order.scanned() && !order.timeElapsed(now)
                    || results.stream().anyMatch(result -> result.factionId().equals(entry.getKey()))) {
                continue;
            }
            Work current = new Work(order);
            work.put(entry.getKey(), current);
            try {
                if (order.scanned()) {
                    startDelivery(entry.getKey(), current);
                } else {
                    ServerLevel level = server.getLevel(order.dimension());
                    if (level == null) {
                        throw new IllegalStateException("Scout dimension is unavailable: " + order.dimension());
                    }
                    current.job = new ScoutJob(order, level);
                }
            } catch (IOException | RuntimeException exception) {
                KalFactions.LOGGER.warn("Could not start scout order {}", order.id(), exception);
                results.add(new Result(entry.getKey(), order, false));
                cleanup(current);
            }
        }

        long started = System.nanoTime();
        var scheduled = new ArrayList<>(work.entrySet());
        int offset = scheduled.isEmpty() ? 0 : Math.floorMod(nextWork++, scheduled.size());
        for (int index = 0; index < scheduled.size(); index++) {
            var entry = scheduled.get((offset + index) % scheduled.size());
            Work current = entry.getValue();
            if (budget <= 0 || System.nanoTime() - started >= TICK_BUDGET_NANOS) {
                break;
            }
            if (current.cancelled || current.job == null || current.future != null) {
                continue;
            }
            try {
                budget -= current.job.tick(budget);
                if (current.job.isDone()) {
                    ScoutJob completed = current.job;
                    current.closeJob();
                    current.phase = Phase.STAGING;
                    current.future = CompletableFuture.supplyAsync(() -> {
                        try {
                            return completed.writeStaging(stagingRoot(current.order));
                        } catch (IOException exception) {
                            throw new CompletionException(exception);
                        }
                    }, executor);
                }
            } catch (RuntimeException exception) {
                KalFactions.LOGGER.warn("Could not survey scout order {}", current.order.id(), exception);
                current.closeJob();
                results.add(new Result(entry.getKey(), current.order, false));
                cleanup(current);
            }
        }
        return results;
    }

    boolean cancel(UUID factionId, ScoutOrder order) {
        Work current = work.get(factionId);
        if (current == null || current.order != order) {
            if (discarded.stream().noneMatch(entry -> entry.getValue() == order)) {
                discarded.add(Map.entry(factionId, order));
            }
            return true;
        }
        if (current.phase == Phase.DELIVERY && !current.gate.cancel()) {
            return false;
        }
        current.cancelled = true;
        current.closeJob();
        if (current.future == null) {
            cleanup(current);
        }
        return true;
    }

    private void startDelivery(UUID factionId, Work current) throws IOException {
        var location = XaeroArchiveStore.location(server, factionId, current.order.dimension().location());
        Path staging = stagingRoot(current.order);
        List<XaeroArchiveStore.IncomingRegion> incoming = new ArrayList<>();
        for (String name : current.order.stagedRegions()) {
            if (!ArchiveRegionDescriptor.isSafeName(name)) {
                throw new IOException("Unsafe scout region name");
            }
            incoming.add(new XaeroArchiveStore.IncomingRegion(name, staging.resolve(name)));
        }
        if (incoming.isEmpty()) {
            throw new IOException("Scout order contains no staged regions");
        }
        current.phase = Phase.DELIVERY;
        current.order.setDeliveryInFlight(true);
        current.future = CompletableFuture.supplyAsync(() -> {
            try {
                XaeroArchiveStore.merge(location, incoming, current.gate::commit);
                return List.of();
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }, executor);
    }

    private void cleanup(Work current) {
        current.phase = Phase.CLEANUP;
        current.future = CompletableFuture.supplyAsync(() -> {
            deleteStaging(stagingRoot(current.order));
            return List.of();
        }, executor);
    }

    private Path stagingRoot(ScoutOrder order) {
        return stagingBase.resolve(order.id().toString());
    }

    private static boolean isOrderDirectory(String name) {
        try {
            return UUID.fromString(name).toString().equals(name);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void deleteStaging(Path root) {
        if (!root.toAbsolutePath().normalize().startsWith(stagingBase) || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            KalFactions.LOGGER.warn("Could not remove scout staging directory {}", root, exception);
        }
    }

    @Override
    public void close() {
        for (Work current : work.values()) {
            current.closeJob();
            current.gate.cancel();
            current.order.setDeliveryInFlight(false);
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        work.clear();
        discarded.clear();
    }

    record Result(UUID factionId, ScoutOrder order, boolean successful) {
    }

    private enum Phase {
        STAGING, DELIVERY, CLEANUP
    }

    private static final class Work {
        private final ScoutOrder order;
        private final ScoutDeliveryGate gate = new ScoutDeliveryGate();
        private ScoutJob job;
        private CompletableFuture<List<String>> future;
        private Phase phase;
        private boolean cancelled;

        private Work(ScoutOrder order) {
            this.order = order;
        }

        private void closeJob() {
            if (job != null) {
                job.close();
                job = null;
            }
        }
    }
}
