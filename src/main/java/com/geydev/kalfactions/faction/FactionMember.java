package com.geydev.kalfactions.faction;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public record FactionMember(UUID playerId, FactionRole role) {
    private static final String TAG_PLAYER = "player";
    private static final String TAG_ROLE = "role";

    public FactionMember {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(role, "role");
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_PLAYER, playerId);
        tag.putString(TAG_ROLE, role.name());
        return tag;
    }

    public static Optional<FactionMember> load(CompoundTag tag) {
        if (!tag.hasUUID(TAG_PLAYER)) {
            return Optional.empty();
        }
        try {
            FactionRole role = FactionRole.valueOf(tag.getString(TAG_ROLE).toUpperCase(Locale.ROOT));
            return Optional.of(new FactionMember(tag.getUUID(TAG_PLAYER), role));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
