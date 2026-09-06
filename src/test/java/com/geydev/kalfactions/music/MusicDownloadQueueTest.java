package com.geydev.kalfactions.music;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class MusicDownloadQueueTest {
    @Test
    void duplicateRequestsRemainReservedDuringReadAndTransmission() {
        MusicDownloadQueue queue = new MusicDownloadQueue(3, 100);
        var reservation = queue.reserve("track", 10);
        assertNotNull(reservation);
        assertNull(queue.reserve("track", 10));
        assertNull(queue.nextChunk());
        assertTrue(queue.complete(reservation, new byte[10]));
        assertFalse(queue.complete(reservation, new byte[10]));
        assertNull(queue.reserve("track", 10));
        assertNotNull(queue.nextChunk());
        assertEquals(0, queue.reservedBytes());
        assertNotNull(queue.reserve("track", 10));
    }

    @Test
    void pendingReadsCountAgainstBothLimits() {
        MusicDownloadQueue queue = new MusicDownloadQueue(2, 100);
        var first = queue.reserve("first", 60);
        assertNull(queue.reserve("oversize", 41));
        assertNotNull(queue.reserve("second", 1));
        assertNull(queue.reserve("third", 1));
        queue.release(first);
        assertEquals(1, queue.reservedBytes());
        assertNotNull(queue.reserve("third", 99));
        assertNull(queue.reserve("empty", 0));
    }

    @Test
    void canceledReadCannotCompleteOrReleaseNewRequestWithSameHash() {
        MusicDownloadQueue queue = new MusicDownloadQueue(2, 100);
        var canceled = queue.reserve("track", 10);
        queue.clear();
        var current = queue.reserve("track", 10);
        assertFalse(queue.complete(canceled, new byte[10]));
        queue.release(canceled);
        assertTrue(queue.contains(current));
        assertEquals(10, queue.reservedBytes());
        assertTrue(queue.complete(current, new byte[10]));
    }

    @Test
    void sendsExactBytesAndResetsChunkIndexBetweenTracks() {
        byte[] data = new byte[MusicLimits.CHUNK_SIZE * 2 + 17];
        Arrays.fill(data, (byte) 37);
        MusicDownloadQueue queue = new MusicDownloadQueue(2, data.length * 2L);
        var first = queue.reserve("first", data.length);
        var second = queue.reserve("second", 1);
        assertFalse(queue.complete(first, new byte[data.length - 1]));
        assertTrue(queue.complete(first, data));
        assertTrue(queue.complete(second, new byte[]{9}));
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        for (int index = 0; index < 3; index++) {
            var chunk = queue.nextChunk();
            assertEquals("first", chunk.hash());
            assertEquals(index, chunk.index());
            assertEquals(data.length, chunk.totalBytes());
            received.writeBytes(chunk.data());
        }
        assertArrayEquals(data, received.toByteArray());
        var next = queue.nextChunk();
        assertEquals("second", next.hash());
        assertEquals(0, next.index());
        assertArrayEquals(new byte[]{9}, next.data());
        assertEquals(0, queue.reservedBytes());
        assertNull(queue.nextChunk());
    }
}
