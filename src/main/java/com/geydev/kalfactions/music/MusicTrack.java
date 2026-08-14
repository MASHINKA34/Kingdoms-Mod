package com.geydev.kalfactions.music;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public record MusicTrack(String hash, String name, long size, UUID uploader, String uploaderName, long uploadedAt) {
    private static final String TAG_HASH = "hash";
    private static final String TAG_NAME = "name";
    private static final String TAG_SIZE = "size";
    private static final String TAG_UPLOADER = "uploader";
    private static final String TAG_UPLOADER_NAME = "uploaderName";
    private static final String TAG_UPLOADED_AT = "uploadedAt";

    public MusicTrack {
        Objects.requireNonNull(hash, "hash");
        name = MusicLimits.sanitizeName(name);
        uploaderName = uploaderName == null ? "" : uploaderName;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_HASH, hash);
        tag.putString(TAG_NAME, name);
        tag.putLong(TAG_SIZE, size);
        if (uploader != null) {
            tag.putUUID(TAG_UPLOADER, uploader);
        }
        tag.putString(TAG_UPLOADER_NAME, uploaderName);
        tag.putLong(TAG_UPLOADED_AT, uploadedAt);
        return tag;
    }

    public static MusicTrack load(CompoundTag tag) {
        return new MusicTrack(
                tag.getString(TAG_HASH),
                tag.getString(TAG_NAME),
                tag.getLong(TAG_SIZE),
                tag.hasUUID(TAG_UPLOADER) ? tag.getUUID(TAG_UPLOADER) : null,
                tag.getString(TAG_UPLOADER_NAME),
                tag.getLong(TAG_UPLOADED_AT)
        );
    }
}
