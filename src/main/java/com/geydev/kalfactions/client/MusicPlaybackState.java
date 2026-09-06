package com.geydev.kalfactions.client;

public final class MusicPlaybackState {
    private final boolean loop;
    private boolean completed;

    public MusicPlaybackState(boolean loop) {
        this.loop = loop;
    }

    public boolean shouldRestart(boolean active, boolean reachedEnd) {
        if (!loop && !active && reachedEnd) {
            completed = true;
        }
        return !active && !completed;
    }

    public boolean completed() {
        return completed;
    }
}
