package com.geydev.kalfactions.music;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public final class MusicManager extends SavedData {
    public static final String DATA_NAME = "kingdoms_music";
    public static final Factory<MusicManager> FACTORY = new Factory<>(MusicManager::new, MusicManager::load);

    private static final String TAG_TRACKS = "tracks";
    private static final String TAG_SPEAKERS = "speakers";

    private final Map<String, MusicTrack> tracks = new LinkedHashMap<>();
    private final Map<SpeakerKey, MusicSpeaker> speakers = new LinkedHashMap<>();
    private long revision;

    public static MusicManager get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static MusicManager get(ServerLevel level) {
        return get(Objects.requireNonNull(level, "level").getServer());
    }

    public synchronized long revision() {
        return revision;
    }

    public synchronized List<MusicTrack> tracks() {
        return List.copyOf(tracks.values());
    }

    public synchronized Optional<MusicTrack> track(String hash) {
        return hash == null ? Optional.empty() : Optional.ofNullable(tracks.get(hash));
    }

    public synchronized Optional<MusicTrack> trackByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return tracks.values().stream().filter(track -> track.name().equalsIgnoreCase(name)).findFirst();
    }

    public synchronized int trackCount() {
        return tracks.size();
    }

    public synchronized long totalBytes() {
        long total = 0L;
        for (MusicTrack track : tracks.values()) {
            total += track.size();
        }
        return total;
    }

    public synchronized boolean addTrack(MusicTrack track) {
        Objects.requireNonNull(track, "track");
        if (tracks.containsKey(track.hash())) {
            return false;
        }
        tracks.put(track.hash(), track);
        revision++;
        setDirty();
        return true;
    }

    public synchronized Optional<MusicTrack> removeTrack(String hash) {
        MusicTrack removed = hash == null ? null : tracks.remove(hash);
        if (removed == null) {
            return Optional.empty();
        }
        for (Map.Entry<SpeakerKey, MusicSpeaker> entry : new ArrayList<>(speakers.entrySet())) {
            if (entry.getValue().hash().equals(hash)) {
                entry.setValue(entry.getValue().withTrack("", "").withPlaying(false));
            }
        }
        revision++;
        setDirty();
        return Optional.of(removed);
    }

    public synchronized List<MusicSpeaker> speakers() {
        return List.copyOf(speakers.values());
    }

    public synchronized List<MusicSpeaker> audibleSpeakers() {
        List<MusicSpeaker> result = new ArrayList<>();
        for (MusicSpeaker speaker : speakers.values()) {
            if (speaker.audible()) {
                result.add(speaker);
            }
        }
        return result;
    }

    public synchronized Optional<MusicSpeaker> speaker(ResourceKey<Level> dimension, BlockPos pos) {
        return Optional.ofNullable(speakers.get(new SpeakerKey(dimension, pos.asLong())));
    }

    public synchronized void putSpeaker(MusicSpeaker speaker) {
        Objects.requireNonNull(speaker, "speaker");
        SpeakerKey key = new SpeakerKey(speaker.dimension(), speaker.pos().asLong());
        MusicSpeaker previous = speakers.put(key, speaker);
        if (!speaker.equals(previous)) {
            revision++;
            setDirty();
        }
    }

    public synchronized boolean removeSpeaker(ResourceKey<Level> dimension, BlockPos pos) {
        if (speakers.remove(new SpeakerKey(dimension, pos.asLong())) == null) {
            return false;
        }
        revision++;
        setDirty();
        return true;
    }

    public synchronized int stopAll() {
        int stopped = 0;
        for (Map.Entry<SpeakerKey, MusicSpeaker> entry : new ArrayList<>(speakers.entrySet())) {
            if (entry.getValue().playing()) {
                entry.setValue(entry.getValue().withPlaying(false));
                stopped++;
            }
        }
        if (stopped > 0) {
            revision++;
            setDirty();
        }
        return stopped;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag tracksTag = new ListTag();
        tracks.values().stream().map(MusicTrack::save).forEach(tracksTag::add);
        tag.put(TAG_TRACKS, tracksTag);
        ListTag speakersTag = new ListTag();
        speakers.values().stream().map(MusicSpeaker::save).forEach(speakersTag::add);
        tag.put(TAG_SPEAKERS, speakersTag);
        return tag;
    }

    static MusicManager load(CompoundTag tag, HolderLookup.Provider registries) {
        MusicManager manager = new MusicManager();
        ListTag tracksTag = tag.getList(TAG_TRACKS, Tag.TAG_COMPOUND);
        for (int index = 0; index < tracksTag.size(); index++) {
            MusicTrack track = MusicTrack.load(tracksTag.getCompound(index));
            if (!track.hash().isEmpty()) {
                manager.tracks.put(track.hash(), track);
            }
        }
        ListTag speakersTag = tag.getList(TAG_SPEAKERS, Tag.TAG_COMPOUND);
        for (int index = 0; index < speakersTag.size(); index++) {
            MusicSpeaker speaker = MusicSpeaker.load(speakersTag.getCompound(index));
            if (speaker != null) {
                manager.speakers.put(new SpeakerKey(speaker.dimension(), speaker.pos().asLong()), speaker);
            }
        }
        manager.revision = 1L;
        return manager;
    }

    private record SpeakerKey(ResourceKey<Level> dimension, long pos) {
    }
}
