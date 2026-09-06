package com.geydev.kalfactions.music;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

final class MusicDownloadQueue {
    private final int maxTransfers;
    private final long maxBytes;
    private final Map<String, Reservation> reserved = new HashMap<>();
    private final Deque<Pending> pending = new ArrayDeque<>();
    private long reservedBytes;
    private int offset;
    private int chunkIndex;

    MusicDownloadQueue(int maxTransfers, long maxBytes) {
        this.maxTransfers = maxTransfers;
        this.maxBytes = maxBytes;
    }

    synchronized boolean contains(String hash) {
        return reserved.containsKey(hash);
    }

    synchronized boolean contains(Reservation reservation) {
        return reserved.get(reservation.hash()) == reservation;
    }

    synchronized long reservedBytes() {
        return reservedBytes;
    }

    synchronized Reservation reserve(String hash, long bytes) {
        if (bytes <= 0L || bytes > maxBytes - reservedBytes
                || reserved.size() >= maxTransfers || reserved.containsKey(hash)) {
            return null;
        }
        Reservation reservation = new Reservation(hash, bytes);
        reserved.put(hash, reservation);
        reservedBytes += bytes;
        return reservation;
    }

    synchronized boolean complete(Reservation reservation, byte[] data) {
        if (!contains(reservation) || data.length != reservation.bytes()
                || pending.stream().anyMatch(entry -> entry.reservation() == reservation)) {
            return false;
        }
        pending.addLast(new Pending(reservation, data));
        return true;
    }

    synchronized void release(Reservation reservation) {
        if (contains(reservation)) {
            reserved.remove(reservation.hash());
            reservedBytes -= reservation.bytes();
        }
    }

    synchronized void clear() {
        reserved.clear();
        pending.clear();
        reservedBytes = 0L;
        offset = 0;
        chunkIndex = 0;
    }

    synchronized Chunk nextChunk() {
        Pending current = pending.peekFirst();
        if (current == null) {
            return null;
        }
        int length = Math.min(MusicLimits.CHUNK_SIZE, current.data().length - offset);
        byte[] bytes = new byte[length];
        System.arraycopy(current.data(), offset, bytes, 0, length);
        Chunk chunk = new Chunk(current.reservation().hash(), chunkIndex++, bytes, current.data().length);
        offset += length;
        if (offset == current.data().length) {
            pending.removeFirst();
            release(current.reservation());
            offset = 0;
            chunkIndex = 0;
        }
        return chunk;
    }

    record Reservation(String hash, long bytes) {
    }

    record Chunk(String hash, int index, byte[] data, int totalBytes) {
    }

    private record Pending(Reservation reservation, byte[] data) {
    }
}
