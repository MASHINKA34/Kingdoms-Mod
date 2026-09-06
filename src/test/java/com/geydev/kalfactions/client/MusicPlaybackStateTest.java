package com.geydev.kalfactions.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MusicPlaybackStateTest {
    @Test
    void completedSingleTrackStaysHandledWithoutRestarting() {
        MusicPlaybackState state = new MusicPlaybackState(false);
        assertFalse(state.shouldRestart(true, false));
        assertFalse(state.shouldRestart(false, true));
        assertTrue(state.completed());
        assertFalse(state.shouldRestart(false, true));
        assertFalse(state.shouldRestart(false, false));
    }

    @Test
    void bufferedEndDoesNotFinishWhileSoundIsPlaying() {
        MusicPlaybackState state = new MusicPlaybackState(false);
        assertFalse(state.shouldRestart(true, true));
        assertFalse(state.completed());
        assertFalse(state.shouldRestart(false, true));
        assertTrue(state.completed());
    }

    @Test
    void interruptedPlaybackAndLoopingTracksCanRecover() {
        MusicPlaybackState single = new MusicPlaybackState(false);
        assertTrue(single.shouldRestart(false, false));
        assertFalse(single.completed());
        MusicPlaybackState loop = new MusicPlaybackState(true);
        assertTrue(loop.shouldRestart(false, true));
        assertFalse(loop.completed());
    }
}
