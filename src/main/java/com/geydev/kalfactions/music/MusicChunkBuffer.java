package com.geydev.kalfactions.music;

public final class MusicChunkBuffer {
    private final byte[] buffer;
    private int expectedIndex;
    private int received;

    public MusicChunkBuffer(int total) {
        if (total < 0) {
            throw new IllegalArgumentException("Negative music buffer size");
        }
        this.buffer = new byte[total];
    }

    public boolean accept(int index, byte[] data) {
        if (data == null || data.length == 0 || data.length > MusicLimits.CHUNK_SIZE) {
            return false;
        }
        if (index != expectedIndex || received + data.length > buffer.length) {
            return false;
        }
        if (data.length != Math.min(MusicLimits.CHUNK_SIZE, buffer.length - received)) {
            return false;
        }
        System.arraycopy(data, 0, buffer, received, data.length);
        received += data.length;
        expectedIndex++;
        return true;
    }

    public boolean complete() {
        return buffer.length > 0 && received == buffer.length;
    }

    public int received() {
        return received;
    }

    public int total() {
        return buffer.length;
    }

    public byte[] data() {
        return buffer;
    }
}
